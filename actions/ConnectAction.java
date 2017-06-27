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
package org.finroc.tools.finstruct.actions;

import java.net.URI;
import java.util.ArrayList;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.remote.Definitions;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteConnectOptions;
import org.finroc.core.remote.RemoteConnector;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteUriConnector;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.SmartConnecting;


/**
 * @author Max Reichardt
 *
 * Action connecting two ports
 */
public class ConnectAction extends FinstructAction {

    /**
     * Note that the connect direction must be correct (if reverse if false, sourceLink must refer to the actual source port; if reverse is true, destinationLink must refer to the actual source port)
     *
     * @param source Fully qualified name of source port
     * @param destination Fully qualified name of destination port
     * @param sourceTypeName Name of source port data type
     * @param destinationTypeName Name of destination port data type
     * @param connectOptions Connect Options. If selection of connect options is to be deferred to later, can be null. They must, however, be set before executing action.
     * @param reverse Whether destinationLink refers to the actual source port
     */
    public ConnectAction(String sourceLink, String destinationLink, String sourceTypeName, String destinationTypeName, RemoteConnectOptions connectOptions, boolean reverse) {
        this.sourceLink = reverse ? destinationLink : sourceLink;
        this.destinationLink = reverse ? sourceLink : destinationLink;
        this.sourceTypeName = reverse ? destinationTypeName : sourceTypeName;
        this.destinationTypeName = reverse ? sourceTypeName : destinationTypeName;
        this.connectOptions = connectOptions;
    }

    @Override
    protected String checkSuccessImplementation() {
        ModelNode sourceNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(sourceLink, LINK_SEPARATOR);
        if (!(sourceNode instanceof RemotePort)) {
            return "Connecting failed: '" + sourceLink + "' no longer exists";
        }
        ModelNode destinationNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(destinationLink, LINK_SEPARATOR);
        if (!(destinationNode instanceof RemotePort)) {
            return "Connecting failed: '" + destinationLink + "' no longer exists";
        }
        if (RemoteRuntime.arePortsConnected((RemotePort)sourceNode, (RemotePort)destinationNode)) {
            return "";
        }
        return null;
    }

    /**
     * Implementation of connect and disconnect
     *
     * @param disconnect Disconnect (instead of connecting)
     * @param actionString Description of action (e.g. for error messages). First letter should be capital.
     */
    protected void connectImplementation(boolean disconnect, String actionString) throws Exception {
        RemotePort sourcePort = getSourcePort();
        RemotePort destinationPort = getDestinationPort();
        if (sourcePort == null && destinationPort == null) {
            throw new Exception(actionString + " failed: Neither '" + sourceLink + "' nor '" + destinationLink + "' found/available");
        }
        if (sourcePort == null) {
            throw new Exception(actionString + " failed: '" + sourceLink + "' not found/available");
        }
        if (destinationPort == null) {
            throw new Exception(actionString + " failed: '" + destinationLink + "' not found/available");
        }
        if (connectOptions == null) {
            throw new Exception(actionString + " failed: remote connect options need to be set");
        }

        String reason = SmartConnecting.mayConnectDirectly(sourcePort, destinationPort, true).impossibleHint;
        if (reason.length() > 0) {
            throw new Exception(actionString + " failed: " + reason);
        }
        RemoteRuntime sourceRuntime = RemoteRuntime.find(sourcePort);
        if (sourceRuntime == null) {
            throw new Exception("No runtime found for '" + sourceLink + "'");
        }
        RemoteRuntime destinationRuntime = RemoteRuntime.find(destinationPort);
        if (destinationRuntime == null) {
            throw new Exception("No runtime found for '" + destinationLink + "'");
        }

        String result = null;
        if (sourceRuntime != null && sourceRuntime == destinationRuntime) {

            if (disconnect) {
                sourceRuntime.getAdminInterface().disconnect(sourcePort, destinationPort);
            } else {
                //if (!sourcePort.isConnectedTo(destinationPort)) {  // removed in order to avoid issues with any parallel incomplete actions
                if (sourceRuntime.getSerializationInfo().getRevision() == 0) {
                    sourceRuntime.getAdminInterface().connectPorts(sourcePort, destinationPort, null);
                } else {
                    result = sourceRuntime.getAdminInterface().connectPorts(sourcePort, destinationPort, connectOptions);  // TODO are synchronous calls to connect sufficiently responsive?
                }
            }
        } else {
            result = "No port is shared";
            boolean legacySourceRuntime = sourceRuntime.getSerializationInfo().getRevision() == 0;
            boolean legacyDestinationRuntime = destinationRuntime.getSerializationInfo().getRevision() == 0;

            if (disconnect) {
                ArrayList<RemoteConnector> connectors = new ArrayList<>();
                sourceRuntime.getConnectors(connectors, sourcePort);
                for (RemoteConnector connector : connectors) {
                    if ((connector instanceof RemoteUriConnector) && connector.getPartnerPort(sourcePort, sourceRuntime) == destinationPort) {
                        sourceRuntime.getAdminInterface().disconnect(connector);
                        result = null;
                    }
                }
                if (result == null) {
                    return;
                }
            }

            if (sourceRuntime.getAdminInterface() != null && destinationPort.getFlag(FrameworkElementFlags.SHARED)) {
                if (legacySourceRuntime && legacyDestinationRuntime) {
                    result = sourceRuntime.getAdminInterface().networkConnect(sourcePort, "", RemoteRuntime.find(destinationPort).uuid, destinationPort.getRemoteHandle(), destinationPort.getUid(), disconnect);
                } else if ((!disconnect) && (!legacySourceRuntime)) {
                    URI uri = new URI("tcp", null, SmartConnecting.getUriPath(destinationPort), null);
                    System.out.println();
                    org.finroc.core.net.generic_protocol.Definitions.setServerSideConversionOptions(connectOptions, destinationConnectOptions, serializedType);
                    result = sourceRuntime.getAdminInterface().createUriConnector(sourcePort, uri.normalize().toString(), connectOptions);
                }
            }
            if (result != null && destinationRuntime.getAdminInterface() != null && sourcePort.getFlag(FrameworkElementFlags.SHARED)) {
                if (legacySourceRuntime && legacyDestinationRuntime) {
                    result = destinationRuntime.getAdminInterface().networkConnect(destinationPort, "", RemoteRuntime.find(sourcePort).uuid, sourcePort.getRemoteHandle(), sourcePort.getUid(), disconnect);
                } else if ((!disconnect) && (!legacyDestinationRuntime)) {
                    URI uri = new URI("tcp", null, SmartConnecting.getUriPath(sourcePort), null);
                    org.finroc.core.net.generic_protocol.Definitions.setServerSideConversionOptions(destinationConnectOptions, connectOptions, serializedType);
                    result = destinationRuntime.getAdminInterface().createUriConnector(destinationPort, uri.normalize().toString(), destinationConnectOptions);
                }
            }
        }
        if (result != null && result.length() > 0) {
            throw new Exception(actionString + " failed: " + result);
        }
    }

