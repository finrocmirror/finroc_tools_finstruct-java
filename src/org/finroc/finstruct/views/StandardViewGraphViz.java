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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JLabel;

import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.finstruct.graphviz.Graph;
import org.finroc.finstruct.graphviz.Graph.Layout;
import org.finroc.finstruct.util.MouseHandler;
import org.finroc.finstruct.util.MouseHandlerManager;
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

    /** MouseHandler manager */
    private MouseHandlerManager mouseHandlers;

    public StandardViewGraphViz() {
        super(Vertex.class);
        testLabel.setFont(FONT);
        this.setBackground(Color.LIGHT_GRAY);
        setLayout(null);
        mouseHandlers = new MouseHandlerManager(this);
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
            mouseHandlers.clear();
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
                    mouseHandlers.add(v, false);
                    int wh = (int)((v.gvVertex.getWidth() + 1) / 2) + 3;
                    int hh = (int)((v.gvVertex.getHeight() + 1) / 2) + 3;
                    if (v.hasFixedPos()) {
                        v.gvVertex.setFixedPosition(v.onRight() ? (width - wh) : wh, v.atBottom() ? (height - hh) : hh);
                        v.gvVertex.setRank(v.atBottom() ? "source" : "sink");
                        //ps.print(", pos=\"" + toInch(v.onRight() ? (width - wh) : wh) + "," + toInch(v.atBottom() ? (height - hh) : hh) + "!\"");
                    }
                }

                // add edges
                edges = getEdges(root);
                for (Edge e : edges) {
                    graph.add(e.gvEdge);
                    mouseHandlers.add(e, false);
                    if (e.isControllerData()) {
                        e.gvEdge.setReversedInDotLayout(true);
                    }
                }
            }

            // Layout
            graph.applyLayout(Layout.dot, false);

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

    }

    /** Different levels of highlighting enums */
    //public enum Highlight { no, little, bright }

    /**
     * Highlight object?
     *
     * @param mh Object that should possibly be highlighted
     * @return Level of highlighting
     */
    //private Highlight highlight(MouseHandler mh) {

    //}

    /**
     * Combined (Finstruct/GraphViz vertex)
     */
    class Vertex extends AbstractFinstructGraphView.VertexAnnotation implements MouseHandler {

        /** graphviz vertex */
        private org.finroc.finstruct.graphviz.Vertex gvVertex;

        /** Expand icon - if group */
        private ExpandIcon expandIcon;

        /** Temporary Rectangle variable */
        private final Rectangle rect = new Rectangle();

        public Vertex(FrameworkElement fe) {
            super(fe);
            reset();
        }

        public void reset() {
            super.reset();
            gvVertex = new org.finroc.finstruct.graphviz.Vertex();
            testLabel.setText(frameworkElement.getDescription());
            gvVertex.setSize(testLabel.getPreferredSize().getWidth() + 5, testLabel.getPreferredSize().getHeight() + 6);
            expandIcon = null;
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            int h = getHighlightLevel();

            updateRectangle();
            Color old = g2d.getColor();
            g2d.setColor(getColor());
            if (h >= 2) {
                g2d.setColor(brighten(g2d.getColor(), 128));
            }
            g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
            if (h >= 1) {
                drawRectangleGlow(g2d, rect.x, rect.y, rect.width, rect.height, 0.7f, 0.075f);
            }
            g2d.setColor(getTextColor());
            g2d.drawString(frameworkElement.getDescription(), rect.x + 3, rect.y + rect.height - 5);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
            if (isGroup()) { // draw + for group
                if (expandIcon == null) {
                    expandIcon = new ExpandIcon(6, 6);
                }
                expandIcon.paint(g2d, rect.x + rect.width - 5, rect.y - 1);
            }
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(old);
        }

        /**
         * @return 0 = no highlighting, 1 = minor highlighting, 2 = major highlighting
         */
        private int getHighlightLevel() {
            MouseHandler mo = mouseHandlers.getMouseOver();
            MouseHandler ma = mouseHandlers.getActiveHandler();

            MouseHandler active = ma == null ? mo : ma;
            if (active == null) {
                return 0;
            }
            if (active == this) {
                return 2;
            }
            for (Edge e : edges) {
                if (e.getSource() == this || e.getDestination() == this) {
                    if (active == e || active == e.getSource() || active == e.getDestination()) {
                        return 1;
                    }
                }
            }
            return 0;
        }

        /**
         * Places current coordinates in rect variable
         */
        public void updateRectangle() {
            Point2D.Double pos = gvVertex.getLayoutPosition();
            rect.x = (int)(pos.x - (gvVertex.getWidth() / 2));
            rect.y = (int)(pos.y - (gvVertex.getHeight() / 2));
            rect.width = (int)gvVertex.getWidth();
            rect.height = (int)gvVertex.getHeight();
        }

        @Override
        public Cursor getCursor() {
            return null;
        }

        @Override
        public boolean handlesPoint(Point p) {
            updateRectangle();
            return rect.contains(p);
        }

        @Override
        public void statusChanged() {
            triggerRepaint();
            for (Edge e : edges) {
                if (e.getSource() == this || e.getDestination() == this) {
                    e.triggerRepaint();
                    ((Vertex)(e.getSource() != this ? e.getSource() : e.getDestination())).triggerRepaint();
                }
            }
        }

        public void triggerRepaint() {
            updateRectangle();
            StandardViewGraphViz.this.repaint(rect.x - 5, rect.y - 5, rect.width + 10, rect.height + 10);
        }
    }

    /**
     * Makes color a little lighter/whiter
     *
     * @param color color
     * @param amount Amount to brighten each color
     * @return Brighter color
     */
    private Color brighten(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount), Math.min(255, color.getGreen() + amount), Math.min(255, color.getBlue() + amount));
    }

    /**
     * Combined (Finstruct/GraphViz edge)
     */
    class Edge extends AbstractFinstructGraphView.Edge implements MouseHandler {

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
            //g2d.set
            processHighlighting(g2d);
            g2d.draw(gvEdge.getPath());
            drawArrow(g2d, !gvEdge.isReversedInDotLayout());
            g2d.setColor(old);
        }

        /**
         * Process Graphics2D object with respect to highligting of this edge
         *
         * @param g2d Graphics2D
         */
        private void processHighlighting(Graphics2D g2d) {
            MouseHandler mo = mouseHandlers.getMouseOver();
            MouseHandler ma = mouseHandlers.getActiveHandler();

            if ((ma == null && mo == this) || ma == this) {
                g2d.setColor(brighten(g2d.getColor(), 170));
            } else if ((ma == null && (mo == getSource() || mo == getDestination())) || ma == getSource() || ma == getDestination()) {
                g2d.setColor(brighten(g2d.getColor(), 128));
            }
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

        @Override
        public Cursor getCursor() {
            return null;
        }

        @Override
        public boolean handlesPoint(Point p) {
            return gvEdge.getPath().intersects(new Rectangle(p.x - 2, p.y - 2, 5, 5));
        }

        @Override
        public void statusChanged() {
            triggerRepaint();
            ((Vertex)getSource()).triggerRepaint();
            ((Vertex)getDestination()).triggerRepaint();
        }

        public void triggerRepaint() {
            StandardViewGraphViz.this.repaint(gvEdge.getPath().getBounds());
        }
    }

    /**
     * Draw "glow" in rectangular form
     *
     * @param g2d Graphics2D object
     * @param x x coordinate of rectangle to make "glow"
     * @param y y coordinate of rectangle to make "glow"
     * @param width width of rectangle to make "glow"
     * @param height height of rectangle to make "glow"
     * @param startAlpha Alpha-Blending value of first "glow" rectangle (1 is minimal)
     * @param alphaDelta Rate to modify alpha per pixel
     */
    public void drawRectangleGlow(Graphics2D g2d, int x, int y, int width, int height, float startAlpha, float alphaDelta) {
        Composite oldComp = g2d.getComposite();
        //g2d.setColor(Color.WHITE);
        for (float al = startAlpha; al < 1.0f; al += alphaDelta) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1.0f - al));
            x--;
            y--;
            width += 2;
            height += 2;
            g2d.drawLine(x + 1, y, x + width, y); // top
            g2d.drawLine(x + 1, y + height + 1, x + width, y + height + 1); // bottom
            g2d.drawLine(x, y, x, y + height + 1); // left
            g2d.drawLine(x + width + 1, y, x + width + 1, y + height + 1); // right
            if (al != startAlpha) {
                //g2d.drawLine(x + 1, y, x, y + 1);
            }
            //g2d.drawRect(x - i + 1, y - i + 1, a.width + 2 * i, a.height + 2 * i);
        }
        g2d.setComposite(oldComp);

    }

    /**
     * Expansion icon in top-left of group representation
     */
    public class ExpandIcon implements MouseHandler {

        /** Icon area */
        private Rectangle bounds = new Rectangle();

        public ExpandIcon(int width, int height) {
            bounds.width = width;
            bounds.height = height;
            mouseHandlers.add(this, true);
        }

        /**
         * Paint icon
         *
         * @param g2d Graphics object to draw to
         * @param x X-Position of icon
         * @param y Y-Position of icon
         */
        private void paint(Graphics2D g2d, int x, int y) {
            Rectangle a = bounds;
            a.x = x;
            a.y = y;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(a.x, a.y, a.width, a.height);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(a.x, a.y, a.width, a.height);
            g2d.drawLine(a.x + 3, a.y + 2, a.x + 3, a.y + 4);
            g2d.drawLine(a.x + 2, a.y + 3, a.x + 4, a.y + 3);

            if (mouseHandlers.getMouseOver() != this) {
                return;
            }

            float startAlpha = mouseHandlers.getActiveHandler() == this ? 0.3f : 0.5f;
            g2d.setColor(Color.WHITE);
            drawRectangleGlow(g2d, a.x + 1, a.y, a.width, a.height, startAlpha, 0.2f);
        }

        @Override
        public Cursor getCursor() {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }

        @Override
        public boolean handlesPoint(Point p) {
            return bounds.contains(p);
        }

        @Override
        public void statusChanged() {
            StandardViewGraphViz.this.repaint(bounds.x - 4, bounds.y - 4, bounds.width + 9, bounds.height + 9);
        }
    }

}
