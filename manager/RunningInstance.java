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
    private long consecutiveFailedRequests = 0;
    private long consecutiveTimesUnnecessary = 0;

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
        consecutiveFailedRequests++;
    }

    public void incrementPerformedRequests() {
        performedRequests++;
        consecutiveFailedRequests = 0;
        consecutiveTimesUnnecessary = 0;
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

    public String getHealthState() {
        if(consecutiveFailedRequests >= 3) {
            return "unhealthy";
        }
        else {
            return "healthy";
        }
    }

    public boolean isUnnecessary() {
        /*
         * Each system health check performed by the autoscaler
         * increments the "unnecessary" mark. This mark is only
         * reset when this instance performs a request successfully.
         * Therefore, if it never performs requests it will endlessly increment
         * the mark and will eventually be classified as unnecessary and terminated.
         */
        if(consecutiveTimesUnnecessary >= 3) {
            return true;
        }
        else {
            if(requests.size() == 0) {
                consecutiveTimesUnnecessary++;
            }
            return false;
        }
    }
}
