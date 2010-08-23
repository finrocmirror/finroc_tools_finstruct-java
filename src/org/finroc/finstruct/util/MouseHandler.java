/**
 * You received this file as part of Finstruct - a tool for
 * the Finroc Framework.
 *
 * Copyright (C) 2010 Robotics Research Lab, University of Kaiserslautern
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
package org.finroc.finstruct.util;

import java.awt.Cursor;
import java.awt.Point;

/**
 * @author max
 *
 * Handles mouse events in particular area of the screen
 */
public interface MouseHandler {

    /**
     * Does mouse handler handle this point?
     *
     * @param p Point
     * @return Answer
     */
    public boolean handlesPoint(Point p);

    /**
     * @return Mouse cursor for area in which handler is active - null for default cursor
     */
    public Cursor getCursor();

    /**
     * Called whenever mouse-over (=selection?) or activation status changed
     */
    public void statusChanged();
}
