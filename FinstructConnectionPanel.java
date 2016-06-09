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
package org.finroc.tools.finstruct;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.admin.AdminClient;
import org.finroc.core.parameter.ParameterInfo;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.core.remote.HasUid;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapper;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.actions.AddPortsToInterfaceAction;
import org.finroc.tools.finstruct.actions.CompositeAction;
import org.finroc.tools.finstruct.actions.FinstructAction;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.ConnectorIcon;
import org.finroc.tools.gui.ConnectorIcon.LineStart;
import org.finroc.tools.gui.util.gui.MJTree;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;

/**
 * @author Max Reichardt
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

    /** Temporary storage for edge destinations - used only by AWT Thread for painting connections */
    private final ArrayList<AbstractPort> tempDestinationList = new ArrayList<AbstractPort>();

    /** Temporary storage hypotheticalConnections - used only by AWT Thread */
    private final ArrayList<RemotePort> tempConnectList1 = new ArrayList<RemotePort>();
    private final ArrayList<ModelNode> tempConnectList2 = new ArrayList<ModelNode>();

    /** More colors */
    public static final ConnectorIcon.IconColor lightGrayColor = new ConnectorIcon.IconColor(new Color(211, 211, 211), new Color(233, 233, 233));
    public static final Color rootViewColor = new Color(211, 211, 211);
    public static final Color inactiveTextColor = new Color(160, 160, 160);

    /** Tooltip-related */
    private Popup toolTip;
    private final Timer toolTipTimer = new Timer(1000, this);
    private Object toolTipTreeNode = null;
    private Point toolTipLocation = null;
    private String toolTipText = null;

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
        toolTipTimer.setRepeats(false);
        toolTipTimer.addActionListener(this);

        miRemoveConnections.setText("Remove visible connections (from port)");
        miRemoveAllConnections.setText("Remove all connections (from port)");
    }

    @Override
    protected boolean canConnect(Object o1, Object o2) {
        if (o1 == null || o2 == null) {
            return false;
        }
        if (getRightTree() instanceof ConfigFileModel) {
            return (((o1 instanceof RemoteFrameworkElement) && ((RemoteFrameworkElement)o1).getAnnotation(ParameterInfo.TYPE) != null && o2.getClass() == ConfigFileModel.ConfigEntryWrapper.class) ||
                    ((o2 instanceof RemoteFrameworkElement) && ((RemoteFrameworkElement)o2).getAnnotation(ParameterInfo.TYPE) != null && o1.getClass() == ConfigFileModel.ConfigEntryWrapper.class));
        } else if (o1 instanceof RemotePort && o2 instanceof RemotePort) {
            return canConnectPorts((RemotePort)o1, (RemotePort)o2).length() == 0;
        }
        return false;
    }

    /**
     * Can the two ports be connected?
     *
     * @param p1 Port 1
     * @param p2 Port 2
     * @return "" if they can be connected. Otherwise the reason why not.
     */
    private String canConnectPorts(RemotePort p1, RemotePort p2) {
        if (p1.isConnectedTo(p2)) {
            return "Ports are connected already";
        }

        if (finstruct.allowDirectConnectingAcrossGroupBoundaries()) {
            return SmartConnecting.mayConnectDirectly(p1, p2, true);
        }
        tempConnectList1.clear();
        tempConnectList2.clear();
        tempConnectList1.add(p1);
        tempConnectList2.add(p2);
        return SmartConnecting.mayConnect(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries()); // TODO: this can be optimized
    }

    /**
     * Processes source nodes to connect.
     *
     * @param portResultList If node list contains only ports, all ports in node list will be added to this list. If node list contains a non-port, non-port's children will be added to this list.
     * @param nodes List of source nodes to connect
     * @return Any non-port node in 'nodes'.
     */
    private RemoteFrameworkElement processSourceNodesToConnect(ArrayList<RemotePort> portResultList, List<Object> nodes) {
        portResultList.clear();
        if (nodes.size() == 0) {
            return null;
        }
        int portNodes = 0, nonPortNodes = 0;
        RemoteFrameworkElement nonPortNode = null;
        for (Object node : nodes) {
            if (node instanceof RemotePort) {
                portNodes++;
                portResultList.add((RemotePort)node);
            } else {
                nonPortNodes++;
                if (node instanceof RemoteFrameworkElement) {
                    nonPortNode = nonPortNode == null ? (RemoteFrameworkElement)node : nonPortNode;
                }
            }
        }
        if (nonPortNodes > 1 || (nonPortNodes == 1 && portNodes > 0)) {
            Log.log(LogLevel.WARNING, "Non-port nodes must be selected exlusively. Ignoring other selected elements.");
        }

        if (nonPortNode != null) {
            portResultList.clear();
            if (nonPortNode.isInterface()) {
                for (int i = 0; i < nonPortNode.getChildCount(); i++) {
                    if (nonPortNode.getChildAt(i) instanceof RemotePort) {
                        portResultList.add((RemotePort)nonPortNode.getChildAt(i));
                    }
                }
            }
        }

        return nonPortNode;
    }

    @Override
    protected Object hypotheticalConnectionImplementation(Object tn) {
        if (getRightTree() instanceof ConfigFileModel) {
            return super.hypotheticalConnectionImplementation(tn);
        }

        MJTree<Object> selTree = selectionFromRight ? rightTree : leftTree;

        // scan nodes to connect
        RemoteFrameworkElement nonPortNode = processSourceNodesToConnect(tempConnectList1, selTree.getSelectedObjects());
        if (tempConnectList1.size() == 0) {
            return nonPortNode != null && nonPortNode.isInterface() ? "Selected interface in other tree is empty" : "Only ports and interfaces can be connected (dragged)";
        }

        if (nonPortNode != null) {
            // only connect to other non-port nodes
            if (tn instanceof RemotePort) {
                return "Interfaces cannot be connected to ports (you may try the other way round)";
            }

            tempConnectList2.clear();
            for (int i = 0; i < tempConnectList1.size(); i++) {
                tempConnectList2.add((ModelNode)tn);
            }
            String reason = SmartConnecting.mayConnect(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries());
            if (reason.length() > 0) {
                return reason;
            }
            ArrayList<Object> result = new ArrayList<Object>();
            result.add(tn);
            return result;
        }

        if (tn instanceof RemotePort) {
            if (tempConnectList1.size() == 1) {
                // Optimized implementation for only one selected port
                String reason = canConnectPorts(tempConnectList1.get(0), (RemotePort)tn);
                if (reason.length() > 0) {
                    return reason;
                }
                ArrayList<Object> result = new ArrayList<Object>();
                result.add(tn);
                return result;
            }

            // Standard hypotheticalConnectionImplementation from base class
            return super.hypotheticalConnectionImplementation(tn);
        } else {
            // Connect all ports to non-port tree node
            tempConnectList2.clear();
            for (int i = 0; i < tempConnectList1.size(); i++) {
                tempConnectList2.add((ModelNode)tn);
            }
            String reason = SmartConnecting.mayConnect(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries());
            if (reason.length() > 0) {
                return reason;
            }
            ArrayList<Object> result = new ArrayList<Object>();
            result.addAll(tempConnectList2);
            return result;
        }
    }

    @Override
    protected void connect(List<Object> nodes1, List<Object> nodes2) {
        if (nodes1 == null || nodes2 == null || nodes1.size() == 0 || nodes2.size() == 0) {
            return;
        }

        ThreadLocalCache.get();
        if (getRightTree() instanceof ConfigFileModel) {
            super.connect(nodes1, nodes2);
            return;
        }

        MJTree<Object> selTree = selectionFromRight ? rightTree : leftTree;

        // scan nodes to connect
        processSourceNodesToConnect(tempConnectList1, selTree.getSelectedObjects());
        if (tempConnectList1.size() == 0) {
            return;
        }

        tempConnectList2.clear();
        for (int i = 0; i < tempConnectList1.size(); i++) {
            tempConnectList2.add((ModelNode)nodes2.get(Math.min(i, nodes2.size() - 1)));
        }

        try {
            List<CompositeAction> actions = SmartConnecting.getConnectAction(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries());
            assert(actions.size() > 0);

            CompositeAction action = actions.get(0);
            if (actions.size() > 1) {
                for (CompositeAction a : actions) {
                    a.setAlternativeDescription(a.getAlternativeDescription() + " (creates " + createConnectionPortCreationString(a) + ")");
                }
                Object choice = JOptionPane.showInputDialog(finstruct, actions.size() == 1 ? "Connection crosses component boundaries" : "There are multiple connect options", "Choose connect option", JOptionPane.QUESTION_MESSAGE, null, actions.toArray(new Object[0]), action);
                if (choice instanceof CompositeAction) {
                    action = (CompositeAction)choice;
                } else {
                    return;
                }
            } else if (action.getActions().get(0) instanceof AddPortsToInterfaceAction) {
                // count ports that are created
                int choice = JOptionPane.showConfirmDialog(finstruct, "This will create " + createConnectionPortCreationString(action) + ". Continue?", "Create Ports?", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            action.execute();
            timer.restart();
        } catch (Exception e) {
            e.printStackTrace();
            Finstruct.showErrorMessage(e.getMessage(), false, false);
        }
    }

    /**
     * @param action Action to check
     * @return Port creation statistics string for action
     */
    private String createConnectionPortCreationString(CompositeAction action) {
        int portsCreated = 0, interfaceCount = 0;
        for (FinstructAction subAction : action.getActions()) {
            if (subAction instanceof AddPortsToInterfaceAction) {
                interfaceCount++;
                portsCreated += ((AddPortsToInterfaceAction)subAction).getNewPortCount();
            }
        }
        if (portsCreated == 0) {
            return "No additional ports are created";
        }
        return portsCreated  + " port" + (portsCreated > 1 ? "s" : "") + " in " + interfaceCount + " component interface" + (interfaceCount > 1 ? "s" : "");
    }

    @Override
    protected void connect(Object port, Object port2) {
        if (getRightTree() instanceof ConfigFileModel) {
            ParameterInfo pi = (port instanceof RemotePort) ? (ParameterInfo)((RemotePort)port).getAnnotation(ParameterInfo.TYPE) : null;
            Object parameter = port;
            Object configNode = port2;
            if (pi == null) {
                pi = (port2 instanceof RemotePort) ? (ParameterInfo)((RemotePort)port2).getAnnotation(ParameterInfo.TYPE) : null;
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
        }
        Log.log(LogLevel.DEBUG_WARNING, this, "Cannot connect ports: " + port  + " " + port2);
    }

    @Override
    protected List<Object> getConnectionPartners(Object port) {
        return getConnectionPartners(port, false);
    }

    /** Implementation of getConnectionPartners */
    protected List<Object> getConnectionPartners(final Object treeNode, boolean includeSourcePorts) {
        final List<Object> result = new ArrayList<Object>();
        if (!(treeNode instanceof RemotePort)) {
            return result;
        }
        RemotePort port = (RemotePort)treeNode;
        if (getRightTree() instanceof ConfigFileModel) {
            ParameterInfo pi = (ParameterInfo)((RemotePort)port).getAnnotation(ParameterInfo.TYPE);
            if (pi != null && pi.getConfigEntry() != null && pi.getConfigEntry().length() > 0) {
                Object pw = ((ConfigFileModel)getRightTree()).get(pi.getConfigEntry());
                if (pw != null) {
                    result.add(pw);
                }
            }
            return result;
        } else {
            result.addAll(port.getOutgoingConnections());

            if (includeSourcePorts) { // computationally expensive
                result.addAll(port.getIncomingConnections());
            }
            return result;
        }
    }

    @Override
    protected void removeConnections(Object object) {
        removeConnections(object, false);
    }

    protected void removeConnections(Object object, boolean visibleConnectionsOnly) {

        ThreadLocalCache.get();
        if (object != null) {
            if (getRightTree() instanceof ConfigFileModel) {
                if (object instanceof ConfigFileModel.ConfigEntryWrapper) {
                    final ModelNode currentRoot = finstruct.getCurrentView().getRootElement();
                    final String uid = ((ConfigFileModel.ConfigEntryWrapper)object).getUid();
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
                } else if (object instanceof RemotePort) {
                    RemoteRuntime rr = RemoteRuntime.find((RemotePort)object);
                    AdminClient ac = rr.getAdminInterface();
                    ParameterInfo pi = (ParameterInfo)((RemotePort)object).getAnnotation(ParameterInfo.TYPE);
                    pi.setConfigEntry("");
                    ac.setAnnotation(((RemotePort)object).getRemoteHandle(), pi);
                }
                return;
            } else if (object instanceof RemotePort) {
                RemotePort port = (RemotePort)object;
                List<Object> partners = getConnectionPartners(port, true);
                NetPort np1 = port.getPort().asNetPort();
                AdminClient ac = RemoteRuntime.find(np1).getAdminInterface();
                MJTree<Object> otherTree = popupOnRight ? leftTree : rightTree;

                if (ac != null) {
                    if (visibleConnectionsOnly) {
                        for (Object partner : partners) {
                            if (otherTree.isVisible(partner) && (partner instanceof RemotePort)) {
                                ac.disconnect(np1, ((RemotePort)partner).getPort().asNetPort());
                            }
                        }
                    } else {
                        ac.disconnectAll(np1);
                    }
                    timer.restart();
                }


                // Remove any network connections in reverse direction
                for (Object partner : partners) {
                    ArrayList<AbstractPort> result = new ArrayList<AbstractPort>();
                    NetPort partnerNetPort = ((RemotePort)partner).getPort().asNetPort();
                    int reverseIndex = partnerNetPort.getRemoteEdgeDestinations(result);
                    for (int i = 0; i < reverseIndex; i++) {
                        AdminClient partnerClient = RemoteRuntime.find(partnerNetPort).getAdminInterface();
                        if (partnerClient != null && ac != partnerClient && result.get(i) == np1.getPort() && ((!visibleConnectionsOnly) || otherTree.isVisible(partner))) {
                            partnerClient.networkConnect(partnerNetPort, "", RemoteRuntime.find(np1).uuid, ((RemotePort)port).getRemoteHandle(), ((HasUid)port).getUid(), true);
                            timer.restart();
                        }
                    }
                }
                return;
            }
        }
        Log.log(LogLevel.DEBUG_WARNING, this, "Cannot disconnect");
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        super.mouseDragged(e);
        MJTree<Object> otherTree = selectionFromRight ? leftTree : rightTree;
        MJTree<Object> selTree = selectionFromRight ? rightTree : leftTree;
        Object element = super.getTreeNodeFromPos(otherTree);

        String tooltip = null;
        if (element == null || showRightTree == false || getRightTree() instanceof ConfigFileModel || selTree.getSelectionCount() == 0 || otherTree.getSelectedObjects().contains(element)) {
            tooltip = null;
        } else {
            Object result = hypotheticalConnectionImplementation(element);
            if (result != null && result.getClass().equals(String.class)) {
                tooltip = result.toString();
            }
        }

        setToolTip(e.getLocationOnScreen(), element, tooltip);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
        setToolTip(e.getLocationOnScreen(), null, null);
    }

    /**
     * Set custom tool tip
     *
     * @param locationOnScreen Location on screen
     * @param element Element that tool tip is displayed for (display of tool tip will be delayed by 1 second on new element)
     * @param text Text to display (null will hide tooltip)
     */
    public void setToolTip(Point locationOnScreen, Object element, String text) {
        if (text == null) {
            toolTipTimer.stop();
            if (toolTip != null) {
                toolTip.hide();
                toolTip = null;
            }
        } else if (element != toolTipTreeNode) {
            toolTipTimer.stop();
            if (toolTip != null) {
                toolTip.hide();
                toolTip = null;
            }

            toolTipLocation = locationOnScreen;
            toolTipTimer.start();
            toolTipText = text;
        } else if (toolTip == null) {
            toolTipLocation = locationOnScreen;
            toolTipText = text;
            if (!toolTipTimer.isRunning()) {
                toolTipTimer.start();
            }
        } else if (!text.equals(toolTipText)) {
            if (toolTip != null) {
                toolTip.hide();
                toolTip = null;
            }

            toolTipText = text;
            toolTipLocation = locationOnScreen;
            showToolTip(locationOnScreen, toolTipText);
        }
        toolTipTreeNode = element;
    }

    /**
     * Shows tool tip
     *
     * @param locationOnScreen Location on screen
     * @param text Text where to display
     */
    public void showToolTip(Point locationOnScreen, String text) {
        if (toolTip != null) {
            toolTip.hide();
            toolTip = null;
        }

        JLabel label = new JLabel(text);
        label.setBackground(Color.WHITE);
        label.setOpaque(true);
        label.setBorder(new LineBorder(Color.black));
        toolTip = PopupFactory.getSharedInstance().getPopup(this, label, locationOnScreen.x, locationOnScreen.y);
        toolTip.show();
    }

    @Override
    protected void setToolTipText(MJTree<Object> tree, Object element) {
    }

    @Override
    protected void drawConnections(Graphics g) {
        drawConnectionsHelper(g, leftTree, rightTree);
        if (!(getRightTree() instanceof ConfigFileModel)) {
            drawConnectionsHelper(g, rightTree, leftTree);
        }
    }

    /**
     * Expand provided node in all visible trees
     *
     * @param node Node to expand
     * @param scrollToExpandedNode Scroll tree to expanded node?
     */
    public void expand(ModelNode node, boolean scrollToExpandedNode) {
        expand(false, node, scrollToExpandedNode);
        if (showRightTree) {
            expand(true, node, scrollToExpandedNode);
        }
    }

    /**
     * Expand provided node in all visible trees
     *
     * @param leftTree Left tree (or rather right one?)
     * @param node Node to expand
     * @param scrollToExpandedNode Scroll tree to expanded node?
     */
    public void expand(boolean leftTree, ModelNode node, boolean scrollToExpandedNode) {
        MJTree<Object> tree = leftTree ? super.leftTree : rightTree;
        if ((tree.getModel().getRoot() instanceof ModelNode) && node.isNodeAncestor((ModelNode)tree.getModel().getRoot())) {
            tree.expandToElement(node);
            if (scrollToExpandedNode) {
                tree.scrollPathToVisible(tree.getTreePathFor(node));
            }
        }
    }

    /**
     * Expand provided nodes in all visible trees
     *
     * @param leftTree Left tree (or rather right one?)
     * @param nodes Nodes to expand
     * @param scrollToExpandedNode Scroll tree to expanded node?
     */
    public void expand(boolean leftTree, Collection<ModelNode> nodes, boolean scrollToExpandedNode) {
        for (ModelNode node : nodes) {
            expand(leftTree, node, scrollToExpandedNode);
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
        MJTree<Object> tree = leftTree ? super.leftTree : rightTree;
        tree.collapseAll();
        for (ModelNode node : nodes) {
            tree.expandToElement(node);
            tree.scrollPathToVisible(tree.getTreePathFor(node));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MJTree<Object> ptree = popupOnRight ? rightTree : leftTree;
        try {
            if (e.getSource() == timer) {
                repaint();
                finstruct.refreshView();
                return;
            } else if (e.getSource() == toolTipTimer) {
                if (toolTipText != null) {
                    showToolTip(toolTipLocation, toolTipText);
                }
            } else if (e.getSource() == miOpenInNewWindow) {
                Object tnp = getTreeNodeFromPos(ptree);
                FinstructWindow fw = new FinstructWindow(finstruct);
                fw.showElement((ModelNode)tnp);
                fw.pack();
                fw.setVisible(true);
            } else if (e.getSource() == miShowPartner) {
                MJTree<Object> otherTree = popupOnRight ? leftTree : rightTree;
                Object portWrapper = getTreeNodeFromPos(ptree);
                List<Object> partners = getConnectionPartners(portWrapper, true);

                for (Object partner : partners) {
                    if (((ModelNode)partner).isHidden(true)) {
                        continue;
                    }
                    TreePath tp = otherTree.getTreePathFor(partner);
                    otherTree.scrollPathToVisible(tp);
                }
                return;
            } else if (e.getSource() == miRemoveConnections) {
                // Remove visible connections
                Object tnp = getTreeNodeFromPos(ptree);
                removeConnections(tnp, true);
                repaint();
                return;
            } else if (e.getSource() == miRemoveAllConnections) {
                // Remove visible connections
                Object tnp = getTreeNodeFromPos(ptree);
                removeConnections(tnp, false);
                repaint();
                return;
            }
            super.actionPerformed(e);
        } catch (Exception exception) {
            Finstruct.showErrorMessage(exception, true);
        }
    }



    @Override
    protected NodeRenderingStyle getNodeAppearance(Object node, MJTree<Object> tree, boolean selected) {
        NodeRenderingStyle result = tempRenderingStyle;
        result.reset();
        if (finstruct == null) {
            return result;
        }
        boolean otherTreeSelection = (selectionFromRight != (tree == rightTree)) && selected;
        if (node instanceof ConfigFileModel.ConfigEntryWrapper) {
            result.textColor = tree.getBackground();
            result.nodeColor = selected ? selectedColor : defaultColor;
            boolean mouseOverFlag = mouseOver == node;
            if (mouseOver instanceof PortWrapper) {
                ParameterInfo pi = (ParameterInfo)((RemotePort)mouseOver).getAnnotation(ParameterInfo.TYPE);
                mouseOverFlag |= pi != null && pi.getConfigEntry() != null && pi.getConfigEntry().length() > 0 && ((ConfigFileModel)getRightTree()).get(pi.getConfigEntry()) == node;
                result.iconType |= ConnectorIcon.OUTPUT & (mouseOverFlag ? ConnectorIcon.BRIGHTER_COLOR : 0);
            }
        } else if (node instanceof RemotePort) {
            RemotePort port = (RemotePort)node;
            result.textColor = tree.getBackground();
            boolean rpc = FinrocTypeInfo.isMethodType(port.getDataType(), true);
            boolean mouseOverFlag = (mouseOver instanceof RemotePort) && ((port.getPort() == ((RemotePort)mouseOver).getPort()) || port.isConnectedTo((RemotePort)mouseOver));
            result.nodeColor = selected ? selectedColor : defaultColor;
            if (getRightTree() instanceof ConfigFileModel) {
                ParameterInfo pi = (ParameterInfo)port.getAnnotation(ParameterInfo.TYPE);
                if (pi != null) {
                    mouseOverFlag |= pi != null && pi.getConfigEntry() != null && pi.getConfigEntry().length() > 0 && ((ConfigFileModel)getRightTree()).get(pi.getConfigEntry()) == mouseOver;
                } else {
                    result.nodeColor = lightGrayColor;
                }
            }
            result.iconType = (port.getFlag(FrameworkElementFlags.OUTPUT_PORT) ? ConnectorIcon.OUTPUT : 0) |
                              (port.isProxy() ? ConnectorIcon.PROXY : 0) |
                              (rpc ? ConnectorIcon.RPC : 0) |
                              (mouseOverFlag ? ConnectorIcon.BRIGHTER_COLOR : 0);
        } else {
            if (node.getClass().equals(RemoteFrameworkElement.class)) {
                RemoteFrameworkElement element = (RemoteFrameworkElement)node;
                if (element.isInterface()) {
                    result.nodeColor = otherTreeSelection ? selectedColor : (element.isSensorInterface() ? sensorInterfaceColor : (element.isControllerInterface() ? controllerInterfaceColor : interfaceColor));
                    boolean rpc = element.isRpcOnlyInterface();
                    result.iconType = (element.isOutputOnlyInterface() ? ConnectorIcon.OUTPUT : 0) |
                                      (element.isProxyInterface() || (rpc) ? ConnectorIcon.PROXY : 0) |
                                      (rpc ? ConnectorIcon.RPC : 0) |
                                      (mouseOver == element ? ConnectorIcon.BRIGHTER_COLOR : 0);
                } else if (otherTreeSelection) {
                    result.nodeColor = selectedColor.plainColor;
                }
            }
            if ((!otherTreeSelection) && node instanceof ModelNode && finstruct.getCurrentView() != null && !((ModelNode)node).isNodeAncestor(finstruct.getCurrentView().getRootElement()) && node != finstruct.getCurrentView().getRootElement()) {
                result.textColor = inactiveTextColor;
            }
        }

        if ((!otherTreeSelection) && finstruct.getCurrentView() != null && node == finstruct.getCurrentView().getRootElement()) {
            result.nodeColor = result.nodeColor instanceof ConnectorIcon.IconColor ? lightGrayColor : lightGrayColor.plainColor;
        }
        result.iconType |= (tree == rightTree) ? ConnectorIcon.RIGHT_TREE : 0;

        return result;
    }

    @Override
    public void setRightTree(TreeModel tm) {
        if (getRightTree() instanceof ConfigFileModel) {
            ((ConfigFileModel)getRightTree()).delete();
        }
        super.setRightTree(tm);
    }

    @Override
    protected LineStart getLineStartPosition(Object port, Object partner) {
        if (getRightTree() instanceof ConfigFileModel) {
            return ConnectorIcon.LineStart.Default;
        }
        NetPort np = port instanceof RemotePort ? ((RemotePort)port).getPort().asNetPort() : null;
        if (np != null && partner instanceof RemotePort) {
            RemotePort partnerPort = (RemotePort)partner;
            int firstReverse = np.getRemoteEdgeDestinations(tempDestinationList);
            int partnerIndex = tempDestinationList.indexOf(partnerPort.getPort());
            if (partnerIndex >= 0) {
                return partnerIndex < firstReverse ? ConnectorIcon.LineStart.Outgoing : ConnectorIcon.LineStart.Incoming;
            } else if (partnerPort.getPort().asNetPort() != null) {
                NetPort npPartner = partnerPort.getPort().asNetPort();
                firstReverse = npPartner.getRemoteEdgeDestinations(tempDestinationList);
                partnerIndex = tempDestinationList.indexOf(np.getPort());
                if (partnerIndex >= 0) {
                    return partnerIndex < firstReverse ? ConnectorIcon.LineStart.Incoming : ConnectorIcon.LineStart.Outgoing;
                }
                // Ok, there's no connection yet
            }
        }
        return ConnectorIcon.LineStart.Default;
    }

    @Override
    protected void updatePopupMenu(Object treeNode) {
        super.updatePopupMenu(treeNode);
        miOpenInNewWindow.setEnabled(treeNode instanceof ModelNode);

        miShowPartner.setEnabled(finstruct.treeToolBar.isSelected(Finstruct.Mode.connect));
        miRemoveAllConnections.setEnabled(miRemoveConnections.isEnabled());

        // Check whether port has any visible connections
        List<Object> partners = getConnectionPartners(treeNode, true);
        MJTree<Object> otherTree = popupOnRight ? leftTree : rightTree;
        boolean hasVisibleConnections = false;
        for (Object partner : partners) {
            hasVisibleConnections |= otherTree.isVisible(partner);
        }
        miRemoveConnections.setEnabled(hasVisibleConnections);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            checkMouseEvent(e);
        }

        super.mousePressed(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        checkMouseEvent(e);
    }

    /**
     * Checks mouse event for view switching.
     * Interfaces are shown with a MOUSE_CLICKED event.
     * All other elements are shown with a MOUSE_PRESSED event.
     * (This an optimization to reduce latency when navigating through the tree - interfaces, however, may be dragged and dropped)
     *
     * @param e Event to process
     */
    public void checkMouseEvent(MouseEvent e) {
        if (e.getSource() == leftTree || e.getSource() == rightTree) {
            JTree tree = (JTree)e.getSource();
            int row = tree.getRowForLocation(e.getX(), e.getY());
            if (row >= 0) {
                Object element = tree.getPathForRow(row).getLastPathComponent();
                if (element instanceof ModelNode && (!(element instanceof RemotePort))) {
                    ModelNode node = (ModelNode)element;
                    if (((!node.isInterface()) && e.getID() == MouseEvent.MOUSE_PRESSED) || (node.isInterface() && e.getID() == MouseEvent.MOUSE_CLICKED)) {
                        //Log.log(LogLevel.USER, "Setting view root to '" + element.toString() + "'");
                        finstruct.showElement((ModelNode)element);
                    }
                }
            }
        }
    }

    @Override
    public boolean acceptElement(Object element) {
        return (element instanceof RemotePort) || (element instanceof ConfigFileModel.ConfigEntryWrapper) ||
               ((element instanceof RemoteFrameworkElement) && (((RemoteFrameworkElement)element).isInterface() || ((RemoteFrameworkElement)element).isComponent()));
    }
}
