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
package org.finroc.finstruct;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import org.finroc.core.FrameworkElement;
import org.finroc.core.plugin.ConnectionListener;
import org.finroc.core.plugin.CreateExternalConnectionAction;
import org.finroc.core.plugin.ExternalConnection;
import org.finroc.core.plugin.Plugins;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.finstruct.dialogs.FindElementDialog;
import org.finroc.finstruct.views.AbstractFinstructGraphView;
import org.finroc.finstruct.views.PortView;
import org.finroc.finstruct.views.StandardView;
import org.finroc.finstruct.views.StandardViewGraphViz;
import org.finroc.gui.ConnectionPanel;
import org.finroc.gui.StatusBar;
import org.finroc.gui.commons.EventRouter;
import org.finroc.gui.util.gui.IconManager;
import org.finroc.gui.util.gui.MToolBar;
import org.finroc.gui.util.treemodel.InterfaceNode;
import org.finroc.gui.util.treemodel.InterfaceTreeModel;
import org.finroc.gui.util.treemodel.TreePortWrapper;
import org.finroc.jc.log.LogDefinitions;
import org.finroc.log.LogDomain;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * Main Window class
 */
public class Finstruct extends JFrame implements ActionListener, ConnectionListener, WindowListener, ConnectionPanel.Owner, TreeSelectionListener, KeyListener {

    /** UID */
    private static final long serialVersionUID = 5790020137768236619L;

    /** Interface of connected runtimes */
    protected InterfaceTreeModel ioInterface = new InterfaceTreeModel();

    /** Menu items */
    private JMenuItem miDisconnectDiscard, miExit, miFind;
    private JRadioButtonMenuItem miAutoView;
    private JMenu miConnectMenu;

    /** Status bar */
    protected StatusBar statusBar;

    /** timer to update status bar */
    private Timer statusBarTimer;

    /** Reference to ConnectionPanel */
    private FinstructConnectionPanel connectionPanel;

    /** Available finstruct views */
    private ArrayList<ViewSelector> views = new ArrayList<ViewSelector>();

    /** Current finstruct view */
    private FinstructView currentView;

    /** Menu bar */
    private JMenuBar menuBar;

    /** Vertical Split pane */
    private JSplitPane splitPane;

    /** Toolbar */
    private transient MToolBar toolBar;

    /** Log domain for this class */
    public static final LogDomain logDomain = LogDefinitions.finroc.getSubDomain("finstruct");

    /** Enable beta features? */
    public static boolean BETA_FEATURES;

    /** Enable experimental features? */
    public static boolean EXPERIMENTAL_FEATURES;

    /** TCP Connect action */
    public ConnectAction tcpConnect;

