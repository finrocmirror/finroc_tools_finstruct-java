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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JMenuBar;
import javax.swing.JToggleButton;

import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.plugins.data_types.Paintable;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.gui.util.gui.IconManager;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MToolBar;

/**
 * @author Max Reichardt
 *
 * View for real-time component visualization
 */
public class ComponentVisualization extends StandardViewGraphViz {

    /** UID */
    private static final long serialVersionUID = -94026792839034582L;

    /** List of currently active/used port accessors to retrieve behaviour data */
    private final ArrayList<ConnectingPortAccessor<?>> visualizationPorts = new ArrayList<ConnectingPortAccessor<?>>();

    /** Default Height of component visualization */
    private static final int DEFAULT_VISUALIZATION_HEIGHT = 90;

    /** Default Max. width of component visualization */
    private static final int DEFAULT_VISUALIZATION_WIDTH = 120;

    /** Current visualization height and width (possibly with zooming applied) */
    private int visualizationHeight = DEFAULT_VISUALIZATION_HEIGHT, visualizationWidth = DEFAULT_VISUALIZATION_WIDTH;

    /** Component background image */
    private ImageIcon background = (ImageIcon)IconManager.getInstance().getIcon("brushed-titanium-max.png");

    /** Reference to toggle button in toolbar */
    private JToggleButton zoomLabelsButton;

    /** Diverse toolbar switches */
    private enum ToolbarSwitches { ZoomLabels }

    /** Current zoom level of visualization */
    private float visualizationZoom = 1.0f;

    /** Enum for level of detail */
    public enum LevelOfDetail { Low, Mid, High };

    /** Port tags for each level of detail - ordered by preference */
    private final static String[][] PORT_TAGS = new String[][] {
        { "visualization-low", "visualization-less", "visualization-all", "visualization-mid", "visualization-more", "visualization-high" },
        { "visualization-mid", "visualization-all", "visualization-more", "visualization-less", "visualization-high", "visualization-low" },
        { "visualization-high", "visualization-more", "visualization-all", "visualization-mid", "visualization-less", "visualization-low" }
    };

    /** Maximum Y resolution for each level of detail */
    private final static int[] MAX_Y_RESOLUTION = { 90, 180, Integer.MAX_VALUE };

