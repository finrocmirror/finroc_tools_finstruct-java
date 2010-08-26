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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuBar;

import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.finstruct.graphviz.Graph;
import org.finroc.finstruct.util.MouseHandler;
import org.finroc.finstruct.util.MouseHandlerManager;
import org.finroc.gui.util.gui.MActionEvent;
import org.finroc.gui.util.gui.MToolBar;
import org.finroc.gui.util.gui.MAction;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardViewGraphViz extends AbstractFinstructGraphView<StandardViewGraphViz.Vertex, StandardViewGraphViz.Edge> implements ActionListener {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Font for display */
    static Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    /** Label for getting node bounds - may only be used in synchronized context */
    private final JLabel testLabel = new JLabel();

    /** current GraphViz graph */
    private Graph graph = new Graph();

    /** List of vertices */
    private Collection<Vertex> vertices = new ArrayList<Vertex>();

    /** List of edges */
    private Collection<Edge> edges;

    /** MouseHandler manager */
    private MouseHandlerManager mouseHandlers;

    /** Expanded groups */
    private ArrayList<FrameworkElement> expandedGroups = new ArrayList<FrameworkElement>();

    /** Expanded groups */
    private ArrayList<Subgraph> subgraphs = new ArrayList<Subgraph>();

    /** Control modes */
    private enum Mode { navigate, connect }

    /** referenct to toolBar */
    private MToolBar toolBar;

    /** Layout modes */
    //private enum Layout { dot, neato, fdp }

    public StandardViewGraphViz() {
        testLabel.setFont(FONT);
        this.setBackground(Color.LIGHT_GRAY);
        setLayout(null);
        mouseHandlers = new MouseHandlerManager(this);
    }

    @Override
    protected Edge createEdgeInstance(Vertex source, Vertex dest) {
        return new Edge(source, dest);
    }

    @Override
    protected Vertex createVertexInstance(FrameworkElement fe) {
        return new Vertex(fe);
    }

    @Override
    protected synchronized void rootElementChanged() {
        expandedGroups.clear();
        relayout();
    }

    /**
     * relayout vertices etc.
     *
     * (SubGraphs are created for entries in expandedGroups)
     */
    private void relayout() {
        try {
            final FrameworkElement root = getRootElement();

            int width = getWidth();
            int height = getHeight();
            //List<String> outputLines;
            graph.clear();
            mouseHandlers.clear();
            vertices.clear();
            subgraphs.clear();
            synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
                if (root == null || (!root.isReady())) {
                    repaint();
                    return;
                }

                // create new graph
                List<Vertex> rootVertices = getVertices(root);
                if (rootVertices.size() == 0) {
                    repaint();
                    return;
                }

                // add vertices and create subgraphs
                for (Vertex v : rootVertices) {
                    if (expandedGroups.contains(v.frameworkElement)) {
                        createSubGraph(graph, v.frameworkElement);
                    } else {
                        graph.add(v.gvVertex);
                        vertices.add(v);
                    }
                }

                // process vertices
                for (Vertex v : vertices) {
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
                edges = getEdges(root, vertices);
                for (Edge e : edges) {
                    graph.add(e.gvEdge);
                    mouseHandlers.add(e, false);
                    if (e.isControllerData()) {
                        e.gvEdge.setReversedInDotLayout(true);
                    }
                }

            }

            // Layout
            graph.applyLayout(toolBar.getSelection(Graph.Layout.values()), false);

            repaint();
        } catch (Exception e) {
            logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
        }
    }

    /**
     * Generate subgraphs for all expanded groups (recursively)
     *
     * @param parent Parent Graph
     * @param group Groupto create subgraph for
     */
    private void createSubGraph(Graph parent, FrameworkElement group) {
        Subgraph graph = new Subgraph(parent, group);
        subgraphs.add(graph);
        List<Vertex> subVertices = getVertices(group);
        for (Vertex v : subVertices) {
            if (expandedGroups.contains(v.frameworkElement)) {
                createSubGraph(graph, v.frameworkElement);
            } else {
                graph.add(v.gvVertex);
                vertices.add(v);
            }
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D)g.create();

        if (edges == null || vertices == null) {
            return;
        }

        // draw subgraph bounds
        for (Subgraph gr : subgraphs) {
            gr.paint(g2d);
        }

        // draw edges
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Edge e : edges) {
            e.paint(g2d);
        }
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // draw vertices
        for (Vertex v : vertices) {
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
            gvVertex.setAttributeQuoted("description", fe.getDescription());
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
            g2d.setColor(getColor());
            if (h >= 2) {
                g2d.setColor(brighten(g2d.getColor(), 128));
            }
            g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
            if (h >= 1) {
                drawRectangleGlow(g2d, rect.x, rect.y, rect.width, rect.height, 0.7f, 0.075f);
            }
            g2d.setColor(getTextColor());
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawString(frameworkElement.getDescription(), rect.x + 3, rect.y + rect.height - 5);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
            if (isGroup()) { // draw + for group
                if (expandIcon == null) {
                    expandIcon = new ExpandIcon(6, 6, frameworkElement);
                }
                expandIcon.paint(g2d, rect.x + rect.width - 5, rect.y - 1, true);
            }
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
            StandardViewGraphViz.this.repaint(rect.x - 5, rect.y - 5, rect.width + 11, rect.height + 11);
        }

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {}
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
            g2d.setColor(getColor());
            //g2d.set
            processHighlighting(g2d);
            g2d.draw(gvEdge.getPath());
            drawArrow(g2d, !gvEdge.isReversedInDotLayout());
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

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {}
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
            g2d.drawLine(x + 1, y + 1, x + width, y + 1); // top
            g2d.drawLine(x + 1, y + height + 1, x + width, y + height + 1); // bottom
            g2d.drawLine(x + 1, y, x + 1, y + height + 1); // left
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

        /** Group that can be expanded/collapsed pressing this icon */
        private FrameworkElement group;

        public ExpandIcon(int width, int height, FrameworkElement group) {
            bounds.width = width;
            bounds.height = height;
            this.group = group;
            mouseHandlers.add(this, true);
        }

        /**
         * Paint icon
         *
         * @param g2d Graphics object to draw to
         * @param x X-Position of icon
         * @param y Y-Position of icon
         * @param expand Draw Expand icon? ('+' rather than '-')
         */
        private void paint(Graphics2D g2d, int x, int y, boolean expand) {
            Rectangle a = bounds;
            a.x = x;
            a.y = y;
            g2d.setColor(Color.WHITE);
            g2d.fillRect(a.x, a.y, a.width, a.height);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(a.x, a.y, a.width, a.height);
            if (expand) {
                g2d.drawLine(a.x + 3, a.y + 2, a.x + 3, a.y + 4);
            }
            g2d.drawLine(a.x + 2, a.y + 3, a.x + 4, a.y + 3);

            if (mouseHandlers.getMouseOver() != this) {
                return;
            }

            float startAlpha = mouseHandlers.getActiveHandler() == this ? 0.3f : 0.5f;
            g2d.setColor(Color.WHITE);
            drawRectangleGlow(g2d, a.x, a.y, a.width, a.height, startAlpha, 0.2f);
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

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {
            if (over == this) {
                if (expandedGroups.contains(group)) {
                    expandedGroups.remove(group);
                } else {
                    expandedGroups.add(group);
                }
                relayout();
            }
        }
    }

    /**
     * Subgraph in GraphViz view
     */
    private class Subgraph extends Graph {

        /** Wrapped framework element */
        private final FrameworkElement frameworkElement;

        /** Collapse icon */
        private ExpandIcon expandIcon;

        private Subgraph(Graph parent, FrameworkElement fe) {
            super(parent);
            frameworkElement = fe;
            expandIcon = new ExpandIcon(6, 6, frameworkElement);
        }

        /**
         * Paint SubGraph boundary and icon
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Color c = brighten(getBackground(), getParentCount() * 30);
            g2d.setColor(c);
            Rectangle r = getBounds();
            g2d.fillRect(r.x, r.y, r.width, r.height);
            g2d.setColor(Color.GRAY);
            g2d.drawRect(r.x, r.y, r.width, r.height);

            expandIcon.paint(g2d, r.x + r.width - 6, r.y, false);
        }
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {
        this.toolBar = toolBar;
        //IconManager.getInstanc
        toolBar.addToggleButton(new MAction(Mode.navigate, null, "Navigation Mode", this));
        toolBar.addToggleButton(new MAction(Mode.connect, null, "Connect Mode", this));
        toolBar.addSeparator();
        toolBar.startNextButtonGroup();
        toolBar.addToggleButton(new MAction(Graph.Layout.dot, null, "dot layout", this));
        toolBar.addToggleButton(new MAction(Graph.Layout.neato, null, "neato layout", this));
        toolBar.addToggleButton(new MAction(Graph.Layout.fdp, null, "fdp layout", this));
        toolBar.setSelected(Mode.navigate);
        toolBar.setSelected(Graph.Layout.dot);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae instanceof MActionEvent) {
            Enum<?> e = ((MActionEvent)ae).getEnumID();
            if (e instanceof Graph.Layout) {
                relayout();
            }
            if (e instanceof Mode) {
                connectionPanel.setRightTree(e == Mode.connect ? connectionPanel.getLeftTree() : null);
            }
        }
    }
}
