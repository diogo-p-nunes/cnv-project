package loadbalancer;

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

@SuppressWarnings("Duplicates")
public class LoadBalancer {
    private static AmazonEC2 ec2;
    static AmazonCloudWatch cloudWatch;

    private static void init() throws AmazonClientException {
        String REGION = "us-east-1";

        AWSCredentials credentials;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
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

        cloudWatch = AmazonCloudWatchClientBuilder.standard()
                                                  .withRegion(REGION)
                                                  .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                                  .build();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) {
            // Get the query.
            final String query = t.getRequestURI().getQuery();
            System.out.println("[INFO] " + t.getRemoteAddress().toString() + " QUERY: " + query);

            String targetIP = getTargetInstanceIP();
            String forwardQuery = "http://" + targetIP + ":8000/climb?" + query;

            try {
                // Send data
                URL url = new URL(forwardQuery);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                System.out.println("[INFO] Forwarded to " + targetIP);

                // Get data from Web Server and send to client
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) {
                    System.out.println("[INFO] Received answer from " + targetIP);

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

                    System.out.println("[INFO] Sent response to " + t.getRemoteAddress().toString());
                    System.out.println();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

    public static String getTargetInstanceIP() {
        Set<Instance> instances = getInstances();

        //TODO: create some algorithm to choose the right target instance
        return instances.iterator().next().getPublicIpAddress();
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

            // CLOUD WATCH OFFSET
            long offsetInMilliseconds = 1000 * 600;
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);


            System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");
            for(Instance instance : instances) {
                String name = instance.getInstanceId();
                String state = instance.getState().getName();

                if (state.equals("running")) {
                    instanceDimension.setValue(name);

                    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                            .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                            .withNamespace("AWS/EC2")
                            .withPeriod(60)
                            .withMetricName("CPUUtilization")
                            .withStatistics("Average")
                            .withDimensions(instanceDimension)
                            .withEndTime(new Date());

                    GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);

                    List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                    double cpuUsageAvg = 0;
                    int count = 0;
                    for (Datapoint dp : datapoints) {
                        cpuUsageAvg += dp.getAverage();
                        count++;
                    }
                    System.out.println("CPU AVG = Instance " + name + " : " + cpuUsageAvg);
                }
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

        System.out.print("[INFO] Load balancer init ... ");
        init();
        System.out.println("complete.");

        /*
         * Start listening for requests to redirect
         */
        final HttpServer server = HttpServer.create(new InetSocketAddress(80), 0);
        server.createContext("/climb", new MyHandler());
        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.print("[INFO] Listening on ");
        System.out.println(server.getAddress().toString());
    }
}
