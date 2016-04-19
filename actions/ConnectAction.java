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

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.SmartConnecting;


/**
 * @author Max Reichardt
 *
 * Action connecting two ports.
 */
public class ConnectAction extends FinstructAction {

    /**
     * @param source Fully qualified name of source
     * @param destination Fully qualified name of destination
     */
    public ConnectAction(String source, String destination) {
        this.sourceLink = source;
        this.destinationLink = destination;
    }

    @Override
    protected void executeImplementation() throws Exception {
        connectImplementation(false, "Connecting");
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
        if (((RemotePort)sourceNode).isConnectedTo((RemotePort)destinationNode)) {
            return "";
        }
        return null;
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        return new DisconnectAction(sourceLink, destinationLink);
    }

    @Override
    public String getDescriptionForEditMenu() {
        return "Connect " + getReadableLinkForMenu(sourceLink);
    }

    /**
     * Implementation of connect and disconnect
     *
     * @param disconnect Disconnect (instead of connecting)
     * @param actionString Description of action (e.g. for error messages). First letter should be capital.
     */
    protected void connectImplementation(boolean disconnect, String actionString) throws Exception {
        ModelNode sourceNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(sourceLink, LINK_SEPARATOR);
        ModelNode destinationNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(destinationLink, LINK_SEPARATOR);
        if (sourceNode == null && destinationNode == null) {
            throw new Exception(actionString + " failed: Neither '" + sourceLink + "' nor '" + destinationLink + "' found");
        }
        if (sourceNode == null) {
            throw new Exception(actionString + " failed: '" + sourceLink + "' not found");
        }
        if (!(sourceNode instanceof RemotePort)) {
            throw new Exception(actionString + " failed: '" + sourceLink + "' is not a port");
        }
        if (destinationNode == null) {
            throw new Exception(actionString + " failed: '" + destinationLink + "' not found");
        }
        if (!(destinationNode instanceof RemotePort)) {
            throw new Exception(actionString + " failed: '" + destinationLink + "' is not a port");
        }

        RemotePort sourcePort = (RemotePort)sourceNode;
        RemotePort destinationPort = (RemotePort)destinationNode;
        String reason = SmartConnecting.mayConnectDirectly(sourcePort, destinationPort, true);
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

        if (sourceRuntime != null && sourceRuntime == destinationRuntime) {

            if (disconnect) {
                sourceRuntime.getAdminInterface().disconnect(sourcePort.getPort().asNetPort(), destinationPort.getPort().asNetPort());
            } else {
                //if (!sourcePort.isConnectedTo(destinationPort)) {  // removed in order to avoid issues with any parallel incomplete actions
                sourceRuntime.getAdminInterface().connect(sourcePort.getPort().asNetPort(), destinationPort.getPort().asNetPort());
            }
        } else {
            String result = "No port is shared";
            if (sourceRuntime.getAdminInterface() != null && destinationPort.getFlag(FrameworkElementFlags.SHARED)) {
                result = sourceRuntime.getAdminInterface().networkConnect(sourcePort.getPort().asNetPort(), "", RemoteRuntime.find(destinationPort.getPort().asNetPort()).uuid, destinationPort.getRemoteHandle(), destinationPort.getUid(), false);
            }
            if (result != null && destinationRuntime.getAdminInterface() != null && sourcePort.getFlag(FrameworkElementFlags.SHARED)) {
                result = destinationRuntime.getAdminInterface().networkConnect(destinationPort.getPort().asNetPort(), "", RemoteRuntime.find(sourcePort.getPort().asNetPort()).uuid, sourcePort.getRemoteHandle(), sourcePort.getUid(), false);
            }
            if (result != null) {
                throw new Exception(actionString + " failed: " + result);
            }
        }
    }

    /** Qualified links to source and destination ports to connect */
    private String sourceLink, destinationLink;
}
