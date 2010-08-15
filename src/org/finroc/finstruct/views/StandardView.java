/**
 * You received this file as part of FinGUI - a universal
 * (Web-)GUI editor for Robotic Systems.
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
package org.finroc.finstruct.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;

import org.apache.commons.collections15.Transformer;
import org.finroc.core.Annotatable;
import org.finroc.core.ChildIterator;
import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.EdgeAggregator;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.net.NetPort;
import org.finroc.finstruct.FinstructView;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout2;
import edu.uci.ics.jung.algorithms.layout.ISOMLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;

/**
 * @author max
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardView extends FinstructView implements Transformer<FrameworkElement, String> {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Graph object */
    private DirectedSparseGraph<FrameworkElement, Edge> graph = new DirectedSparseGraph<FrameworkElement, Edge>();

    /** Layout object */
    private AbstractLayout<FrameworkElement, Edge> layout = new FRLayout<FrameworkElement, Edge>(graph);

    /** Visualization server */
    private BasicVisualizationServer<FrameworkElement, Edge> vv = new BasicVisualizationServer<FrameworkElement, Edge>(layout);

    public StandardView() {
        this.setBackground(Color.DARK_GRAY);
        //vv.setPreferredSize(new Dimension(350,350)); //Sets the viewing area size
        setLayout(new BorderLayout());
        add(vv, BorderLayout.CENTER);
        //vv.setBackground(Color.DARK_GRAY);

        Transformer<FrameworkElement, Paint> vertexPaint = new Transformer<FrameworkElement, Paint>() {
            public Paint transform(FrameworkElement fe) {
                if (isSensorInterface(fe)) {
                    return Color.YELLOW;
                } else if (isControllerInterface(fe)) {
                    return Color.RED;
                }
                return Color.BLUE;
            }
        };

        Transformer<Edge, Paint> edgePaint = new Transformer<Edge, Paint>() {
            public Paint transform(Edge e) {
                boolean sensorData = (e.dataTypeFlags & EdgeAggregator.SENSOR_DATA) != 0;
                boolean controllerData = (e.dataTypeFlags & EdgeAggregator.CONTROLLER_DATA) != 0;
                if (sensorData && (!controllerData)) {
                    return Color.YELLOW;
                }
                if (controllerData && (!sensorData)) {
                    return Color.RED;
                }
                return Color.BLACK;
            }
        };

        vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
        vv.getRenderContext().setVertexLabelTransformer(this);
        vv.getRenderContext().setEdgeDrawPaintTransformer(edgePaint);
        vv.getRenderContext().setArrowFillPaintTransformer(edgePaint);
        //vv.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);
    }

    @Override
    protected void rootElementChanged() {

        // create new graph
        graph = new DirectedSparseGraph<FrameworkElement, Edge>();
        final FrameworkElement root = getRootElement();
        ChildIterator ci = new ChildIterator(root);
        synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
            if (!root.isReady()) {
                return;
            }

            // add vertices
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                graph.addVertex(next);
            }

            // add edges
            FrameworkElementTreeFilter.Callback cb = new FrameworkElementTreeFilter.Callback() {
                @Override
                public void treeFilterCallback(FrameworkElement fe) {
                    if (fe.isChildOf(root)) {
                        NetPort np = ((AbstractPort)fe).asNetPort();
                        for (FrameworkElement feDest : np.getRemoteEdgeDestinations()) {
                            GetParentResult src = getParentInGraph(fe);
                            GetParentResult dest = getParentInGraph(feDest);
                            if (src.parent != null && dest.parent != null && src.parent != dest.parent) {
                                Edge e = graph.findEdge(src.parent, dest.parent);
                                if (e == null) {
                                    e = new Edge();
                                    graph.addEdge(e, src.parent, dest.parent, EdgeType.DIRECTED);
                                }
                                e.dataTypeFlags |= src.dataTypeFlags | dest.dataTypeFlags;
                            }
                        }
                    }
                }
            };
            FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(CoreFlags.IS_PORT | CoreFlags.STATUS_FLAGS, CoreFlags.IS_PORT | CoreFlags.READY | CoreFlags.PUBLISHED);
            filter.traverseElementTree(root, cb, new StringBuilder());

        }

        //layout.initialize();
        layout = new FRLayout<FrameworkElement, Edge>(graph);
        layout.initialize();
        layout.setSize(new Dimension(vv.getWidth() - 115, vv.getHeight() - 25)); // sets the initial size of the space
        layout.setGraph(graph);

        // move special interfaces to fixed position
        for (int controller = 0; controller <= 1; controller++) {
            for (int output = 0; output <= 1; output++) {
                ci.reset();
                FrameworkElement next = null;
                FrameworkElement found = null;
                int foundCount = 0;

                while ((next = ci.next()) != null) {
                    if ((controller == 1 ? isControllerInterface(next) : isSensorInterface(next)) && (output == 1 ? isOutputOnlyInterface(next) : isInputOnlyInterface(next))) {
                        found = next;
                        foundCount++;
                    }
                }

                if (foundCount == 1) {
                    layout.lock(found, true);
                    layout.setLocation(found, controller == 1 ? (layout.getSize().getWidth() - 25) : 25, ((controller + output) % 2 == 0) ? (layout.getSize().getHeight() - 25) : 25);
                }
            }
        }

        //System.out.println("radius " + layout.getRadius());
        //while((next = ci.next()) != null) {
        //    System.out.println(next.toString() + " vertex x " + layout.getX(next));
        //}


        vv.setGraphLayout(layout);
        vv.repaint();
    }


    /**
     * @param fe Framework Element
     * @return Output interface?
     */
    private static boolean isOutputOnlyInterface(FrameworkElement fe) {
        boolean one = false;
        boolean all = true;
        if (isInterface(fe)) {
            ChildIterator ci = new ChildIterator(fe);
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                if (next.isPort()) {
                    boolean output = next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= output;
                    all &= output;
                }
            }
        }
        return (one & all) || (all & fe.getDescription().toLowerCase().contains("output") && (!fe.getDescription().toLowerCase().contains("input")));
    }

    /**
     * @param fe Framework Element
     * @return Input interface?
     */
    private static boolean isInputOnlyInterface(FrameworkElement fe) {
        boolean one = false;
        boolean all = true;
        if (isInterface(fe)) {
            ChildIterator ci = new ChildIterator(fe);
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                if (next.isPort()) {
                    boolean input = !next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= input;
                    all &= input;
                }
            }
        }
        return (one & all) || (all & fe.getDescription().toLowerCase().contains("input") && (!fe.getDescription().toLowerCase().contains("output")));
    }

    /**
     * @param fe Framework Element
     * @return Is framework Element a sensor interface?
     */
    private static boolean isSensorInterface(FrameworkElement fe) {
        return isInterface(fe) && fe.getFlag(EdgeAggregator.SENSOR_DATA) && (!fe.getFlag(EdgeAggregator.CONTROLLER_DATA));
    }

    /**
     * @param fe Framework Element
     * @return Is framework Element a Controller interface?
     */
    private static boolean isControllerInterface(FrameworkElement fe) {
        return isInterface(fe) && fe.getFlag(EdgeAggregator.CONTROLLER_DATA) && (!fe.getFlag(EdgeAggregator.SENSOR_DATA));
    }

    /**
     * @param fe Framework Element
     * @return Is framework element an interface
     */
    private static boolean isInterface(FrameworkElement fe) {
        return fe.getFlag(CoreFlags.EDGE_AGGREGATOR) && fe.getFlag(EdgeAggregator.IS_INTERFACE);
    }


    /**
     * Returns parent of framework element that is displayed in graph
     *
     * @param fe Framework element whose parent to search for
     * @return Parent
     */
    private GetParentResult getParentInGraph(FrameworkElement fe) {
        GetParentResult result = new GetParentResult();
        for (int i = 0; i < fe.getLinkCount(); i++) {
            FrameworkElement current = fe;
            FrameworkElement parent = fe.getLink(i).getParent();
            while (parent != null) {
                if (current.getFlag(CoreFlags.EDGE_AGGREGATOR)) {
                    result.dataTypeFlags |= current.getAllFlags();
                }
                if (parent == getRootElement()) {
                    result.parent = current;
                    return result;
                }
                current = parent;
                parent = current.getParent();
            }
        }
        return result;
    }

    @Override
    public String transform(FrameworkElement fe) {
        return fe.getDescription();
    }

    class GetParentResult {
        FrameworkElement parent;
        int dataTypeFlags;
    }

    public class Edge extends Annotatable {
        int dataTypeFlags;
    }
}
