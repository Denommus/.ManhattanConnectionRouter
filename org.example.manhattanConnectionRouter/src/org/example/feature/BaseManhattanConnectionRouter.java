/*******************************************************************************
 * Copyright (c) offset11, offset12 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-voffset.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/
package org.example.feature;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.bpmn2.modeler.core.features.BendpointConnectionRouter;
import org.eclipse.bpmn2.modeler.core.features.ConnectionRoute;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.AnchorLocation;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil.BoundaryAnchor;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil.LineSegment;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.FixPointAnchor;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.services.Graphiti;


/**
 * A Connection Router that constrains all line segments of a connection to be either
 * horizontal or vertical; thus, diagonal lines are split into two segments that are
 * horizontal and vertical.
 * 
 * This is a final class because it needs to ensure the routing info for
 * the connection is cleaned up when it's done, so we don't want to allow
 * this class to be subclassed.
 */
public class BaseManhattanConnectionRouter extends BendpointConnectionRouter {

	private static final int A_STEP = 15;
	protected LineSegment sourceTopEdge;
	protected LineSegment sourceBottomEdge;
	protected LineSegment sourceLeftEdge;
	protected LineSegment sourceRightEdge;

	protected LineSegment targetTopEdge;
	protected LineSegment targetBottomEdge;
	protected LineSegment targetLeftEdge;
	protected LineSegment targetRightEdge;

	static final int offset = 10;
	static boolean testRouteSolver = false;
	int maxx = 0;
	int maxy = 0;
	

	public BaseManhattanConnectionRouter(IFeatureProvider fp) {
		super(fp);
	}


	private Map<Coordinate, Integer> g_score;
	private Map<Coordinate, Integer> f_score;
	private Map<Coordinate, Coordinate> came_from;

	@Override
	protected ConnectionRoute calculateRoute() {
		if (isSelfConnection())
			return super.calculateRoute();

		// The list of all possible routes. The shortest will be used.
		List<ConnectionRoute> allRoutes = new ArrayList<ConnectionRoute>();
		
		sourceAnchor = this.connection.getStart();
		targetAnchor = this.connection.getEnd();
		
		for(ContainerShape shape:findAllShapes()) {
			int x = Math.floorDiv(shape.getGraphicsAlgorithm().getWidth()+shape.getGraphicsAlgorithm().getX()+A_STEP*5, A_STEP);
			if(x>maxx) maxx=x;
			int y = Math.floorDiv(shape.getGraphicsAlgorithm().getHeight()+shape.getGraphicsAlgorithm().getY()+A_STEP*5, A_STEP);
			if(y>maxy) maxy=y;
		}

		
		Point startP;
		Point endP;

		startP = GraphicsUtil.createPoint(sourceAnchor);
		endP = GraphicsUtil.createPoint(targetAnchor);
		int startModifier = AnchorUtil.getBoundaryAnchorLocation(sourceAnchor).equals(AnchorLocation.LEFT) ? -20 : 20;
		int endModifier = AnchorUtil.getBoundaryAnchorLocation(targetAnchor).equals(AnchorLocation.LEFT) ? -20 : 20;
		Coordinate start = new Coordinate(Math.floorDiv(startP.getX()+startModifier, A_STEP), Math.floorDiv(startP.getY(), A_STEP));
		Coordinate end = new Coordinate(Math.floorDiv(endP.getX()+endModifier, A_STEP), Math.floorDiv(endP.getY(), A_STEP));
		ConnectionRoute route = new ConnectionRoute(this, allRoutes.size()+1, source,target);

		List<Coordinate> astarResult = aStar(start, end);
		List<Point> reducedAstar = calculateSegments(startP, astarResult, endP);
		route.getPoints().addAll(reducedAstar);
		allRoutes.add(route);
		
		drawConnectionRoutes(allRoutes);

		return route;
	}


	@Override
	protected ContainerShape getCollision(Point p1, Point p2) {
		return super.getCollision(p1, p2);
	}

	@Override
	protected List<Connection> findCrossings(Point start, Point end) {
		return super.findCrossings(start, end);
	}

	@Override
	protected List<ContainerShape> findAllShapes() {
		return super.findAllShapes();
	}

