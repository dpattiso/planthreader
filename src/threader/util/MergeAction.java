package threader.util;

import java.math.BigDecimal;

import javaff.data.NullInstantAction;
import javaff.data.TimeStampedAction;

public class MergeAction extends PlanScheduleAction
{
	/**
	 * The duration of a merge action. Can be instant (duration 0), or > 0. The
	 * default value is 0.0001.
	 */
	public static BigDecimal MergeDuration = new BigDecimal("0.0001");
	
	public MergeAction(int id, int time)
	{
		this(id, new BigDecimal(time));
	}
	
	public MergeAction(int id, BigDecimal time)
	{
		super(new TimeStampedAction(new NullInstantAction(), time, MergeAction.MergeDuration), id);
	}
	
	@Override
	public String toString()
	{
		return super.id+": "+super.getTime()+" MergeAction";
	}
	
	@Override
	public Object clone()
	{
		return new MergeAction(this.id, super.getTime()); //BigDecimals are immutable, so can just be passed directly without clone()
	}
	
	@Override
	public boolean equals(Object obj)
	{
		return obj instanceof MergeAction && super.equals(obj);
	}
}