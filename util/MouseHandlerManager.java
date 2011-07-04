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
package org.finroc.tools.finstruct.util;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

/**
 * @author max
 *
 * Manages set of mouse handlers
 */
public class MouseHandlerManager implements MouseListener, MouseMotionListener {

    /** List with current mouse handlers */
    private List<MouseHandler> mouseHandlers = new ArrayList<MouseHandler>();

    /** Currently active handler (handler is active while mouse button is pressed on it) */
    private MouseHandler activeHandler;

    /** Handler that mouse is currently over */
    private MouseHandler mouseOver;

    /** Component that owns this manager */
    private final JComponent owner;

    /** Zoom */
    private double zoom = 1;

    public MouseHandlerManager(JComponent owner) {
        this.owner = owner;
        owner.addMouseListener(this);
        owner.addMouseMotionListener(this);
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        MouseHandler mh = mouseOver;
        if (mh != null) {
            activeHandler = mh;
            statusChange(mouseOver, mh);
        }
    }

    /**
     * Helper method for convenience. Calls statusChanged() on non-null parameters
     *
     * @param m1 parameter 1
     * @param m2 parameter 2
     */
    private static void statusChange(MouseHandler m1, MouseHandler m2) {
        if (m1 != null) {
            m1.statusChanged();
        }
        if (m2 != null) {
            m2.statusChanged();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (activeHandler != null) {
            MouseHandler tmp = activeHandler;
            activeHandler = null;
            statusChange(mouseOver, tmp);
            tmp.mouseReleased(e, mouseOver);
        }
        mouseMoved(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        MouseHandler mh = getMouseHandler(e);
        if (mh != mouseOver) {
            MouseHandler tmp = mouseOver;
            mouseOver = mh;
            statusChange(tmp, mh);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        MouseHandler mh = getMouseHandler(e);
        if (mh != mouseOver) {
            MouseHandler tmp = mouseOver;
            mouseOver = mh;
            statusChange(tmp, mh);
        }
        if (mh != null) {
            Cursor c = mh.getCursor();
            if (c != null) {
                owner.setCursor(c);
                return;
            }
        }
        owner.setCursor(Cursor.getDefaultCursor());
    }

    /**
     * Get Mouse handler for this event
     *
     * @param e Event
     * @return Mouse Handler
     */
    public MouseHandler getMouseHandler(MouseEvent e) {
        Point p = e.getPoint();
        if (zoom != 1.0) {
            p = new Point((int)(e.getX() / zoom), (int)(e.getY() / zoom));
        }
        for (MouseHandler mh : mouseHandlers) {
            if (mh.handlesPoint(p)) {
                return mh;
            }
        }
        return null;
    }

    /**
     * Clear all handlers
     */
    public void clear() {
        mouseHandlers.clear();
    }

    /**
     * @param mh Mouse Handler to add
     * @param first Insert at front of list?
     */
    public void add(MouseHandler mh, boolean first) {
        if (first) {
            mouseHandlers.add(0, mh);
        } else {
            mouseHandlers.add(mh);
        }
    }

    /**
     * @return Currently active handler (handler is active while mouse button is pressed on it)
     */
    public MouseHandler getActiveHandler() {
        return activeHandler;
    }

    /**
     * @return Handler that mouse is currently over
     */
    public MouseHandler getMouseOver() {
        return mouseOver;
    }

    /**
     * @param zoom Zoom
     */
    public void setZoom(double zoom) {
        this.zoom = zoom;
    }
}
