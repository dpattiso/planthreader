package threader;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javax.print.attribute.standard.DateTimeAtCompleted;


import threader.util.ActionLinkTuple;
import threader.util.ActionStateTuple;
import threader.util.MergeAction;
import threader.util.PlanThread;
import threader.util.PlanScheduleAction;
import threader.util.PlanScheduleState;
import threader.util.PlanThreadGraph;
import threader.util.StateActionLink;
import javaff.data.Action;
import javaff.data.Fact;
import javaff.data.GroundFact;
import javaff.data.GroundProblem;
import javaff.data.Parameter;
import javaff.data.TimeStampedAction;
import javaff.data.TimeStampedPlan;
import javaff.data.TotalOrderPlan;
import javaff.planning.STRIPSState;
import javaff.scheduling.MutexType;
import javaff.scheduling.STRIPSScheduler;
import javaff.scheduling.SchedulingException;
import javaff.scheduling.UnobservedInstantAction;

/**
 * This thread scheduler can accept actions in an iterative fashion, rather than the batch
 * based approach of its superclass.
 * 
 * @author David Pattison
 *
 */
public class IterativeThreadScheduler extends ThreaderScheduler
{
	private TotalOrderPlan totalOrderedPlan;
	private PlanThread lastThread;
	
	//keep a lookup of the threads which existed at time T. In a batch threading
	//context this is pointless, but as we are iteratively adding actions which may 
	//be scheduled for time T < T(thread-head), we would need to reconstruct the threads
	//present at the scheduled time, before we could actually do the threading. 
	//This is a pain in the neck to do, so just keep a lookup instead.
	private TreeMap<Integer, TreeSet<PlanThread>> threadsAtT;
	
	/**
	 * Create an iterative scheduler from the specified groud problem
	 * @param problem
	 */
	public IterativeThreadScheduler(STRIPSState initialState)
	{
//		this(initialState, new HashSet<Parameter>());
		super(initialState);

		this.totalOrderedPlan = new TotalOrderPlan(null);
		this.threadsAtT = new TreeMap<Integer, TreeSet<PlanThread>>();
		
	}
		
	
//	public IterativeThreadScheduler(STRIPSState initialState, Set<Parameter> controllerObjects)
//	{
//		super(initialState, controllerObjects);
//		
//		this.totalOrderedPlan = new TotalOrderPlan();
//		this.threadsAtT = new TreeMap<Integer, TreeSet<PlanThread>>();
//	}
	
//	/**
//	 * Overrides a lot of the superclass' functionality to cater for iterative scheduling. 
//	 * Only the final action of the plan provided is used to check for mutexes -- the remaining N-1
//	 * actions are assumed to have had all their mutex relations detected (amongst one-another).
//	 */
//	@Override
//	protected void detectMutexes(List<Action> plan)
//	{
//		if (plan.isEmpty())
//			return;
//		
//		Action newAction = plan.get(plan.size()-1);
////		System.out.println("Scheduling only last action: "+newAction);
//
//		//TODO replace this code which assumes that all statics need checked at every timestep to just use
//		//the single action added
//		this.detectStaticFacts(plan);
//		
//		//detect all effect and PC delete mutexes
//		for (Action a : plan)
//		{
//			HashSet<Action> mutex = new HashSet<Action>();
//					
//			if (a == newAction)
//				continue;
//			
//			if (this.areActionsMutex(a, newAction) != MutexType.None)
//				mutex.add(newAction);
//
//			this.actionMutexes.put(a, mutex);
//			this.actionMutexes.put(newAction, mutex);
//		}
//		
//		//now use the above mutexes to detect any PC fact mutexes
//		HashSet<Fact> allPCs = new HashSet<Fact>();
//		for (Action a : plan)
//		{
//			for (Fact pc : a.getPreconditions())
//				allPCs.add(pc);
//		}
//		
//		for (Fact p : allPCs)
//		{
//			if (p.isStatic())
//			{
////					System.out.println(p+" is static");
//				continue;
//			}
//			
//			HashSet<Fact> mutexGroup = new HashSet<Fact>();
//			for (Fact q : allPCs)
//			{
//				if (q.isStatic() || p == q)
//					continue;
//				
//				//TODO this can be optimised
//				MutexType mutex = this.checkCompetingPreconditionMutex(p, q, plan);
//				if (mutex != MutexType.None)
//				{
//					mutexGroup.add(q);
//				}
//			}
//			this.factMutexes.addMutex(p, mutexGroup); //TODO optimise for q, p
//		}
//		
//		//finally, add the fact mutex info into the action mutexes
//		for (Action a : plan)
//		{
//			//TODO optimise- only check tail of list
////			for (Action b : actions)
////			{		
//				if (a == newAction || (this.actionMutexes.containsKey(a) && this.actionMutexes.get(a).contains(newAction)))
//					continue;
//				
//				for (Fact p : a.getPreconditions())
//				{
//					if (p.isStatic())
//						continue;
//						
//					for (Fact q : newAction.getPreconditions())
//					{
//						if (p == q || q.isStatic())
//							continue;
//						
//						if (this.factMutexes.isMutex((GroundFact)p, (GroundFact)q))
//						{
//							if (this.actionMutexes.containsKey(a))
//							{
//								this.actionMutexes.get(a).add(newAction);
//								this.actionMutexes.get(newAction).add(a);
//							}
//							else
//							{
//								Set<Action> mut = new HashSet<Action>();
//								mut.add(newAction);
//								this.actionMutexes.put(a, mut);
//								this.actionMutexes.put(newAction, mut);
//							}
//						}	
//					}
//				}
////			}
//		}
//	}
		
	

