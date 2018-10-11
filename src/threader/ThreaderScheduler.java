package threader;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

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
import javaff.data.GroundProblem;
import javaff.data.Parameter;
import javaff.data.TimeStampedAction;
import javaff.data.TimeStampedPlan;
import javaff.data.TotalOrderPlan;
import javaff.data.strips.Not;
import javaff.planning.STRIPSState;
import javaff.scheduling.MutexType;
import javaff.scheduling.STRIPSScheduler;
import javaff.scheduling.SchedulingException;
import javaff.scheduling.UnobservedInstantAction;

public class ThreaderScheduler extends STRIPSScheduler
{
//	private Set<Parameter> controllerObjects;
	private int actionIdCounter;

	protected TimeStampedPlan timeStampedPlan;
//	protected PlanThreadGraph graph;
	protected TreeSet<PlanThread> liveThreads;
	private PlanScheduleState initialPSS;
	
	protected int stateIDCounter = 0;

	private Map<Fact, TimeStampedAction> factAchievers;
	private Map<TimeStampedAction, PlanThread> actionThreadMap;

	// private Random rand;

	public ThreaderScheduler(STRIPSState initialState)
	{
		super(initialState);
		
		this.stateIDCounter = 0;
		this.actionIdCounter = 0;

		this.initialPSS = new PlanScheduleState(initialState, this.stateIDCounter++, BigDecimal.ZERO);

//		this.controllerObjects = new HashSet<Parameter>();
		this.factAchievers = new HashMap<Fact, TimeStampedAction>();
		this.actionThreadMap = new HashMap<TimeStampedAction, PlanThread>();

		this.timeStampedPlan = new TimeStampedPlan(null);

//		this.graph = new PlanThreadGraph(this.initialPSS);
		this.liveThreads = new TreeSet<PlanThread>();
	}

//	public ThreaderScheduler(STRIPSState initialState, Set<Parameter> controllerObjects)
//	{
//		this(initialState);
//
//		this.controllerObjects = controllerObjects;
//	}

	/**
	 * Resets all fields such that a new plan can be scheduled. Make sure you really want to call
	 * this, it is really for internal use only. Controller objects are NOT reset for convenience.
	 */
	public void reset()
	{
		this.actionIdCounter = 0;

		this.timeStampedPlan = new TimeStampedPlan(null);
		
		this.actionThreadMap.clear();
		this.factAchievers.clear();

//		this.graph = new PlanThreadGraph(this.initialPSS);
		this.liveThreads = new TreeSet<PlanThread>();
	}

	/**
	 * Schedules the specified plan as normal which is then returned. The scheduled plan
	 * is also stored internally.
	 * 
	 */
	@Override
	public TimeStampedPlan schedule(TotalOrderPlan top) 
			throws SchedulingException
	{
		this.factAchievers = new HashMap<Fact, TimeStampedAction>();
		return this.schedule(top, factAchievers);
	}
	
	/**
	 * Schedules the specified plan as normal which is then returned. The scheduled plan
	 * is also stored internally.
	 * @param top The plant to schedule
	 * @param achieverMap A map of the actions which last achieved facts in the plan. 
	 */
	@Override
	public TimeStampedPlan schedule(TotalOrderPlan top, Map<Fact, TimeStampedAction> achieverMap)
			throws SchedulingException
	{
		this.timeStampedPlan = super.schedule(top, achieverMap);
		this.factAchievers = achieverMap;
		this.timeStampedPlan.setGoal(top.getGoal());
		return this.timeStampedPlan;
	}

	/**
	 * Returns a set of plan threads, represented as PlanHeadTuples. N tuples will be returned,
	 * where N is the number of live heads after graph construction is finished. These can also be
	 * accessed after calling this method, via getLiveHeads(). Note that reset() is called prior to
	 * the plan being scheduled.
	 * 
	 * @param plan
	 * @return
	 * @throws SchedulingException
	 */
	public Collection<PlanThread> getPlanThreads(TotalOrderPlan plan) throws SchedulingException
	{
		HashMap<PlanThread, Collection<PlanThread>> stub = new HashMap<PlanThread, Collection<PlanThread>>();

		return this.getPlanThreads(plan, stub);
	}

