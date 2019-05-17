package manager;

import java.util.ArrayList;
import java.util.List;

public class RunningInstance {

    /*
     * This class represents a running instance on the AWS.
     * This instance is ready to receive requests from the
     * load balancer at any time, given it has the capacity to
     * handle it.
     */

    private String ip = null;
    private String id = null;
    private List<Request> requests = new ArrayList<Request>();
    private long failedRequests = 0;
    private long performedRequests = 0;

    public RunningInstance(String ip, String id) {
        this.ip = ip;
        this.id = id;
    }

    public double getTotalLoad() {
        double totalLoad = 0.0;
        for(Request r : getRequests()) {
            totalLoad += r.cost;
        }
        return totalLoad;
    }

    public void addRequest(Request r) {
        requests.add(r);
    }

    public void incrementFailedRequests() {
        failedRequests++;
    }

    public void incrementPerformedRequests() {
        performedRequests++;
    }

    public void removeRequest(Request r) {
        requests.remove(r);
    }

    public String getIp() {
        return ip;
    }

    public String getId() {
        return id;
    }

    public List<Request> getRequests() {
        return requests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public long getPerformedRequests() {
        return performedRequests;
    }

    public double getAvailability() {
        if((getPerformedRequests() + getFailedRequests()) == 0) return 1;
        return ((double) getPerformedRequests()) / (getPerformedRequests() + getFailedRequests());
    }
}
