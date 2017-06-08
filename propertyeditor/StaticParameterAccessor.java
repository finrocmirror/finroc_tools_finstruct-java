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
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.datatype.CoreString;
import org.finroc.core.parameter.StaticParameterBase;
import org.finroc.core.parameter.StaticParameterList;
import org.finroc.core.remote.RemoteStaticParameterList;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.rrlib.serialization.Serialization;

/**
 * @author Max Reichardt
 *
 * Accessor for StaticParameters
 */
@SuppressWarnings("rawtypes")
public class StaticParameterAccessor implements PropertyAccessor {

    /** Wrapped parameter */
    protected final RemoteStaticParameterList.Parameter wrapped;

    protected final String namePrefix;

    public StaticParameterAccessor(RemoteStaticParameterList.Parameter wrapped, String namePrefix) {
        this.wrapped = wrapped;
        this.namePrefix = namePrefix;
    }

    @Override
    public Class getType() {
        if (wrapped.getType() == null) {
            return String.class;
        } else {
            return wrapped.getType().getDefaultLocalDataType().getJavaClass();
        }
    }

    @Override
    public Object get() throws Exception {
        return Serialization.deepCopy(wrapped.getValue().getData());
    }

    @Override
    public void set(Object newValue) throws Exception {
        wrapped.setValue(newValue);
    }

    @Override
    public String getName() {
        return namePrefix + wrapped.getName();
    }

    @Override
    public Annotation getAnnotation(Class ann) {
        return null;
    }

    /**
     * @param spl Static parameter list
     * @param namePrefix Prefix prepended to name of each parameter name
     * @return List of accessors for list
     */
    public static List < PropertyAccessor<? >> createForList(RemoteStaticParameterList spl, String namePrefix) {
        ArrayList < PropertyAccessor<? >> result = new ArrayList < PropertyAccessor<? >> ();
        for (int i = 0; i < spl.size(); i++) {
            result.add(new StaticParameterAccessor(spl.get(i), namePrefix));
        }
        return result;
    }
    public static List < PropertyAccessor<? >> createForList(RemoteStaticParameterList spl) {
        return createForList(spl, "");
    }

    @Override
    public boolean isModifiable() {
        return true;
    }
}