    public static void main(String[] args) {
        String connect = null;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--beta")) {
                BETA_FEATURES = true;
            } else if (arg.equalsIgnoreCase("--experimental")) {
                EXPERIMENTAL_FEATURES = true;
            } else if (arg.startsWith("--connect=")) {
                connect = arg.substring(arg.indexOf("=") + 1);
            }
        }

        final Finstruct finstruct = new Finstruct();

        // connect ?
        if (connect != null && finstruct.tcpConnect != null) {
            final String address = connect;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        finstruct.tcpConnect.connect(address);
                    } catch (Exception e) {
                        showErrorMessage(e, true);
                    }
                }
            });
        }
    }

    public Finstruct() {
        ThreadLocalCache.get();
        this.setMinimumSize(new Dimension(640, 480));
        setTitle("finstruct");

        IconManager.getInstance().setResourceFolder(Finstruct.class, "icon");

        // Create menu
        menuBar = new JMenuBar();

        // (dis-)connect-menu
        miConnectMenu = new JMenu("Connect");
        //miDisconnectMenu = new JMenu("Disconnect");
        //miReconnectMenu = new JMenu("Reconnect");
        for (CreateExternalConnectionAction ioi : Plugins.getInstance().getExternalConnections().getBackend()) {
            if ((ioi.getFlags() & CreateExternalConnectionAction.REMOTE_EDGE_INFO) != 0) {
                ConnectAction action = new ConnectAction(ioi, false, false);
                miConnectMenu.add(action);
                if (action.ioInterface.getName().startsWith("TCP")) {
                    tcpConnect = action;
                }
            }
            //miDisconnectMenu.add(new ConnectAction(ioi, true, false));
            //miReconnectMenu.add(new ConnectAction(ioi, false, true));
        }

        // file menu
        JMenu menuFile = new JMenu("File");
        menuFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menuFile);
        //miConnect = createMenuEntry("Connect All", menuFile, KeyEvent.VK_C);
        //miReconnect = createMenuEntry("Reconnect All", menuFile, KeyEvent.VK_R);
        miDisconnectDiscard = createMenuEntry("Disconnect & Discard All", menuFile, KeyEvent.VK_D);
        menuFile.add(miConnectMenu);
        menuFile.addSeparator();
        miExit = createMenuEntry("Exit", menuFile, KeyEvent.VK_X);
        //miTest = createMenuEntry("Test", menuFile, KeyEvent.VK_F16);
        menuBar.setVisible(true);

        // Edit menu
        JMenu menuEdit = new JMenu("Edit");
        menuEdit.setMnemonic(KeyEvent.VK_E);
        menuBar.add(menuEdit);
        miFind = createMenuEntry("Find Element...", menuEdit, KeyEvent.VK_F);

        // find views
        ButtonGroup viewSelectGroup = new ButtonGroup();
        views.add(new ViewSelector(StandardViewGraphViz.class, viewSelectGroup));
        views.add(new ViewSelector(PortView.class, viewSelectGroup));
        if (BETA_FEATURES) {
            views.add(new ViewSelector(StandardView.class, viewSelectGroup));
        }

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

        // Status bar
        statusBar = new StatusBar();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(statusBar, BorderLayout.SOUTH);
        connectionPanel = new FinstructConnectionPanel(this, getTreeFont());
        connectionPanel.setLeftTree(ioInterface);
        connectionPanel.setRightTree(null);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, connectionPanel, new JPanel());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        splitPane.setDividerSize(5);
        setJMenuBar(menuBar);

        // Toolbar
        toolBar = new MToolBar("Standard");
        toolBar.setFloatable(false);
        getContentPane().add(toolBar, BorderLayout.PAGE_START);

        // Change to standard view
        try {
            changeView(views.get(0).view.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // create and show GUI
        pack();
        setVisible(true);
        addWindowListener(this);
        //addKeyListener(this);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // set window to screen size
        Rectangle r = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize((r.width / 4) * 3, (r.height / 4) * 3);
        repaint();

        connectionEvent(null, ConnectionListener.NOT_CONNECTED);  // update Menu item and toolbar state
        statusBarTimer = new Timer(2000, this);
        statusBarTimer.setInitialDelay(2000);
        statusBarTimer.start();

        connectionPanel.addSelectionListener(this);
    }

    /**
     * Convenient method the create menu entries and add this Window as listener
     *
     * @param string Text of menu entry
     * @param menuFile Menu to add menu entry to
     * @return Create menu entry
     */
    private JMenuItem createMenuEntry(String string, JMenu menuFile, int mnemonic) {
        JMenuItem item = new JMenuItem(string, mnemonic);
        item.addActionListener(this);
        menuFile.add(item);
        return item;
    }

    /**
     * Changes view
     *
     * @param view View to change to
     */
    private void changeView(FinstructView view) {
        view.finstruct = this;

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

        // Clear toolbar
        toolBar.clear();

        FrameworkElement lastRoot = currentView == null ? null : currentView.getRootElement();
        currentView = view;

        // reinit tool and menu bar with view's entries
        view.initMenuAndToolBar(menuBar, toolBar);
        toolBar.revalidate();
        toolBar.getParent().repaint();

        // fill left part of split pane
        splitPane.setLeftComponent(view.initLeftPanel(connectionPanel));

        // fill right part of split pane
        splitPane.setRightComponent(new JScrollPane(view));

        // update menu
        if (!miAutoView.isSelected()) {
            for (ViewSelector v : views) {
                if (v.view.equals(view.getClass())) {
                    v.setSelected(true);
                    break;
                }
            }
        }

        // restore root element
        if (lastRoot != null) {
            currentView.setRootElement(lastRoot);
        }
    }


    @Override
    public void actionPerformed(ActionEvent ae) {
        try {
            Object src = ae.getSource();
            if (src == miDisconnectDiscard) {
                disconnectDiscard();
            } else if (src == miExit) {
                exit();
            } else if (src == statusBarTimer) {
                connectionEvent(null, 0);
                return;
            } else if (src == miFind) {
                new FindElementDialog(this, false).show(this);
            }
            //requestFocus();
        } catch (Exception e) {
            showErrorMessage(e, true);
        }
    }

    public void disconnectDiscard() throws Exception {
        for (ExternalConnection io : ioInterface.getActiveInterfaces()) {
            if (io.isConnected()) {
                try {
                    io.disconnect();
                } catch (Exception e) {}
            }
            io.managedDelete();
        }
        EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
    }

    public void exit() {
        //if (askForSave()) {
        //    persistentSettings.save();
        System.exit(0);
        //}
    }

    public static void showErrorMessage(Exception e, boolean printStackTrace) {
        if (printStackTrace) {
            logDomain.log(LogLevel.LL_ERROR, "Finstruct", e);
        } else {
            logDomain.log(LogLevel.LL_ERROR, "Finstruct", e.getMessage());
        }
        JOptionPane.showMessageDialog(null, (printStackTrace ? (e.getClass().getName() + "\n") : "") + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void showErrorMessage(String string, boolean throwException, boolean printStackTrace) {
        RuntimeException re = new RuntimeException(string);
        showErrorMessage(re, printStackTrace);
        if (throwException) {
            throw re;
        }
    }

    @Override
    public void connectionEvent(ExternalConnection source, int e) {
        statusBar.setStatus(ioInterface.getActiveInterfaces());
    }

    class ConnectAction extends AbstractAction {

        /** UID */
        private static final long serialVersionUID = 8268564574563185951L;

        private CreateExternalConnectionAction ioInterface;
        private boolean disconnect, reconnect;

        public ConnectAction(CreateExternalConnectionAction ioInterface, boolean disconnect, boolean reconnect) {
            this.ioInterface = ioInterface;
            this.disconnect = disconnect;
            this.reconnect = reconnect;
            putValue(Action.NAME, ioInterface.getName());
        }

        public void actionPerformed(ActionEvent e) {
            try {
                if (disconnect) {
                    //ioInterface.disconnect();
                } else if (reconnect) {
                    //ioInterface.reconnect();
                } else {
                    connect(null);
                    return;
                }
                EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
            } catch (Exception ex) {
                showErrorMessage(ex, true);
            }
        }

        /**
         * Connect
         *
         * @param address Address to connect to
         */
        private void connect(String address) throws Exception {
            //ioInterface.connect(null);
            ExternalConnection ec = ioInterface.createExternalConnection();
            Finstruct.this.ioInterface.getRootFrameworkElement().addChild(ec);
            ec.init();
            ec.addConnectionListener(Finstruct.this);
            ec.connect(address);
            //parent.ioInterface.addModule(ioInterface.createModule());
            EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
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
            try {
                changeView(view.newInstance());
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    public Font getTreeFont() {
        return new JLabel().getFont().deriveFont(Font.PLAIN, 12);
    }

    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
    @Override public void windowClosing(WindowEvent e) {
        exit();
    }

    @Override
    public void addUndoBufferEntry(String string) {
    }

    @Override
    public void refreshConnectionPanelModels() {
        EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
    }

    @Override public void keyPressed(KeyEvent e) {}
    @Override public void keyReleased(KeyEvent e) {
        int ctrlMask = KeyEvent.CTRL_DOWN_MASK;
        int shiftAltMask = KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;

        // Control pressed?
        if ((e.getModifiersEx() & (ctrlMask | shiftAltMask)) == ctrlMask) {
            if (e.getKeyCode() == KeyEvent.VK_F) {
                new FindElementDialog(this, false).show(this);
            }
        }
    }
    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (e.isAddedPath()) {
            Object sel = e.getPath().getLastPathComponent();
            if (!(sel instanceof TreePortWrapper)) {
                if (sel instanceof InterfaceNode) {
                    logDomain.log(LogLevel.LL_DEBUG, getLogDescription(), "Setting view root to " + sel.toString());
                    showElement(((InterfaceNode)sel).getFrameworkElement());
                }
            }
        }
    }

    /**
     * Shows specified framework element in main view - taking view selection into account
     *
     * @param fe Framework element to show
     */
    public void showElement(FrameworkElement fe) {
        if (miAutoView.isSelected()) {
            // auto-select view
            Class <? extends FinstructView > viewClass = (AbstractFinstructGraphView.isInterface(fe) || AbstractFinstructGraphView.isParameters(fe)) ? PortView.class : StandardViewGraphViz.class;
            if (currentView == null || currentView.getClass() != viewClass) {
                try {
                    changeView(viewClass.newInstance());
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            currentView.setRootElement(fe);
        } else {
            if (currentView != null) {
                currentView.setRootElement(fe);
            }
        }
    }

    private String getLogDescription() {
        return "Finroc Main Window";
    }

    /**
     * Called by connection panel when view should be repainted
     */
    public void refreshView() {
        FinstructView view = currentView;
        if (view != null) {
            view.refresh();
        }
    }

    /**
     * @return Interface of connected runtimes
     */
    public InterfaceTreeModel getIoInterface() {
        return ioInterface;
    }

    /**
     * @return Reference to ConnectionPanel
     */
    public FinstructConnectionPanel getConnectionPanel() {
        return connectionPanel;
    }
}