	protected List <BoundaryAnchor> getBoundaryAnchors(Shape s) {
		List <BoundaryAnchor> anchorList= new ArrayList<BoundaryAnchor> ();
		Iterator<Anchor> iterator = s.getAnchors().iterator();
		while (iterator.hasNext()) {
			Anchor anchor = iterator.next();
			String property = Graphiti.getPeService().getPropertyValue(anchor, AnchorUtil.BOUNDARY_FIXPOINT_ANCHOR);
			if (property != null && anchor instanceof FixPointAnchor) {
				BoundaryAnchor boundaryAnchor = new BoundaryAnchor();
				boundaryAnchor.anchor = (FixPointAnchor) anchor;
				boundaryAnchor.locationType = AnchorLocation.getLocation(property);
				boundaryAnchor.location = peService.getLocationRelativeToDiagram(anchor);
				anchorList.add(boundaryAnchor);
			}
		}
		return anchorList;
	}

	public List<Point> calculateSegments(Point start, List<Coordinate> points, Point end) {
		List<Point> result = new ArrayList<Point>();
		result.add(start);
		for (int i = points.size() - 1; i >= 0; i--) {
			Coordinate curr = points.get(i);
			if(i < 1 ){
				result.add(GraphicsUtil.createPoint(curr.x, curr.y));
				continue;
			}

			if( i > points.size() - 2){
				result.add(GraphicsUtil.createPoint(curr.x, curr.y));
				continue;
			}

			Coordinate prev = points.get(i+1);
			Coordinate next = points.get(i-1);
			if(prev.x==curr.x&&curr.x!=next.x) {
				result.add(GraphicsUtil.createPoint(curr.x, curr.y));
			}
			if(prev.y==curr.y&&curr.y!=next.y){
				result.add(GraphicsUtil.createPoint(curr.x, curr.y));
			}
		}
		result.add(end);
		
			
		return result;
	}
	
	public static class Coordinate {
		int x;
		int y;
		
		public Coordinate(int x, int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public String toString() {
			return "Coordinate [x=" + x + ", y=" + y + "]";
		}
		
	    public Point asPoint(){
	    	return GraphicsUtil.createPoint(x*A_STEP, y*A_STEP);
	    }



		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Coordinate other = (Coordinate) obj;
			if (x != other.x )
				return false;
			if (y != other.y )
				return false;
			return true;
		}
	}

	//Based on https://en.wikipedia.org/wiki/A*_search_algorithm
	protected List<Coordinate> aStar(Coordinate start, Coordinate goal) {
		if(getCollision(GraphicsUtil.createPoint(start.x, start.y),
				GraphicsUtil.createPoint(start.x, start.y))!=null ||
				getCollision(GraphicsUtil.createPoint(goal.x, goal.y),
						GraphicsUtil.createPoint(goal.x, goal.y))!=null) {
			List<Coordinate> alterResult = new ArrayList<Coordinate>();
			alterResult.add(new Coordinate(goal.x*A_STEP, goal.y*A_STEP));
			alterResult.add(new Coordinate(start.x*A_STEP, start.y*A_STEP));
			return alterResult;
		}
		
		
		Set<Coordinate> closedset = new HashSet<Coordinate>();
		Set<Coordinate> openset = new HashSet<Coordinate>();
		openset.add(start);
		came_from = new HashMap<Coordinate, Coordinate>();

		g_score = new HashMap<Coordinate, Integer>();
		g_score.put(start, 0);

		f_score = new HashMap<Coordinate, Integer>();
		Integer currentGScore = getGScore(start);
		f_score.put(start,currentGScore+heuristicCostEstimate(start, goal));

		while(!openset.isEmpty()) {
			Coordinate current = lowestFScore(f_score, openset);
			if(current.equals(goal)) {
				return reconstructPath(came_from, goal);
			}


			openset.remove(current);
			closedset.add(current);
			for(Coordinate neighbor:neighborNodes(current)) {
				Coordinate jumpPoint = jump(neighbor, current, goal);
				if(jumpPoint!=null) {
					if(closedset.contains(jumpPoint)) continue;
					int tentative_g_score = heuristicCostEstimate(current, jumpPoint)+getGScore(current);
					if(!openset.contains(jumpPoint) || getGScore(jumpPoint)==null || getGScore(jumpPoint) > tentative_g_score) {
						g_score.put(jumpPoint, tentative_g_score);
						f_score.put(jumpPoint, tentative_g_score+heuristicCostEstimate(jumpPoint, goal));
						came_from.put(jumpPoint, current);
						openset.add(jumpPoint);
					}
				}
			}
		}

		List<Coordinate> alterResult = new ArrayList<Coordinate>();
		alterResult.add(new Coordinate(goal.x*A_STEP, goal.y*A_STEP));
		alterResult.add(new Coordinate(start.x*A_STEP, start.y*A_STEP));
		return alterResult;
	}


	private Integer getGScore(Coordinate start) {
		Integer currentGScore = g_score.get(start);
		return currentGScore;
	}

	public int heuristicCostEstimate(Coordinate a, Coordinate b) {
		return Math.abs(a.x-b.x)+Math.abs(a.y-b.y);
	}

