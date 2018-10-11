package threader.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javaff.data.Fact;
import javaff.data.Parameter;
import javaff.data.TimeStampedAction;
import javaff.data.strips.Not;
import javaff.data.strips.SingleLiteral;
import javaff.planning.STRIPSState;
import javaff.planning.State;

public class PlanThread implements Comparable<PlanThread>
{
	protected TreeSet<ActionStateTuple> actionTuples;
	
	protected Set<SingleLiteral> unusedFacts;
	protected final PlanScheduleState initialState;
	protected Set<Parameter> objectsUsed;
	
	private boolean hasMissingActions;
	
	public PlanThread(PlanScheduleState init)
	{
		this.initialState = init;

		this.actionTuples = new TreeSet<ActionStateTuple>();
		
		this.unusedFacts = new HashSet<SingleLiteral>();
		this.objectsUsed = new HashSet<Parameter>();
		
		this.hasMissingActions = false;
		
		this.update();
	}
	
	/**
	 * Returns a copy of this thread trimmed to the specified time. Note that only the
	 * major time of the successor state is used.
	 * 
	 * The method operates by iterating through each {@link ActionStateTuple} held within this thread
	 * and comparing each tuple's successor state timestamp. However, only the result of 
	 * {@link PlanScheduleState#getMajorTime()} is used, lest epsilon times give unwanted output
	 * (i.e. a state at time 1.0001 being rejected because the latest state time is 1.0000).
	 * @param latestStateTime The latest time for which a successor state will be considered.
	 * @return
	 */
	public PlanThread trim(BigDecimal latestStateTime)
	{
		PlanThread newThread = new PlanThread((PlanScheduleState) this.initialState);
		
		for (ActionStateTuple t : this.actionTuples)
		{
			if (t.succState.getMajorTime().compareTo(latestStateTime) <= 0)
			{
				newThread.actionTuples.add(t); //directly reference the set, so we don't have expensive calls to update()
			}
		}
		
		newThread.update(); //update object/facts used
		
		return newThread;
		
	}
	
//	/**
//	 * Gets the length of this tuple which is equal to the number of parallel action sets, NOT the number
//	 * of actions contained within this head. That is, if the head contains actions A and B which are
//	 * applicable at time T=1, it has length 1. See getActionLength() for this other behaviour (length = 2).
//	 * @return
//	 */
//	public int getLength()
//	{
//		return this.previousActions.size();
//	}
	
	/**
	 * Returns the number of actions in this thread. This is the number of actions at all timesteps. See
	 * getLength() for the simple/scheduled length of the thread.
	 * @return
	 */
	public int getActionLength()
	{
		return this.actionTuples.size();		
	}
		
	/**
	 * Returns the timestamp of this threads head state.
	 * @return
	 */
	public BigDecimal getEndTime()
	{
		return this.getHead().time;
	}
	
	/**
	 * Branch at the end of this plan thread. The new thread is returned, while the original
	 * is unmodified. 
	 * @param toAppend
	 * @return
	 * @see #branch(BigDecimal, ActionStateTuple)
	 */
	public PlanThread branch(ActionStateTuple toAppend)
	{
		BigDecimal lastTime = this.getEndTime();
		
		return this.branch(lastTime, toAppend);
	}
	
	/**
	 * Branch from this thread at the specified timestamp. Branching clones the thread up to the specified
	 * time, then append the specified action.
	 * 
	 * @param branchTime
	 * @param toAppend
	 * @return The newly branched thread.
	 */
	public PlanThread branch(BigDecimal branchTime, ActionStateTuple toAppend)
	{
		PlanThread branch = new PlanThread(this.initialState);
	
		for (ActionStateTuple t : this.actionTuples)
		{			
			int result = t.succState.time.compareTo(branchTime);
			if (result <= 0)
			{
//				branch.actionTuples.add((ActionStateTuple) t.clone());
				branch.actionTuples.add(t);
			}
		}
		branch.add(toAppend); //add() also calls update()
		
		return branch;
	}


	@Override
	public Object clone()
	{
		PlanThread newTup = new PlanThread((PlanScheduleState) this.initialState.clone());
		
		newTup.actionTuples = (TreeSet<ActionStateTuple>) this.actionTuples.clone();
		newTup.unusedFacts = new HashSet<SingleLiteral>(this.unusedFacts);
		newTup.objectsUsed = new HashSet<Parameter>(this.objectsUsed);
		newTup.update();
		
		return newTup;
	}
	
	@Override
	public int hashCode()
	{
//		int hash = this.actionTuples.hashCode() ^ this.unusedFacts.hashCode() ^ this.initialState.hashCode()
//			    ^ this.objectsUsed.hashCode();
//		
//		return hash;
		
		return this.getHead().stateId;
	}
	
	@Override
	public String toString()
	{
		return this.getHead().toString();//+", achieved by "+this.getLastAction().toString();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		PlanThread other = (PlanThread) obj;
		
		if (this.initialState.equals(other.initialState) == false)
			return false;
		
		if (this.objectsUsed.equals(other.objectsUsed) == false)
			return false;
		
		if (this.actionTuples.equals(other.actionTuples) == false)
			return false;
		
		if (this.unusedFacts.equals(other.unusedFacts) == false)
			return false;
		
		return true;
	}

	
	public boolean add(PlanScheduleState prevState, PlanScheduleState succState, PlanScheduleAction action, ActionLinkTuple link)
	{
		ActionStateTuple tuple = new ActionStateTuple(prevState, succState,  action,link);

		return this.add(tuple);
	}
	