	@Override
	public void reset()
	{
		super.reset();
		
		this.totalOrderedPlan = new TotalOrderPlan(null);
		this.lastThread = null;
		this.threadsAtT.clear();
	}
	
	/**
	 * Returns a single plan thread which the specified action has been added to.
	 * 
	 * @param newAction The action to both schedule and append to a thread.
	 * @param replacementMap A set which will be populated with the threads used to create the new thread.
	 * @return The new thread.
	 * @throws SchedulingException
	 */
	public PlanThread getPlanThread(Action newAction, SortedSet<PlanThread> modifiedThreads, Fact goal) throws SchedulingException
	{
		//get a copy of the old plan so we can validate that the new one is correct
		TimeStampedPlan oldPlan = (TimeStampedPlan) this.timeStampedPlan.clone();
			
		this.totalOrderedPlan.setGoal(goal);
		this.totalOrderedPlan.addAction(newAction);
		
		//this is a (somewhat) unnecessary bottleneck, which I have no intention of fixing
		TimeStampedPlan tsp = super.schedule(this.totalOrderedPlan);

		//get a copy of the new plan -- we will modify this and use it to check if it is correct
		TimeStampedPlan newPlan = (TimeStampedPlan) this.timeStampedPlan.clone();
		
		
		//the new plan should contain the newly scheduled action and potentially N unobserved actions
		//which allow it to be achieved. If the difference between the old and new plans is > 1 then 
		//there are unobserved actions to account for
		TreeSet<TimeStampedAction> allNewActions = new TreeSet<TimeStampedAction>(newPlan.getSortedActions());
		allNewActions.removeAll(oldPlan.getSortedActions());
		
		
		//locate the new action in the new scheduled plan
		//have to reverse it first because there may be duplicate actions in the plan
		//but newAction should always be after the first. Either way, we still want the latter version.

		PlanThread realObservationThread = null;
		for (TimeStampedAction bob : allNewActions)
		{
			PlanThread chosenThread = this.scheduleSingleAction(bob, modifiedThreads);
			super.getLiveThreads().removeAll(modifiedThreads); //TODO fix this, it is a bit of a hack to make getGraph() work from in this class
			super.getLiveThreads().add(chosenThread);
			
			BigDecimal newHeadTime = chosenThread.getHead().time;
			BigDecimal roundedHeadTime = newHeadTime.setScale(1, RoundingMode.DOWN);
			Integer roundedHeadTimeInteger = roundedHeadTime.intValue();
			
			//if there are no threads present at time T_New, then create a new set for this and any
			//future threads, and populate it with the current set of live heads, provided by the superclass
			//(which must be appropriately updated).
			if (this.threadsAtT.containsKey(roundedHeadTimeInteger) == false)
			{
				this.threadsAtT.put(roundedHeadTimeInteger, new TreeSet<PlanThread>());
				this.threadsAtT.get(roundedHeadTimeInteger).addAll(super.getLiveThreads());
			}
			
	//		TreeMap<Integer, TreeSet<PlanThread>> toMerge = new TreeMap<Integer, TreeSet<PlanThread>>();
			ArrayList<TreeSet<PlanThread>> reversedThreadsAtT = new ArrayList<TreeSet<PlanThread>>(this.threadsAtT.values());
			Collections.reverse(reversedThreadsAtT);
			
			Integer time = reversedThreadsAtT.size();
			//if we KNOW that the new thread ended at time T_new, then we know that it exists at times
			//T_new to T_max, where T_max is the latest scheduled action of any thread.
			for (TreeSet<PlanThread> threads : reversedThreadsAtT)
			{
				//skip over any collection members at time T < chosenThread(T), as these are unaffected by 
				//the new thread. We know that the action will be scheduled to appear at time T_New, as the
				//scheduler has already determined this apriori, so we are only concerned with 
				//threads present at time T >= T_New
				if (time < roundedHeadTimeInteger)
				{
					--time;
					continue;
				}
	
				//add the new thread to time T_New + K
				this.threadsAtT.get(time).add(chosenThread);
				
				--time;
			}
		
			//we know that at time T_new, all threads which existed at time T will be retained (plus any already existing at time T), so
			//we can add these to the set for time T_new, but ignore the thread from which chosenHead was created, and add in chosenHead
			//itself.
			//1/3/14 -- actually, need to go round everything which comes after the timepoint which
			//theaction was added to, and the oldone removed from. This is because all the other
			//indices in threadsAtT will still be referring to the old value as being value at that
			//time -- not the new one
			for (int i = roundedHeadTimeInteger; i <= this.threadsAtT.size(); i++)
			{
				this.threadsAtT.get(i).removeAll(modifiedThreads);
				this.threadsAtT.get(i).add(chosenThread);
			}
			
			
	//		super.getGraph().generateDotGraph(new File("/tmp/it.dot"));
			
			this.lastThread = chosenThread;
			if (bob.getAction().equals(newAction))
			{
				realObservationThread = chosenThread;
			}
		}
		
		if (realObservationThread == null)
			throw new NullPointerException("Cannot find the thread which the action was appended to");
		
		return realObservationThread;
	}

