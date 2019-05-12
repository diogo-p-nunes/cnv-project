package bitTools;
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

public class AllocStats
{
    private static double newcount = 0;
    private static double newarraycount = 0;
    private static double anewarraycount = 0;
    private static double multianewarraycount = 0;

    private static String cnvProject = "/Users/diogo/cnv-project/metrics";
    private static String metricsOutputFile = "allocStats_metrics.txt";

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


    public static void doAlloc(File in_dir, File out_dir)
    {
        /*
         * Para cada instrucao do ficheiro .class, verificamos o seu OPCODE.
         * Se for uma instrucao de alocacao de memoria (qualquer uma dentro das existentes),
         * adicionamos antes da sua chamada uma invocacao a um metodo de instrumentacao que,
         * baseando-se no OPCODE obtido, incrementa o respectivo counter do tipo de instrucao.
         *
         * Resumindo, a alloc tool obtem o numero total de instrucoes de alocacao de memoria,
         * pelo seu tipo.
         * No final, imprime todos os dados obtidos para o stdout.
         *
         * O unico OVERHEAD implicado nesta instrumentacao e relativamente as instrucoes de alocacao
         * de memoria. Desta forma, se nao houver muitas deste tipo, o OVERHEAD e reduzido.
         *
         */

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
                    InstructionArray instructions = routine.getInstructionArray();

                    for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); )
                    {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode = instr.getOpcode();
                        if ((opcode == InstructionTable.NEW) ||
                                (opcode == InstructionTable.newarray) ||
                                (opcode == InstructionTable.anewarray) ||
                                (opcode == InstructionTable.multianewarray))
                        {
                            instr.addBefore("bitTools.AllocStats", "allocCount", new Integer(opcode));
                        }
                    }
                }
                ci.addAfter("bitTools.AllocStats", "printAlloc", "null");
                ci.write(out_filename);
            }
        }
    }


    public static synchronized void printAlloc(String s)
    {
        double[] metrics = {newcount, newarraycount, anewarraycount, multianewarraycount};
        writeMetricsToFile(metrics);
    }

    public static synchronized void allocCount(int type)
    {
        switch(type)
        {
        case InstructionTable.NEW:
            newcount++;
            break;
        case InstructionTable.newarray:
            newarraycount++;
            break;
        case InstructionTable.anewarray:
            anewarraycount++;
            break;
        case InstructionTable.multianewarray:
            multianewarraycount++;
            break;
        }
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
                doAlloc(in_dir, out_dir);
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
