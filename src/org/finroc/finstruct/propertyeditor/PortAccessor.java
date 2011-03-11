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

import java.lang.annotation.Annotation;

import org.finroc.core.CoreFlags;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.cc.CCPortBase;
import org.finroc.core.port.cc.CCPortDataManager;
import org.finroc.core.port.cc.CCPortDataManagerTL;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.port.std.PortDataManager;
import org.finroc.gui.util.propertyeditor.ObjectCloner;
import org.finroc.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.serialization.DataTypeBase;
import org.finroc.serialization.GenericObject;
import org.finroc.serialization.RRLibSerializable;
import org.finroc.serialization.Serialization;

/**
 * @author max
 *
 * Provides PropertyAccessor adapter for ports.
 * It creates an extra port that is connected to port to wrap.
 */
@SuppressWarnings("rawtypes")
public class PortAccessor<T extends RRLibSerializable> implements PropertyAccessor<T>, PortListener {

    /** port to get and set data */
    protected final AbstractPort wrapped;

    /** Name of element relative to which to display port name */
    protected String name;

    /** Are we registered as listener at port? */
    private boolean listening = false;

    /** Port accessor listener */
    private Listener listener;

    static {
        TypedObjectCloner.register();
    }

    /**
     * @param wrapped Wrapped partner (network) port
     * @param rootName Name of element relative to which to display port name
     */
    public PortAccessor(AbstractPort wrapped, String rootName) {
        assert(wrapped instanceof PortBase || wrapped instanceof CCPortBase);
        this.wrapped = wrapped;
        name = wrapped.getQualifiedLink().substring(rootName.length() + 1);

    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<T> getType() {
        return (Class<T>)wrapped.getDataType().getJavaClass();
    }

    @Override
    public T get() throws Exception {
        if (!wrapped.isReady()) {
            return null;
        }
        if (wrapped instanceof PortBase) {
            PortBase pb = (PortBase)wrapped;
            PortDataManager pd = pb.getLockedUnsafeRaw();
            T result = ObjectCloner.clone(pd.getObject().<T>getData());
            pd.releaseLock();
            return result;
        } else {
            CCPortBase cpb = (CCPortBase)wrapped;
            GenericObject pd = cpb.getAutoLockedRaw();
            T result = ObjectCloner.clone(pd.<T>getData());
            ThreadLocalCache.get().releaseAllLocks();
            return result;
        }
    }

    @Override
    public void set(T newValue) throws Exception {
        AbstractPort ap = portForSetting();
        if (!ap.isReady()) {
            throw new Exception("Port not ready");
        }
        if (ap instanceof CCPortBase) {
            if (ap.getFlag(CoreFlags.NETWORK_ELEMENT)) {
                CCPortDataManager c = ThreadLocalCache.get().getUnusedInterThreadBuffer(DataTypeBase.findType(newValue.getClass()));
                Serialization.deepCopy(newValue, c.getObject().<T>getData(), null);
                RemoteRuntime.find(ap).getAdminInterface().setRemotePortValue(ap.asNetPort(), c);
            } else {
                CCPortDataManagerTL c = ThreadLocalCache.get().getUnusedBuffer(DataTypeBase.findType(newValue.getClass()));
                Serialization.deepCopy(newValue, c.getObject().<T>getData(), null);
                ((CCPortBase)ap).publish(c);
            }
        } else {
            PortBase pb = (PortBase)ap;
            //PortData pd = (PortData)newValue;
            PortDataManager result = PortDataManager.create(DataTypeBase.findType(newValue.getClass()));
            Serialization.deepCopy(newValue, result.getObject().<T>getData(), null);
            if (ap.getFlag(CoreFlags.NETWORK_ELEMENT)) {
                RemoteRuntime.find(ap).getAdminInterface().setRemotePortValue(ap.asNetPort(), result);
            } else {
                pb.publish(result);
            }
        }
    }

    /**
     * @return Is wrapped port remote (so that we should use admin interface to set it?)
     */
    protected AbstractPort portForSetting() {
        return wrapped;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> ann) {
        return null;
    }

    /**
     * Port accessor listener
     */
    public interface Listener {

        /** Called whenever port value changes */
        public void portChanged();
    }

    /**
     * @return Port accessor listener
     */
    public Listener getListener() {
        return listener;
    }

    /**
     * @param listener Port accessor listener
     */
    public void setListener(Listener listener) {
        if ((!listening) && listener != null) {
            listening = true;
            if (wrapped instanceof PortBase) {
                PortBase port = (PortBase)wrapped;
                port.addPortListenerRaw(this);
            } else {
                CCPortBase port = (CCPortBase)wrapped;
                port.addPortListenerRaw(this);
            }
        }
        this.listener = listener;
    }

    @Override
    public void portChanged(AbstractPort origin, Object value) {
        if (listener != null) {
            listener.portChanged();
        }
    }

    @Override
    public boolean isModifiable() {
        return true;
    }
}
