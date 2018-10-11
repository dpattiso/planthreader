package threader.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.ClassBasedEdgeFactory;


import javaff.data.Parameter;
import javaff.data.strips.STRIPSInstantAction;
import javaff.graph.StandardGraph;
import javaff.planning.TemporalMetricState;



public class PlanThreadGraph extends StandardGraph<PlanScheduleState, PlanScheduleAction>
{
	private HashMap<Integer, Set<PlanScheduleState>> stateLayers;
	private HashMap<Integer, Set<PlanScheduleAction>> actionLayers;
	private PlanScheduleState root;
	
	protected PlanThreadGraph()
	{
		super(PlanScheduleAction.class);
		
		this.root = null;
		this.stateLayers = new HashMap<Integer, Set<PlanScheduleState>>();
		this.actionLayers = new HashMap<Integer, Set<PlanScheduleAction>>();
	}
	
	public PlanThreadGraph(PlanScheduleState root)
	{
		this();
		
		this.addVertex(root);
		this.root = root;
	}
	
//	protected EdgeFactory<PlanScheduleState, PlanScheduleActionEdge> getEdgeFactory()
//	{
//		EdgeFactory<PlanScheduleState, PlanScheduleActionEdge> factory =
//				new ClassBasedEdgeFactory<PlanScheduleState, PlanScheduleActionEdge(PlanScheduleActionEdge.class);
//		
//		return factory;
//	}
//	
//	protected class PlanScheduleActionEdge
//	{
//		public PlanScheduleAction
//	}
	
	@Override
	public boolean equals(Object obj)
	{
		if (super.equals(obj) == false)
			return false;
		
		PlanThreadGraph other = (PlanThreadGraph) obj;
		if (this.root.equals(other.root) == false)
			return false;
		
		if (this.actionLayers.equals(other.actionLayers) == false)
			return false;
		if (this.stateLayers.equals(other.stateLayers) == false)
			return false;
		
		return true;
	}
	
	/**
	 * Gets all states in the graph which have no outgoing edges. This set is constructed at
	 * calling.
	 * @return
	 */
	public Set<PlanScheduleState> getLeaves()
	{
		HashSet<PlanScheduleState> heads = new HashSet<PlanScheduleState>();
		for (PlanScheduleState s : this.vertexSet())
		{
			if (this.outDegreeOf(s) == 0)
				heads.add(s);
		}
		
		return heads;
	}
	
	public boolean replaceState(int replaceId, PlanScheduleState newVertex)
	{
		for (PlanScheduleState s : this.vertexSet())
		{
			if (s.stateId == replaceId)
			{
				return this.replaceState(s, newVertex);
			}
		}
		
		return false;
	}	
	
	public boolean replaceState(PlanScheduleState toReplace, PlanScheduleState newVertex)
	{
//		return this.replaceVertex(toReplace.id, newVertex);
		if (super.assertVertexExist(toReplace) == false)
			return false;
		
		if (toReplace.stateId == this.root.stateId)
			this.root = newVertex;
		
		for (PlanScheduleState s : this.vertexSet())
		{
			if (s.equals(toReplace))
			{
				s.stateId = newVertex.stateId;
				s.state = newVertex.state;
				return true;
			}
		}
		
		return false;
	}
	
	public boolean containsVertex(int stateId)
	{
		return this.getVertex(stateId) != null;
	}
	
	public PlanScheduleState getVertex(int stateId)
	{
		for (PlanScheduleState s : super.vertexSet())
		{
			if (s.stateId == stateId)
				return s;
		}
		
		return null;
	}

	public void addVertex(PlanScheduleState state, int layer)
	{
		boolean added = super.addVertex(state);
//		if (this.vertexSet().size() == 1 && added) //if no other nodes exist, then assume this is the root.
//			this.root = state;

		if (this.stateLayers.containsKey(layer))
		{
			this.stateLayers.get(layer).add(state);
		}
		else
		{
			HashSet<PlanScheduleState> states = new HashSet<PlanScheduleState>();
			states.add(state);
			this.stateLayers.put(layer, states);
		}
	}
	

	public PlanScheduleState getInitialStateVertex()
	{
		return root;
	}
	
	/**
	 * Explicitly set the root. If the specified state is not already in the graph an exception is thrown.
	 * @param root
	 */
	public void setRoot(PlanScheduleState root)
	{
		if (super.containsVertex(root) == true)
			this.root = root; //inefficient. dont care.
		else
			throw new NullPointerException("Node not found in graph");		
	}

