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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.datatype.DataTypeReference;
import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.remote.Definitions;
import org.finroc.core.remote.Definitions.TypeConversionRating;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteConnectOptions;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteType;
import org.finroc.tools.finstruct.actions.AddPortsToInterfaceAction;
import org.finroc.tools.finstruct.actions.CompositeAction;
import org.finroc.tools.finstruct.actions.ConnectAction;
import org.finroc.tools.finstruct.actions.FinstructAction;
import org.rrlib.serialization.Register;
import org.rrlib.serialization.rtti.DataTypeBase;

/**
 * @author Max Reichardt
 *
 * Contains functionality for intelligent connecting of two ports -
 * possibly across group boundaries creating all required ports in interfaces.
 *
 * Due to the complexity, this concern is separated in a separated class.
 */
public class SmartConnecting {

//    public static final String CAN_CONNECT = "Yes";
//    public static final String CAN_CONNECT_IMPLICIT_CAST = "Yes - Implicit Cast";
//    public static final String CAN_CONNECT_EXPLICIT_CAST = "Yes - Explicit Cast";

    /**
     * Result of mayConnect* methods
     * Used like a struct -> public fields.
     */
    public static class MayConnectResult {

        /** Connection rating */
        public Definitions.TypeConversionRating rating;

        /** If there is no suitable combination to connect source nodes to partner nodes: contains hint/reason why not (as may be displayed as tool tip atop tree node) */
        public String impossibleHint;

        /**
         * @return Whether connecting is possible
         */
        public boolean isConnectingPossible() {
            return rating != Definitions.TypeConversionRating.IMPOSSIBLE;
        }

        public MayConnectResult(String impossibleHint) {
            rating = TypeConversionRating.IMPOSSIBLE;
            this.impossibleHint = impossibleHint;
        }

        public MayConnectResult(TypeConversionRating rating) {
            this.rating = rating;
            this.impossibleHint = null;
        }
    }

    /**
     * Result of getStreamableTypes method
     */
    public static class StreamableType implements Comparable<StreamableType> {

        /** Rating of connection */
        public Definitions.TypeConversionRating rating;

        /** Name of type (in both runtimes) */
        public String name;

