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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.tools.finstruct.Finstruct.Mode;
import org.finroc.tools.finstruct.views.AbstractGraphView;
import org.finroc.tools.finstruct.views.Ib2cView;
import org.finroc.tools.finstruct.views.PortView;
import org.finroc.tools.finstruct.views.StandardViewGraphViz;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.rrlib.finroc_core_utils.jc.log.LogDefinitions;
import org.rrlib.finroc_core_utils.log.LogDomain;

/**
 * @author max
 *
 * Base Window class
 */
public class FinstructWindow extends JFrame implements ActionListener {

    /** UID */
    private static final long serialVersionUID = -7929615678892958956L;

    /** Reference to finstruct main window */
    protected Finstruct finstruct;

    /** History navigation buttons */
    private JButton back, next;

    /** Available finstruct views */
    protected ArrayList<ViewSelector> views = new ArrayList<ViewSelector>();

    /** Current finstruct view */
    private FinstructView currentView;

    /** Menu bar */
    protected JMenuBar menuBar = new JMenuBar();

    /** Toolbar */
    protected transient MToolBar toolBar;

    /** Log domain for this class */
    public static final LogDomain logDomain = LogDefinitions.finroc.getSubDomain("finstruct");

    /** History of views */
    protected ArrayList<HistoryElement> history = new ArrayList<HistoryElement>();

    /** Current history position - if user hasn't moved backwards: history.size() - 1 */
    protected int historyPos = -1;

    /** Menu item */
    private JRadioButtonMenuItem miAutoView;

