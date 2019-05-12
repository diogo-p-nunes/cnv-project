package bitTools;

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;
import org.apache.commons.lang3.StringUtils;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;

public class Instrumentation {

    // an hashmap with metrics for each thread, by thread ID
    private static Map<Long, Metric> metrics = new HashMap<>();
    
    // where to write the final metrics after each request
    private static String cnvProject = "/Users/diogo/cnv-project/metrics";
    private static String metricsOutputFile = "metrics.txt";

    public static synchronized void writeMetricsToFile(String request) {
        /*
         * Function that based on the thread executing, 
         * prints its metrics to the file.
         * The input "String request" is used for when the web server 
         * statically calls this method to write the metrics to the file
         * together with the request parameters.
         */ 

        Metric metric = getMetricByThread();
        BufferedWriter outputMetrics = null;
        try {
            FileWriter fstream = new FileWriter(cnvProject + "/" + metricsOutputFile, true); //true tells to append data.
            outputMetrics = new BufferedWriter(fstream);
            outputMetrics.write(metric.toString());
            outputMetrics.close();
            metric.resetMetrics();
            System.out.println("> [BIT]: Stored metrics in: " + cnvProject + "/" + metricsOutputFile);
            return;
        }
        catch (Exception e) {
            System.out.println("> [BIT]: " + e.getMessage());
        }
    }


    public static synchronized Metric getMetricByThread() {
        /*
         * Function that gets the threads metrics and returns them.
         * If the thread does not have metrics associated with it,
         * creates it.
         */
        long tid = Thread.currentThread().getId();
        Metric m = metrics.get(tid);
        if(m == null) {
            m = new Metric(tid);
            metrics.put(tid, m);
        }
        return m;
    }


    public static void doInstrumentation(File in_dir, File out_dir) {
        /*
         * Obtem dados similares aos da static tool, sendo que a diferenca esta no facto de estes serem
         * obtidos dinamicamente. Isto significa que a static tool iria considerar (por exemplo) todos os BB
         * do ficheiro .class, indiferentemente do facto de esse BB ser executado ou nao. A dynamic tool adiciona
         * codigo de instrumentacao ANTES da chamada de (por exemplo) cada metodo para incrementar o numero de method
         * calls dinamicamente. Ora, se um metodo nunca e executado aquando da execucao do ficheiro .class, entao
         * o codigo de instrumentacao tambem nao sera, e o contador nao ira contabilizar - que 3 exatamente
         * o comportamento pretendido para esta ferramenta.
         *
         *
         * A dynamic tool tem a particularidade (em comparacao com a static tool) de no final de todos os metodos
         * (ou routines) adicionar uma invocacao a um metodo de instrumentacao que imprime os resultados de analise
         * para o stdout.
         */

        String filelist[] = in_dir.list();

        for (int i = 0; i < filelist.length; i++) {
            String filename = filelist[i];
            if (filename.endsWith(".class")) {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                    routine.addBefore("bitTools.Instrumentation", "dynMethodCount", new Integer(1));

                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("bitTools.Instrumentation", "dynInstrCount", new Integer(bb.size()));
                    }
                }
                // This instruction must be executed by the WebServer because only it knows
                // when a request has finished processing
                //ci.addAfter("Instrumentation", "writeMetricsToFile", "null");
                ci.write(out_filename);
            }
        }
    }

    public static synchronized void dynInstrCount(int incr) {
        Metric m = getMetricByThread();
        m.dyn_instr_count += incr;
        m.dyn_bb_count++;
    }

    public static synchronized void dynMethodCount(int incr) {
        Metric m = getMetricByThread();
        m.dyn_method_count++;
    }


    public static void main(String argv[]) throws Exception {
        if (argv.length != 2) {
            System.out.println("ERROR: Run with \"java bitTools.Instrumentation indir/ outdir/\"");
            System.exit(-1);
        }

        try {
            File in_dir = new File(argv[0]);
            File out_dir = new File(argv[1]);

            if (in_dir.isDirectory() && out_dir.isDirectory()) {
                System.out.println("> [BIT]: Instrumenting classes ...");
                doInstrumentation(in_dir, out_dir);
                System.out.println("> [BIT]: Done.");
            } else {
                System.exit(-1);
            }
        } catch (NullPointerException e) {
            System.exit(-1);
        }
    } 
}
