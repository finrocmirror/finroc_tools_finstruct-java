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

package org.finroc.tools.finstruct;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import org.finroc.core.FrameworkElement;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.rrlib.finroc_core_utils.log.LogDomain;

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

    public static final LogDomain logDomain = Finstruct.logDomain;

    Finstruct finstruct;
    FinstructWindow finstructWindow;

    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Add entries to menu bar and tool bar (optional)
     *
     * @param menuBar Menu bar (already) containing Finstruct's menu entries
     * @param toolBar Tool bar (already) containing Finstruct's standard entries
     */
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {}

    /**
     * Initialize contents of left side of window vertical divider
     * (default is connectionPanel)
     *
     * @param connectionPanel Connection Panel
     * @return Content to put on left side of window vertical divider
     */
    public JComponent initLeftPanel(FinstructConnectionPanel connectionPanel) {
        return connectionPanel;
    }

    /**
     * Called after root element changed
     * (Typically view will display a new graph for this element)
     *
     * @param expandedElements Expanded element - may be null
     */
    protected abstract void rootElementChanged(ArrayList<FrameworkElement> expandedElements);

    /**
     * @param root Root element of view
     * @param expandedElements
     */
    void setRootElement(FrameworkElement root, ArrayList<FrameworkElement> expandedElements) {
        if (rootElement != root) {
            rootElement = root;
            rootElementChanged(expandedElements);
        }
    }

    /**
     * @return Root element of view
     */
    public FrameworkElement getRootElement() {
        return rootElement;
    }

    public String getLogDescription() {
        return getClass().getSimpleName();
    }

    /**
     * Refresh view
     */
    public void refresh() {}

    /**
     * @return Reference to finstruct main window
     */
    public Finstruct getFinstruct() {
        return finstruct;
    }

    /**
     * @return Reference to finstruct parent window
     */
    public FinstructWindow getFinstructWindow() {
        return finstructWindow;
    }

    /**
     * @return Any expanded/selected elements for storing view state in history
     */
    public abstract Collection <? extends FrameworkElement > getExpandedElementsForHistory();
}