        @Override
        public int compareTo(StreamableType o) {
            int c = Integer.compare(o.rating.ordinal(), rating.ordinal());
            return c != 0 ? c : name.compareTo(o.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Checks whether remote ports can be connected directly (ignoring component boundaries)
     *
     * @param sourcePort First remote port
     * @param destinationPort Second remote port
     * @param alsoCheckReverse If false, only checks one direction (from source to destination). If true, checks both directions.
     * @return Result: if and how connecting is possible
     */
    public static MayConnectResult mayConnectDirectly(RemotePort sourcePort, RemotePort destinationPort, boolean alsoCheckReverse) {
        MayConnectResult result = new MayConnectResult(Definitions.TypeConversionRating.IMPOSSIBLE);
        boolean forwardPossible = true, reversePossible = alsoCheckReverse;
        RemoteRuntime sourceRuntime = RemoteRuntime.find(sourcePort);
        if (sourceRuntime == null) {
            result.impossibleHint = "No runtime found for '" + sourcePort.getQualifiedLink() + "'";
            return result;
        }
        RemoteRuntime destinationRuntime = RemoteRuntime.find(destinationPort);
        if (destinationRuntime == null) {
            result.impossibleHint = "No runtime found for '" + destinationPort.getQualifiedLink() + "'";
            return result;
        }

        if (alsoCheckReverse) {
            if (!(sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA) || destinationPort.getFlag(FrameworkElementFlags.EMITS_DATA))) {
                result.impossibleHint = "Neither port emits data ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
                return result;
            }
            if (!(sourcePort.getFlag(FrameworkElementFlags.ACCEPTS_DATA) || destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA))) {
                result.impossibleHint = "Neither port accepts data ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
                return result;
            }
            if (!(sourcePort.getFlag(FrameworkElementFlags.ACCEPTS_DATA) || sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA))) {
                result.impossibleHint = sourcePort.getQualifiedLink() + " neither emits nor accepts data";
                return result;
            }
            if (!(destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA) || destinationPort.getFlag(FrameworkElementFlags.EMITS_DATA))) {
                result.impossibleHint = destinationPort.getQualifiedLink() + " neither emits nor accepts data";
                return result;
            }
            forwardPossible = sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA) && destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA);
            reversePossible = destinationPort.getFlag(FrameworkElementFlags.EMITS_DATA) && sourcePort.getFlag(FrameworkElementFlags.ACCEPTS_DATA);
        } else if (!sourcePort.getFlag(FrameworkElementFlags.EMITS_DATA)) {
            result.impossibleHint = "Source port (" + sourcePort.getQualifiedLink() + ") does not emit data";
            return result;
        } else if (!destinationPort.getFlag(FrameworkElementFlags.ACCEPTS_DATA)) {
            result.impossibleHint = "Destination port (" + sourcePort.getQualifiedLink() + ") does not accept data";
            return result;
        }

        if (sourceRuntime != destinationRuntime) {
            if ((!sourcePort.getFlag(FrameworkElementFlags.SHARED)) && (!destinationPort.getFlag(FrameworkElementFlags.SHARED))) {
                result.impossibleHint = "Neither port is shared ('" + sourcePort.getQualifiedLink() + "' and '" + destinationPort.getQualifiedLink() + "')";
                return result;
            }

            ModelNode commonParent = findCommonParent(sourcePort, destinationPort);
            if (commonParent == Finstruct.getInstance().getIoInterface().getRoot()) {
                result.impossibleHint = "Ports need to be connected to the same protocol/interface. Are parts connected to each other?";
                return result;
            }

            if (forwardPossible) {
                result.rating = getInterRuntimeTypeConversionRating(sourceRuntime, sourcePort.getDataType(), destinationRuntime, destinationPort.getDataType());
            } else {
                result.rating = getBestRating(result.rating, getInterRuntimeTypeConversionRating(destinationRuntime, destinationPort.getDataType(), sourceRuntime, sourcePort.getDataType()));
            }

        } else {
            result.rating = getBestRating(forwardPossible ? sourceRuntime.getTypeConversionRating(sourcePort.getDataType(), destinationPort.getDataType()) : Definitions.TypeConversionRating.IMPOSSIBLE,
                                          reversePossible ? sourceRuntime.getTypeConversionRating(sourcePort.getDataType(), destinationPort.getDataType()) : Definitions.TypeConversionRating.IMPOSSIBLE);
        }

        if (result.rating == Definitions.TypeConversionRating.IMPOSSIBLE) {
            if (forwardPossible && reversePossible) {
                result.impossibleHint = "Types " + sourcePort.getDataType().getName() + " and " + destinationPort.getDataType().getName() + " cannot be converted to each other in either way";
            } else if (forwardPossible) {
                result.impossibleHint = "Type " + sourcePort.getDataType().getName() + " cannot be converted to " + destinationPort.getDataType().getName();
            } else if (reversePossible) {
                result.impossibleHint = "Type " + destinationPort.getDataType().getName() + " cannot be converted to " + sourcePort.getDataType().getName();
            }
        }
        result.impossibleHint = "";
        return result;
    }

    /**
     * Can elements in both lists be connected?
     * (Connects first element in list with first element in other list, second with second etc.
     * => Lists need to have the same size.)
     *
     * @param nodes1 First collection (only ports)
     * @param nodes2 Second collection (possibly containing interface and components)
     * @param allowDirectConnecting Allow direct connecting across component boundaries?
     * @return Result: if and how connecting is possible
     */
    public static MayConnectResult mayConnect(List<RemotePort> nodes1, List<ModelNode> nodes2, boolean allowDirectConnecting) {
        MayConnectResult result = new MayConnectResult(Definitions.TypeConversionRating.IMPOSSIBLE);
        try {
            // TODO: can be optimized
            List<CompositeAction> actions = getConnectActionImplementation(nodes1, nodes2, allowDirectConnecting, false, false);
            for (CompositeAction compositeAction : actions) {
                Definitions.TypeConversionRating minRating = Definitions.TypeConversionRating.NO_CONVERSION;
                for (FinstructAction action : compositeAction.getActions()) {
                    if (action instanceof ConnectAction) {
                        Definitions.TypeConversionRating actionRating = ((ConnectAction)action).getConversionRating();
                        if (actionRating.ordinal() < minRating.ordinal()) {
                            minRating = actionRating;
                        }
                    }
                }
                if (minRating.ordinal() > result.rating.ordinal()) {
                    result.rating = minRating;
                }
            }
        } catch (Exception e) {
            result.impossibleHint = e.getMessage();
        }
        return result;
    }

    /**
     * Infer direction in which remote ports ought to be connected.
     *
     * @param port1 First port
     * @param port2 Second port
     * @return Returns TO_TARGET if port2 should be destination port - otherwise TO_SOURCE. In doubt, returns TO_TARGET.
     */
    public static AbstractPort.ConnectDirection inferConnectDirection(RemotePort port1, RemotePort port2) {
        ModelNode commonParent = SmartConnecting.findCommonParent(port1, port2);

        // Inner-group connection to group's interface port
        if (port1.getParent() != null && port1.getParent().isInterface() && port1.getParent().getParent() == commonParent && (port1.getFlags() & FrameworkElementFlags.PROXY) == FrameworkElementFlags.PROXY) {
            return port1.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT) ? AbstractPort.ConnectDirection.TO_SOURCE : AbstractPort.ConnectDirection.TO_TARGET;
        }
        if (port2.getParent() != null && port2.getParent().isInterface() && port2.getParent().getParent() == commonParent && (port2.getFlags() & FrameworkElementFlags.PROXY) == FrameworkElementFlags.PROXY) {
            return port2.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT) ? AbstractPort.ConnectDirection.TO_TARGET : AbstractPort.ConnectDirection.TO_SOURCE;
        }

        // Standard case
        return port1.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT) ? AbstractPort.ConnectDirection.TO_TARGET : AbstractPort.ConnectDirection.TO_SOURCE;
    }

    /**
     * If two ports to be connected are in different runtime environments, returns type conversion rating of best conversion option
     *
     * @param sourceRuntime Source runtime
     * @param sourceType Source port data type
     * @param destinationRuntime Destination runtime
     * @param destinationType Destination port data type
     * @return List of all streamable types together with connection rating of the best option
     * @throws Throws exception if runtimes are identical
     */
    public static Definitions.TypeConversionRating getInterRuntimeTypeConversionRating(RemoteRuntime sourceRuntime, RemoteType sourceType, RemoteRuntime destinationRuntime, RemoteType destinationType) {
        if (sourceRuntime == destinationRuntime) {
            throw new RuntimeException("Runtimes are identical");
        }

        RemoteType.CachedConversionRatings ratings = sourceRuntime.getTypeConversionRatings(sourceType);
        Register<RemoteType> types = sourceRuntime.getTypes();
        Definitions.TypeConversionRating result = Definitions.TypeConversionRating.IMPOSSIBLE;
        for (int j = 0; j < ratings.size(); j++) {
            RemoteType interTypeSource = types.get(j);
            Definitions.TypeConversionRating rating = ratings.getRating(interTypeSource);
            if (interTypeSource != sourceType && rating.ordinal() > result.ordinal()) {
                RemoteType interTypeDestination = destinationRuntime.getRemoteType(interTypeSource.getName());
                if (interTypeDestination != null) {
                    Definitions.TypeConversionRating rating2 = destinationRuntime.getTypeConversionRating(interTypeDestination, destinationType);
                    if ((rating == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_TO_GENERIC_TYPE && rating2 == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_FROM_GENERIC_TYPE) ||
                            (rating == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_FROM_GENERIC_TYPE && rating2 == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_TO_GENERIC_TYPE)) {
                        rating2 = Definitions.TypeConversionRating.DEPRECATED_CONVERSION;
                    }
                    if (rating2.ordinal() > result.ordinal()) {
                        result = getWorstRating(rating, rating2);
                    }
                }
            }
        }
        return result;
    }

    /**
     * If two ports to be connected are in different runtime environments, returns types that would be suitable for serialization/deserialization over the network
     *
     * @param sourceRuntime Source runtime
     * @param sourceType Source port data type
     * @param destinationRuntime Destination runtime
     * @param destinationType Destination port data type
     * @return List of all streamable types together with connection rating of the best option
     * @throws Throws exception if runtimes are identical
     */
    public static List<StreamableType> getStreamableTypes(RemoteRuntime sourceRuntime, RemoteType sourceType, RemoteRuntime destinationRuntime, RemoteType destinationType) {
        if (sourceRuntime == destinationRuntime) {
            throw new RuntimeException("Runtimes are identical");
        }

        ArrayList<StreamableType> result = new ArrayList<StreamableType>();
        RemoteType.CachedConversionRatings ratings = sourceRuntime.getTypeConversionRatings(sourceType);
        Register<RemoteType> types = sourceRuntime.getTypes();
        for (int i = 0; i < ratings.size(); i++) {
            RemoteType interTypeSource = types.get(i);
            Definitions.TypeConversionRating rating = ratings.getRating(interTypeSource);
            if (rating != Definitions.TypeConversionRating.IMPOSSIBLE) {
                RemoteType interTypeDestination = destinationRuntime.getRemoteType(interTypeSource.getName());
                if (interTypeDestination != null) {
                    Definitions.TypeConversionRating rating2 = destinationRuntime.getTypeConversionRating(interTypeDestination, destinationType);
                    if (rating2 != Definitions.TypeConversionRating.IMPOSSIBLE) {
                        StreamableType option = new StreamableType();
                        option.name = interTypeSource.getName();
                        option.rating = getWorstRating(rating, rating2);
                        if ((rating == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_TO_GENERIC_TYPE && rating2 == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_FROM_GENERIC_TYPE) ||
                                (rating == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_FROM_GENERIC_TYPE && rating2 == Definitions.TypeConversionRating.EXPLICIT_CONVERSION_TO_GENERIC_TYPE)) {
                            option.rating = Definitions.TypeConversionRating.DEPRECATED_CONVERSION;
                        }
                        result.add(option);
                    }
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * @param rating1 First rating
     * @param rating2 Second rating
     * @return Maximum (best) rating of the two
     */
    public static Definitions.TypeConversionRating getBestRating(Definitions.TypeConversionRating rating1, Definitions.TypeConversionRating rating2) {
        return Definitions.TypeConversionRating.values()[Math.max(rating1.ordinal(), rating2.ordinal())];
    }

    /**
     * @param rating1 First rating
     * @param rating2 Second rating
     * @return Minimum (worst) rating of the two
     */
    public static Definitions.TypeConversionRating getWorstRating(Definitions.TypeConversionRating rating1, Definitions.TypeConversionRating rating2) {
        return Definitions.TypeConversionRating.values()[Math.min(rating1.ordinal(), rating2.ordinal())];
    }


    /**
     * Helper class/struct for getConnectActionImplementation
     */
    static class TraceElement {
        RemotePort port;
        String portName;
        RemoteType portDataType;
        PortCreationList.Entry createEntry;
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

                RemoteRuntime node1Runtime = RemoteRuntime.find(nodes1.get(i));
                RemoteRuntime node2Runtime = RemoteRuntime.find(nodes2.get(i));

                // Initialize ports
                TraceElement element1 = new TraceElement(), element2 = new TraceElement();
                ArrayList<TraceElement> outwardTrace1 = new ArrayList<TraceElement>(), outwardTrace2 = new ArrayList<TraceElement>();
                outwardTrace1.add(element1);
                outwardTrace2.add(element2);

                // Port 1
                element1.port = nodes1.get(i);
                element1.portName = element1.port.getName();
                element1.portDataType = element1.port.getDataType();
                if ((!(element1.port.getParent() instanceof RemoteFrameworkElement)) || (!(element1.port.getParent().getParent() instanceof RemoteFrameworkElement))) {
                    throw new Exception("Port must be below two framework elements");
                }
                element1.interface_ = (RemoteFrameworkElement)element1.port.getParent();
                element1.component = (RemoteFrameworkElement)element1.interface_.getParent();

                // Element 2
                element2.portDataType = element1.portDataType;
                if (nodes2.get(i) instanceof RemotePort) {
                    element2.port = (RemotePort)nodes2.get(i);
                    element2.portName = element2.port.getName();
                    element2.portDataType = element2.port.getDataType();
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
                if (outwardOnlyConnection && ((element1.component == commonParent && (element1.port.getFlags() & FrameworkElementFlags.PROXY) != FrameworkElementFlags.PROXY) || (element2.port != null && element2.component == commonParent && (element2.port.getFlags() & FrameworkElementFlags.PROXY) != FrameworkElementFlags.PROXY))) {
                    // Non-proxy port in outer interface -> has same data flow direction to outer and inner elements
                    port2IsOutputDesired = !port2IsOutputDesired;
                }
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
                    RemoteRuntime traceRuntime = j == 0 ? node1Runtime : node2Runtime;
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
                                    boolean rpcType = element1.portDataType.getTypeClassification() == DataTypeBase.CLASSIFICATION_RPC_TYPE;
                                    boolean rpcPortInDataInterface = rpcType && (!element1.interface_.getFlag(FrameworkElementFlags.INTERFACE_FOR_RPC_PORTS));
                                    boolean checkForRpcType = rpcType && (!rpcPortInDataInterface);

                                    boolean directionOk = (outputPorts && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_OUTPUTS)) || ((!outputPorts) && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_INPUTS)) || rpcType;
                                    if (!rpcType) {
                                        score += (outputPorts && candidate.isOutputOnlyInterface()) || ((!outputPorts) && candidate.isInputOnlyInterface()) ? 1 : 0;
                                        score += (candidate.isParameterInterface() == lastElement.interface_.isParameterInterface()) ? 2 : 0;
                                    } else if (rpcPortInDataInterface) {
                                        score += lastElement.interface_.getName().contains("Output") && candidate.getName().contains("Output") || lastElement.interface_.getName().contains("Input") && candidate.getName().contains("Input") ? 1 : 0;
                                    }
                                    boolean typeCheck1 = (sensorData == controllerData) || candidate.getFlag(FrameworkElementFlags.SENSOR_DATA) == candidate.getFlag(FrameworkElementFlags.CONTROLLER_DATA) || (candidate.isControllerInterface() && controllerData) || (candidate.isSensorInterface() && sensorData);
                                    score += (sensorData != controllerData) && ((sensorData && candidate.isSensorInterface()) || (controllerData && candidate.isControllerInterface())) ? 2 : 0;
                                    boolean typeCheck2 = (checkForRpcType && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_RPC_PORTS)) || ((!checkForRpcType) && candidate.getFlag(FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS));
                                    score += (checkForRpcType && candidate.isRpcOnlyInterface()) || ((!checkForRpcType) && (candidate.getFlags() & (FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS | FrameworkElementFlags.INTERFACE_FOR_RPC_PORTS)) == FrameworkElementFlags.INTERFACE_FOR_DATA_PORTS) ? 1 : 0;
                                    if (directionOk && typeCheck1 && typeCheck2 && score >= maxScore) {
                                        if (score > maxScore) {
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
                        Definitions.TypeConversionRating conversionRating = Definitions.TypeConversionRating.NO_CONVERSION;
                        if (childElement != null) {
                            // Check port
                            if (!(childElement instanceof RemotePort)) {
                                throw new Exception("Relevant interface has element with same name which is no port '" + lastElement.portName + "'");
                            }
                            RemotePort port = (RemotePort)childElement;
                            if (port.getFlag(FrameworkElementFlags.IS_OUTPUT_PORT) != outputPorts) {
                                throw new Exception("Relevant interface has port with same name and wrong direction '" + port.getQualifiedLink() + "'");
                            }
                            if (!(port.getFlag(FrameworkElementFlags.EMITS_DATA) && port.getFlag(FrameworkElementFlags.ACCEPTS_DATA)) && k > 0) {
                                throw new Exception("Relevant interface has port with same name which is no proxy '" + port.getQualifiedLink() + "'");
                            }
                            if (port.getDataType() != lastElement.portDataType) {
                                conversionRating = traceRuntime.getTypeConversionRating(outputPorts ? lastElement.portDataType : port.getDataType(), outputPorts ? port.getDataType() : lastElement.portDataType);
                                if (conversionRating == Definitions.TypeConversionRating.IMPOSSIBLE) {
                                    throw new Exception("Relevant interface has port with same name and incompatible data types '" + port.getQualifiedLink() + "'");
                                }
                            }
                            currentElement.port = port;
                            currentElement.portName = port.getName();
                            currentElement.portDataType = port.getDataType();
                        } else {
                            // Create port (?)
                            currentElement.portName = lastElement.portName;
                            currentElement.portDataType = lastElement.portDataType;
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
                                    RemoteType existingEntryType = traceRuntime.getRemoteType(entry.type.toString());
                                    if (existingEntryType == null) {
                                        throw new Exception("Remote type in PortCreationList not found (logic error)");
                                    }
                                    if (existingEntryType != currentElement.portDataType) {
                                        conversionRating = traceRuntime.getTypeConversionRating(outputPorts ? lastElement.portDataType : existingEntryType, outputPorts ? existingEntryType : lastElement.portDataType);
                                        if (conversionRating == Definitions.TypeConversionRating.IMPOSSIBLE) {
                                            throw new Exception("Relevant interface has port (to be created) with same name and incompatible data types '" + existingEntryType + "'");
                                        }
                                        currentElement.portDataType = existingEntryType;
                                    }
                                    currentElement.createEntry = entry;
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                currentElement.createEntry = new PortCreationList.Entry(currentElement.portName, new DataTypeReference(outwardTrace1.get(0).port.getDataType()), outputPorts ? PortCreationList.CREATE_OPTION_OUTPUT : 0);
                                addList.add(currentElement.createEntry);
                            }
                        }

                        // Create connection?
                        if (k > 0 && (lastElement.port == null || currentElement.port == null || (!RemoteRuntime.arePortsConnected(lastElement.port, currentElement.port)))) {
                            ConnectAction connectAction = new ConnectAction(lastElement.getPortLink(), currentElement.getPortLink(), lastElement.portDataType.getName(), currentElement.portDataType.getName(), new RemoteConnectOptions(conversionRating), !outputPorts);
                            action.getActions().add(connectAction);
                        }
                    }
                }

                // Create connection
                TraceElement lastElement1 = outwardTrace1.get(outwardTrace1.size() - 1);
                TraceElement lastElement2 = outwardTrace2.get(outwardTrace2.size() - 1);
                if (lastElement1.port instanceof RemotePort && lastElement2.port instanceof RemotePort) {
                    boolean port1IsSource = inferConnectDirection(lastElement1.port, lastElement2.port) == AbstractPort.ConnectDirection.TO_TARGET;
                    MayConnectResult result = mayConnectDirectly(port1IsSource ? lastElement1.port : lastElement2.port, port1IsSource ? lastElement2.port : lastElement1.port, false);
                    if (result.impossibleHint != null && result.impossibleHint.length() > 0) {
                        throw new Exception(result.impossibleHint);
                    }

                    ConnectAction connectAction = new ConnectAction(lastElement1.getPortLink(), lastElement2.getPortLink(), lastElement1.portDataType.getName(), lastElement2.portDataType.getName(), new RemoteConnectOptions(result.rating), !port1IsSource);
                    action.getActions().add(connectAction);

                } else if (lastElement1.port == null || lastElement2.port == null || (!RemoteRuntime.arePortsConnected(lastElement1.port, lastElement2.port))) {
                    Definitions.TypeConversionRating conversionRating = Definitions.TypeConversionRating.NO_CONVERSION;
                    boolean port1IsSourcePort = port1IsOutput && ((!outwardOnlyConnection) || outwardTrace1.size() > outwardTrace2.size());
                    RemoteType sourceType = port1IsSourcePort ? lastElement1.portDataType : lastElement2.portDataType;
                    RemoteType destinationType = port1IsSourcePort ? lastElement2.portDataType : lastElement1.portDataType;
                    if (node1Runtime == node2Runtime && lastElement1.portDataType != lastElement2.portDataType) {
                        conversionRating = node1Runtime.getTypeConversionRating(sourceType, destinationType);
                        if (conversionRating == Definitions.TypeConversionRating.IMPOSSIBLE) {
                            throw new Exception("Ports have incompatible data types: '" + sourceType.getName() + "' and '" + destinationType.getName() + "'");
                        }
                    } else if (node1Runtime != node2Runtime && (!lastElement1.portDataType.getName().equals(lastElement2.portDataType.getName()))) {
                        conversionRating = port1IsSourcePort ? getInterRuntimeTypeConversionRating(node1Runtime, lastElement1.portDataType, node2Runtime, lastElement2.portDataType) :
                                           getInterRuntimeTypeConversionRating(node2Runtime, lastElement2.portDataType, node1Runtime, lastElement1.portDataType);
                        if (conversionRating == Definitions.TypeConversionRating.IMPOSSIBLE) {
                            throw new Exception("Ports have incompatible data types: '" + sourceType.getName() + "' and '" + destinationType.getName() + "'");
                        }
                        // TODO: maybe shared option needs to be set for port to be created in some scenarios
                    }

                    ConnectAction connectAction = new ConnectAction(lastElement1.getPortLink(), lastElement2.getPortLink(), lastElement1.portDataType.getName(), lastElement2.portDataType.getName(), new RemoteConnectOptions(conversionRating), !port1IsSourcePort);
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

    /**
     * @param port Port whose URI path to obtain
     * @return URI path of port
     */
    public static String getUriPath(RemotePort port) {
        String result = "";
        ModelNode currentElement = port;
        while (currentElement != null) {
            if ((currentElement instanceof RemoteRuntime) || (!(currentElement instanceof RemoteFrameworkElement))) {
                break;
            }
            result = "/" + currentElement.getName().replace("/", "%2F") + result;
            currentElement = currentElement.getParent();
        }
        return result;
    }
}