	/**
	 * Returns a set of plan threads, represented as PlanHeadTuples. N tuples will be returned,
	 * where N is the number of live heads after graph construction is finished. These can also be
	 * accessed after calling this method, via getLiveHeads(). Note that reset() is called prior to
	 * the plan being scheduled.
	 * 
	 * @param plan
	 * @return
	 * @throws SchedulingException
	 */
	public Collection<PlanThread> getPlanThreads(TotalOrderPlan plan,
			Map<PlanThread, Collection<PlanThread>> replacementHeads) throws SchedulingException
	{
		this.reset();
		this.timeStampedPlan = this.schedule(plan);
		
		System.out.println("Successfully scheduled plan...");
//		this.timeStampedPlan.print(System.out);

		System.out.println("Constructing thread graph of scheduled plan...");
		replacementHeads.putAll(this.createThreads(this.timeStampedPlan));

		// this.graph = this.createGraph(replacementHeads.keySet());

		System.out.println("Found " + this.liveThreads.size() + " plan threads");

//		this.graph.generateDotGraph(new File("threadGraph.dot"));

		return this.liveThreads;
	}

	/**
	 * Creates a set of plan threads from the specified scheduled plan.
	 * 
	 * @param tsp
	 * @return
	 * @throws SchedulingException
	 *             Thrown if an action cannot be placed into the thread graph.
	 */
	protected Map<PlanThread, Collection<PlanThread>> createThreads(TimeStampedPlan tsp)
			throws SchedulingException
	{
		// map new_head -> old_heads
		Map<PlanThread, Collection<PlanThread>> replacementHeadsMap = new HashMap<PlanThread, Collection<PlanThread>>();

		// map of the combined state at time T
		HashMap<BigDecimal, STRIPSState> unionStates = new HashMap<BigDecimal, STRIPSState>();
		// initialise with initial state at time 0
		unionStates.put(BigDecimal.ZERO, initial);

		this.liveThreads.clear();

		// original TSA to PSA map for edge additions
		HashMap<TimeStampedAction, PlanScheduleAction> actionPsaMap = new HashMap<TimeStampedAction, PlanScheduleAction>();

		List<Action> actions = tsp.getActions();
		while (actions.isEmpty() == false)
		{
			// once the head of the action queue is not at this time, the loop
			// can be advanced
			BigDecimal thisTime = ((TimeStampedAction) actions.get(0)).getMajorTime(); // equal to
																						// time T

			TreeSet<PlanScheduleAction> toSchedule = new TreeSet<PlanScheduleAction>();
			TimeStampedAction lastAction = null;
			do
			{
				lastAction = (TimeStampedAction) actions.remove(0);
				int id = this.getNewActionID(); //generate a new ID for the action
				PlanScheduleAction actionPSS = new PlanScheduleAction(lastAction, id);
				toSchedule.add(actionPSS);

				actionPsaMap.put(lastAction, actionPSS);
			}
			while (actions.isEmpty() == false && ((TimeStampedAction) actions.get(0)).getMajorTime().equals(thisTime));

			// the set of heads at time T+1. Set only once this iteration has
			// finished
			TreeSet<PlanThread> newThreads = new TreeSet<PlanThread>();
			// likewise, keep a list of all heads at T which are no longer value
			// (have been modified)
			TreeSet<PlanThread> allModifiedThreads = new TreeSet<PlanThread>();

			// schedule the actions at time T
			for (PlanScheduleAction psAction : toSchedule)
			{
				HashSet<PlanThread> modifiedThreads = new HashSet<PlanThread>();
				PlanThread newHead = this.scheduleAction(psAction, this.liveThreads, modifiedThreads);
				
				allModifiedThreads.addAll(modifiedThreads);
				newThreads.add(newHead);
				replacementHeadsMap.put(newHead, modifiedThreads);


//				HashSet<PlanThread> graphThreads = new HashSet<PlanThread>(this.liveThreads);
//				graphThreads.removeAll(allModifiedThreads);
//				graphThreads.addAll(newThreads);
//				this.getGraph(graphThreads).generateDotGraph(new File("/tmp/dl.dot"));
			}
						
			// update the heads available at T+1
			this.liveThreads.removeAll(allModifiedThreads);
			this.liveThreads.addAll(newThreads);

//			this.getGraph(this.liveThreads).generateDotGraph(new File("/tmp/dl.dot"));

		}

		return replacementHeadsMap;
	}

