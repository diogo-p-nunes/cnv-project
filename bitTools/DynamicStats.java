//
// StatisticsTool.java
//
// This program measures and instruments to obtain different statistics
// about Java programs.
//
// Copyright (c) 1998 by Han B. Lee (hanlee@cs.colorado.edu).
// ALL RIGHTS RESERVED.
//
// Permission to use, copy, modify, and distribute this software and its
// documentation for non-commercial purposes is hereby granted provided 
// that this copyright notice appears in all copies.
// 
// This software is provided "as is".  The licensor makes no warrenties, either
// expressed or implied, about its correctness or performance.  The licensor
// shall not be liable for any damages suffered as a result of using
// and modifying this software.

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;
import org.apache.commons.lang3.StringUtils;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class DynamicStats {
    private static double dyn_method_count = 0;
    private static double dyn_bb_count = 0;
    private static double dyn_instr_count = 0;

    private static String cnvProject = "$HOME/cnv-project/metrics";
    private static String metricsOutputFile = "dynamicStats_metrics.txt";

    public static void writeMetricsToFile(double[] metrics) {
        /*
         * Function that receives an array of metrics, transforms into string
         * and writes them in one line
         * separated by "|", to the metrics file
         */ 

        String metricsString = doubleToStringArray(metrics);
        BufferedWriter outputMetrics = null;
        try {
            FileWriter fstream = new FileWriter(cnvProject + "/" + metricsOutputFile, true); //true tells to append data.
            outputMetrics = new BufferedWriter(fstream);
            outputMetrics.write(metricsString);
            outputMetrics.close();
            System.out.println("Stored metrics in: " + cnvProject + "/" + metricsOutputFile);
            return;
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    public static String doubleToStringArray(double[] in) {
        int size = in.length;
        String[] out = new String[size];

        for(int i=0; i<size; i++) {
            out[i] = "" + in[i];
        }
        return StringUtils.join(out, "|") + "\n";
    }


    public static void doDynamic(File in_dir, File out_dir) {
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
                    routine.addBefore("DynamicStats", "dynMethodCount", new Integer(1));

                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("DynamicStats", "dynInstrCount", new Integer(bb.size()));
                    }
                }
                ci.addAfter("DynamicStats", "printDynamic", "null");
                ci.write(out_filename);
            }
        }
    }

    public static synchronized void printDynamic(String foo) {
    
        if (dyn_method_count == 0) {
            double[] metrics = { dyn_method_count, dyn_bb_count, dyn_instr_count};
            writeMetricsToFile(metrics);
            return;
        }

        float instr_per_bb = (float) dyn_instr_count / (float) dyn_bb_count;
        float instr_per_method = (float) dyn_instr_count / (float) dyn_method_count;
        float bb_per_method = (float) dyn_bb_count / (float) dyn_method_count;

        double[] metrics = { dyn_method_count, dyn_bb_count, dyn_instr_count, 
                             instr_per_bb, instr_per_method, bb_per_method};
        writeMetricsToFile(metrics);
    }


    public static synchronized void dynInstrCount(int incr) {
        dyn_instr_count += incr;
        dyn_bb_count++;
    }

    public static synchronized void dynMethodCount(int incr) {
        dyn_method_count++;
    }


    public static void main(String argv[]) throws Exception {
        if (argv.length != 2) {
            System.exit(-1);
        }

        try {
            File in_dir = new File(argv[0]);
            File out_dir = new File(argv[1]);

            if (in_dir.isDirectory() && out_dir.isDirectory()) {
                doDynamic(in_dir, out_dir);
            } else {
                System.exit(-1);
            }
        } catch (NullPointerException e) {
            System.exit(-1);
        }
    } 
}
