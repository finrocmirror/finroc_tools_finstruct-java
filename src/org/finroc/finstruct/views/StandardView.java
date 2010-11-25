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
import java.util.ArrayList;
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
public class StandardView extends AbstractFinstructGraphView<AbstractFinstructGraphView.VertexAnnotation, AbstractFinstructGraphView.Edge> implements Transformer<AbstractFinstructGraphView.VertexAnnotation, String> {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Graph object */
    private DirectedSparseGraph<VertexAnnotation, Edge> graph = new DirectedSparseGraph<VertexAnnotation, Edge>();

    /** Layout object */
    private AbstractLayout<VertexAnnotation, Edge> layout = new FRLayout<VertexAnnotation, Edge>(graph);

    /** Visualization server */
    private BasicVisualizationServer<VertexAnnotation, Edge> vv = new BasicVisualizationServer<VertexAnnotation, Edge>(layout);

    public StandardView() {
        this.setBackground(Color.DARK_GRAY);
        //vv.setPreferredSize(new Dimension(350,350)); //Sets the viewing area size
        setLayout(new BorderLayout());
        add(vv, BorderLayout.CENTER);
        //vv.setBackground(Color.DARK_GRAY);

        Transformer<VertexAnnotation, Paint> vertexPaint = new Transformer<VertexAnnotation, Paint>() {
            public Paint transform(VertexAnnotation g) {
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
    protected void rootElementChanged(ArrayList<FrameworkElement> expandedElements) {

        // create new graph
        graph = new DirectedSparseGraph<VertexAnnotation, Edge>();
        final FrameworkElement root = getRootElement();
        Collection<VertexAnnotation> vertices;
        synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
            if (!root.isReady()) {
                return;
            }

            vertices = getVertices(root);

            // add vertices
            for (VertexAnnotation fe : vertices) {
                graph.addVertex(fe);
            }

            // add edges
            for (Edge e : getEdges(root, vertices)) {
                graph.addEdge(e, e.getSource(), e.getDestination(), EdgeType.DIRECTED);
            }
        }

        //layout.initialize();
        layout = new FRLayout<VertexAnnotation, Edge>(graph);
        layout.initialize();
        layout.setSize(new Dimension(vv.getWidth() - 115, vv.getHeight() - 25)); // sets the initial size of the space
        layout.setGraph(graph);

        // move special interfaces to fixed position
        for (VertexAnnotation ann : vertices) {
            if (ann != null && ann.hasFixedPos()) {
                layout.lock(ann, true);
                layout.setLocation(ann, ann.onRight() ? (layout.getSize().getWidth() - 25) : 25, ann.atBottom() ? (layout.getSize().getHeight() - 25) : 25);
            }
        }

        vv.setGraphLayout(layout);
        vv.repaint();
    }


    @Override
    public String transform(VertexAnnotation fe) {
        return fe.frameworkElement.getDescription();
    }

    @Override
    public Collection <? extends FrameworkElement > getExpandedElementsForHistory() {
        return null;
    }
}