	/**
	 * Merges the N specified threads into a single thread. The single thread encapsulates all these
	 * other threads (parallelises the actions etc.).
	 * 
	 * @param requiredHeads
	 * @return
	 */
	protected PlanThread mergeThreads(SortedSet<PlanThread> requiredHeads)
	{
		if (requiredHeads.isEmpty())
			throw new NullPointerException("No heads to merge");

		// generate a new PSS which will be the final unioned state
		PlanThread unionThread = new PlanThread(requiredHeads.first().getInitialState());

		//lists of previous states (the heads of all required threads), and their links, which will
		//all be of type Merge
		ArrayList<PlanScheduleState> prevStates = new ArrayList<PlanScheduleState>();
		ArrayList<ActionLinkTuple> links = new ArrayList<ActionLinkTuple>();
		
		//first check that the merge is legal by making sure that all threads have the same initial state,
		BigDecimal latestTime = BigDecimal.ZERO;
		BigDecimal earliestTime = new BigDecimal(Integer.MAX_VALUE);
		for (PlanThread t : requiredHeads)
		{
			if (t.getInitialState().equals(unionThread.getInitialState()) == false)
				throw new IllegalArgumentException("All plan threads to be merged must have the same initial state");

			PlanScheduleState head = t.getHead();
			if (head.getMajorTime().compareTo(latestTime) > 0)
				latestTime = head.getMajorTime();
			if (head.getMajorTime().compareTo(earliestTime) < 0)
				earliestTime = head.getMajorTime();
			
			prevStates.add(head);
			links.add(new ActionLinkTuple(StateActionLink.Merge, 0));
			
			unionThread.addAll(t.getActions());
			
		}
		

		
//		BigDecimal halfEpsilon = new BigDecimal(super.getEpsilon()).divide(new BigDecimal(2));
//		BigDecimal mergeStartTime = latestTime.add(halfEpsilon);
//		BigDecimal mergeSuccStateTime = latestTime.add(halfEpsilon);
		
		//the scheduler always assigns an epsilon value to actions which starts at X.0001. Therefore, we use
		//the absolute value of the timestamp (i.e. X.0000) to be the time when merges will be applied -- as
		//this must be done prior to the next applicable action.
		BigDecimal mergeStartTime = latestTime;
		BigDecimal mergeSuccStateTime = latestTime;
		
		//compute a new state
		PlanScheduleState mergeState = new PlanScheduleState(unionThread.computeCurrentState(), this.stateIDCounter++, mergeSuccStateTime);
		
		//create a tuple for the new merge state and all predecessor states
		ActionStateTuple mergeTuple = new ActionStateTuple(prevStates, 
															mergeState,
															new MergeAction(this.getNewActionID(), mergeStartTime), 
															links);
		unionThread.add(mergeTuple);
		
		return unionThread;
	}
	
	/**
	 * Pair mapping of a plan thread and it's link to an action.
	 */
	protected class PSSLinkPair implements Comparable<PSSLinkPair>
	{
		public PlanThread thread;
		public ActionLinkTuple link;
		
		public PSSLinkPair(PlanThread state, ActionLinkTuple link)
		{
			super();
			this.thread = state;
			this.link = link;
		}
		
		public Object clone()
		
		{
			return new PSSLinkPair((PlanThread) this.thread.clone(), (ActionLinkTuple) this.link.clone());
		}

