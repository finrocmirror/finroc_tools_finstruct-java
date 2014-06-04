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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.plugins.data_types.Paintable;
import org.finroc.plugins.data_types.TaskProfile;
import org.finroc.plugins.data_types.TaskProfile.TaskClassification;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.finstruct.propertyeditor.PortAccessor;
import org.finroc.tools.gui.util.gui.IconManager;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;

/**
 * @author Max Reichardt
 *
 * Displays scheduling and profiling information
 */
public class Profiling extends StandardViewGraphViz {

    /** UID */
    private static final long serialVersionUID = -4910356235610029L;

    /** Timestamp of last update */
    private long lastUpdate = 0;

    /** Label for getting node bounds - may only be used in synchronized context */
    private final JLabel testLabel = new JLabel("Test");

    /** Ports to access profiling data */
    private final ArrayList<ProfilingPortConnnection> profilingPorts = new ArrayList<ProfilingPortConnnection>();
//
//    /** Height of component visualization */
//    private static final int VISUALIZATION_HEIGHT = 60;
//
//    /** Max. width of component visualization */
//    private static final int VISUALIZATION_WIDTH = 80;
//
//    /** Component background image */
//    private ImageIcon background = (ImageIcon)IconManager.getInstance().getIcon("brushed-titanium-max.png");

    public Profiling() {
        testLabel.setFont(testLabel.getFont().deriveFont(9.0f));
    }

    public void clear() {
        // Delete old ports
        for (ConnectingPortAccessor<?> port : profilingPorts) {
            port.delete();
        }
        profilingPorts.clear();
    }

    @Override
    protected void destroy() {
        clear();
    }

    @Override
    public void relayout(boolean keepVerticesAndEdges) {
        if (!keepVerticesAndEdges) {
            clear();
        }
        super.relayout(keepVerticesAndEdges);
    }

    @Override
    protected Vertex createVertexInstance(ModelNode fe) {

        // Look for thread container profiling port
        ModelNode current = fe;
        while (current != null) {
            ModelNode port = current.getChildByQualifiedName("Profiling/Details", '/');
            if (port instanceof RemotePort && ((RemotePort)port).getPort().getDataType() == TaskProfile.LIST_TYPE) {
                for (ProfilingPortConnnection connection : profilingPorts) {
                    if (((RemotePort)port).getPort() == connection.getConnectedPort()) {
                        return new Vertex(fe); // a port for this thread container already exists
                    }
                }

                profilingPorts.add(new ProfilingPortConnnection((RemotePort)port, ""));
                return new Vertex(fe);
            }
            current = current.getParent();
        }

        return new Vertex(fe);
    }



    @Override
    protected void updateView() {
        super.updateView();

        long now = System.currentTimeMillis();
        if (now - lastUpdate > 1000) {
            lastUpdate = now;
            repaint();
        }
    }

    /** Single connection to profiling details port */
    private class ProfilingPortConnnection extends ConnectingPortAccessor<TaskProfile.List> implements PortAccessor.Listener {

        public ProfilingPortConnnection(RemotePort partner, String rootName) {
            super(partner, rootName);
            setListener(this);
            init();
            setAutoUpdate(true);
        }

        /** Profile objects to update on value change */
        private ArrayList<TaskProfile> profilesToUpdate = new ArrayList<TaskProfile>();

        /** Is the next port data update the initial one? */
        private boolean initialReceive = true;

