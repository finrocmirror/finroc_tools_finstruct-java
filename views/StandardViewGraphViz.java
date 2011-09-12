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
package org.finroc.tools.finstruct.views;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.finroc.core.FrameworkElement.ChildIterator;
import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.admin.AdminServer;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.dialogs.CreateInterfacesDialog;
import org.finroc.tools.finstruct.dialogs.CreateModuleDialog;
import org.finroc.tools.finstruct.dialogs.ParameterEditDialog;
import org.finroc.tools.finstruct.graphviz.Graph;
import org.finroc.tools.finstruct.util.MouseHandler;
import org.finroc.tools.finstruct.util.MouseHandlerManager;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.gui.MAction;
import org.rrlib.finroc_core_utils.log.LogLevel;

/**
 * @author max
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardViewGraphViz extends AbstractFinstructGraphView<StandardViewGraphViz.Vertex, StandardViewGraphViz.Edge> implements ActionListener, MouseMotionListener, MouseListener, ChangeListener {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Font for display */
    static Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    /** Label for getting node bounds - may only be used in synchronized context */
    private final JLabel testLabel = new JLabel("Test");

    /** Two-line test label */
    private final JLabel twoLineTestLabel = new JLabel("<HTML>X<BR>Y</HTML>");

    /** when drawing a string: y increment per text line */
    protected final int lineIncrementY;

    /** Height of label with a single line */
    private final int labelBaseHeight = testLabel.getPreferredSize().height;

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

    /** reference to toolBar */
    private MToolBar toolBar;

    /** last mouse point during dragging operation */
    private Point2D lastMouseDragPoint;

    /** toolbar buttons */
    private JButton refreshButton, zoomIn, zoomOut, zoom1, start, pause;

    /** Spinners in toolbar */
    private JSpinner nodeSep, rankSep;

    /** Diverse toolbar switches */
    private enum DiverseSwitches { antialiasing, lineBreaks }

    /** Zoom factor */
    private float zoom = 1.0f;

    /** PopUp-Menu for Right-Click */
    private JPopupMenu popupMenu;

    /** PopUp-Menu Items */
    private JMenuItem miCreateModule, miSaveChanges, miEditModule, miDeleteModule, miCreateInterfaces;

    /** Framework element that right-click-menu was opened upon */
    private FrameworkElement rightClickedOn;

    public StandardViewGraphViz() {
        testLabel.setFont(FONT);
        twoLineTestLabel.setFont(FONT);
        lineIncrementY = twoLineTestLabel.getPreferredSize().height - testLabel.getPreferredSize().height;
        this.setBackground(Color.LIGHT_GRAY);
        setLayout(null);
        mouseHandlers = new MouseHandlerManager(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        // Create PopupMenu
        popupMenu = new JPopupMenu();
        miCreateModule = createMenuEntry("Create Element...");
        miCreateInterfaces = createMenuEntry("Create Interfaces...");
        miEditModule = createMenuEntry("Edit Parameters");
        miDeleteModule = createMenuEntry("Delete Element");
        miSaveChanges = createMenuEntry("Save Changes");
    }

    /**
     * Convenient method the create menu entries and add this Window as listener
     *
     * @param string Text of menu entry
     * @return Created menu entry
     */
    private JMenuItem createMenuEntry(String string) {
        JMenuItem item = new JMenuItem(string);
        item.addActionListener(this);
        popupMenu.add(item);
        return item;
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
    protected synchronized void rootElementChanged(ArrayList<FrameworkElement> expandedElements) {
        expandedGroups.clear();
        expandedGroups.addAll(expandedElements != null ? expandedElements : new ArrayList<FrameworkElement>(0));
        relayout();

        // get Admin interface
        /*try {
            AdminClient ac = RemoteRuntime.find(getRootElement()).getAdminInterface();
            if (lastAdminInterface != ac) { // reinit createable modules?
                lastAdminInterface = ac;
                ArrayList<RemoteCreateModuleAction> moduleTypes = ac.getRemoteModuleTypes();
                menuCreate.removeAll();

                TreeMap<String, JMenu> groupMenus = new TreeMap<String, JMenu>();
                for (RemoteCreateModuleAction moduleType : moduleTypes) {

                }
            }

        } catch (Exception e) {
            logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
        }*/
    }

    /**
     * relayout vertices etc.
     *
     * (SubGraphs are created for entries in expandedGroups)
     */
    protected void relayout() {
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
            graph.applyLayout(Finstruct.EXPERIMENTAL_FEATURES ? toolBar.getSelection(Graph.Layout.values()) : Graph.Layout.dot, false);

            revalidate();
            repaint();

            // set start/pause icon state
            ThreadLocalCache.get();
            updateStartPauseEnabled();

        } catch (Exception e) {
            logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
        }
    }

    /**
     * Update whether start and pause buttons are enabled
     */
    private void updateStartPauseEnabled() {
        if (Finstruct.BETA_FEATURES) {
            RemoteRuntime rr = RemoteRuntime.find(getRootElement());
            if (rr == null) {
                start.setEnabled(false);
                pause.setEnabled(false);
                return;
            }
            try {
                int executing = rr.getAdminInterface().isExecuting(rr.getRemoteHandle(getRootElement()));
                start.setEnabled(executing == AdminServer.STOPPED || executing == AdminServer.BOTH);
                pause.setEnabled(executing == AdminServer.STARTED || executing == AdminServer.BOTH);
            } catch (Exception e) {
                start.setEnabled(false);
                pause.setEnabled(false);
            }
        }
    }

    /**
     * Generate subgraphs for all expanded groups (recursively)
     *
     * @param parent Parent Graph
     * @param group Group to create subgraph for
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
        g2d.scale(zoom, zoom);
        boolean antialiasing = toolBar.isSelected(DiverseSwitches.antialiasing);

        if (edges == null || vertices == null) {
            return;
        }

        // draw subgraph bounds
        for (Subgraph gr : subgraphs) {
            gr.paint(g2d);
        }

        // draw edges
        if (antialiasing) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        for (Edge e : edges) {
            e.paint(g2d);
        }
        if (antialiasing) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        // draw vertices
        for (Vertex v : vertices) {
            v.paint(g2d);
        }

        // draw connection line
        MouseHandler mh = mouseHandlers.getActiveHandler();
        if (mh != null && mh instanceof Vertex && inConnectionMode() && lastMouseDragPoint != null) {
            Vertex v = (Vertex)mh;
            g2d.setColor(Color.BLACK);
            if (antialiasing) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g2d.drawLine((int)v.rect.getCenterX(), (int)v.rect.getCenterY(), (int)lastMouseDragPoint.getX(), (int)lastMouseDragPoint.getY());
            if (antialiasing) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            }
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
        protected org.finroc.tools.finstruct.graphviz.Vertex gvVertex;

        /** Expand icon - if group */
        protected ExpandIcon expandIcon;

        /** Temporary Rectangle variable */
        protected final Rectangle rect = new Rectangle();

        /** Label lines to print (framework element description with line changes) */
        protected ArrayList<String> label = new ArrayList<String>();

        /** Timestamp when user last clicked on this element (for double-click) */
        private long lastClick;

        public Vertex(FrameworkElement fe) {
            super(fe);
            reset();
            gvVertex.setAttributeQuoted("description", fe.getDescription());
        }

        public void reset() {
            super.reset();
            gvVertex = new org.finroc.tools.finstruct.graphviz.Vertex();

            // find optimal line breaks
            boolean lineBreaks = toolBar.isSelected(DiverseSwitches.lineBreaks);
            String[] words = frameworkElement.getDescription().split("\\s");
            assert(words.length < 20);
            double bestScore = Integer.MAX_VALUE;
            ArrayList<String> bestText = new ArrayList<String>();
            Dimension bestDim = new Dimension(1000, 1000);
            StringBuilder sb = new StringBuilder();
            ArrayList<String> lines = new ArrayList<String>(30);
            for (int i = 0; i < Math.pow(2, words.length - 1); i++) { // use i as bitmask for line breaks

                // reset temp vars
                int i2 = i;
                double width = 0;
                double height = labelBaseHeight;
                if (sb.length() > 0) {
                    sb.delete(0, sb.length());
                }
                lines.clear();

                // create string
                for (int j = 0; j < (words.length - 1); j++) {
                    boolean lineChange = ((i2 & 1) == 1);
                    i2 >>>= 1;
                    sb.append(words[j]);
                    if (lineChange) {
                        testLabel.setText(sb.toString());
                        lines.add(sb.toString());
                        width = Math.max(width, testLabel.getPreferredSize().getWidth());
                        sb.delete(0, sb.length());
                        height += lineIncrementY;
                    } else {
                        sb.append(" ");
                    }
                }
                sb.append(words[words.length - 1]);
                testLabel.setText(sb.toString());
                lines.add(sb.toString());
                width = Math.max(width, testLabel.getPreferredSize().getWidth());

                // compare to best combination
                //double score = Math.pow(w, 1.1) + (h * 1.7);
                double score = width + (height * 1.7);
                if (score < bestScore) {
                    bestDim.width = (int)width + 5;
                    bestDim.height = (int)height + 6;
                    bestText.clear();
                    bestText.addAll(lines);
                    bestScore = score;
                }

                if (!lineBreaks) {
                    break;
                }
            }

            gvVertex.setSize(bestDim.width, bestDim.height);
            label = bestText;
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
            Color c = getColor();
            g2d.setColor(c);
            if (h >= 2) {
                g2d.setColor(brighten(c, 128));
            } else if (h == 1) {
                g2d.setColor(brighten(c, 64));
            }
            g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
            if (h >= 1) {
                drawRectangleGlow(g2d, rect.x, rect.y, rect.width, rect.height, 0.7f, 0.075f);
            }
            g2d.setColor(getTextColor());
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < label.size(); i++) {
                g2d.drawString(label.get(i), rect.x + 3, (rect.y + rect.height - 5) + ((i + 1) - label.size()) * lineIncrementY);
            }
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
        protected int getHighlightLevel() {
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
            getVertexBounds(rect, gvVertex);
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
            if (zoom == 1.0) {
                StandardViewGraphViz.this.repaint(rect.x - 5, rect.y - 5, rect.width + 11, rect.height + 11);
            } else {
                StandardViewGraphViz.this.repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {
            if (inConnectionMode()) {
                if (over != null && over != this && (over instanceof Vertex)) {
                    Vertex v = (Vertex)over;
                    expandInTree(true, frameworkElement);
                    expandInTree(false, v.frameworkElement);
                }
                StandardViewGraphViz.this.repaint();
            }
            if (over == this) {
                long time = System.currentTimeMillis();
                if (time - lastClick < Finstruct.DOUBLE_CLICK_DELAY) {
                    if (frameworkElement.childCount() > 0 || frameworkElement.getFlag(CoreFlags.FINSTRUCTABLE_GROUP)) {
                        getFinstruct().showElement(frameworkElement);
                    }
                } else {
                    expandInTree(true, frameworkElement);
                    lastClick = System.currentTimeMillis();
                }
            }
        }

        /**
         * Expand specified framework element and all of its interfaces
         *
         * @param leftTree in Left tree?
         * @param frameworkElement Framework element
         */
        private void expandInTree(boolean leftTree, FrameworkElement frameworkElement) {
            ArrayList<FrameworkElement> expand = new ArrayList<FrameworkElement>();
            expand.add(frameworkElement);
            ChildIterator ci = new ChildIterator(frameworkElement);
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                if (isInterface(next)) {
                    expand.add(next);
                }
            }
            connectionPanel.expandOnly(leftTree, expand);
        }
    }

    /**
     * Makes color a little lighter/whiter
     *
     * @param color color
     * @param amount Amount to brighten each color
     * @return Brighter color
     */
    protected Color brighten(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount), Math.min(255, color.getGreen() + amount), Math.min(255, color.getBlue() + amount));
    }

    /**
     * Combined (Finstruct/GraphViz edge)
     */
    class Edge extends AbstractFinstructGraphView.Edge implements MouseHandler {

        /** UID */
        private static final long serialVersionUID = -1804606674849562189L;

        /** graphviz edge */
        private org.finroc.tools.finstruct.graphviz.Edge gvEdge;

        private Edge(Vertex src, Vertex dest) {
            gvEdge = new org.finroc.tools.finstruct.graphviz.Edge(src.gvVertex, dest.gvVertex);
        }

        /**
         * Paint Edge
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Stroke oldStroke = g2d.getStroke();
            g2d.setColor(getColor());
            //g2d.set
            processHighlighting(g2d);
            g2d.draw(gvEdge.getPath());
            drawArrow(g2d, !gvEdge.isReversedInDotLayout());
            if (g2d.getStroke() != oldStroke) {
                g2d.setStroke(oldStroke);
            }
        }

        /**
         * Process Graphics2D object with respect to highlighting of this edge
         *
         * @param g2d Graphics2D
         */
        private void processHighlighting(Graphics2D g2d) {
            MouseHandler mo = mouseHandlers.getMouseOver();
            MouseHandler ma = mouseHandlers.getActiveHandler();

            if ((ma == null && mo == this) || ma == this) {
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.setColor(brighten(g2d.getColor(), 120));
            } else if ((ma == null && (mo == getSource() || mo == getDestination())) || ma == getSource() || ma == getDestination()) {
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.setColor(brighten(g2d.getColor(), 70));
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
            // return gvEdge.getPath().intersects(new Rectangle(p.x - 2, p.y - 2, 5, 5)); not precise
            Rectangle r = new Rectangle(p.x - 2, p.y - 2, 5, 5);
            PathIterator pi = gvEdge.getPath().getPathIterator(null, 2);
            double[] coords = new double[6];
            Line2D line = new Line2D.Double();
            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                if (type == PathIterator.SEG_MOVETO) {
                    line.setLine(0, 0, coords[0], coords[1]);
                } else if (type == PathIterator.SEG_LINETO) {
                    line.setLine(line.getX2(), line.getY2(), coords[0], coords[1]);
                    if (line.intersects(r)) {
                        return true;
                    }
                }
                pi.next();
            }
            return false;
        }

        @Override
        public void statusChanged() {
            triggerRepaint();
            ((Vertex)getSource()).triggerRepaint();
            ((Vertex)getDestination()).triggerRepaint();
        }

        public void triggerRepaint() {
            if (zoom == 1) {
                StandardViewGraphViz.this.repaint(gvEdge.getPath().getBounds());
            } else {
                StandardViewGraphViz.this.repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {
            if (inConnectionMode()) {

                final ArrayList<FrameworkElement> srcPorts = new ArrayList<FrameworkElement>();
                final HashSet<FrameworkElement> destPorts = new HashSet<FrameworkElement>();

                FrameworkElementTreeFilter.Callback<Boolean> cb = new FrameworkElementTreeFilter.Callback<Boolean>() {
                    @Override
                    public void treeFilterCallback(FrameworkElement fe, Boolean unused) {
                        NetPort np = ((AbstractPort)fe).asNetPort();
                        if (np != null) {
                            boolean added = false;
                            List<AbstractPort> dests = np.getRemoteEdgeDestinations();
                            for (AbstractPort port : dests) {
                                if (port.isChildOf(getDestination().getFinrocElement())) {
                                    destPorts.add(port);
                                    if (!added) {
                                        srcPorts.add(fe);
                                    }
                                }
                            }
                        }
                    }
                };
                FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(CoreFlags.IS_PORT | CoreFlags.STATUS_FLAGS, CoreFlags.IS_PORT | CoreFlags.READY | CoreFlags.PUBLISHED);
                filter.traverseElementTree(getSource().getFinrocElement(), cb, false);
                connectionPanel.expandOnly(true, srcPorts);
                connectionPanel.expandOnly(false, destPorts);
            }
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
        public void paint(Graphics2D g2d, int x, int y, boolean expand) {
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
            if (zoom == 1) {
                StandardViewGraphViz.this.repaint(bounds.x - 5, bounds.y - 5, bounds.width + 11, bounds.height + 11);
            } else {
                StandardViewGraphViz.this.repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {
            if (over == this && event.getButton() == MouseEvent.BUTTON1) {
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

        /** label bounds */
        private Rectangle labelBounds = null;

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
            if (labelBounds == null) {
                testLabel.setText(frameworkElement.getDescription());
                labelBounds = new Rectangle(testLabel.getPreferredSize());

                // find label location without collision with other objects
                Rectangle vertexBounds = new Rectangle();
                Point[] candidates = new Point[4];
                candidates[0] = new Point(getBounds().x + 4, getBounds().y + 4);
                candidates[3] = new Point((int)(getBounds().getMaxX() - labelBounds.width - 4), (int)(getBounds().getMaxY() - labelBounds.height - 4));
                candidates[1] = new Point(candidates[3].x, candidates[0].y);
                candidates[2] = new Point(candidates[0].x, candidates[3].y);

                int best = -1;
                for (int i = 0; i < candidates[3].y; i++) {
                    boolean collision = false;
                    if (i < 4) {
                        labelBounds.setLocation(candidates[i]);
                    } else {
                        labelBounds.setLocation(candidates[0].x, candidates[0].y + i);
                    }
                    for (Vertex v : vertices) {
                        getVertexBounds(vertexBounds, v.gvVertex);
                        if (labelBounds.intersects(vertexBounds)) {
                            collision = true;
                        }
                    }
                    for (Graph g : super.getSubgraphs()) {
                        if (labelBounds.intersects(g.getBounds())) {
                            collision = true;
                        }
                    }
                    if (!collision) {
                        best = i;
                        break;
                    }
                }
                if (best == -1) {

                    // oh, well - use top left
                    labelBounds.setLocation(candidates[0]);
                }
            }

            Color c = brighten(getBackground(), (getParentCount() % 2) * 30);
            g2d.setColor(c);
            Rectangle r = getBounds();
            g2d.fillRect(r.x, r.y, r.width, r.height);
            g2d.setColor(Color.GRAY);
            g2d.drawRect(r.x, r.y, r.width, r.height);

            expandIcon.paint(g2d, r.x + r.width - 6, r.y, false);

            g2d.drawString(frameworkElement.getDescription(), labelBounds.x, labelBounds.y + labelBounds.height);
        }
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {

        // tool bar
        this.toolBar = toolBar;

        if (Finstruct.EXPERIMENTAL_FEATURES) {
            toolBar.addToggleButton(new MAction(Graph.Layout.dot, null, "dot layout", this));
            toolBar.addToggleButton(new MAction(Graph.Layout.neato, null, "neato layout", this));
            toolBar.addToggleButton(new MAction(Graph.Layout.fdp, null, "fdp layout", this));
            toolBar.addSeparator();
            toolBar.setSelected(Graph.Layout.dot);
        }

        if (Finstruct.BETA_FEATURES) {
            start = toolBar.createButton("player_play-ubuntu.png", "Start/Resume exectution", this);
            start.setEnabled(false);
            pause = toolBar.createButton("player_pause-ubuntu.png", "Pause/Stop exectution", this);
            pause.setEnabled(false);
            toolBar.addSeparator();
        }

        refreshButton = toolBar.createButton("reload-ubuntu.png", "Refresh graph", this);
        toolBar.addToggleButton(new MAction(DiverseSwitches.antialiasing, "antialias-wikimedia-public_domain.png", "Antialiasing", this), true);
        toolBar.addToggleButton(new MAction(DiverseSwitches.lineBreaks, "line-break-max.png", "Line Breaks", this), true);
        zoomIn = toolBar.createButton("zoom-in-ubuntu.png", "Zoom in", this);
        zoomOut = toolBar.createButton("zoom-out-ubuntu.png", "Zoom out", this);
        zoom1 = toolBar.createButton("zoom-original-ubuntu.png", "Zoom 100%", this);
        toolBar.addSeparator();
        toolBar.add(new JLabel("nodesep"));
        nodeSep = new JSpinner(new SpinnerNumberModel(0.25, 0.05, 2.0, 0.05));
        toolBar.add(nodeSep);
        nodeSep.addChangeListener(this);
        nodeSep.setPreferredSize(new Dimension(50, nodeSep.getPreferredSize().height));
        nodeSep.setMaximumSize(nodeSep.getPreferredSize());
        nodeSep.getEditor().getComponent(0).addKeyListener(getFinstruct());
        toolBar.add(new JLabel("ranksep"));
        rankSep = new JSpinner(new SpinnerNumberModel(0.5, 0.05, 2.0, 0.05));
        toolBar.add(rankSep);
        rankSep.addChangeListener(this);
        rankSep.setPreferredSize(new Dimension(50, rankSep.getPreferredSize().height));
        rankSep.setMaximumSize(rankSep.getPreferredSize());
        rankSep.getEditor().getComponent(0).addKeyListener(getFinstruct());
        toolBar.setSelected(DiverseSwitches.antialiasing, true);
        toolBar.setSelected(DiverseSwitches.lineBreaks, true);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void actionPerformed(ActionEvent ae) {
        ThreadLocalCache.get();
        if (ae instanceof MActionEvent) {
            Enum e = ((MActionEvent)ae).getEnumID();
            if (e instanceof Graph.Layout) {
                relayout();
            } else if (e == DiverseSwitches.antialiasing) {
                repaint();
            } else if (e == DiverseSwitches.lineBreaks) {
                relayout();
            }
        } else if (ae.getSource() == refreshButton) {
            refresh();
        } else if (ae.getSource() == zoomIn) {
            setZoom(zoom * 1.33);
        } else if (ae.getSource() == zoomOut) {
            setZoom(zoom * 0.75);
        } else if (ae.getSource() == zoom1) {
            setZoom(1);
        } else if (ae.getSource() == miCreateModule) {
            CreateModuleDialog cmd = new CreateModuleDialog(getFinstruct());
            cmd.show(rightClickedOn);
            relayout();
        } else if (ae.getSource() == miSaveChanges) {
            FrameworkElement fe = rightClickedOn.getFlag(CoreFlags.FINSTRUCTABLE_GROUP) ? rightClickedOn : rightClickedOn.getParentWithFlags(CoreFlags.FINSTRUCTABLE_GROUP);
            if (fe != null) {
                RemoteRuntime rr = RemoteRuntime.find(fe);
                if (rr == null) {
                    Finstruct.showErrorMessage("Element is not a child of a remote runtime", false, false);
                } else {
                    rr.getAdminInterface().saveFinstructableGroup(rr.getRemoteHandle(fe));
                }
            }
        } else if (ae.getSource() == miDeleteModule) {
            RemoteRuntime rr = RemoteRuntime.find(rightClickedOn);
            if (rr == null) {
                Finstruct.showErrorMessage("Element is not a child of a remote runtime", false, false);
            } else {
                rr.getAdminInterface().deleteElement(rr.getRemoteHandle(rightClickedOn));
            }
        } else if (ae.getSource() == miEditModule) {
            new ParameterEditDialog(getFinstruct()).show(rightClickedOn, true);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
            relayout();
        } else if (ae.getSource() == miCreateInterfaces) {
            new CreateInterfacesDialog(getFinstruct()).show(rightClickedOn);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
            relayout();
        } else if (ae.getSource() == start || ae.getSource() == pause) {
            RemoteRuntime rr = RemoteRuntime.find(getRootElement());
            if (rr == null) {
                Finstruct.showErrorMessage("Root Element is not a child of a remote runtime", false, false);
            } else {
                if (ae.getSource() == start) {
                    rr.getAdminInterface().startExecution(rr.getRemoteHandle(getRootElement()));
                } else {
                    rr.getAdminInterface().pauseExecution(rr.getRemoteHandle(getRootElement()));
                }
                updateStartPauseEnabled();
            }
        }
    }

    /**
     * Set Zoom
     *
     * @param zoom Zoom (100% = 1.0f)
     */
    private void setZoom(double zoom) {
        this.zoom = (float)zoom;
        mouseHandlers.setZoom(zoom);
        revalidate();
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        Point p = e.getPoint();
        if (zoom != 1.0) {
            p = new Point((int)(e.getX() / zoom), (int)(e.getY() / zoom));
        }

        MouseHandler mh = mouseHandlers.getActiveHandler();
        if (inConnectionMode() && mh != null && mh instanceof Vertex) {
            Vertex v = (Vertex)mh;
            if (lastMouseDragPoint == null) {
                lastMouseDragPoint = p;
            }
            int left = Math.min(Math.min(v.rect.x, e.getX()), (int)lastMouseDragPoint.getX());
            int top = Math.min(Math.min(v.rect.y, e.getY()), (int)lastMouseDragPoint.getY());
            int right = Math.max(Math.max((int)v.rect.getMaxX(), e.getX()), (int)lastMouseDragPoint.getX());
            int bottom = Math.max(Math.max((int)v.rect.getMaxY(), e.getY()), (int)lastMouseDragPoint.getY());
            if (zoom == 1) {
                repaint(left - 3, top - 3, right + 6 - left, bottom + 6 - top);
            } else {
                repaint();
            }
            lastMouseDragPoint = p;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {}

    /**
     * @return Is View in connection mode?
     */
    private boolean inConnectionMode() {
        return toolBar.getSelection(Finstruct.Mode.values()) == Finstruct.Mode.connect;
    }

    @Override
    public void refresh() {
        relayout();
    }

    @Override
    public Dimension getPreferredSize() {
        if (graph != null) {
            return new Dimension(((int)(graph.getBounds().width * zoom)) + 1, ((int)(graph.getBounds().height * zoom)) + 1);
        }
        return null;
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == rankSep || e.getSource() == nodeSep) {
            double rs = ((Number)rankSep.getValue()).doubleValue();
            double ns = ((Number)nodeSep.getValue()).doubleValue();
            if (rs > 0 && ns > 0) {
                graph.setAttribute("ranksep", ("" + rs).replace(',', '.'));
                graph.setAttribute("nodesep", ("" + ns).replace(',', '.'));
                relayout();
            }
        }

    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {
        MouseHandler mh = mouseHandlers.getMouseHandler(e);
        Point p = e.getPoint();
        if (zoom != 1.0) {
            p = new Point((int)(e.getX() / zoom), (int)(e.getY() / zoom));
        }

        if (e.getButton() == MouseEvent.BUTTON3 && getRootElement() != null) {
            if (mh == null) {

                // in which group are we?
                rightClickedOn = getRootElement();
                for (Subgraph sg : subgraphs) {
                    if (sg.getBounds().contains(p)) {
                        rightClickedOn = sg.frameworkElement;
                    }
                }
            } else if (mh instanceof Vertex) {
                rightClickedOn = ((Vertex)mh).frameworkElement;
            } else {
                rightClickedOn = null;
            }
            if (rightClickedOn != null) {
                if (rightClickedOn.getFlag(CoreFlags.FINSTRUCTED) || rightClickedOn.getFlag(CoreFlags.FINSTRUCTABLE_GROUP)) {
                    boolean expanded = expandedGroups.contains(rightClickedOn) || rightClickedOn == getRootElement();
                    miCreateModule.setEnabled(expanded);
                    miDeleteModule.setEnabled(!expanded);
                    miCreateInterfaces.setEnabled(!rightClickedOn.isPort());

                    // show right-click-menu with create-action
                    popupMenu.show(this, e.getX(), e.getY());
                }
            }
        }
    }

    /**
     * Returns bounds on screen of Graphview vertex
     *
     * @param rect Rectangle to store result in
     * @param gvVertex Graphview Vertex
     */
    private static void getVertexBounds(Rectangle rect, org.finroc.tools.finstruct.graphviz.Vertex gvVertex) {
        Point2D.Double pos = gvVertex.getLayoutPosition();
        rect.x = (int)(pos.x - (gvVertex.getWidth() / 2));
        rect.y = (int)(pos.y - (gvVertex.getHeight() / 2));
        rect.width = (int)gvVertex.getWidth();
        rect.height = (int)gvVertex.getHeight();
    }

    @Override
    public Collection <? extends FrameworkElement > getExpandedElementsForHistory() {
        return expandedGroups;
    }

//    /**
//     * Action that create a remote module
//     */
//    private class CreateModuleAction extends AbstractAction {
//
//      /** UID */
//      private static final long serialVersionUID = -8208754807823437000L;
//
//      /** wrapped RemoteCreateModuleAction */
//      private final RemoteCreateModuleAction action;
//
//      private CreateModuleAction(RemoteCreateModuleAction action) {
//          this.action = action;
//          putValue(Action.NAME, action.name);
//      }
//
//        public void actionPerformed(ActionEvent e) {
//          try {
//              // show dialog to specify structure parameters
//
//              // create module
//              //action.adminInterface.createModule(action, name, parentHandle, params);
//
//              // repaint after timeout
//              Thread.sleep(500);
//              relayout();
//            } catch (Exception ex) {
//              logDomain.log(LogLevel.LL_ERROR, "RemoteCreateModuleAction " + action.name, ex);
//                Finstruct.showErrorMessage(ex);
//            }
//        }
//    }
}