		@Override
		public int compareTo(PSSLinkPair o)
		{
			int linkResult = this.link.compareTo(o.link);
			
			if (linkResult == 0)
			{
				//compare the Other to This -- because we want to always prefer the later action
				//but the natural ordering of PlanThreads is ascending based upon the plan head. We 
				//want descending, so that we always prefer later states
//				return o.thread.compareTo(this.thread);
				
				if (this.thread.getActionLength() == 0 && o.thread.getActionLength() == 0)
					return linkResult;
				else if (this.thread.getActionLength() > 0 && o.thread.getActionLength() == 0)
					return +1;
				else if (this.thread.getActionLength() == 0 && o.thread.getActionLength() > 0)
					return -1;
				else
				{
					
					ActionStateTuple lastThis = this.thread.getActions().last();
					ActionStateTuple lastThat = o.thread.getActions().last();
					
	//				if (lastThis == null)
	//					return nu
					
					return lastThat.compareTo(lastThis); //reverse normal order;
				}
			}
			else
				return linkResult;
		}

		
		@Override
		public String toString()
		{
			return this.thread.getHead().stateId + " > "+this.link.toString();
		}
	}
	

	/**
	 * Schedules a single action by looking at the current live plan heads and selecting the best
	 * for linking. If the best selected head cannot actually have the action applied in it, a merge
	 * will have to take place between that head and the first N other heads which satisfy the
	 * preconditions of the action. A new thread will be created from this which encapsulates the
	 * threads associated with all N merged heads. The actual action will link the original, but
	 * only partially applicable head, with the new merge head. All other "supporting" heads
	 * required will have MergeActions applied.
	 * 
	 * @param psAction
	 * @param modifiedTheads
	 * @param newHeads
	 * @throws SchedulingException
	 *             Thrown if the action cannot be applied to any combination of heads.
	 */
	protected PlanThread scheduleAction(PlanScheduleAction psAction, Collection<PlanThread> validHeads, 
			Collection<PlanThread> modifiedTheads)
			throws SchedulingException
	{
		TreeSet<PSSLinkPair> threadLinks = new TreeSet<PSSLinkPair>();		

		//if there are no heads in the validHeads, then we have a green field, and can just add 
		//wherever we want, so say that the bestThread is just an empty one containing only the initial state
		if (validHeads.isEmpty() )
		{
			PlanThread initialThread = new PlanThread(this.initialPSS);
			PSSLinkPair linkPair = new PSSLinkPair(initialThread, new ActionLinkTuple(StateActionLink.None, 0));
			threadLinks.add(linkPair);
		}
		//else we have to apply it to an existing head(s)
		else
		{
			//initialise the best link by taking the first plan thread
			Iterator<PlanThread> validThreadIter = validHeads.iterator();
			PlanThread firstThread = validThreadIter.next();
			ActionLinkTuple bestLinkTuple = this.getLink(psAction, firstThread);
			threadLinks.add(new PSSLinkPair(firstThread, bestLinkTuple));
			
			//go through each valid plan thread to consider and find the type of link (if any) between its plan head and the
			//action to apply
			while (validThreadIter.hasNext())
			{
				PlanThread currentThread = validThreadIter.next();
				
				// find how this action links to the current set of live heads.
				// Select the best link as the application state.
				ActionLinkTuple linkTuple = this.getLink(psAction, currentThread);

				PSSLinkPair pair = new PSSLinkPair(currentThread, linkTuple);
				threadLinks.add(pair);
			}

			if (threadLinks.isEmpty())
				throw new SchedulingException("Unable to find link for " + psAction + ". This should be impossible!");
		}

		// find if the action is applicable in the sub-state associated with the
		// chosen plan head
		// if it is, add the edge and move on. If it is not, find the first set
		// of other plan heads which
		// achieve it and link all of them to the successor node
		PSSLinkPair workingLink = threadLinks.pollFirst();
		
		SortedSet<PSSLinkPair> requiredHeads = new TreeSet<PSSLinkPair>();
		requiredHeads.add(workingLink);
		
		
		//TODO this entire loops is deprecated -- the threader just needs to use the 
		//achievers of preconditions from STRIPSScheduler.schedule to determine
		//which threads an action belongs to. But I'm keeping i because it works. 
		//And I think it would provide the same output anyway.
		STRIPSState threadHeadState = workingLink.thread.computeCurrentState();
		while (true)
		{
			//break as soon as we have a valid head state
			if (psAction.action.isApplicable(threadHeadState))
			{
				break;
			}
			
			if (threadLinks.isEmpty())
				throw new NullPointerException("Cannot find set of plan heads which satisfy preconditions of "+psAction+". This should be impossible!");

			//if we have gotten to here, then the action is not applicable in a single
			//plan thread-head, so we want to find the first set of combined plan heads which
			//will allow it to become applicable. We do this by popping off the head of the 
			//sorted set containing plan threads and their link to the action, 1 per iteration of
			//this loop, and merging the threads. Once an acceptable state has been found, the loop 
			//will exit via the IF statement above. This is non-optimal as ordering is important to the
			//output, but it works.
			
			//get the next-best linked head
			PSSLinkPair nextBestHead = threadLinks.pollFirst();
//			PSSLinkPair nextBestHead = threadLinks.pollLast();
			
			requiredHeads.add(nextBestHead);

			//TODO this is a slow way of computing the head state, but I'm unsure if the original
			//method of just merging the head states of required threads achieves a sound output
			PlanThread stubThread = new PlanThread(workingLink.thread.getInitialState());
			for (PSSLinkPair pss : requiredHeads)
			{
				boolean res = stubThread.addAll(pss.thread.getActions());
			}
			
			//FIXME this is a real hack for a subtle bug.
			/*
			 * I am loath to include this try-catch, but it offers a simple solution to the problem at hand.
			 * The problem is this (and manifests itself on Depots 6 if you are interested in a demonstration) --
			 * We have 2 threads which an action is applicable in, A and B. In reality we would choose B but for
			 * whatever reason, A appears to be best so it is selected instead. If the newly appended action
			 * has a gap between itself and its previous timestamped action in the thread, then there is a risk
			 * that later merging of this thread and another which contains a mutex action in this free timestep
			 * will cause an invalid plan to be formed. This is because the action is perfectly valid to be applied
			 * at time T, but is not valid once the context of the other threads actions are taken into account.
			 * In theory, this try-catch should prevent the algorithm ever failing to produce a valid plan, because
			 * it will always just merge threads until a valid head is found (and merging all threads at all timesteps
			 * has the same effect as normal linear planning.
			 */
			try
			{
				threadHeadState = stubThread.computeCurrentState();
			}
			catch (IllegalArgumentException e)
			{
//				if an exception was thrown because the actions could not be successfully merged into 
//				a single thread, then just fail silently and move onto the next available plan thread.
			}
		}
		
		//if more than 1 thread head is required, a merge is needed first
		if (requiredHeads.size() > 1)
		{

			// if we have reached this point, then the heads at T-1 which are
			// required for the
			// action to be applied have been determined. Now merge these
			// threads to get a
			// single unioned plan head.
			TreeSet<PlanThread> toMerge = new TreeSet<PlanThread>();
			for (PSSLinkPair pss : requiredHeads)
			{
				toMerge.add(pss.thread);
				modifiedTheads.add(pss.thread);
			}
			
			//merge the threads, prior to applying the actual action
			PlanThread unionThread = this.mergeThreads(toMerge);

			//generate a new link for the merged head to the action to be applied
			ActionLinkTuple newLink = this.getLink(psAction, unionThread);
			
			PSSLinkPair unionPair = new PSSLinkPair(unionThread, newLink);
			
			requiredHeads.clear();
			requiredHeads.add(unionPair);
			
			
//			HashSet<PlanThread> toGraph = new HashSet<PlanThread>();
//			toGraph.addAll(this.liveThreads);
//			toGraph.removeAll(modifiedTheads);
//			toGraph.add(unionThread);
//			
//			this.getGraph(toGraph).generateDotGraph(new File("/tmp/dl.dot"));
		}
		else
		{
			modifiedTheads.add(requiredHeads.iterator().next().thread);
		}
		
		//Get the thread which will finally be used
		//The naive way to do this is just select one at random or the first found
		PSSLinkPair finalThreadPair = requiredHeads.iterator().next();
//		PSSLinkPair finalThreadPair = (PSSLinkPair) requiredHeads.iterator().next().clone();
		
		//another way to select the best thread is to see how many causal links exist on each thread and
		//select the one with the most pre-existing links.
		
		
		
		//get the previous state -- there should only ever be 1 "head" state in a well-formed thread
		PlanScheduleState prevState = finalThreadPair.thread.getHead(); 
		STRIPSState newHeadState = (STRIPSState) threadHeadState.apply(psAction.action);
		BigDecimal newHeadTime = psAction.action.getMajorTime().add(psAction.action.getDuration());
				
		PlanScheduleState succState = new PlanScheduleState(newHeadState, this.stateIDCounter++, newHeadTime);
		ActionStateTuple toAppend = new ActionStateTuple(prevState, succState, psAction, finalThreadPair.link);
		
		PlanThread branch = finalThreadPair.thread.branch(toAppend);
		
		return branch;
	}
	

//	/**
//	 * Schedules a single action by looking at the current live plan heads and selecting the best
//	 * for linking. If the best selected head cannot actually have the action applied in it, a merge
//	 * will have to take place between that head and the first N other heads which satisfy the
//	 * preconditions of the action. A new thread will be created from this which encapsulates the
//	 * threads associated with all N merged heads. The actual action will link the original, but
//	 * only partially applicable head, with the new merge head. All other "supporting" heads
//	 * required will have MergeActions applied.
//	 * 
//	 * {@link #schedule(TotalOrderPlan, Map)} must have been called prior to this method being invoked.
//	 * 
//	 * @param psAction
//	 * @param modifiedTheads
//	 * @param newHeads
//	 * @throws SchedulingException
//	 *             Thrown if the action cannot be applied to any combination of heads.
//	 */
//	protected PlanThread scheduleAction(PlanScheduleAction psAction, Collection<PlanThread> validHeads, 
//			Collection<PlanThread> modifiedTheads)
//			throws SchedulingException
//	{
//
//		//if there are no heads in the validHeads, then we have a green field, and can just add 
//		//wherever we want, so say that the bestThread is just an empty one containing only the initial state
//		if (validHeads.isEmpty() )
//		{
//			PlanThread initialThread = new PlanThread(this.initialPSS);
//			validHeads.add(initialThread);
//		}
//		
//		
//		TreeSet<PlanThread> requiredThreads = new TreeSet<PlanThread>();
//		for (Fact pc : psAction.action.getPreconditions())
//		{
//			if (pc.isStatic())
//				continue;
//			
//			TimeStampedAction achiever = this.factAchievers.get(pc);
//			
//			//if the achiever is null then we have never seen an action which achieved it
//			// -- But, if it is a Not, then having never seen it is valid, as it means
//			//that the fact has never been deleted, but we assume that it was false in the
//			//initial state.
//			if (achiever == null)
//			{
//				 if (pc instanceof Not)
//					 continue;
//				 else
//					 throw new NullPointerException("Cannot find achiever for ("+pc.toString()+") in "+psAction.toString()+". This should be impossible, even in a partially observable world!");
//			}
//			
//			PlanThread achieverThread = this.actionThreadMap.get(achiever);
//			requiredThreads.add(achieverThread);
//		}
//
//		
//		
//		//if more than 1 thread head is required, a merge is needed first
//		if (requiredThreads.size() > 1)
//		{
//
//			// if we have reached this point, then the heads at T-1 which are
//			// required for the
//			// action to be applied have been determined. Now merge these
//			// threads to get a
//			// single unioned plan head.
//			modifiedTheads.addAll(requiredThreads);
//			
//			//merge the threads, prior to applying the actual action
//			PlanThread unionThread = this.mergeThreads(requiredThreads);
//			
//			requiredThreads.clear();
//			requiredThreads.add(unionThread);
//			
//			
////			HashSet<PlanThread> toGraph = new HashSet<PlanThread>();
////			toGraph.addAll(this.liveThreads);
////			toGraph.removeAll(modifiedTheads);
////			toGraph.add(unionThread);
////			
////			this.getGraph(toGraph).generateDotGraph(new File("/tmp/dl.dot"));
//		}
//		else
//		{
//			modifiedTheads.add(requiredThreads.first());
//		}
//		
//		PlanThread threadToAppendTo = requiredThreads.first();
//		
//		//get the previous state -- there should only ever be 1 "head" state in a well-formed thread
//		PlanScheduleState prevState = threadToAppendTo.getHead(); 
//		STRIPSState newHeadState = (STRIPSState) prevState.state.apply(psAction.action);
//		BigDecimal newHeadTime = psAction.action.getMajorTime().add(psAction.action.getDuration());
//				
//		PlanScheduleState succState = new PlanScheduleState(newHeadState, this.stateIDCounter++, newHeadTime);
//		
//		
//		ActionStateTuple toAppend = new ActionStateTuple(prevState, succState, psAction, new ActionLinkTuple(StateActionLink.Causal, 0));
//		
//
////		boolean added = finalThreadPair.thread.add(toAppend);
//		PlanThread branch = threadToAppendTo.branch(toAppend);
//		this.actionThreadMap.put(psAction.action, branch);
//		
//		return branch;
//	}
	
