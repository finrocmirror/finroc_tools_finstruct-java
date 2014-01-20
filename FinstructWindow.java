//
// You received this file as part of Finroc
// A framework for intelligent robot control
//
// Copyright (C) Finroc GbR (finroc.org)
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//----------------------------------------------------------------------
package org.finroc.tools.finstruct;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.admin.AdministrationService;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.views.AbstractGraphView;
import org.finroc.tools.finstruct.views.Ib2cView;
import org.finroc.tools.finstruct.views.PortView;
import org.finroc.tools.finstruct.views.StandardViewGraphViz;
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
public class FinstructWindow extends JFrame implements ActionListener, WindowListener, MenuListener, MouseListener, CaretListener, KeyListener {

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

    /** Text field containing address of current view */
    private AddressField addressField;

    /** True while setText is called on addressField */
    private boolean settingAddressFieldText;

    /** Last address field text (to detect if something changed) */
    private String lastAddressFieldText = "";

    /** Last time the user clicked on the address field */
    private long lastAddressFieldClick;

    /** Toolbar buttons */
    private JButton refreshButton, start, pause;

    /** Search Thread (for address field) */
    private SearchThread searchThread = new SearchThread();

    /**
     * Timer for periodic checking if current view is still up to date
     * Causes FinstructView.checkViewUpToDate() to be called every 200ms
     */
    protected final Timer periodicViewCheckTimer = new Timer(200, this);

    /** Popup menu for address field */
    private final JPopupMenu popupMenu = new JPopupMenu();

    /** List component for popup menu above */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private final JList addressList = new JList();