    @Override
    protected void executeImplementation() throws Exception {
        connectImplementation(false, "Connecting");
    }


    /**
     * @return Remote connect options. null if they have not been set.
     */
    public RemoteConnectOptions getConnectOptions() {
        return connectOptions;
    }

    /**
     * @return Conversion rating of connection to create
     */
    public Definitions.TypeConversionRating getConversionRating() {
        return (connectOptions != null) ? connectOptions.conversionRating : Definitions.TypeConversionRating.NO_CONVERSION;
    }

    @Override
    public String getDescriptionForEditMenu() {
        return "Connect " + getReadableLinkForMenu(sourceLink);
    }

    /**
     * @return Fully qualified name of destination port
     */
    public String getDestinationLink() {
        return destinationLink;
    }

    /**
     * @return Remote connect options in destination runtime if this is connecting ports in two different runtimes. null if they have not been set.
     */
    public RemoteConnectOptions getDestinationConnectOptions() {
        return destinationConnectOptions;
    }

    /**
     * @return Destination port. null if is currently not available
     */
    public RemotePort getDestinationPort() {
        ModelNode destinationNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(destinationLink, LINK_SEPARATOR);
        return (destinationNode != null && destinationNode instanceof RemotePort) ? (RemotePort)destinationNode : null;
    }

    /**
     * @return Destination type name
     */
    public String getDestinationTypeName() {
        return destinationTypeName;
    }

    /**
     * @return Fully qualified name of source port
     */
    public String getSourceLink() {
        return sourceLink;
    }

    /**
     * @return Source port. null if is currently not available
     */
    public RemotePort getSourcePort() {
        ModelNode sourceNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(sourceLink, LINK_SEPARATOR);
        return (sourceNode != null && sourceNode instanceof RemotePort) ? (RemotePort)sourceNode : null;
    }

    /**
     * @return Source type name
     */
    public String getSourceTypeName() {
        return sourceTypeName;
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        return new DisconnectAction(sourceLink, destinationLink, sourceTypeName, destinationTypeName, connectOptions, false);
    }

    /**
     * Set variant when ports in the same runtime are connected
     *
     * @param connectOptions Connect options to be used
     */
    public void setConnectOptions(RemoteConnectOptions connectOptions) {
        this.connectOptions = connectOptions;
    }

    /**
     * Set variant when ports in different runtimes are connected
     *
     * @param connectOptions Connect options to be used in source runtime
     * @param serializedType Type serialized/sent over the network
     * @param destinationConnectOptions Connect options to be used in destination runtime
     */
    public void setConnectOptions(RemoteConnectOptions sourceConnectOptions, String serializedType, RemoteConnectOptions destinationConnectOptions) {
        this.connectOptions = sourceConnectOptions;
        this.serializedType = serializedType;
        this.destinationConnectOptions = destinationConnectOptions;
    }


    /** Qualified links to source and destination ports to connect */
    private String sourceLink, destinationLink;

    /** Names of source and destination types */
    private String sourceTypeName, destinationTypeName;

    /** Remote connect options if they have been set */
    private RemoteConnectOptions connectOptions;

    /** Remote connect options in destination runtime if this is connecting ports in two different runtimes. null if they have not been set */
    private RemoteConnectOptions destinationConnectOptions;

    /** Type serialized/sent over the network if this is connecting ports in two different runtimes. null if it has not been set */
    private String serializedType;

}
