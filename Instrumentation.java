//package bitTools;

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;

import database.DynamoDB;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;

@SuppressWarnings("Duplicates")
public class Instrumentation {

    // an hashmap with metrics for each thread, by thread ID
    private static Map<Long, Metric> metrics = new HashMap<>();
    
    // where to write the final metrics after each request
    private static String home = "/Users/diogo";
    private static String cnvProject = home + "/cnv-project/metrics";
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
            // open file in append mode to write
            FileWriter fstream = new FileWriter(cnvProject + "/" + metricsOutputFile, true);
            outputMetrics = new BufferedWriter(fstream);
            
            // write the metrics to the file in a new line
            outputMetrics.write(request + "=" + metric.toString());
            System.out.println("> [BIT]: Stored metrics (of T" + Thread.currentThread().getId() + ") in: " + cnvProject + "/" + metricsOutputFile);

            // reset the metrics for the next thread request
            metric.resetMetrics();

            // close file and return
            outputMetrics.close();
        }
        catch (Exception e) {
            System.out.println("> [BIT] EXCEPTION: " + e.getMessage());
        }
    }

    public static synchronized void writeMetricsToDynamoDB(String request) {

        Metric metric = getMetricByThread();
        String[] descriptionParts = request.split("\\|");

        String algorithm = descriptionParts[0];
        long pixelsSearchArea = Long.parseLong(descriptionParts[1]);
        double distFromStartToEnd = Double.parseDouble(descriptionParts[2]);;
        double metricResult = Double.parseDouble(metric.toString());

        try {
            DynamoDB.addItem(DynamoDB.TABLE_METRICS, algorithm, pixelsSearchArea, distFromStartToEnd, metricResult);
            System.out.println("> [BIT]: Stored metrics (of T" + Thread.currentThread().getId() + ") in database.");
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                                + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }
        catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                                + "a serious internal problem while trying to communicate with AWS, "
                                + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
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
                    /*
                    routine.addBefore("Instrumentation", "dynMethodCount", new Integer(1));
                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("Instrumentation", "dynInstrCount", new Integer(bb.size()));
                    }
                    */
                    for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); )
                    {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode = instr.getOpcode();
                        short instr_type = InstructionTable.InstructionTypeTable[opcode];
                        if (opcode == InstructionTable.NEW)
                        {
                            instr.addBefore("Instrumentation", "dynNewCount", new Integer(0));
                        }
                    }
                }
                // This instruction must be executed by the WebServer because only it knows
                // when a request has finished processing
                //ci.addAfter("Instrumentation", "writeMetricsToFile", "null");
                ci.write(out_filename);
            }
        }
    }

    public static synchronized void dynNewCount(int incr) {
        Metric m = getMetricByThread();
        m.dyn_new_count++;
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length != 2) {
            System.out.println("[BIT] Error: Run with \"java bitTools.Instrumentation indir/ outdir/\"");
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
