//package bitTools;

public class Metric {
	// class that represents the metrics obtained from each request/thread
    // (each thread handles one request)

	public long tid;
    public double dyn_new_count = 0;
    
    public Metric(long tid) {
        this.tid = tid;
        //System.out.println("> [BIT]: Created metrics for thread: " + tid);
    }

    public String toString() {
        return "" + dyn_new_count + "\n";
    }

    public void resetMetrics() {
        dyn_new_count = 0;
        //System.out.println("> [BIT]: Reset metrics for thread: " + this.tid);
    }
}