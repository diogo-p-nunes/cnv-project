package manager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

@SuppressWarnings("Duplicates")
public class Manager {
    static AmazonEC2 ec2;
    static String REGION = "us-east-1";
    static int MIN_INSTANCES = 2;
    static double MAX_CAPACITY = 0.95;
    static double MAX_SYSTEM_LOAD = 0.5;
    static double MIN_SYSTEM_LOAD = 0.4;
    static int SYSTEM_HEALTH_CHECK_TIME = 30; //in seconds
    static Map<String, List<Request>> wsRequests = new ConcurrentHashMap<>();
    static Map<String, String> wsIpId = new ConcurrentHashMap<>();

    private static LoabBalancer loadBalancer;
    private static AutoScaler autoScaler;

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
        wsRequests.get(ip).add(r);
    }

    public static synchronized void removeWSRequest(String ip, Request r) {
        wsRequests.get(ip).remove(r);
    }

    public static synchronized void addWS(String ip, String id) {
        if(wsRequests.containsKey(ip)) return;
        wsRequests.put(ip, new ArrayList<Request>());
        wsIpId.put(ip, id);
    }

    public static synchronized String removeWS(String ip) {
        wsRequests.remove(ip);
        String id = wsIpId.get(ip);
        wsIpId.remove(ip);
        return id;
    }

    public static double getWSTotalLoad(String ip) {
        double totalLoad = 0.0;
        for(Request r : wsRequests.get(ip)) {
            totalLoad += r.cost;
        }
        return totalLoad;
    }

    public static double getSystemTotalLoad() {
        double totalLoad = 0;
        for(String ip : Manager.wsRequests.keySet()) {
            totalLoad += Manager.getWSTotalLoad(ip);
        }
        return totalLoad;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("[INFO] LB and AS initialization ... ");
        init();
        autoScaler = new AutoScaler();
        loadBalancer = new LoabBalancer();
        System.out.println("[INFO] Complete.");
        System.out.println("[LB] Listening on " + loadBalancer.getAddress().toString());

        while(true) {
            double systemLoad = autoScaler.getSystemPercentLoad();
            System.out.printf("[INFO] System load: %.2f%%\n", systemLoad*100);
            if(systemLoad > MAX_SYSTEM_LOAD) {
                int amount = autoScaler.calculateAmountOfNeededInstances();
                autoScaler.createInstances(amount);
            }
            else if(systemLoad < MIN_SYSTEM_LOAD && wsRequests.size() > MIN_INSTANCES) {
                autoScaler.removeInstances();
            }
            Thread.sleep(SYSTEM_HEALTH_CHECK_TIME * 1000);
        }
    }
}
