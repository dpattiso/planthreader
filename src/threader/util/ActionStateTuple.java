package threader.util;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ActionStateTuple implements Comparable<ActionStateTuple>
{
	public ArrayList<PlanScheduleState> prevStates;
	public PlanScheduleState succState;
	public PlanScheduleAction action;
	public ArrayList<ActionLinkTuple> links;
	
	protected ActionStateTuple()
	{
		super();
		
		this.prevStates = new ArrayList<PlanScheduleState>();
		this.succState = null;
		this.action = null;
		this.links = new ArrayList<ActionLinkTuple>();
	}
	
	public ActionStateTuple(PlanScheduleState prevState,
			PlanScheduleState succState, PlanScheduleAction action,
			ActionLinkTuple link)
	{
		this();
		
		this.prevStates.add(prevState);
		this.succState = succState;
		this.action = action;
		this.links.add(link);
	}
	
	public ActionStateTuple(List<PlanScheduleState> prevStates,
			PlanScheduleState succState, PlanScheduleAction action,
			List<ActionLinkTuple> links)
	{
		this();
		
		if (prevStates.size() != links.size())
			throw new IllegalArgumentException("Predecessor states and action links sets must be of same size");
		
		this.prevStates = new ArrayList<PlanScheduleState>(prevStates);
		this.succState = succState;
		this.action = action;
		this.links = new ArrayList<ActionLinkTuple>(links);
	}
	
	@Override
	public String toString()
	{
		return this.action.toString();
	}

	@Override
	public int compareTo(ActionStateTuple o)
	{
		int res =  this.action.getTime().compareTo(o.action.getTime());
		if (res == 0)
			res = Integer.compare(this.action.id, o.action.id);
		
		return res;
	}
	
	@Override
	public int hashCode()
	{
		return this.action.hashCode() ^ this.links.hashCode() ^ this.prevStates.hashCode() ^ 
				this.succState.hashCode();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		ActionStateTuple other = (ActionStateTuple) obj;
		
		return this.action.equals(other.action) && this.links.equals(other.links) && this.prevStates.equals(other.prevStates)
				&& this.succState.equals(other.succState);
	}
	
	@Override
	public Object clone()
	{
		ActionStateTuple clone = new ActionStateTuple();
		clone.action = (PlanScheduleAction) this.action.clone();
		clone.links = (ArrayList<ActionLinkTuple>) this.links.clone();
		clone.prevStates = (ArrayList<PlanScheduleState>) this.prevStates.clone();
		clone.succState = (PlanScheduleState) this.succState.clone();
		
		return clone;
	}
}
