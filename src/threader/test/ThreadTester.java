package threader.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import threader.ThreaderScheduler;
import threader.util.PlanThreadGraph;

import javaff.data.Action;
import javaff.data.GroundProblem;
import javaff.data.Parameter;
import javaff.data.TimeStampedPlan;
import javaff.data.TotalOrderPlan;
import javaff.data.Type;
import javaff.data.UngroundProblem;
import javaff.data.strips.Operator;
import javaff.data.strips.PDDLObject;
import javaff.parser.PDDL21parser;
import javaff.parser.ParseException;
import javaff.parser.SolutionParser;
import javaff.scheduling.STRIPSScheduler;
import javaff.scheduling.SchedulingException;
import javaff.test.SchedulerTester;

public class ThreadTester
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
			
//			System.out.println("Controller objects: "+controllers.toString());
			
			ThreaderScheduler threadScheduler = new ThreaderScheduler(gproblem.getSTRIPSInitialState());
			threadScheduler.getPlanThreads(top);

			
			TimeStampedPlan tsp = threadScheduler.getScheduledPlan();
			
			
//			PlanThreadGraph graph = threadScheduler.getGraph();
//			graph.generateDotGraph(new File("/tmp/batch.dot"));
			
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
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	protected static Set<Parameter> getPddlControllerObjects(UngroundProblem uproblem, GroundProblem gproblem)
	{
		//initialise the set of potential controller objects with all Types from the unground problem.
		//The ground problem will be used to populate any types remaining after filtering 
		HashSet<Type> controllerTypes = new HashSet<Type>(uproblem.types);
		
		//foreach action, find the types of all parameters to each action template
		for (Operator o : uproblem.actions)
		{
			HashSet<Type> paramTypes = new HashSet<Type>();
			
			for (Parameter param : o.params)
			{
				paramTypes.add(param.getType());
			}
			
			//retain only those types in the set which appear in the action template parameters. 
			//Each iteration will remove parameters which appear in an action template but are
			//not in the set. Therefore, when the loop ends only Types which appear in every action
			//will be present. These are the Types of the controller objects.
			controllerTypes.retainAll(paramTypes);
		}
		
		//we now know the types of the controller objects, so populate the grounded set of objects with
		//every object of these types.
		HashSet<Parameter> controllers = new HashSet<Parameter>();
		for (Type t : controllerTypes)
		{
			Set<PDDLObject> objs = uproblem.typeSets.get(t);
			controllers.addAll(objs);
		}
		
		return controllers;
	}
}