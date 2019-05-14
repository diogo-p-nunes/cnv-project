//package pt.ulisboa.tecnico.cnv.server;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import database.DynamoDB;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

@SuppressWarnings("Duplicates")
public class WebServer {
	public static DynamoDB database;

	public static void main(final String[] args) throws Exception {

		database = new DynamoDB();

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/climb", new MyHandler());
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
	}

	static class MyHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {

			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query: " + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);
			}

			newArgs.add("-d");

			// Store from ArrayList into regular String[].
			final String[] args = new String[newArgs.size()];
			int i = 0;
			for(String arg: newArgs) {
				args[i] = arg;
				i++;
			}

			SolverArgumentParser ap = null;
			try {
				// Get user-provided flags.
				ap = new SolverArgumentParser(args);
			}
			catch(Exception e) {
				System.out.println(e);
				return;
			}

			System.out.println("> Finished parsing args.");

			// Create solver instance from factory.
			final Solver s = SolverFactory.getInstance().makeSolver(ap);

			// time execution of the solver
			long startTime = System.nanoTime();

			// Write figure file to disk.
			File responseFile = null;
			try {

				final BufferedImage outputImg = s.solveImage();

				final String outPath = ap.getOutputDirectory();

				final String imageName = s.toString();

				if(ap.isDebugging()) {
					System.out.println("> Image name: " + imageName);
				}

				final Path imagePathPNG = Paths.get(outPath, imageName);
				ImageIO.write(outputImg, "png", imagePathPNG.toFile());

				responseFile = imagePathPNG.toFile();

			} catch (final Exception e) {
				e.printStackTrace();
			}

			// time execution of the solver
			long endTime = System.nanoTime();
			long duration = (endTime - startTime); 
			System.out.println("> [TIMER]: " + duration + " miliseconds");

			// This is the end of the execution of the request
			// So we have to write the metrics to a file and then reset them
			// for the next request this thread might execute.
			String requestToString = requestToString(ap, s);
			Instrumentation.writeMetricsToDynamoDB(requestToString);

			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

			t.sendResponseHeaders(200, responseFile.length());

			hdrs.add("Content-Type", "image/png");
			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

			final OutputStream os = t.getResponseBody();
			Files.copy(responseFile.toPath(), os);

			os.close();
			System.out.println("> Sent response to " + t.getRemoteAddress().toString());
		}
	}

	static String requestToString(SolverArgumentParser ap, Solver s) {
		// algoritmo
		String alg = ap.getSolverStrategy().toString();
		
		// size mapa
		String area = "" + ap.getX1() * ap.getY1();

		// distancia
		String dist = "" + Math.sqrt(Math.pow(ap.getStartX() - ap.getX1(), 2)
				 	     + Math.pow(ap.getStartY() - ap.getY1(), 2) );

		return alg + "|" + area + "|" + dist;
	}

}
