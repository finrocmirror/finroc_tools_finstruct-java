//
// You received this file as part of Finroc
// A Framework for intelligent robot control
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//----------------------------------------------------------------------
package org.finroc.tools.finstruct.propertyeditor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.parameter.StaticParameterBase;
import org.finroc.core.parameter.StaticParameterList;
import org.finroc.tools.gui.util.propertyeditor.ObjectCloner;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.rrlib.finroc_core_utils.serialization.RRLibSerializable;
import org.rrlib.finroc_core_utils.serialization.Serialization;

/**
 * @author Max Reichardt
 *
 * Accessor for StaticParameters
 */
@SuppressWarnings("rawtypes")
public class StaticParameterAccessor implements PropertyAccessor {

    /** Wrapped parameter */
    protected final StaticParameterBase wrapped;

    public StaticParameterAccessor(StaticParameterBase wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Class getType() {
        if (wrapped.getType() == null) {
            return String.class;
        } else {
            return wrapped.getType().getJavaClass();
        }
    }

    @Override
    public Object get() throws Exception {
        return ObjectCloner.clone(wrapped.valPointer().getData());
    }

    @Override
    public void set(Object newValue) throws Exception {
        if (newValue instanceof RRLibSerializable) {
            wrapped.set(Serialization.serialize((RRLibSerializable)newValue));
        } else {
            assert(newValue instanceof String);
            wrapped.set(newValue.toString());
        }
    }

    @Override
    public String getName() {
        return wrapped.getName();
    }

    @Override
    public Annotation getAnnotation(Class ann) {
        return null;
    }

    /**
     * @param spl Static parameter list
     * @return List of accessors for list
     */
    public static List < PropertyAccessor<? >> createForList(StaticParameterList spl) {
        ArrayList < PropertyAccessor<? >> result = new ArrayList < PropertyAccessor<? >> ();
        for (int i = 0; i < spl.size(); i++) {
            result.add(new StaticParameterAccessor(spl.get(i)));
        }
        return result;
    }

    @Override
    public boolean isModifiable() {
        return true;
    }
}
