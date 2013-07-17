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

package org.finroc.tools.finstruct;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import org.finroc.core.remote.ModelNode;
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
    private ModelNode rootElement;

    /** Qualified name of root element */
    private String rootElementQualifiedName;

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
    protected abstract void rootElementChanged(ArrayList<ModelNode> expandedElements);

    /**
     * Called every 200ms by Java Swing Thread
     * Can be overridden to perform checks whether current view is still up to date
     */
    protected void updateView() {}

    /**
     * @param root Root element of view
     * @param expandedElements
     */
    void setRootElement(ModelNode root, ArrayList<ModelNode> expandedElements) {
        if (rootElement != root) {
            rootElement = root;
            rootElementQualifiedName = rootElement.getQualifiedName((char)1);
            rootElementChanged(expandedElements);
        }
    }

    /**
     * @return Root element of view
     */
    public ModelNode getRootElement() {
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
     * @return Is there (still) an active connection to the current root node?
     *         If the remote part is terminated, this is no longer the case.
     */
    public boolean isConnectedToRootNode() {
        return rootElement != null && rootElement.isNodeAncestor(finstruct.getIoInterface().getRoot());
    }

    /**
     * If root node is not connected, search for a new (restarted) part containing
     * the same node - and set view root to this.
     */
    public void tryReconnectingToRootNode() {
        if (rootElement != null && (!isConnectedToRootNode())) {
            ModelNode newNode = finstruct.getIoInterface().getChildByQualifiedName(rootElementQualifiedName, (char)1);
            if (newNode != null && newNode != rootElement) {
                setRootElement(newNode, null);
            }
        }
    }

    /**
     * @return Any expanded/selected elements for storing view state in history
     */
    public abstract Collection <? extends ModelNode > getExpandedElementsForHistory();

    /**
     * Called when view is no longer needed and discarded.
     * Any ports in use by view should be deleted in order to avoid leaks.
     */
    protected void destroy() {}
}
