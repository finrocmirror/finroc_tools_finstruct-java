/**
 * You received this file as part of FinGUI - a universal
 * (Web-)GUI editor for Robotic Systems.
 *
 * Copyright (C) 2011 Max Reichardt
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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import org.finroc.core.datatype.CoreString;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.core.port.rpc.MethodCallException;
import org.finroc.core.port.rpc.method.AbstractMethod;
import org.finroc.core.port.rpc.method.AsyncReturnHandler;
import org.finroc.core.port.std.PortDataManager;

/**
 * @author max
 *
 * Connects to ports with unknown data type (uses string serialization)
 */
public class UnknownTypePortAccessor extends ConnectingPortAccessor<CoreString> implements ActionListener, AsyncReturnHandler<CoreString> {

    /** Port to work with */
    private final NetPort np;

    /** Timer that trigges updates */
    private Timer timer = new Timer(UPDATE_RATE, this);

    /** Current value as string */
    private volatile CoreString currentString = new CoreString();

    /** Error message when value could not be received */
    private static final CoreString ERROR_STRING = new CoreString();

    /** Update rate */
    private static final int UPDATE_RATE = 2000;

    static {
        ERROR_STRING.set("(Error retrieving value)");
    }

    public UnknownTypePortAccessor(NetPort remotePort, String rootName) {
        super(remotePort.getPort().getQualifiedLink(), rootName);
        np = remotePort;
        timer.setRepeats(true);
    }

    @Override
    public void delete() {}

    @Override
    public void init() {
        actionPerformed(null);
    }

    @Override
    public synchronized void setAutoUpdate(boolean b) {
        if (b) {
            actionPerformed(null);
            timer.start();
        } else {
            timer.stop();
        }
    }

    @Override
    protected AbstractPort portForSetting() {
        return null;
    }

    @Override
    public AbstractPort getPort() {
        return null;
    }

    @Override
    public Class<CoreString> getType() {
        return CoreString.class;
    }

    @Override
    public CoreString get() throws Exception {
        return currentString;
    }

    @Override
    public void set(CoreString newValue) throws Exception {
        currentString = newValue;
        RemoteRuntime.find(np.getPort()).getAdminInterface().setRemotePortValue(np, np.getPort().getDataType(), newValue.toString());
    }

    @Override
    public boolean isModifiable() {
        return true;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (np.getPort().isReady()) {
            RemoteRuntime.find(np.getPort()).getAdminInterface().getRemotePortValue(np, this);
        }
    }

    @Override
    public void handleReturn(AbstractMethod method, CoreString r) {
        if (r != null) {
            currentString = new CoreString();
            currentString.set(r.toString());
            PortDataManager.getManager(r).releaseLock();
            super.portChanged(np.getPort(), currentString);
        }
    }

    @Override
    public void handleMethodCallException(AbstractMethod method, MethodCallException mce) {
        currentString = ERROR_STRING;
        super.portChanged(np.getPort(), currentString);
    }

    @Override
    public void setListener(org.finroc.tools.finstruct.propertyeditor.PortAccessor.Listener listener) {
        this.listener = listener;
    }
}
