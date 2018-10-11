package threader.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javaff.planning.STRIPSState;
import javaff.planning.State;
import javaff.planning.TemporalMetricState;

/**
 * Simple wrapper class for scheduling plan states.
 * @author David Pattison
 *
 */
public class PlanScheduleState implements Comparable<PlanScheduleState>
{
	public STRIPSState state;
	public int stateId;
	public BigDecimal time;

//	public PlanScheduleState(STRIPSState state, int stateId)
//	{
//		this.state = state;
//		this.id = stateId;
//		this.time = 0;
//	}
	
	public PlanScheduleState(STRIPSState state, int stateID, BigDecimal time)
	{
		this.state = state;
		this.stateId = stateID;
		this.time = time;
	}
	
	@Override
	public String toString()
	{
		return stateId+"";//+": "+tmstate.toString();
	}
	
	@Override
	public int hashCode()
	{
		return stateId ^ state.hashCode() ^ time.hashCode();
	}

	/**
	 * Compares equality, does not include "repeat" field value.
	 */
	@Override
	public boolean equals(Object obj)
	{
		PlanScheduleState other = (PlanScheduleState) obj;
		return this.stateId == other.stateId && this.state.equals(other.state) && this.time.equals(other.time);
	}
	
	@Override
	public Object clone()
	{
		PlanScheduleState clone = new PlanScheduleState((STRIPSState) this.state.clone(), this.stateId, this.time);
		return clone;
	}

	/**
	 * Compares this scheduled state with another. The result is based upon the ID of each
	 * state. 
	 * @return -1 if this state's ID is less than the other state's ID, 0 if equal, and +1 if greater.
	 */
	@Override
	public int compareTo(PlanScheduleState other)
	{
		if (this.stateId < other.stateId)
			return -1;
		else if (this.stateId == other.stateId)
			return 0;
		else 
			return 1;
	}
	
	public BigDecimal getTime()
	{
		return this.time;
	}


	/**
	 * Returns the major time this state is scheduled for. The major time is the
	 * number preceding the floating point, i.e. 2.0001 has major time 2.
	 * @return
	 */
	public BigDecimal getMajorTime()
	{
//		return majorTime;
		return this.getTime().setScale(1, RoundingMode.DOWN);
	}
}