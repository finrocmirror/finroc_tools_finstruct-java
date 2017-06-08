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

import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteConnectOptions;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;


/**
 * @author Max Reichardt
 *
 * Action disconnecting two ports.
 */
public class DisconnectAction extends ConnectAction {

    /**
     * @param source Fully qualified name of source port
     * @param destination Fully qualified name of destination port
     * @param sourceTypeName Name of source port data type
     * @param destinationTypeName Name of destination port data type
     * @param currentConnectOptions Connect Options of current connection (required for undo)
     * @param reverse Whether destinationLink refers to the actual source port
     */
    public DisconnectAction(String sourceLink, String destinationLink, String sourceTypeName, String destinationTypeName, RemoteConnectOptions currentConnectOptions, boolean reverse) {
        super(sourceLink, destinationLink, sourceTypeName, destinationTypeName, new RemoteConnectOptions(), reverse);
        this.currentConnectOptions = currentConnectOptions;
    }

    @Override
    protected void executeImplementation() throws Exception {
        connectImplementation(true, "Disonnecting");
    }

    @Override
    protected String checkSuccessImplementation() {
        ModelNode sourceNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(super.getSourceLink(), LINK_SEPARATOR);
        if (!(sourceNode instanceof RemotePort)) {
            return "";
        }
        ModelNode destinationNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(super.getDestinationLink(), LINK_SEPARATOR);
        if (!(destinationNode instanceof RemotePort)) {
            return "";
        }
        if (!RemoteRuntime.arePortsConnected((RemotePort)sourceNode, (RemotePort)destinationNode)) {
            return "";
        }
        return null;
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        return new ConnectAction(super.getSourceLink(), super.getDestinationLink(), super.getSourceTypeName(), super.getDestinationTypeName(), currentConnectOptions, false);
    }

    @Override
    public String getDescriptionForEditMenu() {
        return "Disconnect " + getReadableLinkForMenu(super.getSourceLink());
    }


    /** Connect Options of current connection (required for undo) */
    private RemoteConnectOptions currentConnectOptions;
}
