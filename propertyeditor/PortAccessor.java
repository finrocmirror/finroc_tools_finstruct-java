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

import java.lang.annotation.Annotation;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortListener;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.cc.CCPortBase;
import org.finroc.core.port.cc.CCPortDataManager;
import org.finroc.core.port.cc.CCPortDataManagerTL;
import org.finroc.core.port.rpc.FutureStatus;
import org.finroc.core.port.rpc.Method;
import org.finroc.core.port.rpc.ResponseHandler;
import org.finroc.core.port.std.PortBase;
import org.finroc.core.port.std.PortDataManager;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.PortDataListImpl;
import org.rrlib.serialization.Serialization;
import org.rrlib.serialization.rtti.DataTypeBase;
import org.rrlib.serialization.rtti.GenericObject;

/**
 * @author Max Reichardt
 *
 * Provides PropertyAccessor adapter for ports.
 * It creates an extra port that is connected to port to wrap.
 */
@SuppressWarnings("rawtypes")
public class PortAccessor<T> implements PropertyAccessor<T>, PortListener {

    /** port to get and set data */
    protected final AbstractPort wrapped;

    /** Name of element relative to which to display port name */
    protected String name;

    /** Are we registered as listener at port? */
    private boolean listening = false;

    /** Port accessor listener */
    protected Listener listener;

    protected final ErrorPrinter errorPrinter = new ErrorPrinter();


    /**
     * @param wrapped Wrapped partner (network) port
     * @param rootName Name of element relative to which to display port name
     */
    public PortAccessor(AbstractPort wrapped, String rootName) {
        assert(wrapped instanceof PortBase || wrapped instanceof CCPortBase);
        this.wrapped = wrapped;
        name = wrapped.getQualifiedLink().substring(rootName.length() + 1);
    }

    /**
     * Constructor for derived classes
     *
     * @param wrapped Wrapped port, may be null
     */
    protected PortAccessor(AbstractPort wrapped) {
        this.wrapped = wrapped;
    }


    @SuppressWarnings("unchecked")
    @Override
    public Class<T> getType() {
        return (wrapped.getDataType().getJavaClass() == null && (wrapped.getDataType().getTypeClassification() == DataTypeBase.CLASSIFICATION_LIST) ? (Class<T>)PortDataListImpl.class : (Class<T>)wrapped.getDataType().getJavaClass());
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() throws Exception {
        if (!wrapped.isReady()) {
            return null;
        }
        if (wrapped instanceof PortBase) {
            PortBase pb = (PortBase)wrapped;
            PortDataManager pd = pb.getLockedUnsafeRaw();
            T result = Serialization.deepCopy((T)pd.getObject().getData());
            pd.releaseLock();
            return result;
        } else {
            CCPortBase cpb = (CCPortBase)wrapped;
            GenericObject pd = cpb.getAutoLockedRaw();
            T result = Serialization.deepCopy((T)pd.getData());
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
            if (ap.getFlag(FrameworkElementFlags.NETWORK_ELEMENT)) {
                CCPortDataManager c = ThreadLocalCache.get().getUnusedInterThreadBuffer(DataTypeBase.findType(newValue.getClass(), ap.getDataType()));
                Serialization.deepCopy(newValue, c.getObject().getData());
                RemoteRuntime.find(RemotePort.get(ap)[0]).getAdminInterface().setRemotePortValue(RemotePort.get(ap)[0], c.getObject(), errorPrinter);
            } else {
                CCPortDataManagerTL c = ThreadLocalCache.get().getUnusedBuffer(DataTypeBase.findType(newValue.getClass(), ap.getDataType()));
                Serialization.deepCopy(newValue, c.getObject().getData());
                ((CCPortBase)wrapped).publish(c);
            }
        } else {
            PortDataManager result = PortDataManager.create((newValue instanceof EnumValue) ? ((EnumValue)newValue).getType() :
                                     ((newValue instanceof PortDataListImpl) ? ((PortDataListImpl)newValue).getElementType().getListType() : DataTypeBase.findType(newValue.getClass(), ap.getDataType())));
            Serialization.deepCopy(newValue, result.getObject().getData(), null);
            if (ap.getFlag(FrameworkElementFlags.NETWORK_ELEMENT)) {
                RemoteRuntime.find(RemotePort.get(ap)[0]).getAdminInterface().setRemotePortValue(RemotePort.get(ap)[0], result.getObject(), errorPrinter);
            } else {
                ((PortBase)wrapped).publish(result);
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

    class ErrorPrinter implements ResponseHandler {

        @Override
        public void handleResponse(Method method, Object r) {
            if (r != null) {
                if (r.toString().length() > 0) {
                    printError(r.toString());
                }
            }
        }

        @Override
        public void handleException(Method method, FutureStatus mce) {
            printError(mce.toString());
        }

        public void printError(String s) {
            Finstruct.showErrorMessage("Setting port value of '" + portForSetting().getQualifiedLink() + "' failed: " + s, false, false);
        }
    }
}
