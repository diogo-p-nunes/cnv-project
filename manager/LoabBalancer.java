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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@SuppressWarnings("Duplicates")
public class LoabBalancer {
    private InetSocketAddress address;
    private static List<Request> requestsCache = new ArrayList<>();
    public static int REQUEST_TIMEOUT = 60;

    public LoabBalancer() throws Exception {
        /*
         * Start listening for requests to redirect
         */
        final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
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
        double max = 0;
        String ip_max = "";

        while(ip_max.equals("")) {
            // Determine supposed load of each instance if it were to run this request
            Map<String,Double> loads = new HashMap<>();
            for(String ip : Manager.getAllInstancesIp()) {
                double load = Manager.getWSTotalLoad(ip);
                loads.put(ip, load + cost);
            }

            // Return the instance that maximizes the load without going over the limit
            for (String ip : loads.keySet()) {
                double load = loads.get(ip);
                if (load <= Manager.MAX_CAPACITY && load >= max && !ip.equals(except)) {
                    max = load;
                    ip_max = ip;
                }
            }

            // sleep for a while when there is no available IP
            if(ip_max.equals("")) {
                try {
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return ip_max;
    }

    public static boolean isRequestInCache(Request requestToLookFor) {
        for(Request request : requestsCache) {
            if (request.equals(requestToLookFor)){
                return true;
            }
        }
        return false;
    }

    public static void addRequestToCache(Request requestToAdd) {
        if (requestsCache.size() == 100) {
            requestsCache.remove(0);
        }
        requestsCache.add(requestToAdd);
    }

    public static double getRequestCostFromCache(Request requestToLookFor) {
        for(Request request : requestsCache) {
            if (requestToLookFor.equals(request)){
                return request.cost;
            }
        }
        return 0;
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) {
            // Get the query.
            final String query = t.getRequestURI().getQuery();
            System.out.println("\n[LB] FROM: " + t.getRemoteAddress().toString() + " QUERY: " + query);

            // Estimate request cost
            Request request = parseArgs(query);
            double cost;
            if (isRequestInCache(request)) {
                cost = getRequestCostFromCache(request);
                request.cost = cost;
                System.out.println("[LB] Request cost from cache - COST: " + cost);
            }
            else {
                cost = Request.requestCostEstimation(request);
                addRequestToCache(request);
                System.out.println("[LB] Request cost from DB - COST: " + cost);
            }

            // Get the target VM based on cost and workload
            String targetIP = getTargetInstanceIP(cost, "");
            boolean done = false;

            int failedAttemptsToGetRequestResponse = 0;

            while(!done) {
                try {
                    if (failedAttemptsToGetRequestResponse == 3 * Manager.getNumberOfInstances()) {
                        System.out.println("[LB] Requesting urgent instance launch.");
                        targetIP = Manager.urgentInstanceLaunch();
                    }
                    // Send data
                    String forwardQuery = "http://" + targetIP + ":8000/climb?" + query;
                    URL url = new URL(forwardQuery);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(REQUEST_TIMEOUT * 1000);
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
                        Manager.removeSuccessfulWSRequest(targetIP, request);
                        done = true;
                    }
                }
                catch (Exception e) {
                    System.out.println("[LB] Instance at " + targetIP + " failed.");
                    System.out.println("[LB] Trying with different WebServer.");

                    // This VM is no longer running this request due to failure
                    Manager.removeFailedWSRequest(targetIP, request);

                    // Get the target VM based on cost and workload except the one that failed
                    targetIP = getTargetInstanceIP(cost, targetIP);
                    failedAttemptsToGetRequestResponse += 1;
                }
            }
        }

    }
}
