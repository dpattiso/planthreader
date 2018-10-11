package threader.util;

import java.math.BigDecimal;

import javaff.data.Action;
import javaff.data.TimeStampedAction;
import javaff.data.strips.STRIPSInstantAction;

/**
 * Simple wrapper class for scheduling plan steps.
 * @author David Pattison
 *
 */
public class PlanScheduleAction implements Comparable<PlanScheduleAction>
{
	public TimeStampedAction action;
	public int id;
//	public int time;
	
	public PlanScheduleAction(TimeStampedAction action, int id)
	{
		this.action = action;
		this.id = id;
//		this.time = earliestStart;
	}
	
	public Object clone()
	{
		return new PlanScheduleAction((TimeStampedAction) this.action.clone(), this.id);
	}
	
	public BigDecimal getTime()
	{
		return this.action.getTime();//getMajorTime();
	}
	
	@Override
	public String toString()
	{
		return ""+id +": "+action.toString();
	}
	
	@Override
	public int hashCode()
	{
		return id ^ action.hashCode();
	}

	/**
	 * Compares equality, does not include "repeat" field value.
	 */
	@Override
	public boolean equals(Object obj)
	{
		PlanScheduleAction other =(PlanScheduleAction) obj;
		return this.id == other.id && this.action.equals(other.action);
	}

	@Override
	public int compareTo(PlanScheduleAction other)
	{
		if (this.id < other.id)
			return -1;
		else if (this.id == other.id)
		{
			return 0;
		}
		else 
			return 1;
	}
}	
