package manager;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

@SuppressWarnings("Duplicates")
public class LoabBalancer {
    private InetSocketAddress address;

    public LoabBalancer() throws Exception {
        /*
         * Start listening for requests to redirect
         */
        final HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
        server.createContext("/climb", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        address = server.getAddress();
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    private static Request parseArgs(String query) {
        // Break it down into String[].
        final String[] params = query.split("&");

        // Store as if it was a direct call to SolverMain.
        final ArrayList<String> newArgs = new ArrayList<>();
        for (final String p : params) {
            final String[] splitParam = p.split("=");
            newArgs.add("-" + splitParam[0]);
            newArgs.add(splitParam[1]);
        }

        // Store from ArrayList into regular String[].
        final String[] args = new String[newArgs.size()];
        int i = 0;
        for(String arg: newArgs) {
            args[i] = arg;
            i++;
        }

        // Get user-provided flags.
        SolverArgumentParser ap = new SolverArgumentParser(args);
        return new Request(ap.getSolverStrategy().toString(), ap.getX1()*ap.getY1(),
                Math.sqrt(Math.pow(ap.getStartX() - ap.getX1(), 2) + Math.pow(ap.getStartY() - ap.getY1(), 2) ));
    }

    private static String getTargetInstanceIP(double cost, String except) {
        // Determine supposed capacity of each instance if it were to run this request
        Map<String,Double> capacities = new HashMap<>();
        for(String ip : Manager.getAllInstancesIp()) {
            double capacity = Manager.getWSTotalLoad(ip);
            capacities.put(ip, capacity+cost);
        }

        // Return the instance that maximizes the capacity without going over the limit
        double max = capacities.values().iterator().next();
        String ip_max = capacities.keySet().iterator().next();
        for(String ip : capacities.keySet()) {
            double cap = capacities.get(ip);
            if(cap <= Manager.MAX_CAPACITY && cap >= max) {
                max = cap;
                ip_max = ip;
            }
        }

        return ip_max;
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) {
            // Get the query.
            final String query = t.getRequestURI().getQuery();
            System.out.println("\n[LB] FROM: " + t.getRemoteAddress().toString() + " QUERY: " + query);

            // Estimate request cost
            Request request = parseArgs(query);
            double cost = Request.requestCostEstimation(request);

            // Get the target VM based on cost and workload
            String targetIP = getTargetInstanceIP(cost, null);
            boolean done = false;

            while(!done) {
                try {
                    // Send data
                    String forwardQuery = "http://" + targetIP + ":8000/climb?" + query;
                    URL url = new URL(forwardQuery);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    System.out.println("[LB] Forwarded to " + targetIP);

                    // Keep tabs on who is running which request for AutoScaling algorithm
                    Manager.addWSRequest(targetIP, request);

                    // Get data from Web Server and send to client
                    int status = conn.getResponseCode();
                    if (status == HttpURLConnection.HTTP_OK) {
                        System.out.println("[LB] Received answer from " + targetIP);

                        final Headers hdrs = t.getResponseHeaders();
                        t.sendResponseHeaders(200, 0);

                        hdrs.add("Content-Type", "image/png");
                        hdrs.add("Access-Control-Allow-Origin", "*");
                        hdrs.add("Access-Control-Allow-Credentials", "true");
                        hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
                        hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, " +
                                    "Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

                        InputStream in  = conn.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        final OutputStream lbos = t.getResponseBody();

                        byte[] b = new byte[2048];
                        int length;
                        while ((length = in.read(b)) != -1) {
                            lbos.write(b, 0, length);
                        }

                        in.close();
                        lbos.close();
                        reader.close();

                        System.out.println("[LB] Sent response back to " + t.getRemoteAddress().toString() + "\n");

                        // This VM has finished this request
                        Manager.removeWSRequest(targetIP, request);
                        done = true;
                    }
                }
                catch (Exception e) {
                    System.out.println("[LB] WebServer " + t.getRemoteAddress().toString() + " failed.");
                    System.out.println("[LB] Trying with different WebServer.");

                    // This VM is no longer running this request due to failure
                    Manager.removeWSRequest(targetIP, request);

                    // Get the target VM based on cost and workload except the one that failed
                    targetIP = getTargetInstanceIP(cost, targetIP);
                }
            }
        }

    }
}