    public void clear() {
        // Delete old ports
        for (ConnectingPortAccessor<?> port : visualizationPorts) {
            port.delete();
        }
        visualizationPorts.clear();
        graphAppearance.modules = new Color(45, 45, 60);
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {
        zoomLabelsButton = toolBar.addToggleButton(new MAction(ToolbarSwitches.ZoomLabels, "zoom-labels-derived.png", "Zoom Labels", this), true);
        super.initMenuAndToolBar(menuBar, toolBar);
    }

    @Override
    protected void destroy() {
        clear();
    }

    @Override
    protected void relayout() {
        clear();
        super.relayout();
    }

    @Override
    protected Vertex createVertexInstance(ModelNode fe) {
        if (hasRealtimeVisualization(fe)) {
            return new AnimatedVertex((RemoteFrameworkElement)fe);
        } else {
            return super.createVertexInstance(fe);
        }
    }

    /**
     * @param fe Framework element
     * @return True if framework element has real-time visualization
     */
    private static boolean hasRealtimeVisualization(ModelNode fe) {
        if (fe instanceof RemoteFrameworkElement) {
            for (RemoteFrameworkElement subElement : fe.getFrameworkElementsBelow(null)) {
                for (String tag : subElement.getTags()) {
                    if (tag.startsWith("visualization-")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param fe Framework element to find visualization port for
     * @param tags Port must have (at least) one of these tags. The first tag has rank 1. The second rank 2 etc.
     * @return Port with one of these tags. If multiple ports have an appropriate tag, one with the lowest rank tag is returned.
     */
    static RemotePort findVisualizationPort(RemoteFrameworkElement fe, final String[] tags) {
        RemotePort result = null;
        int bestResultRank = Integer.MAX_VALUE;
        for (RemoteFrameworkElement subElement : fe.getFrameworkElementsBelow(null)) {
            if (subElement instanceof RemotePort) {
                for (String tag : subElement.getTags()) {
                    for (int i = 0; i < tags.length; i++) {
                        if (i < bestResultRank && tags[i].equals(tag)) {
                            result = (RemotePort)subElement;
                            bestResultRank = i;
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == zoomLabelsButton) {
            if (zoomLabelsButton.isSelected()) {
                setZoom(getZoom() < 1.0 ? getZoom() : visualizationZoom);
            } else {
                visualizationZoom = 1.0f;
                setZoom(getZoom());
            }
            return;
        }
        super.actionPerformed(ae);
    }

    @Override
    public void setZoom(double zoom) {
        int oldHeight = visualizationHeight;
        if (zoomLabelsButton.isSelected()) {
            super.setZoom(zoom);
            visualizationZoom = 1.0f;
            visualizationHeight = DEFAULT_VISUALIZATION_HEIGHT;
            visualizationWidth = DEFAULT_VISUALIZATION_WIDTH;
        } else {
            if (zoom != 1.0f) {
                visualizationZoom *= zoom;
            }
            if (visualizationZoom <= 1.0f || zoom == 1.0f) {
                super.setZoom(zoom);
                visualizationZoom = 1.0f;
                visualizationHeight = DEFAULT_VISUALIZATION_HEIGHT;
                visualizationWidth = DEFAULT_VISUALIZATION_WIDTH;
            } else {
                super.setZoom(1.0);
                visualizationHeight = (int)(DEFAULT_VISUALIZATION_HEIGHT * visualizationZoom);
                visualizationWidth = (int)(DEFAULT_VISUALIZATION_WIDTH * visualizationZoom);
            }
        }
        if (oldHeight != visualizationHeight) {
            relayout();
        }
    }

    /**
     * Vertex that displays real-time component visualization
     */
    @SuppressWarnings("rawtypes")
    public class AnimatedVertex extends Vertex implements PortListener {

        /** ports used to get behaviour data via push */
        private ConnectingPortAccessor<?> port;

        public AnimatedVertex(RemoteFrameworkElement fe) {
            super(fe);

            // Determine appropriate level of detail
            LevelOfDetail levelOfDetail = LevelOfDetail.Low;
            int yResolution = (int)((getZoom() * visualizationZoom) * DEFAULT_VISUALIZATION_HEIGHT);
            for (int i = 0; i <= 2; i++) {
                if (yResolution <= MAX_Y_RESOLUTION[i]) {
                    levelOfDetail = LevelOfDetail.values()[i];
                    break;
                }
            }

            // Create port for visualization data access */
            RemotePort remotePort = findVisualizationPort(fe, PORT_TAGS[levelOfDetail.ordinal()]);

            if (remotePort != null) {
                port = new ConnectingPortAccessor(remotePort, "");
                visualizationPorts.add(port);
                ((PortBase)port.getPort()).addPortListenerRaw(this);
                port.init();
                port.setAutoUpdate(true);
                return;
            }
        }

        public void reset() {
            super.reset();

            // increase height, so that we have space for upper and lower bar
            gvVertex.setSize(Math.max(gvVertex.getWidth(), visualizationWidth), gvVertex.getHeight() + visualizationHeight);
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Object currentVisualization = null;
            try {
                currentVisualization = port.getAutoLocked();
            } catch (Exception e) {
                //super.paint(g2d);
                //return;
            }
            if (currentVisualization == null) {
                //super.paint(g2d);
                //return;
            }

            int h = getHighlightLevel();

            updateRectangle();

            // set colors
            int brighten = h * 64;
            // draw background
            if (true) {
                //Color background = brighten(Color.DARK_GRAY, brighten);
                Color background = brighten(graphAppearance.modules, brighten);
                g2d.setColor(background);
                g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
            } else {
                Rectangle oldClip = g2d.getClipBounds();
                g2d.setClip(rect);
                background.paintIcon(null, g2d, rect.x, rect.y);
                g2d.setClip(oldClip);
            }


            if (h >= 1) {
                drawRectangleGlow(g2d, rect.x, rect.y, rect.width, rect.height, 0.7f, 0.075f);
            }

            // draw text
            g2d.setColor(getTextColor());
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < label.size(); i++) {
                g2d.drawString(label.get(i), rect.x + 3, (rect.y + rect.height - (5 + visualizationHeight)) + ((i + 1) - label.size()) * lineIncrementY);
            }

            // draw visualization
            if (currentVisualization instanceof Paintable) {
                Paintable paintable = (Paintable)currentVisualization;

                // scale to fit etc.
                Rectangle2D originalBounds = paintable.getBounds();
                if (originalBounds != null) {
                    Rectangle2D fitTo = new Rectangle2D.Double(0, 0, rect.getWidth() - 2, visualizationHeight);
                    AffineTransform at = g2d.getTransform();
                    Rectangle oldClip = g2d.getClipBounds();

                    double factorX = (fitTo.getWidth()) / (originalBounds.getWidth());
                    double factorY = (fitTo.getHeight()) / (originalBounds.getHeight());
                    double factor = Math.min(factorX, factorY);

                    g2d.translate(rect.x + 1, rect.y + rect.height - visualizationHeight);
                    g2d.setClip(fitTo.createIntersection(g2d.getClipBounds()));
                    g2d.translate(Math.max(0, (fitTo.getWidth() - factor * originalBounds.getWidth()) / 2), (paintable.isYAxisPointingDownwards() ? 0 : visualizationHeight));
                    g2d.scale(factor, paintable.isYAxisPointingDownwards() ? factor : -factor);
                    g2d.translate(-originalBounds.getMinX(), -originalBounds.getMinY());
                    paintable.paint(g2d);
                    g2d.setTransform(at);
                    g2d.setClip(oldClip);
                }
            }

            // draw border etc.
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
            if (isGroup()) { // draw + for group
                if (expandIcon == null) {
                    expandIcon = new ExpandIcon(6, 6, getModelElement());
                }
                expandIcon.paint(g2d, rect.x + rect.width - 5, rect.y - 1, true);
            }

            releaseAllLocks();
        }

        @Override
        public void portChanged(AbstractPort origin, Object value) {
            if (getZoom() != 1.0f) {
                repaint((int)(rect.x * getZoom()), (int)((rect.y + rect.height - visualizationHeight) * getZoom()), (int)(rect.width * getZoom()), (int)(visualizationHeight * getZoom())); // thread-safe
            } else {
                repaint(rect.x, rect.y + rect.height - visualizationHeight, rect.width, visualizationHeight); // thread-safe
            }
        }
    }
}
