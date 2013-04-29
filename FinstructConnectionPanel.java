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
package org.finroc.tools.finstruct;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.Timer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.admin.AdminClient;
import org.finroc.core.parameter.ParameterInfo;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.core.remote.HasUid;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapperTreeNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.views.AbstractGraphView;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.ConnectorIcon;
import org.finroc.tools.gui.ConnectorIcon.IconColor;
import org.finroc.tools.gui.ConnectorIcon.LineStart;
import org.finroc.tools.gui.util.gui.MJTree;
import org.rrlib.finroc_core_utils.log.LogLevel;

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

    /** menu items */
    private final JMenuItem miOpenInNewWindow;

    /**
     * @param owner Parent of connection panel
     * @param treeFont Font to use for tree
     */
    public FinstructConnectionPanel(Finstruct owner, Font treeFont) {
        super(owner, treeFont);
        finstruct = owner;
        timer.setRepeats(false);

        miOpenInNewWindow = new JMenuItem("Open in new Window");
        miOpenInNewWindow.addActionListener(this);
        popupMenu.add(miOpenInNewWindow, 0);
    }

    @Override
    protected boolean canConnect(PortWrapperTreeNode o1, PortWrapperTreeNode o2) {
        if (o1 == null || o2 == null || o1.getPort() == o2.getPort()) {
            return false;
        }
        if (getRightTree() instanceof ConfigFileModel) {
            return o1.getPort().getAnnotation(ParameterInfo.TYPE) != null || o2.getPort().getAnnotation(ParameterInfo.TYPE) != null;
        } else {
            return o1.getPort().mayConnectTo(o2.getPort(), false) || o2.getPort().mayConnectTo(o1.getPort(), false);
        }
    }

    @Override
    protected void connect(PortWrapperTreeNode port, PortWrapperTreeNode port2) {
        ThreadLocalCache.get();
        if (port != null && port2 != null) {
            if (getRightTree() instanceof ConfigFileModel) {
                ParameterInfo pi = (ParameterInfo)port.getPort().getAnnotation(ParameterInfo.TYPE);
                PortWrapperTreeNode parameter = port;
                PortWrapperTreeNode configNode = port2;
                if (pi == null) {
                    pi = (ParameterInfo)port2.getPort().getAnnotation(ParameterInfo.TYPE);
                    parameter = port2;
                    configNode = port;
                }
                if (pi != null) {
                    RemoteRuntime rr = RemoteRuntime.find((RemotePort)parameter);
                    AdminClient ac = rr.getAdminInterface();
                    pi.setConfigEntry("/" + ((HasUid)configNode).getUid(), true);
                    ac.setAnnotation(((RemotePort)parameter).getRemoteHandle(), pi);
                    return;
                }
            } else {
                NetPort np1 = port.getPort().asNetPort();
                NetPort np2 = port2.getPort().asNetPort();
                AdminClient ac = RemoteRuntime.find(np1).getAdminInterface();
                if (ac != null) {
                    if (port.getPort().mayConnectTo(port2.getPort(), false) || port2.getPort().mayConnectTo(port.getPort(), false)) {
                        ac.connect(np1, np2);
                        timer.restart();
                        return;
                    }
                }
            }
        }
        Finstruct.logDomain.log(LogLevel.LL_DEBUG_WARNING, getLogDescription(), "Cannot connect ports: " + port  + " " + port2);
    }

    @Override
    protected List<PortWrapperTreeNode> getConnectionPartners(PortWrapperTreeNode port) {
        return getConnectionPartners(port, false);
    }

    protected List<PortWrapperTreeNode> getConnectionPartners(final PortWrapperTreeNode port, boolean includeSourcePorts) {
        final List<PortWrapperTreeNode> result = new ArrayList<PortWrapperTreeNode>();
        if (getRightTree() instanceof ConfigFileModel) {
            ParameterInfo pi = (ParameterInfo)port.getPort().getAnnotation(ParameterInfo.TYPE);
            if (pi != null && pi.getConfigEntry() != null && pi.getConfigEntry().length() > 0) {
                PortWrapperTreeNode pw = ((ConfigFileModel)getRightTree()).get(pi.getConfigEntry());
                if (pw != null) {
                    result.add(pw);
                }
            }
            return result;
        } else {
            NetPort np = getNetPort(port);
            if (np != null) {
                for (AbstractPort fe : np.getRemoteEdgeDestinations()) {
                    for (RemotePort remotePort : RemotePort.get(fe)) {
                        result.add(remotePort);
                    }
                }
            }

            if (includeSourcePorts) { // computationally expensive
                FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(FrameworkElementFlags.PORT | FrameworkElementFlags.STATUS_FLAGS,
                        FrameworkElementFlags.PORT | FrameworkElementFlags.READY | FrameworkElementFlags.PUBLISHED);
                filter.traverseElementTree(finstruct.getIoInterface().getRootFrameworkElement(), new FrameworkElementTreeFilter.Callback<Object>() {
                    @Override
                    public void treeFilterCallback(FrameworkElement fe, Object customParam) {
                        AbstractPort scannedPort = (AbstractPort)fe;
                        NetPort netPort = scannedPort.asNetPort();
                        if (netPort != null) {
                            for (AbstractPort destPort : netPort.getRemoteEdgeDestinations()) {
                                if (destPort == port.getPort()) {
                                    for (RemotePort remotePort : RemotePort.get(scannedPort)) {
                                        result.add(remotePort);
                                    }
                                }
                            }
                        }
                    }
                }, null);
            }

            return result;
        }
    }

    private NetPort getNetPort(PortWrapperTreeNode port) {
        if (port == null) {
            return null;
        }
        return port.getPort().asNetPort();
    }

    @Override
    protected void removeConnections(PortWrapperTreeNode port) {
        ThreadLocalCache.get();
        if (port != null) {
            if (getRightTree() instanceof ConfigFileModel) {
                if (port instanceof ConfigFileModel.ConfigEntryWrapper) {
                    final ModelNode currentRoot = finstruct.getCurrentView().getRootElement();
                    final String uid = ((ConfigFileModel.ConfigEntryWrapper)port).getUid();
                    for (RemotePort remotePort : currentRoot.getPortsBelow(null)) {
                        // remove connections
                        ParameterInfo pi = (ParameterInfo)remotePort.getAnnotation(ParameterInfo.TYPE);
                        if (pi != null && pi.getConfigEntry().equals(uid) && ConfigFileModel.findConfigFile(remotePort) == ConfigFileModel.findConfigFile(currentRoot)) {
                            pi.setConfigEntry("");
                            RemoteRuntime rr = RemoteRuntime.find(remotePort);
                            AdminClient ac = rr.getAdminInterface();
                            pi.setConfigEntry("");
                            ac.setAnnotation(remotePort.getRemoteHandle(), pi);
                        }
                    }
                } else {
                    RemoteRuntime rr = RemoteRuntime.find((RemotePort)port);
                    AdminClient ac = rr.getAdminInterface();
                    ParameterInfo pi = (ParameterInfo)((RemotePort)port).getAnnotation(ParameterInfo.TYPE);
                    pi.setConfigEntry("");
                    ac.setAnnotation(((RemotePort)port).getRemoteHandle(), pi);
                }
                return;
            } else {
                NetPort np1 = port.getPort().asNetPort();
                AdminClient ac = RemoteRuntime.find(np1).getAdminInterface();
                if (ac != null) {
                    ac.disconnectAll(np1);
                    timer.restart();
                    return;
                }
            }
        }
        Finstruct.logDomain.log(LogLevel.LL_DEBUG_WARNING, getLogDescription(), "Cannot disconnect port: " + port);
    }

    @Override
    protected void setToolTipText(MJTree<PortWrapperTreeNode> tree, PortWrapperTreeNode element) {
        //tree.setToolTipText("");
    }

    @Override
    protected void drawConnections(Graphics g) {
        drawConnectionsHelper(g, leftTree, rightTree);
        if (!(getRightTree() instanceof ConfigFileModel)) {
            drawConnectionsHelper(g, rightTree, leftTree);
        }
    }

    /**
     * Expand provided node and collapse all others
     *
     * @param leftTree Left tree (or rather right one?)
     * @param node Node to expand
     */
    public void expandOnly(boolean leftTree, ModelNode node) {
        ArrayList<ModelNode> tmp = new ArrayList<ModelNode>();
        tmp.add(node);
        expandOnly(leftTree, tmp);
    }

    /**
     * Expand provided nodes and collapse all others
     *
     * @param leftTree Left tree (or rather right one?)
     * @param nodes Nodes to expand
     */
    public void expandOnly(boolean leftTree, Collection<ModelNode> nodes) {
        if (nodes.size() == 0) {
            return;
        }
        MJTree<PortWrapperTreeNode> tree = leftTree ? super.leftTree : rightTree;
        tree.collapseAll();
        for (ModelNode node : nodes) {
            tree.expandToElement(node);
            tree.scrollPathToVisible(tree.getTreePathFor(node));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == timer) {
            repaint();
            finstruct.refreshView();
            return;
        } else if (e.getSource() == miOpenInNewWindow) {
            MJTree<PortWrapperTreeNode> ptree = popupOnRight ? rightTree : leftTree;
            TreeNode tnp = getTreeNodeFromPos(ptree);
            FinstructWindow fw = new FinstructWindow(finstruct);
            fw.showElement((ModelNode)tnp);
            fw.pack();
            fw.setVisible(true);
        } else if (e.getSource() == miShowPartner) {
            MJTree<PortWrapperTreeNode> ptree = popupOnRight ? rightTree : leftTree;
            MJTree<PortWrapperTreeNode> otherTree = popupOnRight ? leftTree : rightTree;
            PortWrapperTreeNode portWrapper = getPortWrapperTreeNodeFromPos(ptree);
            List<PortWrapperTreeNode> partners = getConnectionPartners(portWrapper, true);

            for (PortWrapperTreeNode partner : partners) {
                TreePath tp = otherTree.getTreePathFor((PortWrapperTreeNode)partner);
                otherTree.scrollPathToVisible(tp);
            }
            return;
        }
        super.actionPerformed(e);
    }

    @Override
    protected Color getBranchBackgroundColor(Object value) {
        if (value instanceof RemoteFrameworkElement) {
            RemoteFrameworkElement fe = (RemoteFrameworkElement)value;
            if (finstruct.getCurrentView() != null && fe == finstruct.getCurrentView().getRootElement()) {
                return new Color(222, 222, 222);
                /*} else if (finstruct.getCurrentView() != null && fe.isChildOf(finstruct.getCurrentView().getRootElement())) {
                    return new Color(233, 233, 233);*/
            } else if (AbstractGraphView.isControllerInterface(fe)) {
                return new Color(255, 190, 210);
            } else if (AbstractGraphView.isSensorInterface(fe)) {
                return new Color(255, 255, 165);
            }
        }
        return null;
    }

    /**
     * Text color for non-ports nodes
     * (may be overridden)
     *
     * @param value Wrapped object
     * @return Color - null, if default color
     */
    protected Color getBranchTextColor(Object value) {
        if (value instanceof RemoteFrameworkElement) {
            RemoteFrameworkElement fe = (RemoteFrameworkElement)value;
            if (finstruct.getCurrentView() != null && !fe.isNodeAncestor(finstruct.getCurrentView().getRootElement()) && fe != finstruct.getCurrentView().getRootElement()) {
                return new Color(160, 160, 160);
            }
        }
        return null;
    }

    @Override
    public void setRightTree(TreeModel tm) {
        if (getRightTree() instanceof ConfigFileModel) {
            ((ConfigFileModel)getRightTree()).delete();
        }
        super.setRightTree(tm);
    }

    @Override
    public boolean drawPortConnected(PortWrapperTreeNode port) {
        return false;
    }

    @Override
    protected LineStart getLineStartPosition(PortWrapperTreeNode port, PortWrapperTreeNode partner) {
        if (getRightTree() instanceof ConfigFileModel) {
            return ConnectorIcon.LineStart.Default;
        }
        NetPort np = getNetPort((PortWrapperTreeNode)port);
        if (np != null) {
            if (np.getRemoteEdgeDestinations().contains(partner.getPort())) {
                return ConnectorIcon.LineStart.Outgoing;
            } else {
                NetPort npPartner = getNetPort((PortWrapperTreeNode)partner);
                if (npPartner.getRemoteEdgeDestinations().contains(port.getPort())) {
                    return ConnectorIcon.LineStart.Incoming;
                }
                // Ok, there's no connection yet
            }
        }
        return ConnectorIcon.LineStart.Default;
    }

    @Override
    public ConnectorIcon getConnectorIcon(PortWrapperTreeNode port, boolean rightTree, IconColor color, boolean brighter) {
        final ConnectorIcon.Type iconType = new ConnectorIcon.Type();
        boolean rpc = FinrocTypeInfo.isMethodType(port.getPort().getDataType(), true);
        iconType.set(port.isInputPort(), port.getPort().getFlag(FrameworkElementFlags.PROXY), rpc, rightTree, brighter, color, rightTree ? rightBackgroundColor : leftBackgroundColor);
        return ConnectorIcon.getIcon(iconType, HEIGHT);
    }

    @Override
    protected void updatePopupMenu(TreeNode treeNode, PortWrapperTreeNode wrapper) {
        super.updatePopupMenu(treeNode, wrapper);
        miOpenInNewWindow.setEnabled(treeNode != null);

        miShowPartner.setEnabled(wrapper != null && finstruct.getToolBar().isSelected(Finstruct.Mode.connect));
    }
}
