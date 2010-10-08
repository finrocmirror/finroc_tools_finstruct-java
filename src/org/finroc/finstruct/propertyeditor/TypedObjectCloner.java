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

import org.finroc.core.buffer.CoreInput;
import org.finroc.core.buffer.CoreOutput;
import org.finroc.core.buffer.MemBuffer;
import org.finroc.core.port.cc.CCPortData;
import org.finroc.core.port.std.PortDataCreationInfo;
import org.finroc.core.portdatabase.CoreSerializable;
import org.finroc.core.portdatabase.TypedObject;
import org.finroc.gui.util.propertyeditor.CloneHandler;
import org.finroc.gui.util.propertyeditor.ObjectCloner;
import org.finroc.jc.log.LogDefinitions;
import org.finroc.log.LogDomain;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 */
public class TypedObjectCloner implements CloneHandler {

    /** Already registered? */
    private static boolean registered = false;

    /** may only be accessed in synchronized context */
    private static final MemBuffer buffer = new MemBuffer();

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
        return o instanceof TypedObject;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T clone(T t) {
        return (T)cloneHelper((CoreSerializable)t, null);
    }

    @SuppressWarnings("unchecked")
    public synchronized static <T extends CoreSerializable> T cloneHelper(T t, T result) {
        try {
            if (t == null) {
                return null;
            }
            if (result == null) {
                result = (T)t.getClass().newInstance();
                PortDataCreationInfo.get().initUnitializedObjects();
            }
            if (result instanceof CCPortData) {
                ((CCPortData)result).assign((CCPortData)t);
            } else {
                buffer.clear();
                CoreOutput co = new CoreOutput(buffer);
                t.serialize(co);
                co.close();
                CoreInput ci = new CoreInput(buffer);
                result.deserialize(ci);
                ci.close();
            }
            return result;
        } catch (Exception e) {
            logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
            return null;
        }
    }

    private static String getLogDescription() {
        return "TypedObjectCloner";
    }


}
