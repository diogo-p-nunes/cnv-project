package manager;

import java.util.*;

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
    static Map<String, List<Request>> wsRequests = new HashMap<>();

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

    public static void addWSRequest(String ip, Request r) {
        wsRequests.get(ip).add(r);
    }

    public static void removeWSRequest(String ip, Request r) {
        wsRequests.get(ip).remove(r);
    }

    public static void addWS(String ip) {
        wsRequests.put(ip, new ArrayList<Request>());
    }

    public double getWSTotalLoad(String ip) {
        double totalLoad = 0.0;
        for(Request r : wsRequests.get(ip)) {
            totalLoad += r.cost;
        }
        return totalLoad;
    }

    public static void main(String[] args) throws Exception {

        System.out.print("[INFO] LB and AS initialization ... ");
        init();
        autoScaler = new AutoScaler();
        loadBalancer = new LoabBalancer();
        System.out.println("complete.");
        System.out.println("[LB] Listening on " + loadBalancer.getAddress().toString());

    }
}
