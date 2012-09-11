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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTags;
import org.finroc.core.datatype.CoreNumber;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.std.PortBase;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;

/**
 * @author Max Reichardt
 *
 * IB2C View based on GraphViz Standard View
 */
public class Ib2cView extends StandardViewGraphViz {

    /** UID */
    private static final long serialVersionUID = -8861192928055307809L;

    /** Names of behaviour ports to display in behaviour vertex */
    private final static String[] SIGNALS = new String[] {
        "Activation",
        "Activity",
        "Target Rating",
    };

    /** List of currently active/used port accessors to retrieve behaviour data */
    private final ArrayList<ConnectingPortAccessor<CoreNumber>> ports4BehaviourAccess = new ArrayList<ConnectingPortAccessor<CoreNumber>>();

    @Override
    protected void relayout() {

        // Delete old ports
        for (ConnectingPortAccessor<CoreNumber> cpa : ports4BehaviourAccess) {
            cpa.delete();
        }
        ports4BehaviourAccess.clear();
        super.relayout();
    }

    @Override
    protected Vertex createVertexInstance(FrameworkElement fe) {
        if (isBehaviour(fe)) {
            return new BehaviourVertex(fe);
        } else {
            return super.createVertexInstance(fe);
        }
    }

    /**
     * @param fe Framework element
     * @return True if framework element is a behaviour
     */
    private static boolean isBehaviour(FrameworkElement fe) {
        return FrameworkElementTags.isTagged(fe, "ib2c_module");
    }

    // we may add this heuristic again, if it turns out to be necessary
    /*
    @Override
    protected boolean drawEdgeDownwards(final Edge edge) {
        if (isBehaviour(edge.getSource().getFinrocElement()) || isBehaviour(edge.getSource().getFinrocElement().getParent())) {
            // draw edge downwards if it is not connected to inhibition port
            FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter();
            final boolean[] result = new boolean[]{ true };
            filter.traverseElementTree(edge.getSource().getFinrocElement(), new FrameworkElementTreeFilter.Callback<Integer>() {
                @Override
                public void treeFilterCallback(FrameworkElement fe, Integer customParam) {
                    String sourceName = fe.getParent().getName();
                    if (fe.isPort() && fe.isReady() && (sourceName.equals("Output") || sourceName.equals("iB2C Output"))) {
                        AbstractPort ap = (AbstractPort)fe;
                        NetPort np = ap.asNetPort();
                        if (np != null) {
                            for (FrameworkElement destPort : np.getRemoteEdgeDestinations()) {
                                if (destPort.isChildOf(edge.getDestination().getFinrocElement()) && destPort.getName().startsWith("Inhibition ")) {
                                    result[0] = false;
                                    return;
                                }
                            }
                        }
                    }
                }
            }, Integer.MAX_VALUE);
            return result[0];
        } else {
            return super.drawEdgeDownwards(edge);
        }
    }
    */

    /**
     * Vertex that displays Behaviour module with
     * Activation, Activity and Target Rating as horizontal Bars
     */
    @SuppressWarnings("rawtypes")
    public class BehaviourVertex extends Vertex implements PortListener, Runnable {

        /** ports used to get behaviour data via push */
        @SuppressWarnings("unchecked")
        private ConnectingPortAccessor<CoreNumber>[] ports = new ConnectingPortAccessor[SIGNALS.length];

        public BehaviourVertex(FrameworkElement fe) {
            super(fe);

            // Create ports for behaviour data access */
            FrameworkElement portGroup = fe.getChild("iB2C Output");
            if (portGroup == null) {
                portGroup = fe.getChild("Sensor Output");
            }
            if (portGroup != null) {
                for (int i = 0; i < SIGNALS.length; i++) {
                    AbstractPort ap = (AbstractPort)portGroup.getChild(SIGNALS[i]);
                    if (ap == null) {
                        continue;
                    }
                    ports[i] = new ConnectingPortAccessor<CoreNumber>(ap, "");
                    ports4BehaviourAccess.add(ports[i]);
                    ((PortBase)ports[i].getPort()).addPortListenerRaw(this);
                    ports[i].init();
                    ports[i].setAutoUpdate(true);
                }
            }
        }

        public void reset() {
            super.reset();

            // increase height, so that we have space for upper and lower bar
            gvVertex.setSize(gvVertex.getWidth(), gvVertex.getHeight() + 6);
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            double v1, v2, v3;
            try {
                v1 = ports[0] == null ? 0 : ports[0].get().doubleValue();
                v2 = ports[1] == null ? 0 : ports[1].get().doubleValue();
                v3 = ports[2] == null ? 0 : ports[2].get().doubleValue();
            } catch (Exception e) {
                super.paint(g2d);
                return;
            }

            int h = getHighlightLevel();

            updateRectangle();

            // set colors
            int brighten = h * 64;
            Color bar1 = brighten(Color.yellow, brighten);
            Color bar2 = brighten(Color.green.darker(), brighten);
            Color bar3 = brighten(Color.red, brighten);
            Color background = brighten(getColor(), brighten);

            // draw background
            g2d.setColor(background);
            g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
            if (h >= 1) {
                drawRectangleGlow(g2d, rect.x, rect.y, rect.width, rect.height, 0.7f, 0.075f);
            }

            // draw bars
            g2d.setColor(bar1);
            g2d.fillRect(rect.x, rect.y + 1, getBarWidth(v1), 3);
            g2d.setColor(bar2);
            g2d.fillRect(rect.x, rect.y + 4, getBarWidth(v2), rect.height - 7);
            g2d.setColor(bar3);
            g2d.fillRect(rect.x, (int)(rect.getMaxY() - 3), getBarWidth(v3), 3);

            g2d.setColor(getTextColor());
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < label.size(); i++) {
                g2d.drawString(label.get(i), rect.x + 3, (rect.y + rect.height - 8) + ((i + 1) - label.size()) * lineIncrementY);
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
         * @param d value between 0 and 1
         * @return Width of bar
         */
        private int getBarWidth(double d) {
            if (d > 1) {
                d = 1;
            } else if (d < 0) {
                d = 0;
            }
            return (int)(d * ((double)rect.width));
        }

        @Override
        public void run() {
            repaint();
        }

        @Override
        public void portChanged(AbstractPort origin, Object value) {
            SwingUtilities.invokeLater(this);
        }
    }
}
