package manager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Executors;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

@SuppressWarnings("Duplicates")
public class Manager {
    private static AmazonEC2 ec2;
    private static AmazonCloudWatch cw;
    private static String REGION = "us-east-1";
    private static Map<String, Request> wsRequest = new HashMap<>();

    private static void init() throws AmazonClientException {

        AWSCredentials credentials;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        }
        catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard()
                                    .withRegion(REGION)
                                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                    .build();

        cw = AmazonCloudWatchClientBuilder.standard()
                                          .withRegion(REGION)
                                          .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                          .build();
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
                    wsRequest.put(targetIP, request);

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
                        hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

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
                        wsRequest.remove(targetIP);
                        done = true;
                    }
                }
                catch (Exception e) {
                    System.out.println("[LB] WebServer " + t.getRemoteAddress().toString() + " failed.");
                    System.out.println("[LB] Trying with different WebServer.");

                    // This VM is no longer running this request due to failure
                    wsRequest.remove(targetIP);

                    // Get the target VM based on cost and workload except the one that failed
                    targetIP = getTargetInstanceIP(cost, targetIP);
                }
            }
        }

    }

    public static Request parseArgs(String query) {
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

    public static String getTargetInstanceIP(double cost, String except) {
        Map<String, Double> cpuUsage = getInstancesRunningCPUAverage();

        //TODO: create some algorithm to choose the right target instance
        // For now it gets the one with minimum CPU usage
        double min = Double.MAX_VALUE;
        String ip = cpuUsage.keySet().iterator().next();
        for(String key : cpuUsage.keySet()) {
            double usage = cpuUsage.get(key);
            if(usage < min) {
                min = usage;
                ip = key;
            }
        }
        return ip;
    }

    public static Map<String, Double> getInstancesRunningCPUAverage() {
        Set<Instance> instances = getInstances();
        Map<String, Double> res = new HashMap<>();

        // CLOUD WATCH OFFSET
        long offsetInMilliseconds = 1000 * 600;
        Dimension instanceDimension = new Dimension();
        instanceDimension.setName("InstanceId");
        List<Dimension> dims = new ArrayList<Dimension>();
        dims.add(instanceDimension);

        System.out.println("[INFO] You have " + instances.size() + " instances.");

        int instCount = 0;
        for(Instance instance : instances) {
            String ip = instance.getPublicIpAddress();
            String name = instance.getInstanceId();
            String state = instance.getState().getName();

            if (state.equals("running")) {
                instCount++;
                instanceDimension.setValue(name);

                GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                        .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                        .withNamespace("AWS/EC2")
                        .withPeriod(60)
                        .withMetricName("CPUUtilization")
                        .withStatistics("Average")
                        .withDimensions(instanceDimension)
                        .withEndTime(new Date());

                GetMetricStatisticsResult getMetricStatisticsResult = cw.getMetricStatistics(request);

                List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                double cpuUsageAvg = 0;
                int count = 0;
                for (Datapoint dp : datapoints) {
                    cpuUsageAvg += dp.getAverage();
                    count++;
                }

                res.put(instance.getPublicIpAddress(), cpuUsageAvg/count);
                System.out.println("[INFO] CPU AVG = " + ip + " : " + cpuUsageAvg/count);
            }
        }
        System.out.println("[INFO] You have " + instCount + "/" + instances.size() + " instances running.");
        return res;
    }

    public static Set<Instance> getInstances() {
        /*
         * Get info about instances
         */
        Set<Instance> instances = null;

        try {
            // GET ALL INSTANCES
            DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
            List<Reservation> reservations = describeInstancesRequest.getReservations();
            instances = new HashSet<>();
            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }

        return instances;
    }

    public static void main(String[] args) throws Exception {

        System.out.print("[INFO] LB and AS init ... ");
        init();
        System.out.println("complete.");

        /*
         * Start listening for requests to redirect
         */
        final HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
        server.createContext("/climb", new MyHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[LB] Listening on " + server.getAddress().toString());
    }
}
