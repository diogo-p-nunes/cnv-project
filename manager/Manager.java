package manager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import database.DynamoDB;

@SuppressWarnings("Duplicates")
public class Manager {
    static AmazonEC2 ec2;
    private static AutoScaler autoScaler;
    public static String REGION = "us-east-1";
    static int MIN_INSTANCES = 2;
    static double MAX_CAPACITY = 1;
    static double MAX_SYSTEM_LOAD = 0.8;
    private static int SYSTEM_HEALTH_CHECK_TIME = 30; //in seconds
    private static Map<String, RunningInstance> wsRequests = new ConcurrentHashMap<>();

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
    }

    public static synchronized void addWSRequest(String ip, Request r) {
        wsRequests.get(ip).addRequest(r);
    }

    private static synchronized void removeWSRequest(String ip, Request r) {
        wsRequests.get(ip).removeRequest(r);
    }

    public static void removeSuccessfulWSRequest(String ip, Request request) {
        removeWSRequest(ip, request);
        wsRequests.get(ip).incrementPerformedRequests();
    }

    public static void removeFailedWSRequest(String ip, Request request) {
        removeWSRequest(ip, request);
        wsRequests.get(ip).incrementFailedRequests();
    }

    public static synchronized void addWS(String ip, String id) {
        if(wsRequests.containsKey(ip)) return;
        wsRequests.put(ip, new RunningInstance(ip, id));
    }

    public static synchronized String removeWS(String ip) {
        String id = wsRequests.get(ip).getId();
        wsRequests.remove(ip);
        return id;
    }

    public static Collection<RunningInstance> getRunningInstances() {
        return wsRequests.values();
    }

    public static double getWSTotalLoad(String ip) {
        return wsRequests.get(ip).getTotalLoad();
    }

    public static double getSystemTotalLoad() {
        double totalLoad = 0;
        for(String ip : Manager.wsRequests.keySet()) {
            totalLoad += Manager.getWSTotalLoad(ip);
        }
        return totalLoad;
    }

    public static double getSystemMaxCapacity() {
        return getNumberOfInstances() * MAX_CAPACITY;
    }

    public static int getNumberOfInstances() {
        return wsRequests.size();
    }

    public static Set<String> getAllInstancesIp() {
        return wsRequests.keySet();
    }

    public static int getInstanceNumberOfRunningRequests(String ip) {
        return wsRequests.get(ip).getRequests().size();
    }

    private static void printSystemReport() {
        // Print the system report - each instance state and running requests
        System.out.println();
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("\t\tSYSTEM REPORT");
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf(" n-instances: %d\t\t   sys-load: %.2f%%\n",
                getNumberOfInstances(), getSystemTotalLoad()/getSystemMaxCapacity() * 100);
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("%9s %26s %20s %10s %20s\n", "INSTANCE", "PUBLIC IP", "RUNNING REQS", "LOAD", "AVAILABILITY");
        System.out.println("-----------------------------------------------------------------------------------------");
        for(RunningInstance ws : wsRequests.values()) {
            System.out.format("%20s %19s %5d %21.2f%% %13.2f%%\n",
                    ws.getId(), ws.getIp(), ws.getRequests().size(), (ws.getTotalLoad()/1)*100, ws.getAvailability()*100);
        }
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        /*
         * Launch the whole system in this specified order.
         * Only then is it ready to accept requests.
         */

        System.out.println("[INFO] DB, LB and AS initialization ... ");
        init();
        DynamoDB database = new DynamoDB();
        autoScaler = new AutoScaler();
        LoabBalancer loadBalancer = new LoabBalancer();
        System.out.println("[INFO] Initialization complete.\n");
        System.out.println("[LB] Listening on " + loadBalancer.getAddress().toString());

        /*
         * Continuously have the auto scaler perform health checks
         * on the whole system in order to scale up or down if necessary.
         * It is also important to check if an instance is continuously failing
         * to answer to requests - if so, it has to be terminated.
         */
        long printReport = 0;
        while(true) {
            Thread.sleep(SYSTEM_HEALTH_CHECK_TIME * 1000);
            autoScaler.performSystemHealthCheck();

            if(printReport % 5 == 0) {
                // Every 5 system health checks print the system report for logging purposes
                printSystemReport();
            }
            printReport++;
        }
    }

    public static String urgentInstanceLaunch() throws InterruptedException {
        return autoScaler.urgentInstanceLaunch();
    }
}
