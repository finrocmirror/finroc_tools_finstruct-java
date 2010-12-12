/**
 * You received this file as part of FinGUI - a universal
 * (Web-)GUI editor for Robotic Systems.
 *
 * Copyright (C) 2010 Max Reichardt
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
package org.finroc.finstruct.propertyeditor;

import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.cc.CCPortBase;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.portdatabase.TypedObject;

/**
 * @author max
 *
 * Provides PropertyAccessor adapter for ports.
 * It creates an extra port that is connected to port to wrap.
 */
public class ConnectingPortAccessor<T extends TypedObject> extends PortAccessor<T> {

    /** Wrapped partner (network) port */
    protected final AbstractPort partner;

    public ConnectingPortAccessor(AbstractPort partner, String rootName) {
        super((partner instanceof PortBase) ? new PortBase(createPci(partner)) : new CCPortBase(createPci(partner)), "");
        this.partner = partner;
        name = partner.getQualifiedLink().substring(rootName.length() + 1);
    }

    /**
     * @param partner Partner port
     * @return port creation info
     */
    protected static PortCreationInfo createPci(AbstractPort partner) {
        PortCreationInfo pci = new PortCreationInfo(partner.getDescription() + "-panel");
        pci.dataType = partner.getDataType();
        if (partner.isOutputPort()) {
            pci.flags = PortFlags.INPUT_PORT;
        } else {
            pci.flags = PortFlags.ACCEPTS_REVERSE_DATA_PUSH | PortFlags.OUTPUT_PORT;
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
        if (wrapped.isOutputPort()) {
            wrapped.connectToTarget(partner);
        } else {
            wrapped.connectToSource(partner);
        }
        //setAutoUpdate(false);
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