	public void addEdge(
			PlanScheduleState from, PlanScheduleState to,
			PlanScheduleAction action, int layer)
	{
		boolean newRoot = false;
		if (this.vertexSet().size() == 0)
			newRoot = true;
		
		boolean edge = super.addEdge(from, to, action);
		
		if (newRoot)
		{
			this.root = from;
		}

		if (this.actionLayers.containsKey(layer))
		{
			this.actionLayers.get(layer).add(action);
		}
		else
		{
			HashSet<PlanScheduleAction> actions = new HashSet<PlanScheduleAction>();
			actions.add(action);
			this.actionLayers.put(layer, actions);
		}
	}
	
	public void generateDotGraph(File dotFile)
    {
//		HashMap<TemporalMetricState
		
		
    	FileWriter writer;
    	BufferedWriter bufWriter;
    	try
    	{
	    	writer = new FileWriter(dotFile);
	    	bufWriter =  new BufferedWriter(writer);
	    	
	    	bufWriter.write("digraph Tree {\n\tnode [shape=circle, fontsize=14, color=black, fillcolor=white, fontcolor=black];\n\t edge [style=solid, color=black];\n");
	    	
	    	System.out.println("vertex size is "+this.vertexSet().size());
	    	System.out.println("edge size is "+this.edgeSet().size());
//		    	
//		    	int counter = 0;
//		    	for (Object v : this.vertexSet())
//		    	{
//		    		String vert = ((Vertex)v).toString().replace(' ', '_');
//
//	    			bufWriter.write(counter++ +" [label=\""+vert+"\"];\n");
//		    	}
	    	
	    	for (PlanScheduleAction e : super.edgeSet())
	    	{
//	    			bufWriter.write(counter++ +" [label=\""+e+"\"];\n");
	    		//System.out.println("Vertex: "+p);
//		    		Iterator<RelationshipEdge> outEdgesIter = this.graph.outgoingEdgesOf(p).iterator();
//		    		while (outEdgesIter.hasNext())
//		    		{
//		    			RelationshipEdge e = outEdgesIter.next();
	    			//System.out.println("Outgoing edge: "+e);

	    		String startVert = ""+super.getEdgeSource(e).stateId;
	    		String endVert = ""+super.getEdgeTarget(e).stateId;
//	    		String startVert = ""+super.getEdgeSource(e).stateId+" [label=\""+super.getEdgeSource(e).stateId+" "+super.getEdgeSource(e).time.toString()+"\"]";
//	    		String endVert = ""+super.getEdgeTarget(e).stateId+" [label=\""+super.getEdgeTarget(e).stateId+" "+super.getEdgeTarget(e).time.toString()+"\"]";
//	    		String startVert = getEdgeSource(e).state.toString().replace(' ', '_');
//	    		String endVert = getEdgeTarget(e).state.toString().replace(' ', '_');
//	    		startVert = startVert.replace('-', '_');
//	    		startVert = startVert.replace('#', '_');
//	    		startVert = startVert.replace(',', '_');
//	    		startVert = startVert.replace(' ', '_');
//	    		endVert = endVert.replace('-', '_');	    
//	    		endVert = endVert.replace('#', '_');	
//	    		endVert = endVert.replace(',', '_');
//	    		endVert = endVert.replace(' ', '_');   
	    		
	    		String edge = e.toString();
	    		edge = edge.replace('-', '_');	    
	    		edge = edge.replace('#', '_');	
	    		edge = edge.replace(',', '_');
	    		edge = edge.replace(' ', '_');
	    		edge = "\""+edge+"\"";
	    		bufWriter.write(startVert+" -> "+endVert+"[label="+edge+"];\n");
//		    		}
	    	}
	    	
	    	bufWriter.write("}\n");
	    	
    		//writer.close();
    		bufWriter.close();
    		
    		System.out.println("writing file "+dotFile.getAbsolutePath());
//	    		Process p = Runtime.getRuntime().exec("dot -Tpng \'"+dotFile.getAbsolutePath()+"_dot\' > \'test.png/'");
//	    		p.waitFor();
    	}
    	catch (IOException ioe)
    	{
    		System.out.println("Cannot create file: "+ioe.getMessage());
    		ioe.printStackTrace();
    	}
//			catch (InterruptedException e)
//			{
//	    		System.out.println("Cannot create file: "+e.getMessage());
//				e.printStackTrace();
//			}
    	finally
    	{
    	}
	}
	

	/**
	 * @author David Pattison
	 *
	 */
	public class PlanNode
	{
		public float time;
		public TemporalMetricState state;
		public HashSet<Parameter> objects;
		
		public PlanNode(TemporalMetricState state, float time)
		{
			this.state = state;
			this.time = time;
		}
		
		@Override
		public String toString()
		{
			return time+" "+state;
		}
	}

}