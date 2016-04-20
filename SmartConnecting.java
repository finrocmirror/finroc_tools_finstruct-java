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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.datatype.DataTypeReference;
import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.actions.AddPortsToInterfaceAction;
import org.finroc.tools.finstruct.actions.CompositeAction;
import org.finroc.tools.finstruct.actions.ConnectAction;
import org.finroc.tools.finstruct.actions.FinstructAction;

/**
 * @author Max Reichardt
 *
 * Contains functionality for intelligent connecting of two ports -
 * possibly across group boundaries creating all required ports in interfaces.
 *
 * Due to the complexity, this concern is separated in a separated class.
 */
public class SmartConnecting {

    /**
     * Checks whether remote ports can be connected directly (ignoring component boundaries)
     *
     * @param sourcePort First remote port
     * @param destinationPort Second remote port
     * @param alsoCheckReverse If false, only checks one direction (from source to destination). If true, checks both directions.
     * @return Empty string if connecting is possible. If connecting is not possible, reason for this.
     */
    public static String mayConnectDirectly(RemotePort sourcePort, RemotePort destinationPort, boolean alsoCheckReverse) {
        RemoteRuntime sourceRuntime = RemoteRuntime.find(sourcePort);
        if (sourceRuntime == null) {
            return "No runtime found for '" + sourcePort.getQualifiedLink() + "'";
        }
        RemoteRuntime destinationRuntime = RemoteRuntime.find(destinationPort);
        if (destinationRuntime == null) {
            return "No runtime found for '" + destinationPort.getQualifiedLink() + "'";
        }

        if (alsoCheckReverse) {
            if (!(sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA) || destinationPort.getFlag(FrameworkElementFlags.EMITS_DATA))) {
                return "Neither port emits data ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
            }
            if (!(sourcePort.getFlag(FrameworkElementFlags.ACCEPTS_DATA) || destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA))) {
                return "Neither port accepts data ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
            }
        } else if (!sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA)) {
            return "Source port (" + sourcePort.getQualifiedLink() + ") does not emit data";
        } else if (!destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA)) {
            return "Destination port (" + sourcePort.getQualifiedLink() + ") does not accept data";
        }

        if (sourcePort.getDataType() != destinationPort.getDataType()) {
            return "Ports have different types ('" + sourcePort.getQualifiedLink() + "' has type '" + sourcePort.getDataType().getName() + "' and '" + destinationPort.getQualifiedLink() + "' has type '" + destinationPort.getDataType().getName() + "')";
        }

        if (sourceRuntime != destinationRuntime) {
            if ((!sourcePort.getFlag(FrameworkElementFlags.SHARED)) && (!destinationPort.getFlag(FrameworkElementFlags.SHARED))) {
                return "Neither port is shared ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
            }
            if (!((sourceRuntime.getAdminInterface() != null && destinationPort.getFlag(FrameworkElementFlags.SHARED)) || (destinationRuntime.getAdminInterface() != null && sourcePort.getFlag(FrameworkElementFlags.SHARED)))) {
                return "One non-shared port needs admin interface";
            }

            ModelNode commonParent = findCommonParent(sourcePort, destinationPort);
            if (commonParent == Finstruct.getInstance().getIoInterface().getRoot()) {
                return "Ports need to be connected to the same protocol/interface. Are parts connected to each other.";
            }
        }

        return "";
    }

    /**
     * Can elements in both lists be connected?
     * (Connects first element in list with first element in other list, second with second etc.
     * => Lists need to have the same size.)
     *
     * @param nodes1 First collection (only ports)
     * @param nodes2 Second collection (possibly containing interface and components)
     * @param allowDirectConnecting Allow direct connecting across component boundaries?
     * @return Empty string if connecting is possible. If connecting is not possible, reason for this.
     */
    public static String mayConnect(List<RemotePort> nodes1, List<ModelNode> nodes2, boolean allowDirectConnecting) {
        try {
            // TODO: can be optimized
            getConnectActionImplementation(nodes1, nodes2, allowDirectConnecting, false, false);
        } catch (Exception e) {
            return e.getMessage();
        }
        return "";
    }

    /**
     * Helper class/struct for getConnectActionImplementation
     */
    static class TraceElement {
        RemotePort port;
        String portName;
        RemoteFrameworkElement interface_;
        RemoteFrameworkElement component;

        public String getPortLink() {
            return component.getQualifiedName(FinstructAction.LINK_SEPARATOR) + FinstructAction.LINK_SEPARATOR + interface_.getName() + FinstructAction.LINK_SEPARATOR + portName;
        }
    }

