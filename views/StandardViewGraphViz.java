//
// You received this file as part of Finroc
// A framework for intelligent robot control
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//----------------------------------------------------------------------
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
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
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
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.finstructable.EditableInterfaces;
import org.finroc.core.parameter.StaticParameterList;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.util.Files;
import org.finroc.plugins.data_types.StdStringList;
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
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Standard View - similar to standard view in MCABrowser
 */
public class StandardViewGraphViz extends AbstractGraphView<StandardViewGraphViz.Vertex, StandardViewGraphViz.Edge> implements ActionListener, MouseMotionListener, MouseListener, ChangeListener {

    /** UID */
    private static final long serialVersionUID = 5168689573715463737L;

    /** Font for display */
    static Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    /** Test Label for getting node bounds - may only be used in synchronized context */
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
    private ArrayList<ModelNode> expandedGroups = new ArrayList<ModelNode>();

    /** Expanded groups */
    private ArrayList<Subgraph> subgraphs = new ArrayList<Subgraph>();

    /** reference to toolBar */
    private MToolBar toolBar;

    /** last mouse point during dragging operation */
    private Point2D lastMouseDragPoint;

    /** toolbar buttons */
    private JButton zoomIn, zoomOut, zoom1;

    private static final double NODE_SEP_DEFAULT = 0.25, RANK_SEP_DEFAULT = 0.5;

    /** Spinners in toolbar */
    private JSpinner nodeSep = new JSpinner(new SpinnerNumberModel(NODE_SEP_DEFAULT, 0.05, 2.0, 0.05));
    private JSpinner rankSep = new JSpinner(new SpinnerNumberModel(RANK_SEP_DEFAULT, 0.05, 2.0, 0.05));

    /** Diverse toolbar switches */
    protected enum DiverseSwitches { antialiasing, lineBreaks }

    /** Zoom factor */
    private float zoom = 1.0f;

    /** PopUp-Menu for Right-Click */
    private JPopupMenu popupMenu;

    /** PopUp-Menu Items */
    private JMenuItem miCreateModule, miSaveChanges, miEditModule, miDeleteModule, miCreateInterfaces, miSaveAllFiles, miEditInterfaces, miHide;

    /** Framework element that right-click-menu was opened upon */
    private ModelNode rightClickedOn;

    /** Is the currently displayed graph drawn monochrome (due to disconnect)? */
    private boolean graphDrawnMonochrome = false;

    /** Reference to toggle buttons in toolbar */
    private JToggleButton antialiasButton, linebreakButton;

    /** The maximum number of lines displayed in a vertex */
    private static final int MAX_VERTEX_LABEL_LENGTH = 2000;

    /** Don't perform line breaks below this vertex label width */
    private static final int MIN_VERTEX_LABEL_WIDTH = 20;

    static {
        boolean ok = false;
        try {
            Process p = Runtime.getRuntime().exec("dot -V");
            p.waitFor();
            if (Files.readLines(p.getErrorStream()).get(0).contains("graphviz")) {
                ok = true;
            }
        } catch (Exception e) {}
        if (!ok) {
            Log.log(LogLevel.ERROR, "Graphviz (dot executable) does not seem to be installed.");
            System.exit(-1);
        }
    }