    public FinstructWindow(Finstruct f) {
        finstruct = f;

        // find views
        ButtonGroup viewSelectGroup = new ButtonGroup();
        views.add(new ViewSelector(StandardViewGraphViz.class, viewSelectGroup));
        views.add(new ViewSelector(PortView.class, viewSelectGroup));
        views.add(new ViewSelector(Ib2cView.class, viewSelectGroup));

        // view menu
        JMenu menuView = new JMenu("View");
        menuView.setMnemonic(KeyEvent.VK_V);
        menuBar.add(menuView);
        miAutoView = new JRadioButtonMenuItem("Auto Select", true);
        viewSelectGroup.add(miAutoView);
        menuView.add(miAutoView);
        miAutoView.addActionListener(this);
        menuView.addSeparator();
        for (ViewSelector view : views) {
            menuView.add(view);
        }
        setJMenuBar(menuBar);

        // Toolbar
        toolBar = new MToolBar("Standard");
        toolBar.setFloatable(false);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolBar, BorderLayout.PAGE_START);
    }

    /**
     * Changes view
     *
     * @param view View to change to
     * @param restoreRoot call setRootElement with old root on new View?
     */
    protected void changeView(FinstructView view, boolean restoreRoot) {
        view.finstruct = (finstruct != null) ? finstruct : (Finstruct)this;
        view.finstructWindow = this;

        // Clear menu bar
        for (int i = menuBar.getComponentCount() - 1; i >= 0; i--) {
            if (menuBar.getComponent(i) instanceof JMenu) {
                String name = ((JMenu)menuBar.getComponent(i)).getText();
                if (name.equals("File") || name.equals("View") || name.equals("Edit")) {
                    continue;
                }
            }
            menuBar.remove(i);
        }

        // Clear toolbar and add default navigation buttons
        Mode lastMode = toolBar.getSelection(Mode.values());
        toolBar.clear();
        back = toolBar.createButton("back-ubuntu.png", "To last view", this);
        next = toolBar.createButton("forward-ubuntu.png", "To next view", this);
        if (this instanceof Finstruct) {
            toolBar.addSeparator();
            toolBar.addToggleButton(new MAction(Mode.navigate, null, "Navigate", this));
            toolBar.addToggleButton(new MAction(Mode.connect, null, "Connect", this));
            toolBar.addToggleButton(new MAction(Mode.paramconnect, null, "Parameter-Connect", this));
            toolBar.setSelected(lastMode == null ? Mode.navigate : lastMode);
        }
        toolBar.addSeparator();
        toolBar.startNextButtonGroup();

        ModelNode lastRoot = currentView == null ? null : currentView.getRootElement();
        currentView = view;

        // reinit tool and menu bar with view's entries
        view.initMenuAndToolBar(menuBar, toolBar);
        toolBar.revalidate();
        toolBar.getParent().repaint();

        if (!(this instanceof Finstruct)) {
            getContentPane().removeAll();
            getContentPane().add(toolBar, BorderLayout.PAGE_START);
            getContentPane().add(new JScrollPane(getCurrentView()), BorderLayout.CENTER);
        }

        // update menu
        /*if (!miAutoView.isSelected()) {
            for (ViewSelector v : views) {
                if (v.view.equals(view.getClass())) {
                    v.setSelected(true);
                    break;
                }
            }
        }*/

        // restore root element
        if (lastRoot != null && restoreRoot) {
            setViewRootElement(lastRoot, null);
        }

        updateHistoryButtonState();
    }

    public void setViewRootElement(ModelNode root, ArrayList<ModelNode> expandedElements) {
        currentView.setRootElement(root, expandedElements);
        if (!(this instanceof Finstruct)) {
            setTitle(root.getQualifiedName('/'));
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        try {
            Object src = ae.getSource();
            if (src == next) {
                showHistoryElement(historyPos + 1);
            } else if (src == back) {
                showHistoryElement(historyPos - 1);
            }
            //requestFocus();
        } catch (Exception e) {
            Finstruct.showErrorMessage(e, true);
        }
    }

    /**
     * Shows specified framework element in main view - taking view selection into account
     *
     * @param fe Framework element to show
     */
    public void showElement(ModelNode fe) {
        boolean portViewCandidate = AbstractGraphView.hasOnlyPortChildren(fe, false) &&
                                    (!((fe instanceof RemoteFrameworkElement) && ((RemoteFrameworkElement)fe).getFlag(FrameworkElementFlags.FINSTRUCTABLE_GROUP)));
        if (miAutoView.isSelected() || portViewCandidate) {
            // auto-select view
            Class <? extends FinstructView > viewClass = portViewCandidate ? PortView.class : StandardViewGraphViz.class;
            if (currentView == null || currentView.getClass() != viewClass) {
                try {
                    changeView(viewClass.newInstance(), false);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            setViewRootElement(fe, null);
        } else {
            Class <? extends FinstructView > selectedView = null;
            for (ViewSelector v : views) {
                if (v.isSelected()) {
                    selectedView = v.view;
                    break;
                }
            }

            if (currentView == null || (!currentView.getClass().equals(selectedView))) {
                try {
                    changeView(selectedView.newInstance(), false);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            setViewRootElement(fe, null);
        }

        // create history element for this view
        pushViewToHistory();
    }

    /**
     * Save current view as next history element
     */
    public void pushViewToHistory() {
        if (currentView.getRootElement() == null) {
            return;
        }
        HistoryElement he = new HistoryElement(currentView.getClass(), currentView.getRootElement());
        Collection <? extends ModelNode > expanded = currentView.getExpandedElementsForHistory();
        if (expanded != null) {
            for (ModelNode fe : expanded) {
                he.expandedElements.add(fe.getQualifiedName('/'));
            }
        }
        if (history.size() == 0 || historyPos <= 0 || (!history.get(historyPos).equals(he))) {
            // remove any "next" history elements
            while ((historyPos + 1) < history.size()) {
                history.remove(historyPos + 1);
            }
            history.add(he);
            historyPos = history.size() - 1;
        }
        updateHistoryButtonState();
    }

    /**
     * Show element stored in history
     *
     * @param newHistoryElement Index of element in history to show
     */
    private void showHistoryElement(int newHistoryElement) {
        historyPos = newHistoryElement;
        HistoryElement he = history.get(historyPos);
        ModelNode root = finstruct.ioInterface.getChildByQualifiedName(he.root, HISTORY_SEPARATOR);
        if (root == null) {
            return;
        }
        ArrayList<ModelNode> expanded = new ArrayList<ModelNode>();
        for (String e : he.expandedElements) {
            ModelNode fe = finstruct.ioInterface.getChildByQualifiedName(e, HISTORY_SEPARATOR);
            if (fe != null) {
                expanded.add(fe);
            }
        }

        if (currentView == null || currentView.getClass() != he.view) {
            try {
                changeView(he.view.newInstance(), false);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        setViewRootElement(root, expanded);

        updateHistoryButtonState();
    }

    /**
     * @return Current finstruct view
     */
    public FinstructView getCurrentView() {
        return currentView;
    }

    /**
     * Update state of history buttons
     */
    public void updateHistoryButtonState() {
        next.setEnabled(historyPos < history.size() - 1);
        back.setEnabled(historyPos > 0);
    }

    /**
     * @return the toolBar
     */
    public MToolBar getToolBar() {
        return toolBar;
    }

    /**
     * Menu Item that selects a different view
     */
    class ViewSelector extends JRadioButtonMenuItem implements ActionListener {

        /** UID */
        private static final long serialVersionUID = 5767567242843313612L;

        Class <? extends FinstructView > view;

        ViewSelector(Class <? extends FinstructView > view, ButtonGroup group) {
            super(view.getSimpleName());
            group.add(this);
            this.view = view;
            addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            pushViewToHistory();
            try {
                changeView(view.newInstance(), true);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    /** Separator for qualified names used in history */
    static final char HISTORY_SEPARATOR = (char)0;

    /**
     * Element of view history
     */
    public class HistoryElement {

        /** View used */
        Class <? extends FinstructView > view;

        /** Root framework element */
        String root;

        /** Expanded/Selected framework element entries */
        ArrayList<String> expandedElements = new ArrayList<String>();


        public HistoryElement(Class <? extends FinstructView > view, ModelNode root) {
            this.view = view;
            this.root = root.getQualifiedName(HISTORY_SEPARATOR);
        }

        public boolean equals(Object other) {
            if (other == null || (!(other instanceof HistoryElement))) {
                return false;
            }
            HistoryElement he = (HistoryElement)other;
            if (view != he.view || (!root.equals(he.root)) || expandedElements.size() != he.expandedElements.size()) {
                return false;
            }
            for (int i = 0; i < expandedElements.size(); i++) {
                if (!expandedElements.get(i).equals(he.expandedElements.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
