package threader.util;

import threader.util.StateActionLink;




public class ActionLinkTuple implements Comparable<ActionLinkTuple>
{
	public StateActionLink link;
	public int matchCount;

	public ActionLinkTuple(StateActionLink link, int matches)
	{
		this.link = link;
		this.matchCount = matches;				
	}

	@Override
	public int compareTo(ActionLinkTuple o)
	{
		if (this.link.ordinal() > o.link.ordinal())
			return -1;
		else if (this.link.ordinal() < o.link.ordinal())
			return +1;
		else
		{
			//flipped order because we want the higher number of links to be prioritised
			return ((Integer)o.matchCount).compareTo((Integer)this.matchCount);
		}
	}
	
	@Override
	public String toString()
	{
		return this.link.toString()+" ("+this.matchCount+")";
	}
	
	@Override
	public Object clone()
	{
		return new ActionLinkTuple(this.link,  this.matchCount);
	}
}
