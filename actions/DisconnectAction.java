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
import org.finroc.core.remote.RemotePort;
import org.finroc.tools.finstruct.Finstruct;


/**
 * @author Max Reichardt
 *
 * Action disconnecting two ports.
 */
public class DisconnectAction extends ConnectAction {

    /**
     * @param source Fully qualified name of source
     * @param destination Fully qualified name of destination
     */
    public DisconnectAction(String source, String destination) {
        super(source, destination);
    }

    @Override
    protected void executeImplementation() throws Exception {
        connectImplementation(true, "Disonnecting");
    }

    @Override
    protected String checkSuccessImplementation() {
        ModelNode sourceNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(sourceLink, LINK_SEPARATOR);
        if (!(sourceNode instanceof RemotePort)) {
            return "";
        }
        ModelNode destinationNode = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(destinationLink, LINK_SEPARATOR);
        if (!(destinationNode instanceof RemotePort)) {
            return "";
        }
        if (!((RemotePort)sourceNode).isConnectedTo((RemotePort)destinationNode)) {
            return "";
        }
        return null;
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        return new ConnectAction(sourceLink, destinationLink);
    }

    @Override
    public String getDescriptionForEditMenu() {
        return "Disonnect " + getReadableLinkForMenu(sourceLink);
    }

    /** Qualified links to source and destination ports to connect */
    private String sourceLink, destinationLink;
}
