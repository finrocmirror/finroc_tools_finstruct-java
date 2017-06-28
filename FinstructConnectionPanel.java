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
import java.awt.Rectangle;
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
import org.finroc.core.remote.Definitions;
import org.finroc.core.remote.HasUid;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapper;
import org.finroc.core.remote.RemoteConnector;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteUriConnector;
import org.finroc.tools.finstruct.SmartConnecting.MayConnectResult;
import org.finroc.tools.finstruct.actions.AddPortsToInterfaceAction;
import org.finroc.tools.finstruct.actions.CompositeAction;
import org.finroc.tools.finstruct.actions.ConnectAction;
import org.finroc.tools.finstruct.actions.FinstructAction;
import org.finroc.tools.finstruct.dialogs.CreateConnectorOptionsDialog;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.ConnectorIcon;
import org.finroc.tools.gui.ConnectorIcon.LineStart;
import org.finroc.tools.gui.util.gui.MJTree;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.serialization.rtti.DataTypeBase;

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

    /** Temporary storage hypotheticalConnections - used only by AWT Thread */
    private final ArrayList<RemotePort> tempConnectList1 = new ArrayList<RemotePort>();
    private final ArrayList<ModelNode> tempConnectList2 = new ArrayList<ModelNode>();
    private final ArrayList<RemoteConnector> tempConnectorList = new ArrayList<RemoteConnector>();

    /** More colors */
    public static final ConnectorIcon.IconColor lightGrayColor = new ConnectorIcon.IconColor(new Color(211, 211, 211), new Color(233, 233, 233));
    public static final Color rootViewColor = new Color(211, 211, 211);
    public static final Color inactiveTextColor = new Color(160, 160, 160);
    public static final ConnectorIcon.IconColor errorColor = connectionPartnerMissingColor;
    public static final ConnectorIcon.IconColor connectorNoConversion = connectedColor;
    public static final Color connectorImplicitConversion = new Color(80, 200, 30);
    public static final Color connectorExplicitConversion = new Color(160, 230, 30);
    public static final Color connectorDeprecatedConversion = new Color(230, 140, 30);
    public static final Color connectorNetwork = new Color(40, 40, 200);

    public static final ConnectorIcon.IconColor portSelected20 = createConnectionCandidateColor(0.2);
    public static final ConnectorIcon.IconColor portSelected40 = createConnectionCandidateColor(0.4);
    public static final ConnectorIcon.IconColor portSelected60 = createConnectionCandidateColor(0.6);
    public static final ConnectorIcon.IconColor portSelected80 = createConnectionCandidateColor(0.8);

    public static final ConnectorIcon.IconColor[] candidatePortRatingColors = new ConnectorIcon.IconColor[] {
        defaultColor,
        portSelected20,
        portSelected40,
        portSelected60,
        portSelected60,
        portSelected60,
        portSelected60,
        portSelected80,
        portSelected80,
        selectedColor
    };

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
            return canConnectPorts((RemotePort)o1, (RemotePort)o2).isConnectingPossible();
        }
        return false;
    }

    /**
     * Can the two ports be connected?
     *
     * @param p1 Port 1
     * @param p2 Port 2
     * @return If connecting is possible: SmartConnecting.CAN_CONNECT, CAN_CONNECT_IMPLICIT_CAST, or CAN_CONNECT_EXPLICIT_CAST (all 'possible' strings start with CAN_CONNECT). If connecting is not possible, reason for this.
     */
    private SmartConnecting.MayConnectResult canConnectPorts(RemotePort p1, RemotePort p2) {
        if (RemoteRuntime.arePortsConnected(p1, p2)) {
            return new SmartConnecting.MayConnectResult("Ports are connected already");
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
     * Create color for candidate ports for connections
     *
     * @param redFactor The amount of red in color (0...1)
     * @return Color
     */
    private static ConnectorIcon.IconColor createConnectionCandidateColor(double redFactor) {
        ConnectorIcon.IconColor base = defaultColor;
        ConnectorIcon.IconColor red = selectedColor;
        double baseFactor = 1 - redFactor;
        return new ConnectorIcon.IconColor((int)(baseFactor * base.getRed() + redFactor * red.getRed()), (int)(baseFactor * base.getGreen() + redFactor * red.getGreen()), (int)(baseFactor * base.getBlue() + redFactor * red.getBlue()));
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
    protected CheckConnectResult checkConnect(Object other, CheckConnectResult resultBuffer) {
        if (getRightTree() instanceof ConfigFileModel) {
            return super.checkConnect(other, resultBuffer);
        }

        // Init objects
        resultBuffer.partnerNodes.clear();
        resultBuffer.connectionScores.clear();
        resultBuffer.minScore = Definitions.TypeConversionRating.IMPOSSIBLE;
        resultBuffer.impossibleHint = "";
        if (other == null) {
            resultBuffer.impossibleHint = "Partner object is null";
            return resultBuffer;
        }

        MJTree<Object> selectionTree = selectionFromRight ? rightTree : leftTree;

        // scan nodes to connect
        RemoteFrameworkElement nonPortNode = processSourceNodesToConnect(tempConnectList1, selectionTree.getSelectedObjects());
        if (tempConnectList1.size() == 0) {

            resultBuffer.impossibleHint = nonPortNode != null && nonPortNode.isInterface() ? "Selected interface in other tree is empty" : "Only ports and interfaces can be connected (dragged)";
            return resultBuffer;
        }

        if (nonPortNode != null) {
            // only connect to other non-port nodes
            if (other instanceof RemotePort) {
                resultBuffer.impossibleHint = "Interfaces cannot be connected to ports (you may try the other way round)";
                return resultBuffer;
            }

            tempConnectList2.clear();
            for (int i = 0; i < tempConnectList1.size(); i++) {
                tempConnectList2.add((ModelNode)other);
            }
            MayConnectResult mayConnect = SmartConnecting.mayConnect(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries());
            if (!mayConnect.isConnectingPossible()) {
                resultBuffer.impossibleHint = mayConnect.impossibleHint;
                return resultBuffer;
            }
            resultBuffer.partnerNodes.add(other);
            resultBuffer.connectionScores.add(mayConnect.rating);
            resultBuffer.minScore = mayConnect.rating;
            return resultBuffer;
        }

        if (other instanceof RemotePort) {
            if (tempConnectList1.size() == 1) {
                // Optimized implementation for only one selected port
                SmartConnecting.MayConnectResult mayConnect = canConnectPorts(tempConnectList1.get(0), (RemotePort)other);
                if (!mayConnect.isConnectingPossible()) {
                    resultBuffer.impossibleHint = mayConnect.impossibleHint;
                    return resultBuffer;
                }
                resultBuffer.partnerNodes.add(other);
                resultBuffer.connectionScores.add(mayConnect.rating);
                resultBuffer.minScore = mayConnect.rating;
                return resultBuffer;
            }

            // Standard hypotheticalConnectionImplementation from base class
            return super.checkConnect(other, resultBuffer);
        } else {
            // Connect all ports to non-port tree node
            tempConnectList2.clear();
            for (int i = 0; i < tempConnectList1.size(); i++) {
                tempConnectList2.add((ModelNode)other);
            }
            MayConnectResult mayConnect = SmartConnecting.mayConnect(tempConnectList1, tempConnectList2, finstruct.allowDirectConnectingAcrossGroupBoundaries());
            if (!mayConnect.isConnectingPossible()) {
                resultBuffer.impossibleHint = mayConnect.impossibleHint;
                return resultBuffer;
            }
            resultBuffer.partnerNodes.add(other);
            resultBuffer.connectionScores.add(mayConnect.rating);
            resultBuffer.minScore = mayConnect.rating;
            return resultBuffer;
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

            // Query for any type conversions
            boolean showCreateConnectorDialog = false;
            for (FinstructAction a : ((CompositeAction)action).getActions()) {
                if (a instanceof ConnectAction) {
                    showCreateConnectorDialog |= ((ConnectAction)a).getConnectOptions().requiresOperationSelection() || FinstructAction.findRemoteRuntime(((ConnectAction)a).getSourceLink()) != FinstructAction.findRemoteRuntime(((ConnectAction)a).getDestinationLink());
                }
            }
            if (showCreateConnectorDialog) {
                CreateConnectorOptionsDialog dialog = new CreateConnectorOptionsDialog(finstruct);
                dialog.show(((CompositeAction)action).getActions());
                if (dialog.cancelled()) {
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
                ac.setAnnotation(((RemotePort)parameter).getRemoteHandle(), ParameterInfo.TYPE.getName(), pi);
                return;
            }
        }
        Log.log(LogLevel.DEBUG_WARNING, this, "Cannot connect ports: " + port  + " " + port2);
    }

    @Override
    protected List<Object> getConnectionPartners(Object treeNode) {
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
            RemoteRuntime runtime = RemoteRuntime.find(port);
            ArrayList<RemoteConnector> connectors = new ArrayList<>();
            runtime.getConnectors(connectors, port);
            for (RemoteConnector connector : connectors) {
                RemotePort partnerPort = connector.getPartnerPort(port, runtime);
                if (partnerPort != null) {
                    result.add(partnerPort);
                }
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
                            ac.setAnnotation(remotePort.getRemoteHandle(), ParameterInfo.TYPE.getName(), pi);
                        }
                    }
                } else if (object instanceof RemotePort) {
                    RemoteRuntime rr = RemoteRuntime.find((RemotePort)object);
                    AdminClient ac = rr.getAdminInterface();
                    ParameterInfo pi = (ParameterInfo)((RemotePort)object).getAnnotation(ParameterInfo.TYPE);
                    pi.setConfigEntry("");
                    ac.setAnnotation(((RemotePort)object).getRemoteHandle(), ParameterInfo.TYPE.getName(), pi);
                }
                return;
            } else if (object instanceof RemotePort) {
                RemotePort port = (RemotePort)object;
                RemoteRuntime runtime = RemoteRuntime.find(port);
                if (runtime == null || runtime.getAdminInterface() == null) {
                    return;
                }
                ArrayList<RemoteConnector> connectors = new ArrayList<>();
                runtime.getConnectors(connectors, port);
                MJTree<Object> otherTree = popupOnRight ? leftTree : rightTree;

                if (visibleConnectionsOnly) {
                    for (RemoteConnector connector : connectors) {
                        RemotePort partnerPort = connector.getPartnerPort(port, runtime);
                        if (partnerPort != null && otherTree.isVisible(partnerPort)) {
                            runtime.getAdminInterface().disconnect(connector);
                        }
                    }
                } else {
                    runtime.getAdminInterface().disconnectAll(port);
                }
                timer.restart();


                // Remove any network connections in reverse direction
//                for (Object partner : partners) {
//                    ArrayList<AbstractPort> result = new ArrayList<AbstractPort>();
//                    NetPort partnerNetPort = ((RemotePort)partner).getPort().asNetPort();
//                    int reverseIndex = partnerNetPort.getRemoteEdgeDestinations(result);
//                    for (int i = 0; i < reverseIndex; i++) {
//                        AdminClient partnerClient = RemoteRuntime.find(partnerNetPort).getAdminInterface();
//                        if (partnerClient != null && ac != partnerClient && result.get(i) == np1.getPort() && ((!visibleConnectionsOnly) || otherTree.isVisible(partner))) {
//                            partnerClient.networkConnect(partnerNetPort, "", RemoteRuntime.find(np1).uuid, ((RemotePort)port).getRemoteHandle(), ((HasUid)port).getUid(), true);
//                            timer.restart();
//                        }
//                    }
//                }
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
            CheckConnectResult result = new CheckConnectResult();
            checkConnect(element, result);
            if (result.minScore == Definitions.TypeConversionRating.IMPOSSIBLE) {
                tooltip = result.impossibleHint;
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
        if (getRightTree() instanceof ConfigFileModel) {
            drawConnectionsHelper(g, leftTree, rightTree);
        } else if (showRightTree) {

            Rectangle visible = this.getVisibleRect();
            List<Object> visibleNodesLeft = leftTree.getVisibleObjects();
            ArrayList<RemoteConnector> connectorList = new ArrayList<RemoteConnector>();
            finstruct.getIoInterface().updateUriConnectors();

            for (Object node : visibleNodesLeft) {
                if (node instanceof RemotePort) {
                    RemotePort portLeft = (RemotePort)node;
                    RemoteRuntime runtime = RemoteRuntime.find(portLeft);
                    runtime.getConnectors(connectorList, portLeft);
                    for (RemoteConnector connector : connectorList) {
                        RemotePort portRight = connector.getPartnerPort(portLeft, runtime);
                        if (portRight != null && rightTree.isVisible(portRight)) {
                            Point p1 = getLineStartPoint(leftTree, portLeft, getLineStartPosition(connector, portLeft, portRight));
                            Point p2 = getLineStartPoint(rightTree, portRight, getLineStartPosition(connector, portRight, portLeft));
                            RemoteRuntime runtimeRight = RemoteRuntime.find(portRight);
                            Color color = connectedColor;
                            if (runtime != runtimeRight) {
                                color = connectorNetwork;
                            } else {
                                boolean leftIsSource = connector.getSourceHandle() == portLeft.getRemoteHandle();
                                Definitions.TypeConversionRating rating = connector.getRemoteConnectOptions().getTypeConversionRating(leftIsSource ? portLeft : portRight, runtime, leftIsSource ? portRight : portLeft);
                                switch (rating) {
                                case NO_CONVERSION:
                                    color = connectorNoConversion;
                                    break;
                                case IMPOSSIBLE:
                                case DEPRECATED_CONVERSION:
                                    color = connectorDeprecatedConversion;
                                    break;
                                case IMPLICIT_CAST:
                                case TWO_IMPLICIT_CASTS:
                                    color = connectorImplicitConversion;
                                    break;
                                default:
                                    color = connectorExplicitConversion;
                                    break;
                                }
                            }
                            if (mouseOver != null) {
                                if (mouseOver == portLeft || mouseOver == portRight) {
                                    color = color.brighter();
                                }
                            }
                            if (visible.contains(p1) && visible.contains(p2)) {
                                drawLine(g, p1, p2, color, true, false);
                            } else if (g.getClipBounds().intersectsLine(p1.x, p1.y, p2.x, p2.y)) {
                                drawLine(g, p1, p2, color, false, false);
                            }
                        }
                    }
                }
            }
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
                List<Object> partners = getConnectionPartners(portWrapper);

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
        } else if (node instanceof RemoteFrameworkElement) {
            // erroneous element?
            RemoteFrameworkElement element = (RemoteFrameworkElement)node;
            RemoteRuntime runtime = RemoteRuntime.find(element);
            boolean configFileMode = getRightTree() instanceof ConfigFileModel;
            if (runtime != null && (!configFileMode)) {
                for (RemoteFrameworkElement errorElement : runtime.getElementsInErrorState()) {
                    if (element.isNodeAncestor(errorElement)) {
                        result.nodeColor = errorColor;
                        break;
                    }
                }
            }

            if (node.getClass().equals(RemotePort.class)) {
                RemotePort port = (RemotePort)element;
                result.textColor = tree.getBackground();
                boolean rpc = (port.getDataType().getTypeTraits() & DataTypeBase.IS_RPC_TYPE) != 0;
                boolean mouseOverFlag = (mouseOver instanceof RemotePort) && ((port.getPort() == ((RemotePort)mouseOver).getPort()) || RemoteRuntime.arePortsConnected(port, (RemotePort)mouseOver));
                result.nodeColor = selected ? selectedColor : (result.nodeColor != errorColor ? defaultColor : errorColor);
                if (otherTreeSelection) {
                    Definitions.TypeConversionRating rating = highlightedElementRatings.get(node);
                    if (rating != null) {
                        result.nodeColor = candidatePortRatingColors[rating.ordinal()];
                    }
                }
                if (configFileMode) {
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
        }
        if (result.textColor == null && (!otherTreeSelection) && node instanceof ModelNode && finstruct.getCurrentView() != null && !((ModelNode)node).isNodeAncestor(finstruct.getCurrentView().getRootElement()) && node != finstruct.getCurrentView().getRootElement()) {
            result.textColor = inactiveTextColor;
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
        if (port instanceof RemotePort && partner instanceof RemotePort) {
            RemotePort remotePort = (RemotePort)port;
            RemotePort partnerPort = (RemotePort)partner;
            RemoteRuntime runtime = RemoteRuntime.find(remotePort);
            runtime.getConnectors(tempConnectorList, remotePort);
            for (RemoteConnector connector : tempConnectorList) {
                if (connector.getPartnerPort(remotePort, runtime) == partner) {
                    return getLineStartPosition(connector, remotePort, partnerPort);
                }
            }
        }
        return ConnectorIcon.LineStart.Default;
    }

    /**
     * Get line start position for remote connector
     *
     * @param connector Remote connector
     * @param port Port in question
     * @param partner Partner port
     * @return Line starting position
     */
    protected LineStart getLineStartPosition(RemoteConnector connector, RemotePort port, RemotePort partner) {
        if (connector instanceof RemoteUriConnector) {
            return SmartConnecting.inferConnectDirection(port, partner) == AbstractPort.ConnectDirection.TO_TARGET ? ConnectorIcon.LineStart.Outgoing : ConnectorIcon.LineStart.Incoming;
        } else {
            return connector.getSourceHandle() == port.getRemoteHandle() ? ConnectorIcon.LineStart.Outgoing : ConnectorIcon.LineStart.Incoming;
        }
    }

    @Override
    protected void updatePopupMenu(Object treeNode) {
        super.updatePopupMenu(treeNode);
        miOpenInNewWindow.setEnabled(treeNode instanceof ModelNode);

        miShowPartner.setEnabled(finstruct.treeToolBar.isSelected(Finstruct.Mode.connect));
        miRemoveAllConnections.setEnabled(miRemoveConnections.isEnabled());

        // Check whether port has any visible connections
        List<Object> partners = getConnectionPartners(treeNode);
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