	private Coordinate lowestFScore(Map<Coordinate, Integer> f_score, Set<Coordinate>  openset) {
		Coordinate minCoord = null;
		Integer min = null;
		for(Coordinate entry : openset) {
			int f = f_score.get(entry);
			if((min==null || min > f)) {
				min = f;
				minCoord = entry;
			}
		}
		return minCoord;
	}

	private List<Coordinate> neighborNodes(Coordinate current) {
		List<Coordinate> list = new ArrayList<Coordinate>();
		Coordinate parent = came_from.get(current);
		if(parent!=null) {
			int dx = (current.x - parent.x) / Math.max(Math.abs(current.x - parent.x), 1);
			int dy = (current.y - parent.y) / Math.max(Math.abs(current.y - parent.y), 1);
			if(dx!=0) {
				Coordinate neighbor1 = new Coordinate(current.x, current.y-1);
				Coordinate neighbor2 = new Coordinate(current.x, current.y+1);
				Coordinate neighbor3 = new Coordinate(current.x+dx, current.y);
				if(walkable(neighbor1, current)) list.add(neighbor1);
				if(walkable(neighbor2, current)) list.add(neighbor2);
				if(walkable(neighbor3, current)) list.add(neighbor3);
			}
			else if(dy!=0) {
				Coordinate neighbor1 = new Coordinate(current.x+1, current.y);
				Coordinate neighbor2 = new Coordinate(current.x-1, current.y);
				Coordinate neighbor3 = new Coordinate(current.x, current.y+dy);
				if(walkable(neighbor1, current)) list.add(neighbor1);
				if(walkable(neighbor2, current)) list.add(neighbor2);
				if(walkable(neighbor3, current)) list.add(neighbor3);
			}
		}
		else {
			Coordinate neighbor1 = new Coordinate(current.x, current.y-1);
			Coordinate neighbor2 = new Coordinate(current.x, current.y+1);
			Coordinate neighbor3 = new Coordinate(current.x+1, current.y);
			Coordinate neighbor4 = new Coordinate(current.x-1, current.y);
			if(walkable(neighbor1, current)) list.add(neighbor1);
			if(walkable(neighbor2, current)) list.add(neighbor2);
			if(walkable(neighbor3, current)) list.add(neighbor3);
			if(walkable(neighbor4, current)) list.add(neighbor4);
		}
		return list;
	}
	
	private Coordinate jump(Coordinate current, Coordinate neighbor, Coordinate goal) {
		while(walkable(current, neighbor)) {
			int dx = current.x - neighbor.x;
			int dy = current.y - neighbor.y;

			if(current.equals(goal)) return current;

			if(dx!=0) {
				if((walkable(new Coordinate(current.x, current.y-1), current) && !walkable(new Coordinate(current.x-dx, current.y-1), current)) ||
						(walkable(new Coordinate(current.x, current.y+1), current) && !walkable(new Coordinate(current.x-dx, current.y+1), current))) {
					return current;
				}
			} else if(dy!=0) {
				if((walkable(new Coordinate(current.x-1, current.y), current) && !walkable(new Coordinate(current.x-1, current.y-dy), current)) ||
						(walkable(new Coordinate(current.x+1, current.y+1), current) && !walkable(new Coordinate(current.x+1, current.y-dy), current))) {
					return current;
				}

				if(jump(new Coordinate(current.x+1, current.y), current, goal)!=null
						|| jump(new Coordinate(current.x-1, current.y), current, goal)!=null) return current;
			} else {
				throw new InvalidParameterException("No diagonal movement allowed");
			}
			neighbor = current;
			current = new Coordinate(current.x+dx, current.y+dy);
		}
		return null;
	}

	private boolean walkable(Coordinate node, Coordinate prev) {
		if(node.x<0 || node.y<0 || node.x>maxx || node.y > maxy) return false;
		if(getCollision(node.asPoint(),node.asPoint())!=null) return false;
		List<Connection> crossings = findCrossings(node.asPoint(), prev.asPoint());
		for(Connection c : crossings) {
			if(c!=this.connection) return false;
		}
		return true;
	}

	private List<Coordinate> reconstructPath(Map<Coordinate, Coordinate> came_from, Coordinate current) {
		List<Coordinate> totalPath = new ArrayList<Coordinate>();
		totalPath.add(new Coordinate(current.x*A_STEP, current.y*A_STEP));
		while(came_from.containsKey(current)) {
			current = came_from.get(current);
			totalPath.add(new Coordinate(current.x*A_STEP, current.y*A_STEP));
		}
		return totalPath;
	}
}
