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

public class LoadStoreStats
{
    private static double loadcount = 0;
    private static double storecount = 0;
    private static double fieldloadcount = 0;
    private static double fieldstorecount = 0;

    //private static String cnvProject = "/home/beatriz/Documents/CNV/cnv-project/metrics";
    private static String cnvProject = "$HOME/cnv-project/metrics";
    private static String metricsOutputFile = "loadStoreStats_metrics.txt";

    public static void writeMetricsToFile(double[] metrics)
    {
        /*
         * Function that receives an array of metrics, transforms into string
         * and writes them in one line
         * separated by "|", to the metrics file
         */

        String metricsString = doubleToStringArray(metrics);
        BufferedWriter outputMetrics = null;
        try
        {
            FileWriter fstream = new FileWriter(cnvProject + "/" + metricsOutputFile, true); //true tells to append data.
            outputMetrics = new BufferedWriter(fstream);
            outputMetrics.write(metricsString);
            outputMetrics.close();
            System.out.println("Stored metrics in: " + cnvProject + "/" + metricsOutputFile);
            return;
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }


    public static String doubleToStringArray(double[] in)
    {
        int size = in.length;
        String[] out = new String[size];

        for(int i = 0; i < size; i++)
        {
            out[i] = "" + in[i];
        }
        return StringUtils.join(out, "|") + "\n";
    }


    public static void doLoadStore(File in_dir, File out_dir)
    {
        String filelist[] = in_dir.list();

        for (int i = 0; i < filelist.length; i++)
        {
            String filename = filelist[i];
            if (filename.endsWith(".class"))
            {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);

                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); )
                {
                    Routine routine = (Routine) e.nextElement();

                    for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); )
                    {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode = instr.getOpcode();
                        if (opcode == InstructionTable.getfield)
                            instr.addBefore("LoadStoreStats", "LSFieldCount", new Integer(0));
                        else if (opcode == InstructionTable.putfield)
                            instr.addBefore("LoadStoreStats", "LSFieldCount", new Integer(1));
                        else
                        {
                            short instr_type = InstructionTable.InstructionTypeTable[opcode];
                            if (instr_type == InstructionTable.LOAD_INSTRUCTION)
                            {
                                instr.addBefore("LoadStoreStats", "LSCount", new Integer(0));
                            }
                            else if (instr_type == InstructionTable.STORE_INSTRUCTION)
                            {
                                instr.addBefore("LoadStoreStats", "LSCount", new Integer(1));
                            }
                        }
                    }
                }
                ci.addAfter("LoadStoreStats", "printLoadStore", "null");
                ci.write(out_filename);
            }
        }
    }


    public static synchronized void printLoadStore(String s)
    {
        double[] metrics = {fieldloadcount, fieldstorecount, loadcount, storecount};
        writeMetricsToFile(metrics);
    }

    public static synchronized void LSFieldCount(int type)
    {
        if (type == 0)
            fieldloadcount++;
        else
            fieldstorecount++;
    }

    public static synchronized void LSCount(int type)
    {
        if (type == 0)
            loadcount++;
        else
            storecount++;
    }


    public static void main(String argv[]) throws Exception
    {
        if (argv.length != 2)
        {
            System.exit(-1);
        }

        try
        {
            File in_dir = new File(argv[0]);
            File out_dir = new File(argv[1]);

            if (in_dir.isDirectory() && out_dir.isDirectory())
            {
                doLoadStore(in_dir, out_dir);
            }
            else
            {
                System.exit(-1);
            }
        }
        catch (NullPointerException e)
        {
            System.exit(-1);
        }
    }
}