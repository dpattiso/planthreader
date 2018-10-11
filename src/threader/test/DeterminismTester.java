package threader.test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jgrapht.Graph;

import javaff.data.Action;
import javaff.data.GroundProblem;
import javaff.data.Parameter;
import javaff.data.TimeStampedPlan;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.scheduling.SchedulingException;
import threader.IterativeThreadScheduler;
import threader.ThreaderScheduler;
import threader.util.PlanThread;
import threader.util.PlanThreadGraph;

public class DeterminismTester extends ThreadTester
{
	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		File domain = new File(args[0]); 
		File pfile = new File(args[1]);
		File soln = new File(args[2]);
		
		UngroundProblem uproblem = PDDL21parser.parseFiles(domain, pfile);
		GroundProblem gproblem = uproblem.ground();
		gproblem.decompileADL();
		
		TotalOrderPlan top = null;
		try
		{
			top = SolutionParser.parse(uproblem, soln);
			
//			Set<Parameter> controllers = new HashSet<Parameter>();
//
//			//Use SAS+ method of getting controllers
//			controllers = ThreadTester.getSasControllerObjects(domain, pfile, gproblem); 

			//use PDDL way of finding controllers
//			controllers = ThreadTester.getPddlControllerObjects(uproblem, gproblem); // -- TODO this method does not work correctly as it includes "static" objects, such as locations in driverlog
			
			
			//schedule the plan using the batch based scheduler
			ThreaderScheduler batchScheduler = new ThreaderScheduler(gproblem.getSTRIPSInitialState());
			batchScheduler.getPlanThreads(top);
			PlanThreadGraph batchGraph = batchScheduler.getGraph();
			Collection<PlanThread> finalBatchThreads = batchScheduler.getLiveThreads();
			TimeStampedPlan batchPlan = batchScheduler.getScheduledPlan();

			//schedule the plan again using the iterative scheduler
			IterativeThreadScheduler iterativeScheduler = new IterativeThreadScheduler(gproblem.getSTRIPSInitialState());
			TreeSet<PlanThread> replacements =new TreeSet<PlanThread>();
			for (Action a : top.getActions())
			{
				iterativeScheduler.getPlanThread(a, replacements, gproblem.getGoal());
			}
			PlanThreadGraph iterGraph = iterativeScheduler.getGraph();
			Collection<PlanThread> finalIterativeThreads = iterativeScheduler.getLiveThreads();
			TimeStampedPlan iterPlan = iterativeScheduler.getScheduledPlan();
			
			System.out.println("Checking batch vs iterative scheduler outputs...");
			if (batchPlan.equals(iterPlan) == false)
				System.out.println("FAIL: Scheduled plans are not equal");
			else
				System.out.println("PASS: Scheduled plans are equal");
				
			if (finalBatchThreads.equals(finalIterativeThreads) == false)
				System.out.println("FAIL: Final threads not equal");
			else
				System.out.println("PASS: Final threads are equal");
			
			if (batchGraph.equals(iterGraph) == false)
				System.out.println("FAIL: Graphs are not equal");
			else
				System.out.println("PASS: Graphs are equal");
			
			
//			TimeStampedPlan tsp = threadScheduler.schedule(top);
			

			
//			File scheduled = new File(args[3]);
//
//			scheduled.delete();
//			scheduled.createNewFile();
//			
//			FileOutputStream outputStream = new FileOutputStream(scheduled);
//			PrintWriter printWriter = new PrintWriter(outputStream);
//			tsp.print(printWriter);
			
//			printWriter.close();
//			outputStream.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (ParseException e)
		{
			e.printStackTrace();
		}
		catch (SchedulingException e)
		{
			e.printStackTrace();
		}
//		catch (sas.parser.ParseException e)
//		{
//			e.printStackTrace();
//		}
//		catch (UnsolveableProblemException e)
//		{
//			e.printStackTrace();
//		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

}
