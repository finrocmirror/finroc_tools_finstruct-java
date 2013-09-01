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

import org.finroc.core.portdatabase.SerializationHelper;
import org.finroc.tools.gui.util.propertyeditor.CloneHandler;
import org.finroc.tools.gui.util.propertyeditor.ObjectCloner;
import org.rrlib.finroc_core_utils.jc.log.LogDefinitions;
import org.rrlib.finroc_core_utils.log.LogDomain;
import org.rrlib.finroc_core_utils.serialization.RRLibSerializable;

/**
 * @author Max Reichardt
 *
 */
public class TypedObjectCloner implements CloneHandler {

    /** Already registered? */
    private static boolean registered = false;

    /** Log domain for this class */
    public static final LogDomain logDomain = LogDefinitions.finroc.getSubDomain("property_editor");

    /**
     * Register cloner at ObjectCloner, if this didn't happen already
     */
    public static synchronized void register() {
        if (!registered) {
            ObjectCloner.registerCloner(new TypedObjectCloner(), true);
            registered = true;
        }
    }

    @Override
    public boolean handles(Object o) {
        return o instanceof RRLibSerializable;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T clone(T t) {
        return (T)SerializationHelper.deepCopy((RRLibSerializable)t, null);
    }
}
