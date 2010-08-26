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
package org.finroc.finstruct;

import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.net.NetPort;
import org.finroc.gui.ConnectionPanel;
import org.finroc.gui.util.gui.MJTree;
import org.finroc.gui.util.treemodel.PortWrapper;
import org.finroc.gui.util.treemodel.TreePortWrapper;

/**
 * @author max
 *
 * Connection panel with some modifications for finstruct
 */
public class FinstructConnectionPanel extends ConnectionPanel {

    /** UID */
    private static final long serialVersionUID = -3932548321837127173L;

    /** Finstruct reference */
    private final Finstruct finstruct;

    /**
     * @param owner Parent of connection panel
     * @param treeFont Font to use for tree
     */
    public FinstructConnectionPanel(Finstruct owner, Font treeFont) {
        super(owner, treeFont);
        finstruct = owner;
    }

    @Override
    protected boolean canConnect(TreePortWrapper o1, TreePortWrapper o2) {
        if (o1 == null || o2 == null || o1.getPort() == o2.getPort()) {
            return false;
        }
        return o1.getPort().mayConnectTo(o2.getPort()) || o2.getPort().mayConnectTo(o1.getPort());
    }

    @Override
    protected void connect(TreePortWrapper port, TreePortWrapper port2) {
        if (port != null && port2 != null && port.getPort().mayConnectTo(port2.getPort())) {
            // TODO RPC call
        }
    }



    @Override
    protected List<PortWrapper> getConnectionPartners(TreePortWrapper port) {
        NetPort np = getNetPort(port);
        List<PortWrapper> result = new ArrayList<PortWrapper>();
        if (np != null) {
            for (AbstractPort fe : np.getRemoteEdgeDestinations()) {
                result.add((PortWrapper)finstruct.ioInterface.getInterfaceNode(fe));
            }
        }
        return result;
    }

    private NetPort getNetPort(TreePortWrapper port) {
        if (port == null) {
            return null;
        }
        return port.getPort().asNetPort();
    }

    @Override
    protected void removeConnections(TreePortWrapper tnp) {
        if (tnp != null) {
            // TODO RPC call
        }
    }

    @Override
    protected void setToolTipText(MJTree<TreePortWrapper> tree, TreePortWrapper element) {
        //tree.setToolTipText("");
    }

    @Override
    protected void drawConnections(Graphics g) {
        drawConnectionsHelper(g, rightTree, leftTree);
        drawConnectionsHelper(g, leftTree, rightTree);
    }

}
