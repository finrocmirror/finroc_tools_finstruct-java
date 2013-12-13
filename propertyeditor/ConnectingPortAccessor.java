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
package org.finroc.tools.finstruct.propertyeditor;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.remote.RemotePort;
import org.rrlib.serialization.BinarySerializable;

/**
 * @author Max Reichardt
 *
 * Provides PropertyAccessor adapter for ports.
 * It creates an extra port that is connected to port to wrap.
 */
public class ConnectingPortAccessor<T extends BinarySerializable> extends PortAccessor<T> {

    /** Wrapped partner (network) port */
    protected final AbstractPort partner;

    public ConnectingPortAccessor(RemotePort partner, String rootName) {
        //super((partner.getPort() instanceof PortBase) ? new PortBase(createPci(partner.getPort())) : new CCPortBase(createPci(partner.getPort())), "");
        super(new PortBase(createPci(partner.getPort()))); // we only have standard ports in finstruct
        this.partner = partner.getPort();
        name = partner.getQualifiedName('/').substring(rootName.length() + 1);
    }

    /**
     * Constructor for derived classes
     */
    protected ConnectingPortAccessor(String portLink, String rootName) {
        super(null);
        partner = null;
        name = portLink.substring(rootName.length() + 1);
    }

    /**
     * @param partner Partner port
     * @return port creation info
     */
    protected static PortCreationInfo createPci(AbstractPort partner) {
        PortCreationInfo pci = new PortCreationInfo(partner.getName() + "-panel");
        pci.dataType = partner.getDataType();
        if (partner.isOutputPort()) {
            pci.flags = FrameworkElementFlags.INPUT_PORT;
        } else {
            pci.flags = FrameworkElementFlags.OUTPUT_PORT | FrameworkElementFlags.PUSH_STRATEGY_REVERSE;
        }
        return pci;
    }

    /** delete panel */
    public void delete() {
        wrapped.managedDelete();
    }

    /** Init port */
    public void init() {
        wrapped.init();
        wrapped.connectTo(partner, wrapped.isOutputPort() ? AbstractPort.ConnectDirection.TO_TARGET : AbstractPort.ConnectDirection.TO_SOURCE, false);
    }

    /**
     * Sets auto-update for panel
     *
     * @param b value to set
     */
    public void setAutoUpdate(boolean b) {
        if (wrapped.isOutputPort()) {
            wrapped.setReversePushStrategy(b);
        } else {
            wrapped.setPushStrategy(b);
        }
    }

    @Override
    protected AbstractPort portForSetting() {
        return partner;
    }

    /**
     * @return Wrapped port
     */
    public AbstractPort getPort() {
        return wrapped;
    }
}
