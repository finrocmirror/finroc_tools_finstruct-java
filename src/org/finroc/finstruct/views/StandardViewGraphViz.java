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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.Collection;

import javax.swing.JLabel;

import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.finstruct.graphviz.Graph;
import org.finroc.finstruct.graphviz.Graph.Layout;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardViewGraphViz extends AbstractFinstructGraphView<StandardViewGraphViz.Vertex, StandardViewGraphViz.Edge> {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Font for display */
    static Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    /** Label for getting node bounds - may only be used in synchronized context */
    private final JLabel testLabel = new JLabel();

    /** current GraphViz graph */
    private Graph graph = new Graph();

    /** List of vertices */
    private Collection<FrameworkElement> vertices;

    /** List of edges */
    private Collection<Edge> edges;

    public StandardViewGraphViz() {
        super(Vertex.class);
        testLabel.setFont(FONT);
        this.setBackground(Color.LIGHT_GRAY);
        setLayout(null);
    }

    @Override
    protected Edge createEdgeInstance(FrameworkElement source, FrameworkElement dest) {
        return new Edge(getOrCreateAnnotation(source), getOrCreateAnnotation(dest));
    }

    @Override
    protected Vertex createVertexInstance(FrameworkElement fe) {
        return new Vertex(fe);
    }

    @Override
    protected synchronized void rootElementChanged() {

        try {
            final FrameworkElement root = getRootElement();

            int width = getWidth();
            int height = getHeight();
            //List<String> outputLines;
            graph.clear();
            synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
                if (!root.isReady()) {
                    repaint();
                    return;
                }

                // create new graph
                vertices = getVertices(root);
                if (vertices.size() == 0) {
                    repaint();
                    return;
                }

                // add vertices
                for (FrameworkElement fe : vertices) {
                    Vertex v = getOrCreateAnnotation(fe);
                    graph.add(v.gvVertex);
                    int wh = (int)((v.gvVertex.getWidth() + 1) / 2) + 3;
                    int hh = (int)((v.gvVertex.getHeight() + 1) / 2) + 3;
                    if (v.hasFixedPos()) {
                        v.gvVertex.setFixedPosition(v.onRight() ? (width - wh) : wh, v.atBottom() ? (height - hh) : hh);
                        v.gvVertex.setRank(v.atBottom() ? "source" : "sink");
                        //ps.print(", pos=\"" + toInch(v.onRight() ? (width - wh) : wh) + "," + toInch(v.atBottom() ? (height - hh) : hh) + "!\"");
                    }
                }

//              Process p = Runtime.getRuntime().exec("neato");
//              PrintStream ps = new PrintStream(new BufferedOutputStream(p.getOutputStream()));
//              //PrintStream ps = System.out;
//              ps.println("digraph \"finstruct\" {");
//              //ps.println("graph [bb= \"0,0," + width + "," + height + "\"];"); // graph bounding box
//              ps.println("null [shape=box, width=\"0.0001\", height=\"0.0001\", fixedsize=true, pos=\"0,0!\"];");
//
//              // add vertices
//              for (FrameworkElement fe : vertices) {
//                  FinstructVertex ann = getOrCreateAnnotation(fe);
//                  Vertex v = new Vertex(fe, ann);
//                  ann.graphObject = v;
//                  ps.print("" + fe.getHandle() + " [shape=box, width=\"" + v.getInchWidth() + "\", height=\"" + v.getInchHeight() + "\"");
//                  int wh = (int)((v.getPreferredSize().getWidth() + 1) / 2) + 3;
//                  int hh = (int)((v.getPreferredSize().getHeight() + 1) / 2) + 3;
//                  if (ann.hasFixedPos()) {
//                      ps.print(", pos=\"" + toInch(ann.onRight() ? (width - wh) : wh) + "," + toInch(ann.atBottom() ? (height - hh) : hh) + "!\"");
//                  }
//                  ps.println("];");
//              }

                // add edges
                edges = getEdges(root);
                for (Edge e : edges) {
                    graph.add(e.gvEdge);
                    if (e.isControllerData()) {
                        e.gvEdge.setReversedInDotLayout(true);
                    }
                }
            }

            // Layout
            graph.applyLayout(Layout.dot, false);

//          boolean started = false;
//          for (String s : outputLines) {
//              System.out.println(s);
//              s = s.trim();
//              if (s.startsWith("null ")) {
//                  started = true;
//                  continue;
//              }
//              if (s.startsWith("}")) {
//                  break;
//              }
//              if (!started) {
//                  continue;
//              }
//              boolean edge = s.contains(" -> ");
//              if (!edge) {
//                  FrameworkElement fe = RuntimeEnvironment.getInstance().getElement(Integer.parseInt(s.split("\\[")[0].replace('"', ' ').trim()));
//                  FinstructVertex ann = getAnnotation(fe);
//                  Vertex v = (Vertex)ann.graphObject;
//                  int wh = (int)((v.getPreferredSize().getWidth() + 1) / 2);
//                  int hh = (int)((v.getPreferredSize().getHeight() + 1) / 2);
//                  add(v);
//                  String posString = s.split("pos=\"")[1].split("\"")[0];
//                  int x = Integer.parseInt(posString.split(",")[0]);
//                  int y = Integer.parseInt(posString.split(",")[1]);
//                  v.setBounds(x - wh, y - hh, (int)v.getPreferredSize().getWidth(), (int)v.getPreferredSize().getHeight());
//              }
//          }

            repaint();
        } catch (Exception e) {
            logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (edges == null || vertices == null) {
            return;
        }

        // draw edges
        for (Edge e : edges) {
            e.paint(g2d);
        }

        // draw vertices
        for (FrameworkElement fe : vertices) {
            Vertex v = getAnnotation(fe);
            v.paint(g2d);
        }

        //g.drawString("This is my custom Panel!",10,20);
        //redSquare.paintSquare(g);
    }


    /**
     * Combined (Finstruct/GraphViz vertex)
     */
    class Vertex extends AbstractFinstructGraphView.VertexAnnotation {

        /** graphviz vertex */
        private org.finroc.finstruct.graphviz.Vertex gvVertex;

        public Vertex(FrameworkElement fe) {
            super(fe);
            gvVertex = new org.finroc.finstruct.graphviz.Vertex();
            testLabel.setText(fe.getDescription());
            gvVertex.setSize(testLabel.getPreferredSize().getWidth() + 2, testLabel.getPreferredSize().getHeight() + 2);
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Color old = g2d.getColor();
            g2d.setColor(getColor());
            Point2D.Double pos = gvVertex.getLayoutPosition();
            double x2 = pos.x - (gvVertex.getWidth() / 2);
            double y2 = pos.y - (gvVertex.getHeight() / 2);
            g2d.fillRect((int)x2, (int)y2, (int)gvVertex.getWidth(), (int)gvVertex.getHeight());
            g2d.setColor(getTextColor());
            g2d.drawString(frameworkElement.getDescription(), (int)x2 + 1, (int)(y2 + gvVertex.getHeight() - 3));
            g2d.setColor(old);
        }
    }

    /**
     * Combined (Finstruct/GraphViz edge)
     */
    class Edge extends AbstractFinstructGraphView.Edge {

        /** UID */
        private static final long serialVersionUID = -1804606674849562189L;

        /** graphviz edge */
        private org.finroc.finstruct.graphviz.Edge gvEdge;

        private Edge(Vertex src, Vertex dest) {
            gvEdge = new org.finroc.finstruct.graphviz.Edge(src.gvVertex, dest.gvVertex);
        }

        /**
         * Paint Edge
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Color old = g2d.getColor();
            g2d.setColor(getColor());
            g2d.draw(gvEdge.getPath());
            drawArrow(g2d, !gvEdge.isReversedInDotLayout());
            g2d.setColor(old);
        }

        /**
         * @param g2d Graphics object
         * @param atSource Draw arrow at source (reverse direction)
         */
        private void drawArrow(Graphics2D g2d, boolean atSource) {
            PathIterator pi = gvEdge.getPath().getPathIterator(null, 2);
            double[] coords = new double[6];
            double x1 = 0, y1 = 0, x2 = 0, y2 = 0;
            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                if (type == PathIterator.SEG_MOVETO) {
                    x2 = coords[0];
                    y2 = coords[1];
                } else if (type == PathIterator.SEG_LINETO) {
                    x1 = x2;
                    y1 = y2;
                    x2 = coords[0];
                    y2 = coords[1];
                    if (atSource) {
                        drawArrow(g2d, x2, y2, x1, y1);
                        return;
                    }
                }
                pi.next();
            }
            drawArrow(g2d, x1, y1, x2, y2);
        }

        private final int ARROW_LEN = 10;
        private final double ARROW_ANGLE = 2.7;


        /**
         * Draw arrow at the end of provided line segment
         *
         * @param g2d Graphics object
         * @param x1 line start x
         * @param y1 line start y
         * @param x2 line end x
         * @param y2 line end y
         */
        private void drawArrow(Graphics2D g2d, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double angle = Math.atan2(dy, dx);
            Path2D.Double path = new Path2D.Double();
            path.moveTo(x2 + Math.cos(angle + ARROW_ANGLE) * ARROW_LEN, y2 + Math.sin(angle + ARROW_ANGLE) * ARROW_LEN);
            path.lineTo(x2, y2);
            path.lineTo(x2 + Math.cos(angle - ARROW_ANGLE) * ARROW_LEN, y2 + Math.sin(angle - ARROW_ANGLE) * ARROW_LEN);
            g2d.draw(path);
        }
    }

//  public class Vertex extends JLabel {
//
//      /** UID */
//      private static final long serialVersionUID = -3674733880560752988L;
//
//      public final FrameworkElement fe;
//      public final FinstructVertex ann;
//      public Vertex(FrameworkElement fe, FinstructVertex ann) {
//          this.fe = fe;
//          this.ann = ann;
//          this.setText(fe.getDescription());
//          this.setBackground(ann.getColor());
//          this.setOpaque(true);
//          this.setBorder(new EmptyBorder(4, 4, 4, 4));
//          //this.setBorder(new EtchedBorder(EtchedBorder.RAISED));
//          //this.setFont(FONT);
//      }
//      public String getInchHeight() {
//          return toInch((double)getPreferredSize().getHeight());
//      }
//      public String getInchWidth() {
//          return toInch((double)getPreferredSize().getWidth());
//      }
//  }
}
