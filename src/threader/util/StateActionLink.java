package threader.util;


/**
 * The type of link between a state and an action which can be applied in it.
 * 
 * @author David Pattison
 *
 */
public enum StateActionLink
{
	/**
	 * There is no link.
	 */
	None,
	/**
	 * Indicates that while there may be an explicit link between the state and action, the link forms
	 * a larger merge of multiple states.
	 */
	Merge,
	/**
	 * There is a common object link.
	 */
	Object,
	/**
	 * There is a controller link.
	 * @deprecated Not used any more
	 */
	Controller,
	/**
	 * There is a causal link.
	 */
	Causal,
	/**
	 * There is both a causal and controller link.
	 * @deprecated Not used any more
	 */
	CausalController;
	
}