	/**
	 * Schedules a single action in a set of specified thread-heads.
	 * @param tsa
	 * @param modifiedHeads
	 * @return
	 * @throws SchedulingException
	 */
	protected PlanThread scheduleSingleAction(TimeStampedAction tsa, Set<PlanThread> modifiedHeads) throws SchedulingException
	{
		PlanScheduleAction psAction = new PlanScheduleAction(tsa, super.getNewActionID());
		
//		TreeSet<PlanThread> headsAtT = this.constructThreadsUntilT(this.liveThreads, tsa.getMajorTime());
//		TreeSet<PlanThread> headsAtT = this.liveThreads;
//		BigDecimal actionTime = tsa.getMajorTime();
		Integer actionTime = tsa.getMajorTime().intValue();
		TreeSet<PlanThread> headsAtT = this.threadsAtT.get(actionTime);
		if (headsAtT == null)
			headsAtT = new TreeSet<PlanThread>();
		
		PlanThread newThread = super.scheduleAction(psAction, headsAtT, modifiedHeads);
		
		return newThread;
	}


//	private TreeSet<PlanThread> constructThreadsUntilT(TreeSet<PlanThread> liveHeads,
//			BigDecimal stopTime)
//	{
//		TreeSet<PlanThread> trimmedHeads = new TreeSet<PlanThread>();
//		
//		for (PlanThread h : liveHeads)
//		{
//			PlanThread newThread = h.trim(stopTime);
//			
//			trimmedHeads.add(newThread);
//			
//		}
//		
//		return trimmedHeads;
//	}


	/**
	 * Returns the plan thread which the previous action was added to.
	 * @return
	 */
	public PlanThread getLastThread()
	{
		return lastThread;
	}

	
}


