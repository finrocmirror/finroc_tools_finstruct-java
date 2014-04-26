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
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.plugins.data_types.Paintable;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.gui.util.gui.IconManager;

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

    /** Height of component visualization */
    private static final int VISUALIZATION_HEIGHT = 60;

    /** Max. width of component visualization */
    private static final int VISUALIZATION_WIDTH = 80;

    /** Component background image */
    private ImageIcon background = (ImageIcon)IconManager.getInstance().getIcon("brushed-titanium-max.png");


    public void clear() {
        // Delete old ports
        for (ConnectingPortAccessor<?> port : visualizationPorts) {
            port.delete();
        }
        visualizationPorts.clear();
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
     * Vertex that displays real-time component visualization
     */
    @SuppressWarnings("rawtypes")
    public class AnimatedVertex extends Vertex implements PortListener, Runnable {

        /** ports used to get behaviour data via push */
        private ConnectingPortAccessor<?> port;

        public AnimatedVertex(RemoteFrameworkElement fe) {
            super(fe);

            // Create port for visualization data access */
            for (RemoteFrameworkElement subElement : fe.getFrameworkElementsBelow(null)) {
                for (String tag : subElement.getTags()) {
                    if (tag.startsWith("visualization-") && (subElement instanceof RemotePort)) {
                        port = new ConnectingPortAccessor((RemotePort)subElement, "");
                        visualizationPorts.add(port);
                        ((PortBase)port.getPort()).addPortListenerRaw(this);
                        port.init();
                        port.setAutoUpdate(true);
                        return;
                    }
                }
            }
        }

        public void reset() {
            super.reset();

            // increase height, so that we have space for upper and lower bar
            gvVertex.setSize(Math.max(gvVertex.getWidth(), VISUALIZATION_WIDTH), gvVertex.getHeight() + VISUALIZATION_HEIGHT);
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            Object currentVisualization = null;
            try {
                currentVisualization = port.get();
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
                Color background = brighten(new Color(45, 45, 60), brighten);
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
                g2d.drawString(label.get(i), rect.x + 3, (rect.y + rect.height - (5 + VISUALIZATION_HEIGHT)) + ((i + 1) - label.size()) * lineIncrementY);
            }

            // draw visualization
            if (currentVisualization instanceof Paintable) {
                Paintable paintable = (Paintable)currentVisualization;

                // scale to fit etc.
                Rectangle2D originalBounds = paintable.getBounds();
                if (originalBounds != null) {
                    Rectangle2D fitTo = new Rectangle2D.Double(0, 0, rect.getWidth(), VISUALIZATION_HEIGHT);
                    AffineTransform at = g2d.getTransform();

                    double factorX = (fitTo.getWidth()) / (originalBounds.getWidth());
                    double factorY = (fitTo.getHeight()) / (originalBounds.getHeight());
                    double factor = Math.min(factorX, factorY);

                    g2d.translate(rect.x + Math.max(0, (fitTo.getWidth() - factor * originalBounds.getWidth()) / 2), rect.y + rect.height - (paintable.isYAxisPointingDownwards() ? VISUALIZATION_HEIGHT : 0));
                    g2d.scale(factor, paintable.isYAxisPointingDownwards() ? factor : -factor);
                    g2d.translate(-originalBounds.getMinX(), -originalBounds.getMinY());
                    Rectangle oldClip = g2d.getClipBounds();
                    g2d.setClip(new Rectangle2D.Double(originalBounds.getX() * (factorX / factor), originalBounds.getY() * (factorY / factor),
                                                       originalBounds.getWidth() * (factorX / factor), originalBounds.getHeight() * (factorY / factor)));
                    paintable.paint(g2d);
                    g2d.setClip(oldClip);
                    g2d.setTransform(at);
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
        }

        @Override
        public void run() {
            repaint(rect.x, rect.y + rect.height - VISUALIZATION_HEIGHT, rect.width, VISUALIZATION_HEIGHT);
        }

        @Override
        public void portChanged(AbstractPort origin, Object value) {
            SwingUtilities.invokeLater(this);
        }
    }
}