    /** Any view instances that were created in this window */
    private final HashMap < Class <? extends FinstructView > , FinstructView > viewInstances = new HashMap < Class <? extends FinstructView > , FinstructView > ();

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
        toolBar.setBorder(BorderFactory.createEmptyBorder(2, 2, 1, 3));
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolBar, BorderLayout.PAGE_START);

        // popup menu
        popupMenu.setLayout(new BorderLayout());
        popupMenu.add(addressList, BorderLayout.CENTER);
        this.addKeyListener(this);

        // Init periodic view update
        periodicViewCheckTimer.start();

        addWindowListener(this);
        addressList.addMouseListener(this);
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
        toolBar.clear();
        toolBar.setLayout(new BoxLayout(toolBar, BoxLayout.LINE_AXIS));
        back = toolBar.createButton("back-ubuntu.png", "To last view", this);
        next = toolBar.createButton("forward-ubuntu.png", "To next view", this);
        addressField = new AddressField("Search or enter address");
        addressField.setCaretPosition(0);
        addressField.setForeground(Color.lightGray);
        addressField.addMouseListener(this);
        addressField.addCaretListener(this);
        addressField.addKeyListener(this);
        toolBar.add(addressField);
        refreshButton = toolBar.createButton("reload-ubuntu.png", "Refresh view", this);

        toolBar.addSeparator();
        start = toolBar.createButton("player_play-ubuntu.png", "Start/Resume execution", this);
        start.setEnabled(false);
        pause = toolBar.createButton("player_pause-ubuntu.png", "Pause/Stop execution", this);
        pause.setEnabled(false);
        toolBar.addSeparator();

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
            setViewRootElement(lastRoot, null, true);
        }

        updateHistoryButtonState();
    }

    public void setViewRootElement(ModelNode root, XMLNode viewConfiguration, boolean forceReload) {
        currentView.setRootElement(root, viewConfiguration, forceReload);
        String qualifiedName = root.getQualifiedName('/');
        String address = qualifiedName.substring(Math.min(qualifiedName.length(), "Interfaces/".length()));
        if (!(this instanceof Finstruct)) {
            setTitle(address);
        }
        updateStartPauseEnabled();
        addressField.setForeground(Color.black);
        lastAddressFieldText = address;
        settingAddressFieldText = true;
        addressField.setText(address);
        settingAddressFieldText = false;
    }

    /**
     * @param viewClass Desired view class
     * @return View instance of desired class (any view of that class already created - otherwise new instance)
     */
    public FinstructView getViewInstance(Class <? extends FinstructView > viewClass) {
        FinstructView result = viewInstances.get(viewClass);
        if (result == null) {
            try {
                result = viewClass.newInstance();
                viewInstances.put(viewClass, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == periodicViewCheckTimer) {
            //System.out.println("ViewUpdate " + this);
            if (currentView != null) {
                currentView.updateView();
            }
            return;
        } else if (ae.getSource() == refreshButton) {
            if (currentView != null) {
                setViewRootElement(currentView.getRootElement(), null, true);
            }
            return;
        } else if (ae.getSource() == start || ae.getSource() == pause) {
            if (currentView != null && currentView.getRootElement() != null) {
                RemoteRuntime rr = RemoteRuntime.find(currentView.getRootElement());
                if (rr == null) {
                    Finstruct.showErrorMessage("Root Element is not a child of a remote runtime", false, false);
                } else if (currentView.getRootElement() instanceof RemoteFrameworkElement) {
                    if (ae.getSource() == start) {
                        rr.getAdminInterface().startExecution(((RemoteFrameworkElement)currentView.getRootElement()).getRemoteHandle());
                    } else {
                        rr.getAdminInterface().pauseExecution(((RemoteFrameworkElement)currentView.getRootElement()).getRemoteHandle());
                    }
                    updateStartPauseEnabled();
                }
            }
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
                        Finstruct.logDomain.log(LogLevel.WARNING, "Could not parse bookmark entry: " + bookmark.get().getXMLDump(true), e);
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
                        boolean enabled = viewClass != null && root != null;
                        String color = enabled ? "000000" : "909090";

                        bookmarks.add(new BookmarkSelector(name,
                                                           "<html><font color=#" + color + "><b>" + rootSimpleName + "</b> in <i>" + rootPath + "</i> (" + viewClass.getSimpleName() + ")</font></html>",
                                                           viewClass, bookmark.get(), enabled));
                    }
                } catch (Exception e) {
                    Finstruct.logDomain.log(LogLevel.WARNING, "Could not parse bookmark entry: " + bookmark.get().getXMLDump(true), e);
                }
            }

            boolean first = true;
            for (BookmarkSelector bookmark : bookmarks) {
                if (bookmark.isEnabled()) {
                    if (first) {
                        bookmarkMenu.addSeparator();
                        first = false;
                    }
                    bookmarkMenu.add(bookmark);
                }
            }
            first = true;
            for (BookmarkSelector bookmark : bookmarks) {
                if (!bookmark.isEnabled()) {
                    if (first) {
                        bookmarkMenu.addSeparator();
                        first = false;
                    }
                    bookmarkMenu.add(bookmark);
                }
            }
        }
    }

    /**
     * Update whether start and pause buttons are enabled
     */
    public void updateStartPauseEnabled() {
        if (getCurrentView() == null || currentView.getRootElement() == null) {
            return;
        }
        RemoteRuntime rr = RemoteRuntime.find(currentView.getRootElement());
        if (rr == null) {
            start.setEnabled(false);
            pause.setEnabled(false);
            return;
        }
        try {
            AdministrationService.ExecutionStatus executing = rr.getAdminInterface().isExecuting(((RemoteFrameworkElement)currentView.getRootElement()).getRemoteHandle());
            start.setEnabled(executing == AdministrationService.ExecutionStatus.PAUSED || executing == AdministrationService.ExecutionStatus.BOTH);
            pause.setEnabled(executing == AdministrationService.ExecutionStatus.RUNNING || executing == AdministrationService.ExecutionStatus.BOTH);
        } catch (Exception e) {
            start.setEnabled(false);
            pause.setEnabled(false);
        }
    }

    @Override
    public void menuDeselected(MenuEvent e) {}

    @Override
    public void menuCanceled(MenuEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getSource() == addressField) {
            long time = System.currentTimeMillis();
            if (time - lastAddressFieldClick < Finstruct.DOUBLE_CLICK_DELAY) {
                addressField.selectAll();
                lastAddressFieldClick = 0;
            } else {
                lastAddressFieldClick = time;
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getSource() == addressList) {
            try {
                loadAddress(((SearchThread.SearchResult)(addressList.getSelectedValue())).link);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}

    @Override
    public void caretUpdate(CaretEvent e) {
        if (e.getSource() == addressField) {
            if ((!settingAddressFieldText) && (!lastAddressFieldText.equals(addressField.getText()))) {
                lastAddressFieldText = addressField.getText();
                searchThread.newSearchRequest(lastAddressFieldText, false);
            }
        }
    }

    @Override public void keyPressed(KeyEvent e) {
        //System.out.println(e.getKeyCode());
    }

    @Override public void keyReleased(KeyEvent e) {
//        System.out.println("1 " + e.getKeyCode());
//        int ctrlMask = KeyEvent.CTRL_DOWN_MASK;
//        int shiftAltMask = KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
//
//        // Control pressed?
//        if ((e.getModifiersEx() & (ctrlMask | shiftAltMask)) == ctrlMask) {
//            if (e.getKeyCode() == KeyEvent.VK_F) {
//                new FindElementDialog(this, false).show(this);
//            }
//        }
//      System.out.println("typed:3 " + addressField.getText());
    }

    @Override public void keyTyped(KeyEvent e) {
//        System.out.println("2 " + e.getKeyCode());
        if (e.getSource() == addressField) {
            if (addressField.getForeground() == Color.lightGray) {
                addressField.setText("");
                addressField.setForeground(Color.black);
            }
        }
    }

    /**
     * Loads address originating from address field
     *
     * @param address Address to load (null to load current text of address field)
     */
    private void loadAddress(String address) {
        if (address == null) {
            address = addressField.getText();
        } else {
            settingAddressFieldText = true;
            addressField.setText(address);
            settingAddressFieldText = false;
            lastAddressFieldText = addressField.getText();
            addressField.requestFocusInWindow();
        }
        popupMenu.setVisible(false);
        if (!address.startsWith("/")) {
            address = "/" + address;
        }
        ModelNode node = finstruct.getIoInterface().getChildByQualifiedName("Interfaces" + address, '/');
        if (node != null) {
            pushViewToHistory();
            if (currentView != null && currentView.getRootElement() == node) {
                setViewRootElement(node, null, true);
            } else {
                showElement(node);
            }
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
        portViewCandidate |= (fe instanceof RemotePort);
        if (miAutoView.isSelected() || portViewCandidate) {
            // auto-select view
            Class <? extends FinstructView > viewClass = portViewCandidate ? PortView.class : StandardViewGraphViz.class;
            pushViewToHistory(); // create history element for current view
            if (currentView == null || currentView.getClass() != viewClass) {
                changeView(getViewInstance(viewClass), false);
            }
            setViewRootElement(fe, null, false);
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
                changeView(getViewInstance(selectedView), false);
            }
            setViewRootElement(fe, null, false);
        }
    }

    /**
     * Save current view as next history element
     */
    public void pushViewToHistory() {
        if (currentView == null || currentView.getRootElement() == null) {
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
                changeView(getViewInstance(viewClass), false);
            }

            setViewRootElement(root, viewData, true);
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
            changeView(getViewInstance(view), true);
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
            changeView(getViewInstance(view), false);
            setViewRootElement(root, xmlNode, true);

            if (finstruct.getConnectionPanel() != null) {
                finstruct.getConnectionPanel().expandOnly(true, root);
            }
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

    /**
     * Address field component
     */
    class AddressField extends JTextField {

        /** UID */
        private static final long serialVersionUID = 5563572568684859617L;

        public AddressField(String string) {
            super(string);
        }

//      @Override
//      protected void processComponentKeyEvent(KeyEvent e) {
//          super.pr
//      }

        @Override
        protected void processKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED && (e.getKeyCode() == KeyEvent.VK_KP_DOWN || e.getKeyCode() == KeyEvent.VK_DOWN)) {
                if (!popupMenu.isVisible()) {
                    lastAddressFieldText = addressField.getText();
                    searchThread.newSearchRequest(lastAddressFieldText, true);
                } else if (addressList.getSelectedIndex() < 0) {
                    addressList.setSelectedIndex(0);
                } else {
                    if (addressList.getSelectedIndex() + 1 < addressList.getModel().getSize()) {
                        addressList.setSelectedIndex(addressList.getSelectedIndex() + 1);
                    }
                }
                return;
            } else if (e.getID() == KeyEvent.KEY_PRESSED && (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP)) {
                if (addressList.getSelectedIndex() > 0) {
                    addressList.setSelectedIndex(addressList.getSelectedIndex() - 1);
                } else {
                    addressList.setSelectedIndices(new int[0]);
                    //addressList.setSelectedIndex(-1);
                }
                return;
            } else if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyChar() == '\n') {
                if (popupMenu.isVisible() && addressList.getSelectedIndex() >= 0) {
                    loadAddress(((SearchThread.SearchResult)(addressList.getSelectedValue())).link);
                } else {
                    loadAddress(null);
                }
                return;
            }


            super.processKeyEvent(e);
        }

    }


    /** Maximum number of results */
    private final int MAX_RESULTS = 20;

    /**
     * Search Thread for address field popup
     */
    class SearchThread extends Thread {

        class SearchResult implements Comparable<SearchResult> {

            /** Link to root */
            String link;

            /** Bookmark info in case this is a bookmark */
            String bookmark;

            /** Number of matches at word beginning? */
            int wordBeginMatches;

            /** String representation of result (in html) */
            String stringRepresentation;

            /** Compare with respect to ranking in result list */
            public int compareTo(int linkLength, boolean bookmark, int wordBeginMatches) {
                if (this.bookmark == null && bookmark) {
                    return -1;
                } else if (this.bookmark != null && (!bookmark)) {
                    return 1;
                } else if (this.wordBeginMatches < wordBeginMatches) {
                    return -1;
                } else if (this.wordBeginMatches > wordBeginMatches) {
                    return 1;
                } else if (this.link.length() > linkLength) {
                    return -1;
                } else if (this.link.length() < linkLength) {
                    return 1;
                }
                return 0;
            }

            @Override
            public int compareTo(SearchResult o) {
                return compareTo(o.link.length(), o.bookmark != null, o.wordBeginMatches);
            }

            public String toString() {
                return stringRepresentation;
            }
        }

        /** Current search request */
        private String searchRequest = "";

        public SearchThread() {
            super("SearchThread");
            this.setDaemon(true);
            start();
        }

        /**
         * Request new search
         * (when ready, this thread will take care of showing/hiding a list below the address field)
         *
         * @param newRequest New Search Request String ("" to cancel search)
         * @param enforceRequest Enfroce Requests - even if search string has not changed?
         */
        public void newSearchRequest(String newRequest, boolean enforceRequest) {
            newRequest = newRequest.trim();
            if (enforceRequest || (!newRequest.equals(searchRequest))) {
                synchronized (this) {
                    searchRequest = newRequest;
                    this.notify();
                }
            }
        }

        @Override
        public void run() {
            String currentSearchRequest = "";
            while (true) {
                synchronized (this) {
                    if (currentSearchRequest.equals(searchRequest) || searchRequest.length() == 0) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    currentSearchRequest = searchRequest;
                }

                // Ok, let's search
                final ArrayList<SearchResult> searchResult = new ArrayList<SearchResult>(MAX_RESULTS);
                try {
                    if (currentSearchRequest.length() > 0) {

                        // Prepare arrays containing what we're looking for
                        String[] words = currentSearchRequest.toLowerCase().split(" ");
                        String[] slashedWords = new String[words.length];
                        for (int i = 0; i < words.length; i++) {
                            slashedWords[i] = "/" + words[i];
                        }

                        // Search bookmarks first
                        XMLNode bookmarkNode = finstruct.getSettings().getTopLevelNode("bookmarks");
                        for (XMLNode.ConstChildIterator bookmark = bookmarkNode.getChildrenBegin(); bookmark.get() != null; bookmark.next()) {
                            try {
                                if (bookmark.get().getName().equals("bookmark")) {
                                    String name = bookmark.get().getStringAttribute("root").substring("Interfaces".length());
                                    String nameLower = name.toLowerCase();
                                    boolean foundAll = true;
                                    int wordBeginMatches = 0;
                                    for (String word : words) {
                                        foundAll &= nameLower.contains(word);
                                        if (nameLower.contains("/" + word)) {
                                            wordBeginMatches++;
                                        }
                                    }

                                    if (foundAll) {
                                        if (finstruct.ioInterface.getChildByQualifiedName("Interfaces" + name, '/') != null) {
                                            addSearchResult(searchResult, new StringBuilder(name), bookmark.get().getXMLDump(false), wordBeginMatches);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Finstruct.logDomain.log(LogLevel.WARNING, "Could not parse bookmark entry: " + bookmark.get().getXMLDump(true), e);
                            }
                        }

                        if (searchResult.size() < MAX_RESULTS) {

                            // ok, now search whole tree
                            StringBuilder sb = new StringBuilder();
                            StringBuilder sbLower = new StringBuilder();
                            ModelNode root = finstruct.getIoInterface().getRoot();
                            for (int i = 0; i < root.getChildCount(); i++) {
                                search(searchResult, words, slashedWords, sb, sbLower, root.getChildAt(i));
                            }
                        }

                        // Prepare string representation of result
                        for (SearchResult result : searchResult) {

                            // Determine parts of string to display bold
                            boolean[] boldMask = new boolean[result.link.length()];
                            String linkLower = result.link.toLowerCase();
                            for (String word : words) {
                                int startIndex = 0;
                                while (startIndex < linkLower.length()) {
                                    int index = linkLower.indexOf(word, startIndex);
                                    if (index < 0) {
                                        break;
                                    }
                                    for (int i = 0; i < word.length(); i++) {
                                        boldMask[index + i] = true;
                                    }
                                    startIndex = index + 1;
                                }
                            }

                            // Create Html representation
                            boolean bold = false; // is text currently printed bold?
                            StringBuilder html = new StringBuilder("<html>");
                            for (int i = 1; i < result.link.length(); i++) {
                                if ((!bold) && boldMask[i]) {
                                    html.append("<b>");
                                    bold = true;
                                } else if (bold && (!boldMask[i])) {
                                    html.append("</b>");
                                    bold = false;
                                }
                                html.append(result.link.charAt(i));
                            }
                            if (bold) {
                                html.append("</b>");
                            }
                            html.append("</html>");
                            result.stringRepresentation = html.toString();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // can happen - due to concurrent modifications to the remote model
                }

                // Publish result
                synchronized (this) {
                    if (currentSearchRequest.equals(searchRequest)) {
                        SwingUtilities.invokeLater(new Runnable() {

                            @SuppressWarnings({ "serial", "rawtypes", "unchecked" })
                            @Override
                            public void run() {
                                addressList.setModel(new AbstractListModel() {

                                    @Override
                                    public int getSize() {
                                        return searchResult.size();
                                    }

                                    @Override
                                    public Object getElementAt(int index) {
                                        return searchResult.get(index);
                                    }
                                });
                                boolean hasFocus = addressField.hasFocus();
                                if (searchResult.size() == 0) {
                                    popupMenu.setVisible(false);
                                } else {
                                    popupMenu.show(addressField, 2, addressField.getHeight());
                                    //addressList.setMinimumSize(new Dimension(addressField.getWidth(), 50));
                                    //addressList.setPreferredSize(new Dimension(addressField.getWidth(), addressList.getPreferredSize().height));
                                    popupMenu.setPopupSize(addressField.getWidth() - 4, addressList.getPreferredSize().height + 12);
                                }
                                if (hasFocus) {
                                    addressField.requestFocusInWindow();
                                }
                            }
                        });
                    }
                }
            }
        }

        /**
         * Recursive search through the remote model tree
         *
         * @param searchResult List with search results
         * @param words Words to search for
         * @param slashedWords Words to search for with slash prepended
         * @param sb StringBuilder containing concatenated string
         * @param sbLower Same as 'sb', but in lower case letters
         * @param node Current node
         */
        private void search(ArrayList<SearchResult> searchResult, String[] words, String[] slashedWords, StringBuilder sb, StringBuilder sbLower, ModelNode node) {

            // Append node's name to sb and sbLower
            int oldLength = sb.length();
            sb.append('/').append(node.getName());
            sbLower.setLength(sb.length());
            for (int i = oldLength; i < sb.length(); i++) {
                sbLower.setCharAt(i, Character.toLowerCase(sb.charAt(i)));
            }

            // Check whether this node is a search result
            boolean foundAll = true;
            int wordBeginMatches = 0;
            for (String word : words) {
                foundAll &= (sbLower.indexOf(word) >= 0);
            }
            if (foundAll) {
                for (String word : slashedWords) {
                    if (sbLower.indexOf(word) >= 0) {
                        wordBeginMatches++;
                    }
                }
                addSearchResult(searchResult, sb, null, wordBeginMatches);
            }

            // Check child nodes
            for (int i = 0; i < node.getChildCount(); i++) {
                search(searchResult, words, slashedWords, sb, sbLower, node.getChildAt(i));
            }

            // Restore StringBuilders to original state
            sb.setLength(oldLength);
            sbLower.setLength(oldLength);
        }

        /**
         * Add result with the specified properties to result list
         * (if it is among the top MAX_RESULTS results - otherwise return without modifying list)
         *
         * @param searchResult Result list
         * @param name Qualified name of result
         * @param xmlDump XML dump (if bookmark)
         * @param wordBeginMatches Number of matches at word beginning
         */
        private void addSearchResult(ArrayList<SearchResult> searchResult, StringBuilder name, String xmlDump, int wordBeginMatches) {
            //System.out.println(name);
            SearchResult result = null;
            if (searchResult.size() < MAX_RESULTS) {
                result = new SearchResult();
            } else if (searchResult.get(MAX_RESULTS - 1).compareTo(name.length(), xmlDump != null, wordBeginMatches) >= 0) {
                return;
            } else {
                result = searchResult.remove(MAX_RESULTS - 1); // Reuse last object
            }
            result.link = name.toString();
            result.bookmark = xmlDump;
            result.wordBeginMatches = wordBeginMatches;

            if (searchResult.size() == 0 || searchResult.get(searchResult.size() - 1).compareTo(result) >= 0) {
                searchResult.add(result);
            } else {
                for (int i = 0; i < searchResult.size(); i++) {
                    if (searchResult.get(i).compareTo(result) < 0) {
                        searchResult.add(i, result);
                        break;
                    }
                }
            }
        }
    }
}
