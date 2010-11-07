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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.Timer;

import org.finroc.core.FrameworkElement;
import org.finroc.core.admin.AdminClient;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.finstruct.views.AbstractFinstructGraphView;
import org.finroc.gui.ConnectionPanel;
import org.finroc.gui.util.gui.MJTree;
import org.finroc.gui.util.treemodel.InterfaceNode;
import org.finroc.gui.util.treemodel.PortWrapper;
import org.finroc.gui.util.treemodel.TreePortWrapper;
import org.finroc.log.LogLevel;

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

    /** Swing timer for repaint */
    private final Timer timer = new Timer(500, this);

    /**
     * @param owner Parent of connection panel
     * @param treeFont Font to use for tree
     */
    public FinstructConnectionPanel(Finstruct owner, Font treeFont) {
        super(owner, treeFont);
        finstruct = owner;
        timer.setRepeats(false);
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
        ThreadLocalCache.get();
        if (port != null && port2 != null) {
            NetPort np1 = port.getPort().asNetPort();
            NetPort np2 = port2.getPort().asNetPort();
            AdminClient ac = RemoteRuntime.find(np1).getAdminInterface();
            if (ac != null) {
                if (port.getPort().mayConnectTo(port2.getPort()) || port2.getPort().mayConnectTo(port.getPort())) {
                    ac.connect(np1, np2);
                    timer.restart();
                    return;
                }
            }
        }
        Finstruct.logDomain.log(LogLevel.LL_DEBUG_WARNING, getLogDescription(), "Cannot connect ports: " + port  + " " + port2);
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
    protected void removeConnections(TreePortWrapper port) {
        ThreadLocalCache.get();
        if (port != null) {
            NetPort np1 = port.getPort().asNetPort();
            AdminClient ac = RemoteRuntime.find(np1).getAdminInterface();
            if (ac != null) {
                ac.disconnectAll(np1);
                timer.restart();
                return;
            }
        }
        Finstruct.logDomain.log(LogLevel.LL_DEBUG_WARNING, getLogDescription(), "Cannot disconnect port: " + port);
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

    /**
     * Expand provided node and collapse all others
     *
     * @param leftTree Left tree (or rather right one?)
     * @param node Node to expand
     */
    public void expandOnly(boolean leftTree, FrameworkElement node) {
        ArrayList<FrameworkElement> tmp = new ArrayList<FrameworkElement>();
        tmp.add(node);
        expandOnly(leftTree, tmp);
    }

    /**
     * Expand provided nodes and collapse all others
     *
     * @param leftTree Left tree (or rather right one?)
     * @param nodes Nodes to expand
     */
    public void expandOnly(boolean leftTree, Collection<FrameworkElement> nodes) {
        if (nodes.size() == 0) {
            return;
        }
        MJTree<TreePortWrapper> tree = leftTree ? super.leftTree : rightTree;
        tree.collapseAll();
        for (FrameworkElement node : nodes) {
            InterfaceNode node2 = finstruct.ioInterface.getInterfaceNode(node);
            tree.expandToElement(node2);
            tree.scrollPathToVisible(tree.getTreePathFor(node2));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == timer) {
            repaint();
            finstruct.refreshView();
            return;
        }
        super.actionPerformed(e);
    }

    @Override
    protected Color getBranchBackgroundColor(Object value) {
        if (value instanceof InterfaceNode) {
            FrameworkElement fe = ((InterfaceNode)value).getFrameworkElement();
            if (AbstractFinstructGraphView.isControllerInterface(fe)) {
                return new Color(255, 190, 210);
            } else if (AbstractFinstructGraphView.isSensorInterface(fe)) {
                return new Color(255, 255, 165);
            }
        }
        return null;
    }
}
