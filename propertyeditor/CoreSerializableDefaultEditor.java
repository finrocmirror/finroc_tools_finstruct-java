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
package org.finroc.tools.finstruct.propertyeditor;

import org.finroc.core.portdatabase.MaxStringSerializationLength;
import org.finroc.tools.gui.util.propertyeditor.StringEditor;

/**
 * @author max
 *
 * Default text editor for CoreSerializable objects
 */
public class CoreSerializableDefaultEditor extends StringEditor {

    /** UID */
    private static final long serialVersionUID = -4413682261110284958L;

    /** Finroc Data type */
    //private DataType type;

    public CoreSerializableDefaultEditor(Class<?> cl) {
        super(scanForStringLength(cl, true));
        //type = dt;
    }

    /**
     * Scans for string length of specified class
     *
     * @param c Class
     * @param initialCall First level of recursion?
     * @return String length parameter (Integer.MIN_VALUE if no parameter could be found)
     */
    private static int scanForStringLength(Class<?> c, boolean initialCall) {
        MaxStringSerializationLength len = c.getAnnotation(MaxStringSerializationLength.class);
        if (len != null) {
            return len.value();
        } else {
            if (c.getSuperclass() != null) {
                int result = scanForStringLength(c.getSuperclass(), false);
                if (result > Integer.MIN_VALUE) {
                    return result;
                }
            }
            for (Class<?> iface : c.getInterfaces()) {
                int result = scanForStringLength(iface, false);
                if (result > Integer.MIN_VALUE) {
                    return result;
                }
            }
        }
        return initialCall ? 0 : Integer.MIN_VALUE;
    }
}