	/**
	 * Internal method for generating a thread graph from a user-specified set of threads.
	 * 
	 * @param threads
	 * @return
	 */
	protected PlanThreadGraph getGraph(Collection<PlanThread> threads)
	{
		PlanThreadGraph g = new PlanThreadGraph(threads.iterator().next().getInitialState());

		int uniqueEdgeCounter = 0;
		ArrayList<PlanScheduleAction> edgesSeen = new ArrayList<PlanScheduleAction>();
		
		for (PlanThread t : threads)
		{
			for (ActionStateTuple tuple : t.getActions())
			{
				for (PlanScheduleState prev : tuple.prevStates)
				{
					PlanScheduleAction edge;
					if (g.containsEdge(prev, tuple.succState))
						continue;
					
//					int index = edgesSeen.indexOf(tuple.action);
//					if (index >= 0)
//					{
//						edge = edgesSeen.get(index);
//					}
//					else
//					{
						edge = new PlanThreadGraphActionEdge(uniqueEdgeCounter++, tuple.action);
//					}
					
					boolean res = g.addEdge(prev, tuple.succState, edge);
					if (!res)
						throw new NullPointerException("Adding edge to graph failed");
					
					edgesSeen.add(edge);
				}
			}
		}
		
		return g;
		
	}
	
