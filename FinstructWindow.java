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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.StringReader;
import java.util.ArrayList;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

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
import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.xml.XMLDocument;
import org.rrlib.finroc_core_utils.xml.XMLNode;
import org.xml.sax.InputSource;

/**
 * @author Max Reichardt
 *
 * Base Window class
 */
public class FinstructWindow extends JFrame implements ActionListener, WindowListener, MenuListener {

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

    /** History of views (XML view configuration) */
    protected ArrayList<String> history = new ArrayList<String>();

    /** Current history position - if user hasn't moved backwards: history.size() */
    protected int historyPos = 0;

    /** Bookmark menu */
    private JMenu bookmarkMenu;

    /** View menu item */
    private JRadioButtonMenuItem miAutoView;

    /** Bookmark menu items */
    private JMenuItem miAddBookmark, miRemoveBookmark;

    /**
     * Timer for periodic checking if current view is still up to date
     * Causes FinstructView.checkViewUpToDate() to be called every 200ms
     */
    private final Timer periodicViewCheckTimer = new Timer(200, this);

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

        // bookmark menu
        bookmarkMenu = new JMenu("Bookmarks");
        bookmarkMenu.setMnemonic(KeyEvent.VK_B);
        miAddBookmark = createMenuEntry("Add Bookmark", bookmarkMenu, KeyEvent.VK_A);
        miRemoveBookmark = createMenuEntry("Remove Bookmark", bookmarkMenu, KeyEvent.VK_R);
        bookmarkMenu.addMenuListener(this);
        menuBar.add(bookmarkMenu);

        setJMenuBar(menuBar);

