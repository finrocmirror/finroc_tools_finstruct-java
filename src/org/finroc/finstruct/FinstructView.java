/**
 * You received this file as part of FinGUI - a universal
 * (Web-)GUI editor for Robotic Systems.
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
package org.finroc.finstruct;

import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import org.finroc.core.FrameworkElement;
import org.finroc.gui.ConnectionPanel;

/**
 * @author max
 *
 * Base class for all views in Finstruct
 *
 * (Note that this class is a JComponent and will be displayed on the right side of the window vertical divider)
 */
public abstract class FinstructView extends JPanel {

    /** UID */
    private static final long serialVersionUID = -957994679491024743L;

    /** Root element of view */
    private FrameworkElement rootElement;

    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Add entries to menu bar (optional)
     *
     * @param menuBar Menu bar containing Finstruct's menu entries
     */
    public void initMenuBar(JMenuBar menuBar) {}

    /**
     * Initialize contents of left side of window vertical divider
     * (default is connectionPanel)
     *
     * @param connectionPanel Connection Panel
     * @return Content to put on left side of window vertical divider
     */
    public JComponent initLeftPanel(ConnectionPanel connectionPanel) {
        return connectionPanel;
    }

    /**
     * Called after root element changed
     * (Typically view will display a new graph for this element)
     */
    protected abstract void rootElementChanged();

    /**
     * @param root Root element of view
     */
    void setRootElement(FrameworkElement root) {
        if (rootElement != root) {
            rootElement = root;
            rootElementChanged();
        }
    }

    /**
     * @return Root element of view
     */
    public FrameworkElement getRootElement() {
        return rootElement;
    }
}