	/**
	 * Wrapper class for plan schedule actions -- we want the same edge to appear multiple times in a graph
	 * but JGraphT thinks that PlanScheduleActions can only appear once (only a single instance of an edge
	 * is allowed thanks to the use of Sets). This class just wraps the action and additionally takes a
	 * unique ID to allow distinguishing between edges. 
	 * 
	 * @author David Pattison
	 *
	 */
	private class PlanThreadGraphActionEdge extends PlanScheduleAction
	{
		private PlanScheduleAction action;
		private int id;
		
		public PlanThreadGraphActionEdge(int id, PlanScheduleAction a)
		{
			super(a.action, a.id);
			this.id = id;
			this.action = a;
		}

		public Object clone()
		{
			return new PlanThreadGraphActionEdge(this.id, (PlanScheduleAction) this.action.clone());
		}

		public BigDecimal getTime()
		{
			return action.getTime();
		}

		public String toString()
		{
			return action.toString();
		}

		public int hashCode()
		{
			return this.id; //action.hashCode();
		}

		public boolean equals(Object obj)
		{
			return this.id == ((PlanThreadGraphActionEdge)obj).id;
		}

		public int compareTo(PlanScheduleAction other)
		{
//			int res = Integer.compare(this.id, other.id);
//			
//			if (res == 0)
//			{
				return this.action.compareTo(other);
//			}
//			
//			return res;
		}
	}
	