        // Toolbar
        toolBar = new MToolBar("Standard");
        toolBar.setFloatable(false);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolBar, BorderLayout.PAGE_START);

        // Init periodic view update
        periodicViewCheckTimer.start();
        addWindowListener(this);
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
                if (name.equals("File") || name.equals("View") || name.equals("Edit") || name.equals("Bookmarks")) {
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
        if (currentView != null) {
            currentView.destroy();
        }
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

    public void setViewRootElement(ModelNode root, XMLNode viewConfiguration) {
        currentView.setRootElement(root, viewConfiguration);
        if (!(this instanceof Finstruct)) {
            setTitle(root.getQualifiedName('/'));
        }
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == periodicViewCheckTimer) {
            //System.out.println("ViewUpdate " + this);
            if (currentView != null) {
                currentView.updateView();
            }
            return;
        }
        try {
            Object src = ae.getSource();
            if (src == next) {
                showHistoryElement(historyPos + 1);
            } else if (src == back) {
                int historyPosTemp = historyPos;
                if (historyPos == history.size()) {
                    pushViewToHistory();
                }
                showHistoryElement(historyPosTemp - 1);
            } else if (src == miAddBookmark || src == miRemoveBookmark) {
                // Remove current view from bookmarks
                XMLNode bookmarkNode = finstruct.getSettings().getTopLevelNode("bookmarks");
                for (XMLNode.ConstChildIterator bookmark = bookmarkNode.getChildrenBegin(); bookmark.get() != null; bookmark.next()) {
                    try {
                        if (bookmark.get().getName().equals("bookmark")) {
                            String name = bookmark.get().getStringAttribute("root");
                            String viewName = bookmark.get().getStringAttribute("view");
                            Class <? extends FinstructView > viewClass = getViewClassByName(viewName);
                            if (getCurrentView().getRootElement() != null && name.equals(getCurrentView().getRootElement().getQualifiedName('/')) && viewClass.equals(getCurrentView().getClass())) {
                                bookmarkNode.removeChildNode(bookmark.get());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Finstruct.logDomain.log(LogLevel.LL_WARNING, "Could not parse bookmark entry: " + bookmark.get().getXMLDump(true), e);
                    }
                }

                if (src == miAddBookmark && currentView != null && currentView.getRootElement() != null) {
                    XMLNode bookmark = bookmarkNode.addChildNode("bookmark");
                    storeCurrentView(bookmark);
                    finstruct.getSettings().saveSettings();
                }
            }
            //requestFocus();
        } catch (Exception e) {
            Finstruct.showErrorMessage(e, true);
        }
    }

    @Override
    public void menuSelected(MenuEvent event) {
        if (event.getSource() == bookmarkMenu) {
            // Remove everything except of add and remove bookmark entries
            bookmarkMenu.removeAll();
            bookmarkMenu.add(miAddBookmark);
            bookmarkMenu.add(miRemoveBookmark);

            miAddBookmark.setEnabled(getCurrentView().getRootElement() != null);
            miRemoveBookmark.setEnabled(false);

            ArrayList<BookmarkSelector> bookmarks = new ArrayList<BookmarkSelector>();

            XMLNode bookmarkNode = finstruct.getSettings().getTopLevelNode("bookmarks");
            for (XMLNode.ConstChildIterator bookmark = bookmarkNode.getChildrenBegin(); bookmark.get() != null; bookmark.next()) {
                try {
                    if (bookmark.get().getName().equals("bookmark")) {
                        String name = bookmark.get().getStringAttribute("root");
                        String viewName = bookmark.get().getStringAttribute("view");
                        Class <? extends FinstructView > viewClass = getViewClassByName(viewName);
                        ModelNode root = finstruct.ioInterface.getChildByQualifiedName(name, '/');

                        if (getCurrentView().getRootElement() != null && name.equals(getCurrentView().getRootElement().getQualifiedName('/')) && viewClass.equals(getCurrentView().getClass())) {
                            miRemoveBookmark.setEnabled(true);
                        }

                        String rootSimpleName = name.substring(name.lastIndexOf('/') + 1);
                        String rootPath = name.substring(0, name.lastIndexOf('/'));

                        bookmarks.add(new BookmarkSelector(name, "<html><b>" + rootSimpleName + "</b> in <i>" + rootPath + "</i> (" + viewClass.getSimpleName() + ")</html>",
                                                           viewClass, bookmark.get(), viewClass != null && root != null));
                    }
                } catch (Exception e) {
                    Finstruct.logDomain.log(LogLevel.LL_WARNING, "Could not parse bookmark entry: " + bookmark.get().getXMLDump(true), e);
                }
            }

            boolean first = true;
            for (BookmarkSelector bookmark : bookmarks) {
                if (bookmark.isEnabled()) {
                    if (first) {
                        bookmarkMenu.addSeparator();
                    }
                    bookmarkMenu.add(bookmark);
                }
            }
            first = true;
            for (BookmarkSelector bookmark : bookmarks) {
                if (!bookmark.isEnabled()) {
                    if (first) {
                        bookmarkMenu.addSeparator();
                    }
                    bookmarkMenu.add(bookmark);
                }
            }
        }
    }

    @Override
    public void menuDeselected(MenuEvent e) {}

    @Override
    public void menuCanceled(MenuEvent e) {}


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
            pushViewToHistory(); // create history element for current view
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
            pushViewToHistory(); // create history element for current view
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
    }

    /**
     * Save current view as next history element
     */
    public void pushViewToHistory() {
        if (currentView.getRootElement() == null) {
            return;
        }

        try {
            XMLDocument document = new XMLDocument();
            document.addRootNode("history");
            storeCurrentView(document.getRootNode());
            String viewData = document.getXMLDump(false);
            if (history.size() == 0 || historyPos <= 0 || (!history.get(historyPos - 1).equals(viewData))) {
                // remove any "next" history elements
                while ((historyPos) < history.size()) {
                    history.remove(historyPos);
                }
                history.add(viewData);
                historyPos = history.size();
            }
            updateHistoryButtonState();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Show element stored in history
     *
     * @param newHistoryElement Index of element in history to show
     */
    private void showHistoryElement(int newHistoryElement) {
        historyPos = newHistoryElement;
        try {
            XMLNode viewData = new XMLDocument(new InputSource(new StringReader(history.get(historyPos))), false).getRootNode();
            ModelNode root = finstruct.ioInterface.getChildByQualifiedName(viewData.getStringAttribute("root"), '/');
            Class <? extends FinstructView > viewClass = getViewClassByName(viewData.getStringAttribute("view"));

            if (currentView == null || currentView.getClass() != viewClass) {
                try {
                    changeView(viewClass.newInstance(), false);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }

            setViewRootElement(root, viewData);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
     * Convenient method the create menu entries and add this Window as listener
     *
     * @param string Text of menu entry
     * @param menuFile Menu to add menu entry to
     * @return Create menu entry
     */
    protected JMenuItem createMenuEntry(String string, JMenu menuFile, int mnemonic) {
        JMenuItem item = new JMenuItem(string, mnemonic);
        item.addActionListener(this);
        menuFile.add(item);
        return item;
    }

    /**
     * @param viewClassName Name of view class
     * @return View class with this name - or null if no such view exists
     */
    private Class <? extends FinstructView > getViewClassByName(String viewClassName) {
        for (ViewSelector view : views) {
            if (view.view.getSimpleName().equals(viewClassName)) {
                return view.view;
            }
        }
        return null;
    }

    /**
     * Stores current view configuration to provided XML node so that it can be restored later
     *
     * @param node Node to store current view configuration to
     */
    private void storeCurrentView(XMLNode node) {
        if (currentView != null) {
            node.setAttribute("view", currentView.getClass().getSimpleName());
            node.setAttribute("root", currentView.getRootElement().getQualifiedName('/'));
            currentView.storeViewConfiguration(node);
        }
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

    class BookmarkSelector extends JMenuItem implements ActionListener {

        /** UID */
        private static final long serialVersionUID = 8754721481877765029L;

        final String name;
        final Class <? extends FinstructView > view;
        final XMLNode xmlNode;

        public BookmarkSelector(String name, String menuEntryString, Class <? extends FinstructView > view, XMLNode xmlNode, boolean enable) {
            super(menuEntryString);
            this.name = name;
            this.view = view;
            this.xmlNode = xmlNode;
            this.setEnabled(enable);
            addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ModelNode root = finstruct.ioInterface.getChildByQualifiedName(name, '/');
            if (root != null) {
                pushViewToHistory();
            }
            try {
                changeView(view.newInstance(), false);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            getCurrentView().setRootElement(root, xmlNode);
        }
    }

    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
    @Override public void windowClosing(WindowEvent e) {
        if (currentView != null) {
            currentView.destroy();
        }
        periodicViewCheckTimer.stop();
    }
}