    public StandardViewGraphViz() {
        testLabel.setFont(FONT);
        twoLineTestLabel.setFont(FONT);
        lineIncrementY = twoLineTestLabel.getPreferredSize().height - testLabel.getPreferredSize().height;
        //this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setLayout(null);
        mouseHandlers = new MouseHandlerManager(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        // Create PopupMenu
        popupMenu = new JPopupMenu();
        miCreateModule = createMenuEntry("Create Element...");
        miEditInterfaces = createMenuEntry("Edit Interfaces...");
        miEditModule = createMenuEntry("Edit Parameters...");
        miDeleteModule = createMenuEntry("Delete Element");
        miSaveChanges = createMenuEntry("Save");
        miSaveAllFiles = createMenuEntry("Save All Files");
        //popupMenu.addSeparator();
        miCreateInterfaces = createMenuEntry("Create Interfaces... (deprecated)");
        miHide = createMenuEntry("Hide Element");
    }

    /**
     * @return Collection of vertices
     */
    public Collection<Vertex> getVertices() {
        return vertices;
    }

    /**
     * @return Collection of edges
     */
    public Collection<Edge> getEdges() {
        return edges;
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
    protected Vertex createVertexInstance(ModelNode fe) {
        return new Vertex(fe);
    }

    @Override
    protected synchronized void rootElementChanged(XMLNode viewConfiguration) {
        super.rootElementChanged(viewConfiguration);
        expandedGroups.clear();
        if (viewConfiguration != null) {
            for (XMLNode child : viewConfiguration.children()) {
                if (child.getName().equals("expanded")) {
                    String rootString = getRootElement().getQualifiedName('/') + "/";
                    StdStringList stringList = new StdStringList();
                    try {
                        stringList.deserialize(child);
                        for (int i = 0; i < stringList.stringCount(); i++) {
                            ModelNode expanded = getFinstruct().getIoInterface().getChildByQualifiedName(rootString + stringList.getString(i).toString(), '/');
                            if (expanded != null) {
                                expandedGroups.add(expanded);
                            }
                        }
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                toolBar.setSelected(DiverseSwitches.antialiasing, viewConfiguration.getBoolAttribute("antialiasing"));
            } catch (Exception e) {
                toolBar.setSelected(DiverseSwitches.antialiasing, true);
            }
            try {
                toolBar.setSelected(DiverseSwitches.lineBreaks, viewConfiguration.getBoolAttribute("line-breaks"));
            } catch (Exception e) {
                toolBar.setSelected(DiverseSwitches.lineBreaks, true);
            }
            try {
                zoom = (float)viewConfiguration.getDoubleAttribute("zoom");
            } catch (Exception e) {
                zoom = 1.0f;
            }
            try {
                rankSep.setValue(viewConfiguration.getDoubleAttribute("ranksep"));
            } catch (Exception e) {
                rankSep.setValue(RANK_SEP_DEFAULT);
            }
            try {
                nodeSep.setValue(viewConfiguration.getDoubleAttribute("nodesep"));
            } catch (Exception e) {
                nodeSep.setValue(NODE_SEP_DEFAULT);
            }
        }

        relayout(false);

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
     *
     * @param keepVerticesAndEdges Vertices and edges are not changed/recreated. Their bounds are merely adapted to new FontMetrics and zoom level.
     */
    public void relayout(boolean keepVerticesAndEdges) {
        try {
            if (!doingPdfExport()) {
                setFontMetricsToDefault(zoom);
            }
            if (!keepVerticesAndEdges) {
                final ModelNode root = getRootElement();

                int width = getWidth();
                int height = getHeight();
                //List<String> outputLines;
                graph.clear();
                mouseHandlers.clear();
                vertices.clear();
                subgraphs.clear();
                synchronized (RuntimeEnvironment.getInstance().getRegistryLock()) {
                    if (root == null) {
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
                        if (expandedGroups.contains(v.getModelElement())) {
                            createSubGraph(graph, v.getModelElement());
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
                        e.processFlags();
                    }

                    // flood unknown edges in graph
                    final ArrayList<Edge> visitedList = new ArrayList<Edge>();
                    for (Edge e : edges) {
                        if (e.isClassified()) {
                            visitedList.clear();
                            floodEdges(e, true, visitedList, edges); // forwards
                            visitedList.clear();
                            floodEdges(e, false, visitedList, edges); // backwards
                        }
                    }

                    // See whether we can classify more edges with flooding information
                    for (Edge e : edges) {
                        e.classify();
                    }
                }
            } else {
                for (Vertex v : vertices) {
                    v.reset();
                }
            }

            // Layout
            graph.applyLayout(Finstruct.EXPERIMENTAL_FEATURES ? toolBar.getSelection(Graph.Layout.values()) : Graph.Layout.dot, false);

            revalidate();
            repaint();

            // set start/pause icon state
            ThreadLocalCache.get();
            getFinstructWindow().updateStartPauseEnabled();

        } catch (Exception e) {
            Log.log(LogLevel.ERROR, this, e);
        }
    }

    /**
     * Floods unknown edges in graph until already classified edges are reached
     *
     * @param e Edge to flood from
     * @param forwards Flood edges in forward direction?
     * @param visited Visited edges
     * @param edges List of edges
     */
    private void floodEdges(Edge e, boolean forwards, ArrayList<Edge> visited, Collection<Edge> edges) {
        visited.add(e);
        AbstractGraphView.Vertex nextVertex = forwards ? e.destination : e.source;
        for (Edge nextEdge : edges) {
            if (!nextEdge.isClassified() && (!visited.contains(nextEdge))) {
                if ((forwards && nextEdge.source == nextVertex) || ((!forwards) && nextEdge.destination == nextVertex)) {
                    nextEdge.floodedFlags = e.flags | e.floodedFlags;
                    floodEdges(nextEdge, forwards, visited, edges);
                }
            }
        }
    }

    /**
     * Determines whether edge should be drawn upwards or downwards
     * (May be overridden by subclass)
     *
     * @param edge Edge to check
     * @return True, if edge should be drawn downwards
     */
    protected boolean drawEdgeDownwards(Edge edge) {
        return (edge.flags & (Edge.DRAW_DOWNWARDS | Edge.DRAW_UPWARDS)) == Edge.DRAW_DOWNWARDS;
    }

    /**
     * Generate subgraphs for all expanded groups (recursively)
     *
     * @param parent Parent Graph
     * @param group Group to create subgraph for
     */
    private void createSubGraph(Graph parent, ModelNode group) {
        Subgraph graph = new Subgraph(parent, group);
        subgraphs.add(graph);
        List<Vertex> subVertices = getVertices(group);
        for (Vertex v : subVertices) {
            if (expandedGroups.contains(v.getModelElement())) {
                createSubGraph(graph, v.getModelElement());
            } else {
                graph.add(v.gvVertex);
                vertices.add(v);
            }
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        Graphics2D g2d = null;
        BufferedImage imageBuffer = null;

        graphDrawnMonochrome = !isConnectedToRootNode();
        boolean monochrome = graphDrawnMonochrome;
        if (monochrome && (!doingPdfExport())) {
            imageBuffer = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            g2d = imageBuffer.createGraphics();
            g2d.setColor(new Color(0, 0, 0, 0));
            g2d.fillRect(0, 0, 10000, 10000);
        } else {
            g2d = (Graphics2D)g.create();
        }
        if (!doingPdfExport()) {
            super.paintComponent(g);
        }

        g2d.scale(zoom, zoom);
        boolean antialiasing = toolBar.isSelected(DiverseSwitches.antialiasing);

        if (edges != null && vertices != null) {

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

        if (monochrome && (!doingPdfExport())) {
            ImageFilter greyScaleFilter = new RGBImageFilter() {

                @Override
                public int filterRGB(int x, int y, int rgb) {
                    int a = rgb & 0xFF000000;
                    int b = (rgb & 0x00FF0000) >> 16;
                    int g = (rgb & 0x0000FF00) >> 8;
                    int r = rgb & 0x000000FF;
                    int gray = (b + g + r) / 3;
                    return a | (gray << 16) | (gray << 8) | gray;
                }
            };

            Image resultImage = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(imageBuffer.getSource(), greyScaleFilter));
            g.drawImage(resultImage, 0, 0, null);
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
    class Vertex extends AbstractGraphView.Vertex implements MouseHandler {

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

        public Vertex(ModelNode fe) {
            super(fe);
            gvVertex = new org.finroc.tools.finstruct.graphviz.Vertex();
            reset();
            gvVertex.setAttributeQuoted("description", fe.getName());
        }

        /**
         * @param text Text
         * @return Vertex width (in pixel) for a vertex with the specified text
         */
        private int getVertexWidth(String text) {
            //testLabel.setText(text);
            //return testLabel.getPreferredSize().width + 5;
            return getCurrentFontMetrics().stringWidth(text) + 5;
        }

        /**
         * @param lineCount Number of lines the vertex text has
         * @return Vertex height (in pixel) for a vertex with the specified number of lines
         */
        private int getVertexHeight(int lineCount) {
            return labelBaseHeight + ((lineCount - 1) * lineIncrementY) + 6;
        }

        /**
         * (may be overridden)
         * @return Don't perform line breaks below this label width
         */
        protected int getMinimumLineWidth() {
            return MIN_VERTEX_LABEL_WIDTH;
        }

        public void reset() {
            super.reset();
            boolean lineBreaks = toolBar.isSelected(DiverseSwitches.lineBreaks);
            String elementName = getModelElement().getName();
            if (elementName.length() > MAX_VERTEX_LABEL_LENGTH) {
                elementName = elementName.substring(0, MAX_VERTEX_LABEL_LENGTH) + " ...";
            }

            if (!lineBreaks) {
                label.clear();
                label.add(elementName);
                gvVertex.setSize(getVertexWidth(elementName), getVertexHeight(1));
            } else {
                String[] words = elementName.split("\\s");
                double bestScore = Integer.MAX_VALUE;
                Dimension bestDim = new Dimension(1000, 1000);
                StringBuilder sb = new StringBuilder();
                ArrayList<String> lines = new ArrayList<String>(30);
                ArrayList<String> bestText = new ArrayList<String>();

                // find optimal line breaks

                // Determine width of all words
                int[] wordWidth = new int[words.length];
                int spaceLength = getVertexWidth(" X") - getVertexWidth("X");
                int emptyLength = getVertexWidth("");
                int minWidth = emptyLength;
                for (int i = 0; i < words.length; i++) {
                    wordWidth[i] = getVertexWidth(words[i]) - emptyLength;
                    minWidth = Math.max(minWidth, wordWidth[i]); // try every width from 'longest word width' to 'Math.max(1000, longest word width + 200)'
                }
                minWidth = Math.max(getMinimumLineWidth(), minWidth + emptyLength);
                int maxWidth = Math.max(1000, minWidth + 200);

                // Simply try different widths ("semi-efficient" - but within O(n), n = #words)
                for (int width = minWidth; width <= maxWidth; width++) {
                    sb.setLength(0);
                    lines.clear();
                    int lineLength = emptyLength;

                    // create string
                    for (int i = 0; i < words.length; i++) {
                        if (sb.length() == 0) {
                            sb.append(words[i]);
                            lineLength += wordWidth[i];
                        } else {
                            if (lineLength + spaceLength + wordWidth[i] < width) {
                                sb.append(" ").append(words[i]);
                                lineLength = lineLength + spaceLength + wordWidth[i];
                            } else {
                                lines.add(sb.toString());
                                sb.setLength(0);
                                sb.append(words[i]);
                                lineLength = wordWidth[i] + emptyLength;
                            }
                        }
                    }
                    if (sb.length() != 0) {
                        lines.add(sb.toString());
                    }

                    // compare to best combination
                    //double score = Math.pow(w, 1.1) + (h * 1.7);
                    int height = getVertexHeight(lines.size());
                    double score = width + (height * 1.7);
                    if (score < bestScore) {
                        bestDim.width = width;// + emptyLength;
                        bestDim.height = height;
                        bestText.clear();
                        bestText.addAll(lines);
                        bestScore = score;
                    }
                    if (lines.size() == 1) {
                        break;
                    }
                }

                gvVertex.setSize(bestDim.width, bestDim.height);
                label = bestText;
            }

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
            //g2d.setFont(testLabel.getFont());
            int yOffset = doingPdfExport() ? 6 : 5;
            for (int i = 0; i < label.size(); i++) {
                g2d.drawString(label.get(i), rect.x + 3, (rect.y + rect.height - yOffset) + ((i + 1) - label.size()) * lineIncrementY);
            }
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
            if (isGroup()) { // draw + for group
                if (expandIcon == null) {
                    expandIcon = new ExpandIcon(6, 6, getModelElement());
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
            if (gvVertex.getLayoutPosition() != null) {
                updateRectangle();
                return rect.contains(p);
            }
            return false;
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
                    expandInTree(true, getModelElement());
                    expandInTree(false, v.getModelElement());
                }
                StandardViewGraphViz.this.repaint();
            }
            if (over == this) {
                long time = System.currentTimeMillis();
                if (time - lastClick < Finstruct.DOUBLE_CLICK_DELAY) {
                    if (getModelElement().getChildCount() > 0 ||
                            (getFinrocElement() != null && getFinrocElement().getFlag(FrameworkElementFlags.FINSTRUCTABLE_GROUP))) {
                        getFinstructWindow().showElement(getModelElement());
                    }
                } else {
                    expandInTree(true, getModelElement());
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
        private void expandInTree(boolean leftTree, ModelNode frameworkElement) {
            ArrayList<ModelNode> expand = new ArrayList<ModelNode>();
            expand.add(frameworkElement);
            for (int i = 0; i < frameworkElement.getChildCount(); i++) {
                ModelNode child = (ModelNode)frameworkElement.getChildAt(i);
                if ((child instanceof RemoteFrameworkElement) && isInterface((RemoteFrameworkElement)child)) {
                    expand.add(child);
                }
            }
            if (connectionPanel != null) {
                connectionPanel.expandOnly(leftTree, expand);
            }
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
    protected class Edge extends AbstractGraphView.Edge implements MouseHandler {

        /** UID */
        private static final long serialVersionUID = -1804606674849562189L;

        /** graphviz edge */
        private org.finroc.tools.finstruct.graphviz.Edge gvEdge;

        /** Flags determining in which direction to draw edges and how to classify them */
        public static final int SENSOR_DATA = 1, CONTROLLER_DATA = 2, DRAW_UPWARDS = 4, DRAW_DOWNWARDS = 8;

        /** Flags for edge */
        protected int flags;

        /** Flags from flooding all unknown edges in graph */
        protected int floodedFlags;

        protected Edge(Vertex src, Vertex dest) {
            gvEdge = new org.finroc.tools.finstruct.graphviz.Edge(src.gvVertex, dest.gvVertex);
        }

        /**
         * Inizializes flags variable
         */
        protected void processFlags() {
            if ((dataTypeFlags & FrameworkElementFlags.CONTROLLER_DATA) != 0) {
                flags |= CONTROLLER_DATA | DRAW_DOWNWARDS;
            }
            if ((dataTypeFlags & FrameworkElementFlags.SENSOR_DATA) != 0) {
                flags |= SENSOR_DATA | DRAW_UPWARDS;
            }
        }

        /**
         * Classify edge after flooding graph
         */
        public void classify() {
            assert(flags == 0 || floodedFlags == 0);
            if (flags == 0) {
                flags = floodedFlags;
            }
            if (drawEdgeDownwards(this)) {
                gvEdge.setReversedInDotLayout(true);
            }
        }

        /**
         * \return Does edge have fixed classification as sensor and/or controller data?
         */
        public boolean isClassified() {
            return flags != 0;
        }

        /**
         * @return Does edge transport controller data (only)?
         */
        public boolean isControllerData() {
            return (flags & (SENSOR_DATA | CONTROLLER_DATA)) == CONTROLLER_DATA;
        }

        /**
         * @return Does edge transport sensor data (only)?
         */
        public boolean isSensorData() {
            return (flags & (SENSOR_DATA | CONTROLLER_DATA)) == SENSOR_DATA;
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
            // g2d.draw(path);  // this looks not as nice
            path.lineTo(x2 - Math.cos(angle) * ARROW_LEN * 0.7, y2 - Math.sin(angle) * ARROW_LEN * 0.7);
            path.closePath();
            g2d.fill(path);
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
            if (zoom == 1 && gvEdge.getPath() != null) {
                StandardViewGraphViz.this.repaint(gvEdge.getPath().getBounds());
            } else {
                StandardViewGraphViz.this.repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event, MouseHandler over) {
            if (!inConnectionMode()) {
                getFinstruct().treeToolBar.setSelected(Finstruct.Mode.connect);
                connectionPanel.setRightTree(connectionPanel.getLeftTree());
            }

            final ArrayList<ModelNode> srcPorts = new ArrayList<ModelNode>();
            final HashSet<ModelNode> destPorts = new HashSet<ModelNode>();
            final ArrayList<AbstractPort> remoteEdgeDestinations = new ArrayList<AbstractPort>();

            // all forward edges
            ArrayList<RemotePort> remotePorts = getSource().getModelElement().getPortsBelow(null);
            for (RemotePort remotePort : remotePorts) {
                NetPort np = remotePort.getPort().asNetPort();
                if (np != null) {
                    boolean added = false;
                    remoteEdgeDestinations.clear();
                    int reverseIndex = np.getRemoteEdgeDestinations(remoteEdgeDestinations);
                    for (int i = 0; i < reverseIndex; i++) {
                        AbstractPort port = remoteEdgeDestinations.get(i);
                        for (RemotePort remoteDestPort : RemotePort.get(port)) {
                            if (remoteDestPort.isNodeAncestor(getDestination().getModelElement())) {
                                destPorts.add(remoteDestPort);
                                if (!added) {
                                    srcPorts.add(remotePort);
                                    added = true;
                                }
                            }
                        }
                    }
                }
            }

            // reverse network edges
            remotePorts = getDestination().getModelElement().getPortsBelow(null);
            for (RemotePort remotePort : remotePorts) {
                NetPort np = remotePort.getPort().asNetPort();
                if (np != null) {
                    remoteEdgeDestinations.clear();
                    int reverseIndex = np.getRemoteEdgeDestinations(remoteEdgeDestinations);
                    for (int i = reverseIndex; i < remoteEdgeDestinations.size(); i++) {
                        AbstractPort port = remoteEdgeDestinations.get(i);
                        for (RemotePort remoteSourcePort : RemotePort.get(port)) {
                            if (remoteSourcePort.isNodeAncestor(getSource().getModelElement())) {
                                if (!srcPorts.contains(remoteSourcePort)) {
                                    srcPorts.add(remoteSourcePort);
                                }
                                if (!destPorts.contains(remotePort)) {
                                    destPorts.add(remotePort);
                                }
                            }
                        }
                    }
                }
            }

            if (connectionPanel != null) {
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
        if (doingPdfExport()) {
            return;
        }
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

    @Override
    public void initAsEmbeddedView(StandardViewGraphViz parentView, ModelNode root) {
        this.toolBar = parentView.getFinstructWindow().getToolBar();
        super.initAsEmbeddedView(parentView, root);
    }

    /**
     * Expansion icon in top-left of group representation
     */
    public class ExpandIcon implements MouseHandler {

        /** Icon area */
        private Rectangle bounds = new Rectangle();

        /** Group that can be expanded/collapsed pressing this icon */
        private ModelNode group;

        public ExpandIcon(int width, int height, ModelNode group) {
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
                relayout(false);
            }
        }
    }

    /**
     * Subgraph in GraphViz view
     */
    private class Subgraph extends Graph {

        /** Wrapped framework element */
        private final ModelNode modelNode;

        /** Collapse icon */
        private ExpandIcon expandIcon;

        /** label bounds */
        private Rectangle labelBounds = null;

        private Subgraph(Graph parent, ModelNode fe) {
            super(parent);
            modelNode = fe;
            expandIcon = new ExpandIcon(6, 6, modelNode);
        }

        /**
         * Paint SubGraph boundary and icon
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            if (labelBounds == null) {
                testLabel.setText(modelNode.getName());
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

            g2d.drawString(modelNode.getName(), labelBounds.x, labelBounds.y + labelBounds.height);
        }
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {
        super.initMenuAndToolBar(menuBar, toolBar);

        // tool bar
        this.toolBar = toolBar;

        if (Finstruct.EXPERIMENTAL_FEATURES) {
            toolBar.addToggleButton(new MAction(Graph.Layout.dot, null, "dot layout", this));
            toolBar.addToggleButton(new MAction(Graph.Layout.neato, null, "neato layout", this));
            toolBar.addToggleButton(new MAction(Graph.Layout.fdp, null, "fdp layout", this));
            toolBar.addSeparator();
            toolBar.setSelected(Graph.Layout.dot);
        }

        boolean antialiasEnabled = antialiasButton != null ? antialiasButton.isSelected() : true;
        boolean linebreakEnabled = linebreakButton != null ? linebreakButton.isSelected() : true;
        antialiasButton = toolBar.addToggleButton(new MAction(DiverseSwitches.antialiasing, "antialias-wikimedia-public_domain.png", "Antialiasing", this), true);
        linebreakButton = toolBar.addToggleButton(new MAction(DiverseSwitches.lineBreaks, "line-break-max.png", "Line Breaks", this), true);
        zoomIn = toolBar.createButton("zoom-in-ubuntu.png", "Zoom in", this);
        zoomOut = toolBar.createButton("zoom-out-ubuntu.png", "Zoom out", this);
        zoom1 = toolBar.createButton("zoom-original-ubuntu.png", "Zoom 100%", this);
        toolBar.addSeparator();
        toolBar.add(new JLabel("nodesep"));
        toolBar.add(nodeSep);
        nodeSep.addChangeListener(this);
        nodeSep.setPreferredSize(new Dimension(60, nodeSep.getPreferredSize().height));
        nodeSep.setMaximumSize(nodeSep.getPreferredSize());
        toolBar.add(new JLabel("ranksep"));
        toolBar.add(rankSep);
        rankSep.addChangeListener(this);
        rankSep.setPreferredSize(new Dimension(60, rankSep.getPreferredSize().height));
        rankSep.setMaximumSize(rankSep.getPreferredSize());
        toolBar.setSelected(DiverseSwitches.antialiasing, antialiasEnabled);
        toolBar.setSelected(DiverseSwitches.lineBreaks, linebreakEnabled);
        if (getFinstruct() != null) {
            nodeSep.getEditor().getComponent(0).addKeyListener(getFinstruct());
            rankSep.getEditor().getComponent(0).addKeyListener(getFinstruct());
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void actionPerformed(ActionEvent ae) {
        ThreadLocalCache.get();
        if (ae instanceof MActionEvent) {
            Enum e = ((MActionEvent)ae).getEnumID();
            if (e instanceof Graph.Layout) {
                relayout(true);
            } else if (e == DiverseSwitches.antialiasing) {
                repaint();
            } else if (e == DiverseSwitches.lineBreaks) {
                relayout(true);
            }
        } else if (ae.getSource() == zoomIn) {
            setZoom(zoom * 1.33);
        } else if (ae.getSource() == zoomOut) {
            setZoom(zoom * 0.75);
        } else if (ae.getSource() == zoom1) {
            setZoom(1);
        } else if (ae.getSource() == miCreateModule) {
            if (rightClickedOn instanceof RemoteFrameworkElement) {
                CreateModuleDialog cmd = new CreateModuleDialog(getFinstructWindow());
                cmd.show((RemoteFrameworkElement)rightClickedOn, getFinstruct().getIoInterface());
                relayout(false);
            }
        } else if (ae.getSource() == miSaveChanges) {
            RemoteFrameworkElement fe = RemoteFrameworkElement.getParentWithFlags(rightClickedOn, FrameworkElementFlags.FINSTRUCTABLE_GROUP, true);
            if (fe != null) {
                RemoteRuntime rr = RemoteRuntime.find(fe);
                if (rr == null) {
                    Finstruct.showErrorMessage("Element is not a child of a remote runtime", false, false);
                } else {
                    rr.getAdminInterface().saveFinstructableGroup(fe.getRemoteHandle());
                }
            }
        } else if (ae.getSource() == miSaveAllFiles) {
            RemoteRuntime rr = RemoteRuntime.find(getRootElement());
            if (rr == null) {
                Finstruct.showErrorMessage("Current view root element is not a child of a remote runtime", false, false);
            } else {
                rr.getAdminInterface().saveAllFinstructableFiles();
            }
        } else if (ae.getSource() == miDeleteModule) {
            RemoteRuntime rr = RemoteRuntime.find(rightClickedOn);
            if (rr == null) {
                Finstruct.showErrorMessage("Element is not a child of a remote runtime", false, false);
            } else if (rightClickedOn instanceof RemoteFrameworkElement) {
                rr.getAdminInterface().deleteElement(((RemoteFrameworkElement)rightClickedOn).getRemoteHandle());
                refreshViewAfter(500);
            }
        } else if (ae.getSource() == miEditModule) {
            if (rightClickedOn instanceof RemoteFrameworkElement) {
                new ParameterEditDialog(getFinstruct()).show((RemoteFrameworkElement)rightClickedOn, true, false);
                refreshViewAfter(500);
            }
        } else if (ae.getSource() == miCreateInterfaces) {
            if (rightClickedOn instanceof RemoteFrameworkElement) {
                new CreateInterfacesDialog(getFinstruct()).show((RemoteFrameworkElement)rightClickedOn, getFinstruct().getIoInterface());
                refreshViewAfter(500);
            }
        } else if (ae.getSource() == miEditInterfaces) {
            if (rightClickedOn instanceof RemoteFrameworkElement) {
                new ParameterEditDialog(getFinstruct()).show((RemoteFrameworkElement)rightClickedOn, true, true);
                refreshViewAfter(500);
            }
        } else if (ae.getSource() == miHide) {
            String link = rightClickedOn.getQualifiedName('/');
            if (!getFinstruct().hiddenElements.contains(link)) {
                getFinstruct().hiddenElements.add(link);
                relayout(false);
            }
        } else if (ae.getSource() instanceof Timer) {
            relayout(false);
        } else {
            super.actionPerformed(ae);
        }
    }

    /**
     * Refreshes view after the specified number of milliseconds
     *
     * @param ms Milliseconds to wait until refreshing
     */
    private void refreshViewAfter(int ms) {
        Timer timer = new Timer(ms, this);
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    protected void updateView() {
        if (getRootElement() != null) {
            boolean monochrome = !isConnectedToRootNode();
            boolean repaint = monochrome != graphDrawnMonochrome;
            if (monochrome) {
                tryReconnectingToRootNode();
                repaint |= isConnectedToRootNode();
            }
            if (repaint) {
                repaint();
            }
        }
    }

    /**
     * Set Zoom
     *
     * @param zoom Zoom (100% = 1.0f)
     */
    public void setZoom(double zoom) {
        this.zoom = (float)zoom;
        mouseHandlers.setZoom(zoom);
        relayout(true);
        revalidate();
        repaint();
    }

    /**
     * @return Current zoom factor
     */
    public float getZoom() {
        return this.zoom;
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
        return getFinstruct().treeToolBar.getSelection(Finstruct.Mode.values()) == Finstruct.Mode.connect;
    }

    @Override
    public void refresh() {
        relayout(false);
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
                relayout(true);
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
                        rightClickedOn = sg.modelNode;
                    }
                }
            } else if (mh instanceof Vertex) {
                rightClickedOn = ((Vertex)mh).getModelElement();
            } else {
                rightClickedOn = null;
            }
            if (rightClickedOn != null) {
                boolean rootNodeConnected = isConnectedToRootNode();
                boolean finstructedElement = rootNodeConnected && (rightClickedOn instanceof RemoteFrameworkElement &&
                                             (((RemoteFrameworkElement)rightClickedOn).getFlag(FrameworkElementFlags.FINSTRUCTED) ||
                                              ((RemoteFrameworkElement)rightClickedOn).getFlag(FrameworkElementFlags.FINSTRUCTABLE_GROUP)));
                boolean expanded = rightClickedOn == getRootElement() || expandedGroups.contains(rightClickedOn);
                miCreateModule.setEnabled(finstructedElement && expanded);
                miDeleteModule.setEnabled(finstructedElement && !expanded);
                miCreateInterfaces.setEnabled(finstructedElement && (!(rightClickedOn instanceof RemotePort)));
                miEditInterfaces.setEnabled(false);
                miEditModule.setEnabled(false);
                miSaveChanges.setEnabled(false);
                miSaveChanges.setText("Save");
                miSaveAllFiles.setEnabled(false);
                miSaveAllFiles.setText("Save All Files");
                miHide.setEnabled(rightClickedOn != getRootElement());
                RemoteRuntime rr = RemoteRuntime.find(getRootElement());
                if (rr != null) {
                    if (rightClickedOn instanceof RemoteFrameworkElement) {
                        try {
                            StaticParameterList parameterList = (StaticParameterList)rr.getAdminInterface().getAnnotation(
                                                                    ((RemoteFrameworkElement)rightClickedOn).getRemoteHandle(), StaticParameterList.TYPE);
                            miEditModule.setEnabled(parameterList != null);
                        } catch (Exception exception) {}
                    }

                    if (finstructedElement) {
                        RemoteFrameworkElement finstructableGroup = RemoteFrameworkElement.getParentWithFlags(rightClickedOn, FrameworkElementFlags.FINSTRUCTABLE_GROUP, true);
                        if (finstructableGroup != null) {
                            final String STRUCTURE_FILE_TAG_START = "finstructable structure file:";
                            String structureFile = "";
                            for (String tag : finstructableGroup.getTags()) {
                                if (tag.startsWith(STRUCTURE_FILE_TAG_START)) {
                                    structureFile = tag.substring(STRUCTURE_FILE_TAG_START.length()).trim();
                                    break;
                                }
                            }
                            if (structureFile.length() == 0) {
                                try {
                                    StaticParameterList parameterList = (StaticParameterList)rr.getAdminInterface().getAnnotation(
                                                                            finstructableGroup.getRemoteHandle(), StaticParameterList.TYPE);
                                    for (int i = 0; i < parameterList.size(); i++) {
                                        if (parameterList.get(i).getName().equalsIgnoreCase("xml file")) {
                                            structureFile = parameterList.get(i).valPointer().getData().toString();
                                            break;
                                        }
                                    }
                                } catch (Exception exception) {
                                }
                            }
                            //miSaveChanges.setText("<html>Save <font color=#8080D0>(to " + file + ")</font></html>");
                            if (structureFile.length() > 0) {
                                /*if (structureFile.contains("/")) {
                                    structureFile = structureFile.substring(structureFile.lastIndexOf("/") + 1);
                                }*/
                                miSaveChanges.setEnabled(true);
                                miSaveChanges.setText("Save \"" + structureFile + "\"");
                            }
                        }

                        if (((RemoteFrameworkElement)rightClickedOn).getFlag(FrameworkElementFlags.FINSTRUCTABLE_GROUP)) {
                            try {
                                EditableInterfaces editableInterfaces = (EditableInterfaces)rr.getAdminInterface().getAnnotation(
                                        ((RemoteFrameworkElement)rightClickedOn).getRemoteHandle(), EditableInterfaces.TYPE);
                                miEditInterfaces.setEnabled(editableInterfaces != null);
                            } catch (Exception exception) {}
                        }
                    }
                    miSaveAllFiles.setEnabled(true);
                    //miSaveAllChanges.setText("<html>Save All Files <font color=#8080D0>(in " + rr.getName() + ")</font></html>");
                    miSaveAllFiles.setText("<html>Save All Files <font color=#8080D0>in '" + rr.getName() + "'</font></html>");
                    //miSaveAllChanges.setText("Save All Files in \"" + rr.getName() + "\"");
                }

                // show right-click-menu with create-action
                popupMenu.show(this, e.getX(), e.getY());
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
    public void storeViewConfiguration(XMLNode node) {
        super.storeViewConfiguration(node);
        if (expandedGroups.size() > 0) {
            StdStringList serializedExpandedGroups = new StdStringList();
            for (ModelNode expandedGroup : expandedGroups) {
                if (expandedGroup instanceof RemoteFrameworkElement) {
                    serializedExpandedGroups.add(expandedGroup.getQualifiedName('/').substring(getRootElement().getQualifiedName('/').length() + 1));
                }
            }
            try {
                serializedExpandedGroups.serialize(node.addChildNode("expanded"));
            } catch (Exception e) {
                Log.log(LogLevel.ERROR, this, e);
            }
        }
        if (!toolBar.isSelected(DiverseSwitches.antialiasing)) {
            node.setAttribute("antialiasing", false);
        }
        if (!toolBar.isSelected(DiverseSwitches.lineBreaks)) {
            node.setAttribute("line-breaks", false);
        }
        if (zoom != 1.0f) {
            node.setAttribute("zoom", zoom);
        }
        if (((Number)rankSep.getValue()).doubleValue() != RANK_SEP_DEFAULT) {
            node.setAttribute("ranksep", ((Number)rankSep.getValue()).doubleValue());
        }
        if (((Number)nodeSep.getValue()).doubleValue() != NODE_SEP_DEFAULT) {
            node.setAttribute("nodesep", ((Number)nodeSep.getValue()).doubleValue());
        }
    }

    /**
     * @return True if antialiasing should be used for rendering view
     */
    public boolean isAntialiasingEnabled() {
        return toolBar.isSelected(DiverseSwitches.antialiasing);
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
//              // show dialog to specify static parameters
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