	public PlanScheduleState getHead()
	{
		if (this.getActionLength() == 0)
			return this.initialState;
		
		return this.actionTuples.last().succState;
	}
	
	public boolean addAll(Collection<ActionStateTuple> tuples)
	{
		boolean allAdded = true;
		for (ActionStateTuple t : tuples)
		{
			for (PlanScheduleState s : t.prevStates)
			{
				if (t.action.action.isApplicable(s.state) == false)
					throw new IllegalArgumentException("Cannot apply action in predecessor state: "+t.action);
			}
			
			boolean res = this.actionTuples.add(t);
			if (!res)
				allAdded = false;
		}
		
		//only update once all actions have been added in order to speed up return
		this.update();
		
		return allAdded;
	}
	
	public boolean add(ActionStateTuple tuple)
	{
		for (PlanScheduleState s : tuple.prevStates)
		{
			if (tuple.action.action.isApplicable(s.state) == false)
				throw new IllegalArgumentException("Cannot apply action in plan head "+s.stateId+": "+tuple.action);
		}
		
		boolean res = this.actionTuples.add(tuple);
		
		if (res)
			this.update();
		
		return res;
	}
	
	public STRIPSState computeCurrentState()
	{
		STRIPSState curr = (STRIPSState) this.initialState.state;
		for (ActionStateTuple tuple : this.actionTuples)
		{
			if (tuple.action.action.isApplicable(curr) == false && this.hasMissingActions() == false)
				throw new IllegalArgumentException("Action which currently exists in plan head "+this.toString()+" is not applicable at assigned timestep: "+tuple.action.action.toString());
			
			curr = (STRIPSState) curr.apply(tuple.action.action.getAction());
		}
		
		return curr;
	}

	public boolean hasCausalLink(PlanScheduleAction action)
	{
		for (Object po : action.action.getPreconditions())
		{
			if (this.unusedFacts.contains(po))
				return true;
		}
		
		return false;
	}
	
	public int hasObjectLink(PlanScheduleAction action)
	{
		int hits = 0;
		for (Object po : action.action.getParameters())
		{
			if (this.objectsUsed.contains(po))
				hits++;
		}
		
		return hits;
	}
	
	/**
	 * Updates the internal state of the thread -- unused achieved facts and objects which
	 * have appeared as parameters.
	 */
	protected void update()
	{
		HashSet<SingleLiteral> unused = new HashSet<SingleLiteral>();
		this.unusedFacts.clear();
		this.objectsUsed.clear();
		
		for (ActionStateTuple t : this.actionTuples)
		{
			if (t.action instanceof MergeAction)
			{
				continue;
			}
			
			//update effects/causal links
			Set<Fact> add = t.action.action.getAddPropositions();

			for (Fact f : add)
			{
				unused.add((SingleLiteral) f);
			}
			//remove the deleted facts
			for (Not del : t.action.action.getDeletePropositions())
			{
				unused.remove(del.getLiteral());
			}
			
			//update objects used -- NO LONGER USED -- WILL NEVER BE APPLIED IN NEW ALGORITHM, AS ONLY CAUSAL LINKS CONSIDERED
			this.objectsUsed.addAll(t.action.action.getParameters());
		}
		this.unusedFacts = unused;
	}
	
	public BigDecimal getStartTime()
	{
		return this.initialState.time; 
	}

	public PlanScheduleState getInitialState()
	{
		return initialState;
	}


	public SortedSet<ActionStateTuple> getActions()
	{
		return this.actionTuples;
	}


	public Set<SingleLiteral> getUnusedFacts()
	{
		return unusedFacts;
	} 

	public Set<Parameter> getObjectsUsed()
	{
		return objectsUsed;
	} 

	public void setUnusedFacts(Set<SingleLiteral> unusedFacts)
	{
		this.unusedFacts = unusedFacts;
	}

	/**
	 * Compares the two thread's current states, which will in turn compare their IDs. Note that
	 * it should be illegal for two threads to have the same current state, as this would make them part of the 
	 * same thread -- something which should have been fixed before reaching this point.
	 */
	@Override
	public int compareTo(PlanThread other)
	{
		int thisLastId = (this.getActionLength() == 0) ? this.initialState.stateId : this.actionTuples.last().succState.stateId;
		int otherLastId = (other.getActionLength() == 0) ? other.initialState.stateId :other.actionTuples.last().succState.stateId;
		
		return Integer.compare(thisLastId, otherLastId);
	}

	/**
	 * Returns true if this thread has actions in it which do not have all of their preconditions
	 * satisfied by a predecessor action. This occurs in partially-observable environments.
	 * @return
	 */
	public boolean hasMissingActions()
	{
		return hasMissingActions;
	}

	public void setHasMissingActions(boolean hasMissingActions)
	{
		this.hasMissingActions = hasMissingActions;
	}

	/**
	 * The Cost of a thread is the total cost of the actions which comprise it. In a STRIPS only context, 
	 * this is the number of "real" actions which appear in the thread. MergeActions have cost of 0.
	 * @return
	 */
	public BigDecimal getCost()
	{
		BigDecimal cost = BigDecimal.ZERO;
		for (ActionStateTuple s : this.getActions())
		{
			cost = cost.add(s.action.action.getCost());
		}
		
		return cost;
	}
	
	/**
	 * Gets the cost of this plan thread as a double. Should only be used if you are sure that overflow is not
	 * going to occur when computing the real cost.
	 * @see #getCost()
	 * @return The result of {@link #getCost()} as a double.
	 */
	public double getCostAsDouble()
	{
		return this.getCost().doubleValue();
	}
}