	/**
	 * Generate a graph of the threads active at the time of calling. The graph is generated
	 * on calling this method, which can potentially be an time-expensive process.
	 * @return
	 */
	public PlanThreadGraph getGraph()
	{
		return this.getGraph(this.liveThreads);
	}

	protected int getNewActionID()
	{
		return ++this.actionIdCounter;
	}


	protected ActionLinkTuple getLink(Entry<PlanScheduleAction, PlanThread> h)
	{
		return this.getLink(h.getKey(), h.getValue());
	}

	/**
	 * Gets the link between an action and plan head. The matchCount parameter holds the number of
	 * matches for Causal and Object links such that the called can distinguish between better
	 * links.
	 * 
	 * @param a
	 * @param h
	 * @param previousBestObjectCount
	 * @return

	 */
	protected ActionLinkTuple getLink(PlanScheduleAction a, PlanThread h)
	{
		HashSet<Fact> used = new HashSet<Fact>(h.getUnusedFacts());
		used.retainAll(a.action.getPreconditions());
		int matchCount = used.size();

//		for (Parameter p : controllerObjects)
//		{
//			if (a.action.getParameters().contains(p) && h.getObjectsUsed().contains(p))
//			{
//				if (matchCount == 0)
//					return new ActionLinkTuple(StateActionLink.Controller, matchCount);
//				else
//					return new ActionLinkTuple(StateActionLink.CausalController, matchCount);
//			}
//		}

		if (matchCount > 0)
			return new ActionLinkTuple(StateActionLink.Causal, matchCount);

		// else if there is no explicit causal link, try to choose one which has
		// used the most same objects so far
		HashSet<Parameter> shared = new HashSet<Parameter>(h.getObjectsUsed());
		shared.retainAll(a.action.getParameters());
		matchCount = shared.size();

		if (matchCount > 0)
			return new ActionLinkTuple(StateActionLink.Object, matchCount);

		return new ActionLinkTuple(StateActionLink.None, 0);
	}

//	protected MutexType checkControllerMutex(Action a, Action b)
//	{
//		
//		HashSet<Parameter> newSet = new HashSet<Parameter>();
//		newSet.addAll(a.getParameters());
//		newSet.addAll(b.getParameters());
//		newSet.retainAll(this.controllerObjects);
//		
//		if (newSet.size() == 1)
//			return MutexType.ControlObject;
//		
//		return MutexType.None;
//	}

//	/**
//	 * In addition to the superclass' implementation, checks for {@link MutexType#ControlObject} mutexes.
//	 * @see {@link STRIPSScheduler#areActionsMutex(Action, Action)}.
//	 */
//	@Override
//	public MutexType areActionsMutex(Action a, Action b)
//	{
//		MutexType basicMutex = super.areActionsMutex(a, b);
//		if (basicMutex != MutexType.None)
//			return basicMutex;
//
//		// check controller mutex
//		basicMutex = this.checkControllerMutex(a, b);
//
//		return basicMutex;
//	}

//	/**
//	 * In addition to the superclass' implementation, checks for {@link MutexType#ControlObject} mutexes.
//	 * @see {@link STRIPSScheduler#areActionsMutex(Action, Set)}.
//	 */
//	@Override
//	public MutexType areActionsMutex(Action a, Set<Action> others)
//	{
//		for (Action o : others)
//		{
//			MutexType basicMutex = super.areActionsMutex(a, o);
//			if (basicMutex != MutexType.None)
//				return basicMutex;
//
//			// check controller mutex
//			basicMutex = this.checkControllerMutex(a, o);
//			if (basicMutex != MutexType.None)
//				return basicMutex;
//		}
//
//		return MutexType.None;
//	}

//	public Set<Parameter> getControllerCausalObjects()
//	{
//		return controllerObjects;
//	}
//
//	public void setControllerCausalObjects(Set<Parameter> controllerCausalObjects)
//	{
//		this.controllerObjects = controllerCausalObjects;
//	}

//	public PlanThreadGraph getGraph()
//	{
//		return graph;
//	}

	public TreeSet<PlanThread> getLiveThreads()
	{
		return liveThreads;
	}

	/**
	 * Gets the last scheduled plan. This will be empty if schedule() or getPlanThreads() has never
	 * been called.
	 * 
	 * @return
	 */
	public TimeStampedPlan getScheduledPlan()
	{
		return timeStampedPlan;
	}

}
