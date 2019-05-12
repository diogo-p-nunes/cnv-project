package bitTools;

public class Metric {
	// class that represents the metrics obtained from each request/thread
    // (each thread handles one request)

	static public long tid;
    static public double dyn_method_count = 0;
    static public double dyn_bb_count = 0;
    static public double dyn_instr_count = 0;
    
    public Metric(long tid) {
        this.tid = tid;
        System.out.println("> [BIT]: Created metrics for thread: " + tid);
    }

    public String toString() {
        float instr_per_bb = (float) dyn_instr_count / (float) dyn_bb_count;
        float instr_per_method = (float) dyn_instr_count / (float) dyn_method_count;
        float bb_per_method = (float) dyn_bb_count / (float) dyn_method_count;

        return "" + dyn_method_count + "|" + dyn_bb_count + "|" + dyn_instr_count + "|"
                  + instr_per_bb + "|" + instr_per_method + "|" + bb_per_method + "\n";
    }

    public static void resetMetrics() {
        dyn_method_count = 0;
        dyn_bb_count = 0;
        dyn_instr_count = 0;
        System.out.println("> [BIT]: Reset metrics for thread: " + tid);
    }
}