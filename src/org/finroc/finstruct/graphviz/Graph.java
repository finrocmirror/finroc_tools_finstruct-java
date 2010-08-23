/**
 * You received this file as part of Finstruct - a tool for
 * the Finroc Framework.
 *
 * Copyright (C) 2010 Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.finroc.finstruct.graphviz;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.util.Files;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * A graph or subgraph - that can be exported to or layout with graphviz.
 */
public class Graph extends GraphVizElement {

    /** lookup table for all graph elements */
    private ArrayList<GraphVizElement> elements = new ArrayList<GraphVizElement>(200);

    /** first possible free slot in list above */
    private int firstFreeElementSlot = 0;

    /** list of and lookup table for all graph vertices */
    private ArrayList<Vertex> vertices = new ArrayList<Vertex>(200);

    /** list of edges in this graph */
    private ArrayList<Edge> edges = new ArrayList<Edge>(200);

    /** list of subgraphs */
    private ArrayList<Graph> subgraphs = new ArrayList<Graph>(30);

    /** Parent graph - null if none */
    private final Graph parent;

    public enum Layout { dot, neato, fdp }

    public Graph() {
        addElement(this);
        parent = null;
    }

    /**
     * @param parentGraph Graph of which this graph is a subgraph
     */
    public Graph(Graph parentGraph) {
        parentGraph.addElement(this);
        parentGraph.subgraphs.add(this);
        parent = parentGraph;
    }

    /**
     * Add element to Graph
     *
     * @param e Element
     * @return handle of element in Graph
     */
    private int addElement(GraphVizElement e) {
        if (parent != null) {
            return parent.addElement(e);
        }
        for (int i = firstFreeElementSlot; i < elements.size(); i++) {
            if (elements.get(i) == null) {
                elements.set(i, e);
                firstFreeElementSlot = i + 1;
                e.setHandle(i);
                return i;
            }
        }
        elements.add(e);
        firstFreeElementSlot = elements.size();
        e.setHandle(elements.size() - 1);
        return elements.size() - 1;
    }

    /**
     * Add vertex to graph
     *
     * @param v Vertex
     */
    public void add(Vertex v) {
        addElement(v);
        vertices.add(v);
    }

    /**
     * Add edge to graph
     *
     * @param e edge
     */
    public void add(Edge e) {
        addElement(e);
        edges.add(e);
    }

    /**
     * @param handle Handle
     * @return Returns graph element with specified handle
     */
    public GraphVizElement getElement(int handle) {
        if (parent == null) {
            return elements.get(handle);
        } else {
            return parent.getElement(handle);
        }
    }

    /**
     * @param layout Layout that is used
     * @param keepPositions Keep node positions?
     * @return Content of dot file as string
     */
    public String getAsDotFile(Layout layout, boolean keepPositions) {
        StringBuffer sb = new StringBuffer();
        writeToDot(sb, layout, keepPositions);
        return sb.toString();
    }

    @Override
    public void writeToDot(StringBuffer sb, Layout layout, boolean keepPositions) {
        writeToDot(sb, layout, keepPositions, true);
    }

    /**
     * Write element to dot file
     *
     * @param sb StringBuffer to write to
     * @param layout Layout that is used
     * @param keepPositions Keep node positions?
     * @param isRoot Is this the root graph (or has parent been written?)
     */
    private void writeToDot(StringBuffer sb, Layout layout, boolean keepPositions, boolean isRoot) {

        if (isRoot) {
            sb.append("digraph \"finstruct\" {\n");
            if (layout != Layout.dot) {
                sb.append("null [shape=box, width=\"0.0001\", height=\"0.0001\", fixedsize=true, pos=\"0,0!\"];\n"); // in order to obtain null vector
            }
        } else {
            sb.append("subgraph cluster" + getHandle() + " {\n");
        }

        // add vertices
        for (Vertex v : vertices) {
            v.writeToDot(sb, layout, keepPositions);
        }

        // add edges
        for (Edge e : edges) {
            e.writeToDot(sb, layout, keepPositions);
        }

        // add subgraphs
        for (Graph g : subgraphs) {
            g.writeToDot(sb, layout, keepPositions, false);
        }

        // close graph
        sb.append("}\n");
    }

    /**
     * Apply layout to graph
     *
     * @param layout Layout to apply
     * @param keepPositions
     */
    public void applyLayout(Layout layout, boolean keepPositions) throws Exception {
        Process p = Runtime.getRuntime().exec(layout.name());
        String graph = getAsDotFile(layout, keepPositions);
        logDomain.log(LogLevel.LL_DEBUG, "GraphViz graph prior to layout", graph);
        PrintStream ps = new PrintStream(new BufferedOutputStream(p.getOutputStream()));
        ps.println(graph);
        ps.close();
        List<String> outputLines = Files.readLines(p.getInputStream());
        Point2D.Double nullVector = new Point2D.Double(0, 0);
        for (String s : outputLines) {
            logDomain.log(LogLevel.LL_DEBUG, "GraphViz graph after layout", s);
            if (s.trim().startsWith("null [")) {
                nullVector = toPoint(extractAttributeValue(s, "pos"));
            }

            if (s.contains("pos=\"") && s.contains(HANDLE_KEY + "=")) { // sign for edge or vertex
                //String tmp = s.split(HANDLE_KEY + "=")[1];
                //int handle = Integer.parseInt(tmp.substring(0, Math.min(tmp.indexOf(']'), tmp.indexOf(','))));
                getElement(Integer.parseInt(extractAttributeValue(s, HANDLE_KEY))).processLineFromLayouter(s, nullVector);
            }
        }
    }

    @Override
    public void processLineFromLayouter(String line, Double nullVector) {
        // do nothing, currently
    }

    /**
     * Remove all edges, vertices and subgraphs from graph
     */
    public void clear() {
        for (GraphVizElement elem : elements) {
            elem.resetHandle();
        }
        elements.clear();
        vertices.clear();
        edges.clear();
        subgraphs.clear();
    }
}
