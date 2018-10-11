package threader.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import threader.IterativeThreadScheduler;
import threader.ThreaderScheduler;
import threader.util.PlanThread;

import javaff.data.Action;
import javaff.data.GroundProblem;
import javaff.data.Parameter;
import javaff.data.TimeStampedAction;
import javaff.data.TimeStampedPlan;
import javaff.data.TotalOrderPlan;
import javaff.data.UngroundProblem;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.scheduling.STRIPSScheduler;
import javaff.scheduling.SchedulingException;
import javaff.test.SchedulerTester;

public class IterativeThreadTester extends ThreadTester
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
			
			Set<Parameter> controllers = new HashSet<Parameter>();

			//Use SAS+ method of getting controllers
		//	controllers = getSasControllerObjects(domain, pfile, gproblem); 

			//use PDDL way of finding controllers
//			controllers = ThreadTester.getPddlControllerObjects(uproblem, gproblem);

			
			int count = 0;
			IterativeThreadScheduler threadScheduler = new IterativeThreadScheduler(gproblem.getSTRIPSInitialState());
			for (Action a : top.getActions())
			{
				System.out.println("Threading action "+a);
				TreeSet<PlanThread> replacements = new TreeSet<PlanThread>();
				PlanThread thread = threadScheduler.getPlanThread(a, replacements, gproblem.getGoal());
				++count;
			}
			
//			threadScheduler.getGraph().generateDotGraph(new File("/tmp/it.dot"));
			
			
//			TimeStampedPlan tsp = threadScheduler.schedule(top);
			TimeStampedPlan tsp = threadScheduler.getScheduledPlan();
			
			File scheduled = new File(args[3]);

			scheduled.delete();
			scheduled.createNewFile();
			
			FileOutputStream outputStream = new FileOutputStream(scheduled);
			PrintWriter printWriter = new PrintWriter(outputStream);
			tsp.print(printWriter);
			
			printWriter.close();
			outputStream.close();
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
	}

}