//  /**
//   * Get connections from port to ports in interface of other component
//   *
//   * @param port Port to check
//   * @param otherComponent Other component
//   * @return List of connections
//   */
//  private ArrayList<RemotePort> getConnectionsTo(RemotePort port, RemoteFrameworkElement otherComponent) {
//      ArrayList<RemotePort> result = new ArrayList<RemotePort>();
//      if (port.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT)) {
//          for (RemotePort otherPort : port.getOutgoingConnections()) {
//              if (otherPort.getParent() != null && otherPort.getParent().isInterface() && otherPort.getParent().getParent() == otherComponent) {
//                  result.add(otherPort);
//              }
//          }
//      } else {
//          // Optimization: trace outgoing connections from all ports in target component interfaces
//          for (int i = 0; i < otherComponent.getChildCount(); i++) {
//              if (otherComponent.getChildAt(i).isInterface()) {
//                  RemoteFrameworkElement interface_ = (RemoteFrameworkElement)otherComponent.getChildAt(i);
//                  for (int j = 0; j < interface_.getChildCount(); j++) {
//                      if (interface_.getChildAt(j) instanceof RemotePort) {
//                          RemotePort remotePort = (RemotePort)interface_.getChildAt(j);
//                          if (remotePort.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT)) {
//                              for (RemotePort p : remotePort.getOutgoingConnections()) {
//                                  if (p == port) {
//                                      result.add(remotePort);
//                                  }
//                              }
//                          }
//                      }
//                  }
//              }
//          }
//      }
//      return result;
//  }

    /**
     * @param node1 Node 1
     * @param node2 Node 2
     * @return Node that is nearest common ancestor of node1 and node2 (including possibly node1 or node2 themselves)
     */
    public static ModelNode findCommonParent(ModelNode node1, ModelNode node2) {
        ModelNode commonParent = node1;
        while (!(node2.isNodeAncestor(commonParent) || node2 == commonParent)) {
            commonParent = commonParent.getParent();
            if (commonParent == null) {
                throw new RuntimeException("Elements have no common parent (this is unusual - they should have at least root node in common)");
            }
        }
        return commonParent;
    }

    /**
     * Compute composite actions that connect the elements in both node lists cleanly via interfaces (possibly creating ports).
     *
     * Connects first element in list with first element in other list, second with second etc.
     * => Lists need to have the same size.
     *
     * @param nodes1 First collection (only ports)
     * @param nodes2 Second collection (possibly containing interface and components)
     * @param allowDirectConnecting Allow direct connecting across component boundaries?
     * @return Alternative Actions to connect elements
     * @throws Throws Exception if collections cannot be connected cleanly (with reason as message)
     */
    public static List<CompositeAction> getConnectAction(List<RemotePort> nodes1, List<ModelNode> nodes2, boolean allowDirectConnecting) throws Exception {
        return getConnectActionImplementation(nodes1, nodes2, allowDirectConnecting, false, false);
    }

    /**
     * Implementation of getConnectAction. May be called recursively.
     */
    public static List<CompositeAction> getConnectActionImplementation(List<RemotePort> nodes1, List<ModelNode> nodes2, boolean allowDirectConnecting, boolean controllerRun, boolean directConnectingRun) throws Exception {
        if (nodes1.size() != nodes2.size()) {
            throw new Exception("Collections need to have the same size");
        }
        if (nodes1.size() == 0) {
            throw new Exception("No elements provided.");
        }
        boolean addControllerRun = false;
        Set<RemoteFrameworkElement> crossedComponentInterfaces = new HashSet<RemoteFrameworkElement>();
        CompositeAction action = new CompositeAction("Connect '" + FinstructAction.getReadableLinkForMenu(nodes1.get(0).getQualifiedName(FinstructAction.LINK_SEPARATOR)) + (nodes1.size() > 1 ? "' etc." : "'"));
        Map<RemoteFrameworkElement, AddPortsToInterfaceAction> addPortActions = new HashMap<RemoteFrameworkElement, AddPortsToInterfaceAction>();
        Exception caughtException = null;

        try {
            for (int i = 0; i < nodes1.size(); i++) {

                // Initialize ports
                TraceElement element1 = new TraceElement(), element2 = new TraceElement();
                ArrayList<TraceElement> outwardTrace1 = new ArrayList<TraceElement>(), outwardTrace2 = new ArrayList<TraceElement>();
                outwardTrace1.add(element1);
                outwardTrace2.add(element2);

                // Port 1
                element1.port = nodes1.get(i);
                element1.portName = element1.port.getName();
                if ((!(element1.port.getParent() instanceof RemoteFrameworkElement)) || (!(element1.port.getParent().getParent() instanceof RemoteFrameworkElement))) {
                    throw new Exception("Port must be below two framework elements");
                }
                element1.interface_ = (RemoteFrameworkElement)element1.port.getParent();
                element1.component = (RemoteFrameworkElement)element1.interface_.getParent();

                // Element 2
                if (nodes2.get(i) instanceof RemotePort) {
                    element2.port = (RemotePort)nodes2.get(i);
                    element2.portName = element2.port.getName();
                    if ((!(element2.port.getParent() instanceof RemoteFrameworkElement)) || (!(element2.port.getParent().getParent() instanceof RemoteFrameworkElement))) {
                        throw new Exception("Port must be below two framework elements");
                    }
                    element2.interface_ = (RemoteFrameworkElement)element2.port.getParent();
                    element2.component = (RemoteFrameworkElement)element2.interface_.getParent();
                } else if ((nodes2.get(i) instanceof RemoteFrameworkElement) && nodes2.get(i).isInterface()) {
                    element2.interface_ = (RemoteFrameworkElement)nodes2.get(i);
                    if (!(element2.interface_.getParent() instanceof RemoteFrameworkElement)) {
                        throw new Exception("Interface must be below framework element");
                    }
                    element2.component = (RemoteFrameworkElement)element2.interface_.getParent();
                } else if (nodes2.get(i) instanceof RemoteFrameworkElement) {
                    element2.component = (RemoteFrameworkElement)nodes2.get(i);
                } else {
                    throw new Exception("Can only connect to remote ports, interfaces, and components");
                }

                // Find common parent
                ModelNode commonParent = findCommonParent(element1.component, element2.component);

                // Check that ports fit
                boolean outwardOnlyConnection = element1.component != element2.component && (element1.component == commonParent || element2.component == commonParent);
                boolean port1IsOutput = element1.port.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT);
                boolean port2IsOutputDesired = outwardOnlyConnection ? port1IsOutput : (!port1IsOutput);
                if (element2.port != null || (element2.interface_ != null && (element2.interface_.isInputOnlyInterface() || element2.interface_.isOutputOnlyInterface()))) {
                    boolean port2IsOutput = (element2.port != null && element2.port.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT)) || (element2.port == null && element2.interface_.isOutputOnlyInterface());
                    if (port2IsOutput != port2IsOutputDesired) {
                        throw new Exception("Unsuitable data flow directions");
                    }
                }

                // Trace components towards common parent
                for (int j = 0; j < 2 && (!directConnectingRun); j++) {
                    ArrayList<TraceElement> trace = j == 0 ? outwardTrace1 : outwardTrace2;
                    TraceElement element = trace.get(0);
                    while (element.component != commonParent && element.component.getParent() != commonParent && element.component.getParent() instanceof RemoteFrameworkElement && (!(element.component.getParent() instanceof RemoteRuntime))) {
                        TraceElement newElement = new TraceElement();
                        newElement.component = (RemoteFrameworkElement)element.component.getParent();
                        trace.add(newElement);
                        element = newElement;
                    }
                }

                // Decide on whether to use sensor or controller interfaces
                boolean sensorData = element1.interface_.isSensorInterface() || (element2.interface_ != null && element2.interface_.isSensorInterface());
                boolean controllerData = element1.interface_.isControllerInterface() || (element2.interface_ != null && element2.interface_.isControllerInterface());
//              int index = 1;
//              ArrayList<RemotePort> portSet1 = new ArrayList<RemotePort>();
//              portSet1.add(element1.port);
//              ArrayList<RemotePort> portSet2 = new ArrayList<RemotePort>();
//              if (element2.port != null) {
//                  portSet2.add(element2.port);
//              }
//              // Trace any outward connections to classify port data
//              while ((!sensorData) && (!controllerData) && (portSet1.size() > 0 || portSet2.size() > 0)) {
//                  for (int j = 0; j < 2; j++) {
//                      ArrayList<TraceElement> trace = j == 0 ? outwardTrace1 : outwardTrace2;
//                      ArrayList<RemotePort> portSet = j == 0 ? portSet1 : portSet2;
//                      if (portSet.size() > 0 && index < trace.size()) {
//                          ArrayList<RemotePort> newSet = new ArrayList<RemotePort>();
//                          for (RemotePort port : portSet) {
//                              newSet.addAll(getConnectionsTo(port, trace.get(index).component));
//                          }
//                          for (RemotePort port : newSet) {
//                              sensorData |= (port.getParent() instanceof RemoteFrameworkElement) && ((RemoteFrameworkElement)port.getParent()).getFlag(FrameworkElementFlags.SENSOR_DATA);
//                              controllerData |= (port.getParent() instanceof RemoteFrameworkElement) && ((RemoteFrameworkElement)port.getParent()).getFlag(FrameworkElementFlags.CONTROLLER_DATA);
//                          }
//                          portSet.clear();
//                          portSet.addAll(newSet);
//                      } else {
//                          portSet.clear();
//                      }
//                  }
//                  index++;
//              }

                // Process outward traces
                ArrayList<RemoteFrameworkElement> interfaceCandidates = new ArrayList<RemoteFrameworkElement>();

                for (int j = 0; j < 2; j++) {
                    ArrayList<TraceElement> trace = j == 0 ? outwardTrace1 : outwardTrace2;
                    for (int k = 0; k < trace.size(); k++) {
                        TraceElement currentElement = trace.get(k);
                        if (currentElement.port != null) {
                            continue;
                        }
                        TraceElement lastElement = (k > 0) ? trace.get(k - 1) : outwardTrace1.get(0); // 0 is only possible with outwardTrace2
                        boolean outputPorts = j == 0 ? port1IsOutput : port2IsOutputDesired;

                        // Select an interface
                        if (currentElement.interface_ == null) {
                            ModelNode sameNameInterface = currentElement.component.getChildByName(lastElement.interface_.getName());
                            if (k > 0 && sameNameInterface instanceof RemoteFrameworkElement && ((RemoteFrameworkElement)sameNameInterface).isInterface() &&
                                    (((RemoteFrameworkElement)sameNameInterface).isEditableInterface() || sameNameInterface.getChildByName(lastElement.portName) instanceof RemotePort)) {
                                currentElement.interface_ = (RemoteFrameworkElement)sameNameInterface;
                            } else if (currentElement.component.getEditableInterfaces() != null) {
                                interfaceCandidates.clear();
                                int maxScore = -1;
                                for (RemoteFrameworkElement candidate : currentElement.component.getEditableInterfaces()) {
                                    int score = 0;
                                    boolean directionOk = (outputPorts && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_OUTPUTS)) || ((!outputPorts) && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_INPUTS));
                                    score += (outputPorts && candidate.isOutputOnlyInterface()) || ((!outputPorts) && candidate.isInputOnlyInterface()) ? 1 : 0;
                                    boolean typeCheck1 = (sensorData == controllerData) || candidate.getFlag(FrameworkElementFlags.SENSOR_DATA) == candidate.getFlag(FrameworkElementFlags.CONTROLLER_DATA) || candidate.isControllerInterface() && controllerData || candidate.isSensorInterface() && sensorData;
                                    score += (sensorData != controllerData) && ((sensorData && candidate.isSensorInterface()) || (controllerData && candidate.isControllerInterface())) ? 2 : 0;
                                    boolean rpcType = FinrocTypeInfo.isMethodType(element1.port.getDataType(), true);
                                    boolean typeCheck2 = (rpcType && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_RPC_PORTS)) || ((!rpcType) && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS));
                                    score += (rpcType && candidate.isRpcOnlyInterface()) || ((!rpcType) && (candidate.getFlags() & (FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS | FrameworkElementFlags.INTERFACE_FOR_RPC_PORTS)) == FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS) ? 1 : 0;
                                    if (directionOk && typeCheck1 && typeCheck2 && score >= maxScore) {
                                        if (score >= maxScore) {
                                            interfaceCandidates.clear();
                                        }
                                        interfaceCandidates.add(candidate);
                                        maxScore = score;
                                    }
                                }

                                // evaluate candidates
                                if (interfaceCandidates.size() == 0) {
                                    throw new Exception("No suitable interface in " + currentElement.component.getQualifiedName('/'));
                                } else if (interfaceCandidates.size() == 1) {
                                    currentElement.interface_ = interfaceCandidates.get(0);
                                } else if (interfaceCandidates.size() == 2 && sensorData == controllerData && interfaceCandidates.get(0).isControllerInterface() != interfaceCandidates.get(0).isSensorInterface() &&
                                           interfaceCandidates.get(1).isControllerInterface() != interfaceCandidates.get(1).isSensorInterface() && interfaceCandidates.get(0).isSensorInterface() != interfaceCandidates.get(1).isSensorInterface()) {
                                    // sensor/controller-data ambiguity: two runs (select sensor in first)
                                    currentElement.interface_ = interfaceCandidates.get(0).isControllerInterface() == controllerRun ? interfaceCandidates.get(0) : interfaceCandidates.get(1);
                                    addControllerRun = !controllerRun;
                                } else {
                                    throw new Exception("Interface selection ambiguous for component " + currentElement.component.getQualifiedName('/'));
                                }
                            } else {
                                throw new Exception("Component '" + currentElement.component.getQualifiedName('/') + "' has no editable interfaces");
                            }
                        }

                        // Component interfaces crossed
                        if (k > 0) {
                            crossedComponentInterfaces.add(currentElement.interface_);
                        }

                        // Check or create port
                        ModelNode childElement = currentElement.interface_.getChildByName(lastElement.portName);
                        if (childElement != null) {
                            // Check port
                            if (!(childElement instanceof RemotePort)) {
                                throw new Exception("Existing element with name '" + lastElement.portName + "' is no port");
                            }
                            RemotePort port = (RemotePort)childElement;
                            if (port.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT) != outputPorts) {
                                throw new Exception("Existing port '" + port.getQualifiedName('/') + "' has wrong direction");
                            }
                            if (!(port.getFlag(FrameworkElementFlags.EMITS_DATA) && port.getFlag(FrameworkElementFlags.ACCEPTS_DATA)) && k > 0) {
                                throw new Exception("Existing port '" + port.getQualifiedName('/') + "' must be a proxy");
                            }
                            if (port.getDataType() != outwardTrace1.get(0).port.getDataType()) {
                                throw new Exception("Existing port '" + port.getQualifiedName('/') + "' needs same data type");
                            }
                            currentElement.port = port;
                            currentElement.portName = port.getName();
                        } else {
                            // Create port (?)
                            currentElement.portName = lastElement.portName;
                            AddPortsToInterfaceAction addPortAction = addPortActions.get(currentElement.component);
                            if (addPortAction == null) {
                                addPortAction = new AddPortsToInterfaceAction(currentElement.component);
                                if (addPortActions.size() == 0) {
                                    action.getActions().add(0, CompositeAction.CHECK_SUCCESS_BEFORE_CONTINUE);
                                }
                                action.getActions().add(0, addPortAction);
                                addPortActions.put(currentElement.component, addPortAction);
                            }
                            List<PortCreationList.Entry> addList = addPortAction.getPortsToAdd(currentElement.interface_.getName());

                            // Check whether entry is already in list
                            boolean found = false;
                            for (PortCreationList.Entry entry : addList) {
                                if (entry.name.equals(currentElement.portName)) {
                                    if (entry.type.get() != outwardTrace1.get(0).port.getDataType()) {
                                        throw new Exception("Existing port '" + currentElement.getPortLink() + "' needs same data type");
                                    } else {
                                        found = true;
                                    }
                                    break;
                                }
                            }

                            if (!found) {
                                addList.add(new PortCreationList.Entry(currentElement.portName, new DataTypeReference(outwardTrace1.get(0).port.getDataType()), outputPorts ? PortCreationList.CREATE_OPTION_OUTPUT : 0));
                            }
                        }

                        // Create connection?
                        if (k > 0 && (lastElement.port == null || currentElement.port == null || (!lastElement.port.isConnectedTo(currentElement.port)))) {
                            ConnectAction connectAction = new ConnectAction(lastElement.getPortLink(), currentElement.getPortLink());
                            action.getActions().add(connectAction);
                        }
                    }
                }

                // Create connection
                TraceElement lastElement1 = outwardTrace1.get(outwardTrace1.size() - 1);
                TraceElement lastElement2 = outwardTrace2.get(outwardTrace2.size() - 1);
                if (lastElement1.port instanceof RemotePort && lastElement2.port instanceof RemotePort) {
                    String result = mayConnectDirectly((RemotePort)lastElement1.port, (RemotePort)lastElement2.port, true);
                    if (result.length() > 0) {
                        throw new Exception(result);
                    }
                }
                if (lastElement1.port == null || lastElement2.port == null || (!lastElement1.port.isConnectedTo(lastElement2.port))) {
                    ConnectAction connectAction = new ConnectAction(lastElement1.getPortLink(), lastElement2.getPortLink());
                    action.getActions().add(connectAction);
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
            caughtException = new Exception("Internal Error (see console)");
        } catch (Exception e) {
            caughtException = e;
        }

        if (caughtException == null && action.getActions().size() == 0) {
            caughtException = new Exception("Nothing to do");
        }

        // Prepare result
        ArrayList<CompositeAction> result = new ArrayList<CompositeAction>();
        if (caughtException == null) {
            // Set description
            String appendix = addControllerRun || controllerRun ? (" (" + (controllerRun ? "Controller" : "Sensor") + " interface" + (crossedComponentInterfaces.size() > 1 ? "s" : "") + ")") : "";
            action.setAlternativeDescription((crossedComponentInterfaces.size() == 0 ? "Connect directly" : ("Connect via " + crossedComponentInterfaces.size() + " interface" + (crossedComponentInterfaces.size() > 1 ? "s" : ""))) + appendix);
            result.add(action);
        }
        if (addControllerRun && (!controllerRun)) {
            try {
                result.addAll(getConnectActionImplementation(nodes1, nodes2, allowDirectConnecting, true, directConnectingRun));
            } catch (Exception e) {}
        }
        if (crossedComponentInterfaces.size() > 0 && allowDirectConnecting && (!directConnectingRun) && (!controllerRun)) {
            try {
                result.addAll(getConnectActionImplementation(nodes1, nodes2, allowDirectConnecting, false, true));
            } catch (Exception e) {
                caughtException = e;
            }
        }
        if (result.isEmpty()) {
            throw caughtException;
        }
        return result;
    }
}
