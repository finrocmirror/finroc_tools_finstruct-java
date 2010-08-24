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
package org.finroc.finstruct.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;
import java.util.Collection;

import org.apache.commons.collections15.Transformer;
import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;

/**
 * @author max
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardView extends AbstractFinstructGraphView<AbstractFinstructGraphView.VertexAnnotation, AbstractFinstructGraphView.Edge> implements Transformer<FrameworkElement, String> {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Graph object */
    private DirectedSparseGraph<FrameworkElement, Edge> graph = new DirectedSparseGraph<FrameworkElement, Edge>();

    /** Layout object */
    private AbstractLayout<FrameworkElement, Edge> layout = new FRLayout<FrameworkElement, Edge>(graph);

    /** Visualization server */
    private BasicVisualizationServer<FrameworkElement, Edge> vv = new BasicVisualizationServer<FrameworkElement, Edge>(layout);

    public StandardView() {
        super(VertexAnnotation.class);
        this.setBackground(Color.DARK_GRAY);
        //vv.setPreferredSize(new Dimension(350,350)); //Sets the viewing area size
        setLayout(new BorderLayout());
        add(vv, BorderLayout.CENTER);
        //vv.setBackground(Color.DARK_GRAY);

        Transformer<FrameworkElement, Paint> vertexPaint = new Transformer<FrameworkElement, Paint>() {
            public Paint transform(FrameworkElement fe) {
                VertexAnnotation g = getAnnotation(fe);
                if (g != null) {
                    return g.getColor();
                }
                return Color.blue;
            }
        };

        Transformer<Edge, Paint> edgePaint = new Transformer<Edge, Paint>() {
            public Paint transform(Edge e) {
                return e.getColor();
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
        Collection<FrameworkElement> vertices;
        synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
            if (!root.isReady()) {
                return;
            }

            vertices = getVertices(root);

            // add vertices
            for (FrameworkElement fe : vertices) {
                graph.addVertex(fe);
            }

            // add edges
            for (Edge e : getEdges(root)) {
                graph.addEdge(e, e.getSource().frameworkElement, e.getDestination().frameworkElement, EdgeType.DIRECTED);
            }
        }

        //layout.initialize();
        layout = new FRLayout<FrameworkElement, Edge>(graph);
        layout.initialize();
        layout.setSize(new Dimension(vv.getWidth() - 115, vv.getHeight() - 25)); // sets the initial size of the space
        layout.setGraph(graph);

        // move special interfaces to fixed position
        for (FrameworkElement fe : vertices) {
            VertexAnnotation ann = getAnnotation(fe);
            if (ann != null && ann.hasFixedPos()) {
                layout.lock(fe, true);
                layout.setLocation(fe, ann.onRight() ? (layout.getSize().getWidth() - 25) : 25, ann.atBottom() ? (layout.getSize().getHeight() - 25) : 25);
            }
        }

        vv.setGraphLayout(layout);
        vv.repaint();
    }


    @Override
    public String transform(FrameworkElement fe) {
        return fe.getDescription();
    }


}