        @Override
        public void portChanged() {
            try {
                TaskProfile.List profiles = this.getAutoLocked();
                if (initialReceive) {
                    initialReceive = false;
                    for (int i = 0; i < profiles.size(); i++) {
                        TaskProfile profile = profiles.get(i);
                        RemoteRuntime remoteRuntime = RemoteRuntime.find(Profiling.this.getRootElement());
                        if (remoteRuntime == null) {
                            return;
                        }
                        RemoteFrameworkElement profileElement = remoteRuntime.getRemoteElement(profile.handle);
                        if (profileElement == null) {
                            continue;
                        }

                        // Is task in graph?
                        for (StandardViewGraphViz.Vertex vertex : Profiling.this.getVertices()) {
                            if (vertex.getFinrocElement() != null) {
                                if (vertex.getFinrocElement().getRemoteHandle() == profile.handle ||
                                        (profileElement.getFlag(FrameworkElementFlags.INTERFACE) &&
                                         profileElement.getParent() == vertex.getModelElement())) {

                                    // Add profile variable
                                    TaskProfile profileToUpdate = new TaskProfile();
                                    profileToUpdate.copyFrom(profile);
                                    profilesToUpdate.add(profileToUpdate);
                                    Vertex v = (Vertex)vertex;
                                    TaskProfile[] newProfiles = null;
                                    if (v.currentProfiles == null) {
                                        newProfiles = new TaskProfile[1];
                                    } else {
                                        TaskProfile[] old = v.currentProfiles;
                                        newProfiles = new TaskProfile[old.length + 1];
                                        System.arraycopy(old, 0, newProfiles, 0, old.length);
                                    }
                                    newProfiles[newProfiles.length - 1] = profileToUpdate;
                                    v.currentProfiles = newProfiles;
                                }
                            }
                        }

                    }
                } else {
                    for (int i = 0; i < profiles.size(); i++) {
                        TaskProfile profile = profiles.get(i);
                        for (TaskProfile profileToUpdate : profilesToUpdate) {
                            if (profileToUpdate.handle == profile.handle && profileToUpdate.taskClassification == profile.taskClassification) {
                                profileToUpdate.copyFrom(profile);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.log(LogLevel.ERROR, e);
            }
            releaseAllLocks();
        }

        private AbstractPort getConnectedPort() {
            return partner;
        }
    }

    /**
     * Vertex that displays real-time component visualization
     */
    public class Vertex extends StandardViewGraphViz.Vertex {

        /**
         * Current Profiling data for vertex
         * (access is non-synchronized - as only plain integers are written and read;
         *  and in the worst case even minor glitches would not be a problem at all)
         */
        private TaskProfile[] currentProfiles;

        public Vertex(ModelNode node) {
            super(node);
        }

        /**
         * Paint Vertex
         *
         * @param g2d Graphics object
         */
        public void paint(Graphics2D g2d) {
            super.paint(g2d);

            final int RADIUS = 8;
            final int START_Y_OFFSET = 4;

            if (currentProfiles != null) {
                // Save g2d state
                Stroke oldStroke = g2d.getStroke();
                Font oldFont = g2d.getFont();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw
                int currentYOffset = START_Y_OFFSET + rect.y;
                for (TaskProfile profile : currentProfiles) {
                    g2d.setStroke(new BasicStroke(3));
                    g2d.setColor(Color.white);
                    g2d.fillOval(rect.x + rect.width - RADIUS, currentYOffset - RADIUS, 2 * RADIUS, 2 * RADIUS);
                    g2d.setColor(profile.taskClassification == TaskClassification.SENSE ? Color.yellow : (profile.taskClassification == TaskClassification.CONTROL ? Color.red : Color.gray));
                    g2d.drawOval(rect.x + rect.width  - RADIUS, currentYOffset - RADIUS, 2 * RADIUS, 2 * RADIUS);
                    g2d.setStroke(oldStroke);
                    g2d.setColor(Color.black);
                    testLabel.setText("" + profile.schedulePosition);
                    int textWidth = testLabel.getPreferredSize().width;
                    g2d.setFont(testLabel.getFont());
                    g2d.drawString(testLabel.getText(), rect.x + rect.width - textWidth / 2, currentYOffset + 4);
                    currentYOffset += 2 * RADIUS + 3;
                }

                // Restore g2d state
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setStroke(oldStroke);
                g2d.setFont(oldFont);
            }
        }
    }
}
