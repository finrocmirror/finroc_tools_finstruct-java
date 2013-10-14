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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.RuntimeSettings;
import org.finroc.core.plugin.ConnectionListener;
import org.finroc.core.plugin.CreateExternalConnectionAction;
import org.finroc.core.plugin.ExternalConnection;
import org.finroc.core.plugin.Plugins;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapperTreeNode;
import org.finroc.core.util.Files;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.GUIUiBase;
import org.finroc.tools.gui.StatusBar;
import org.finroc.tools.gui.commons.EventRouter;
import org.finroc.tools.gui.util.gui.IconManager;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.treemodel.InterfaceTreeModel;
import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Main Window class
 */
public class Finstruct extends FinstructWindow implements ConnectionListener, WindowListener, ConnectionPanel.Owner, TreeSelectionListener, TreeModelListener {

    /** UID */
    private static final long serialVersionUID = 5790020137768236619L;

    /** Control modes */
    public enum Mode { navigate, connect, paramconnect }

    /** Interface of connected runtimes */
    protected InterfaceTreeModel ioInterface = new InterfaceTreeModel();

    /** Menu items */
    private JMenuItem miDisconnectDiscard, miExit;
    private JMenu miConnectMenu;

    /** Status bar */
    protected StatusBar statusBar;

    /** timer to update status bar */
    private Timer statusBarTimer;

    /** Reference to ConnectionPanel */
    private FinstructConnectionPanel connectionPanel;

    /** Vertical Split pane */
    private JSplitPane splitPane;

    /** Enable beta features? */
    public static boolean BETA_FEATURES;

    /** Enable experimental features? */
    public static boolean EXPERIMENTAL_FEATURES;

    /** TCP Connect action */
    public ConnectAction tcpConnect;

    /** Finstruct persistent settings instance */
    private FinstructSettings settings = new FinstructSettings();

    /** Finstruct singleton instance */
    private static Finstruct finstructInstance;

    /** Double-click delay in ms */
    public static final long DOUBLE_CLICK_DELAY = 300;

    /** Tool bar for different modes of tree */
    public final MToolBar treeToolBar = new MToolBar("Tree mode");

    public static void main(String[] args) {
        RuntimeSettings.setUseCCPorts(false);
        RuntimeSettings.setMaxCoreRegisterIndexBits(19);
        RuntimeEnvironment.getInstance().setProgramName("finstruct");

        String connect = null;
        boolean shiny = true;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--beta")) {
                BETA_FEATURES = true;
            } else if (arg.equalsIgnoreCase("--experimental")) {
                EXPERIMENTAL_FEATURES = true;
            } else if (arg.startsWith("--connect=")) {
                connect = arg.substring(arg.indexOf("=") + 1);
            } else if (arg.equals("--classic")) {
                shiny = false;
            } else if (arg.equals("-h") || arg.equals("--help")) {
                try {
                    for (String s : Files.readLines(Finstruct.class.getResourceAsStream("help.txt"))) {
                        System.out.println(s);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
        }

        if (shiny) {
            try {
                for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
            } catch (Exception e) {}
        }

        final String address = connect;
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                final Finstruct finstruct = new Finstruct();

                // connect
                if (finstruct.tcpConnect != null) {
                    try {
                        finstruct.tcpConnect.connect(address);
                    } catch (Exception e) {
                        showErrorMessage(e, true);
                    }
                }
            }
        });
    }

    public Finstruct() {
        super(null);
        super.finstruct = this;
        assert(finstructInstance == null);
        finstructInstance = this;
        ThreadLocalCache.get();
        this.setMinimumSize(new Dimension(640, 480));
        setTitle("finstruct");

        IconManager.getInstance().addResourceFolder(GUIUiBase.class, "icons");
        IconManager.getInstance().addResourceFolder(GUIUiBase.class, "themes");
        IconManager.getInstance().addResourceFolder(Finstruct.class, "icon");

        // (dis-)connect-menu
        miConnectMenu = new JMenu("Connect");
        //miDisconnectMenu = new JMenu("Disconnect");
        //miReconnectMenu = new JMenu("Reconnect");
        for (CreateExternalConnectionAction ioi : Plugins.getInstance().getExternalConnections()) {
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
        menuBar.add(menuFile, 0);
        //miConnect = createMenuEntry("Connect All", menuFile, KeyEvent.VK_C);
        //miReconnect = createMenuEntry("Reconnect All", menuFile, KeyEvent.VK_R);
        miDisconnectDiscard = createMenuEntry("Disconnect & Discard All", menuFile, KeyEvent.VK_D);
        menuFile.add(miConnectMenu);
        menuFile.addSeparator();
        miExit = createMenuEntry("Exit", menuFile, KeyEvent.VK_X);
        //miTest = createMenuEntry("Test", menuFile, KeyEvent.VK_F16);
        menuBar.setVisible(true);

        // Status bar
        statusBar = new StatusBar();
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        // Connection Panel
        connectionPanel = new FinstructConnectionPanel(this, getTreeFont());
        connectionPanel.setLeftTree(ioInterface);
        connectionPanel.setRightTree(null);
        treeToolBar.addToggleButton(new MAction(Mode.navigate, null, "Navigate", this));
        treeToolBar.addSeparator();
        treeToolBar.addToggleButton(new MAction(Mode.connect, null, "Connect", this));
        treeToolBar.addSeparator();
        treeToolBar.addToggleButton(new MAction(Mode.paramconnect, null, "Parameter-Connect", this));
        treeToolBar.setSelected(Mode.navigate);
        treeToolBar.setBackground(Color.white);

        // Split pane
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, connectionPanel, new JPanel());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        splitPane.setDividerSize(5);

        // Change to standard view
        changeView(getViewInstance(views.get(0).view), true);

        // create and show GUI
        pack();
        setVisible(true);
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
        ioInterface.addTreeModelListener(this);
    }

    /**
     * @return Finstruct singleton instance (instance is created by main())
     */
    public static Finstruct getInstance() {
        assert(finstructInstance != null);
        return finstructInstance;
    }

    /**
     * Changes view
     *
     * @param view View to change to
     * @param restoreRoot call setRootElement with old root on new View?
     */
    @Override
    protected void changeView(FinstructView view, boolean restoreRoot) {
        super.changeView(view, restoreRoot);

        // fill left part of split pane
        splitPane.setLeftComponent(getCurrentView().initLeftPanel(connectionPanel));

        // fill right part of split pane
        JScrollPane scrollPane = new JScrollPane(getCurrentView());
        //scrollPane.setBorder(BorderFactory.createLineBorder(Color.gray));
        //scrollPane.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.gray));
        //scrollPane.setBorder(BorderFactory.createMatteBorder(4, 4, 4, 4, getCurrentView().getBackground()));
        //scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        //scrollPane.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        scrollPane.setBorder(new CompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        //scrollPane.setBorder(BorderFactory.createEtchedBorder());
        splitPane.setRightComponent(scrollPane);
    }

    @Override
    public void setViewRootElement(ModelNode root, XMLNode viewConfiguration, boolean forceReload) {
        super.setViewRootElement(root, viewConfiguration, forceReload);

        if (treeToolBar.isSelected(Mode.paramconnect)) {
            connectionPanel.setRightTree(new ConfigFileModel(root));
        }
        connectionPanel.repaint();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void actionPerformed(ActionEvent ae) {
        super.actionPerformed(ae);
        try {
            Object src = ae.getSource();
            if (src == miDisconnectDiscard) {
                disconnectDiscard();
            } else if (src == miExit) {
                settings.saveSettings();
                exit();
            } else if (src == statusBarTimer) {
                connectionEvent(null, 0);
                return;
            } else if (ae instanceof MActionEvent) {
                Enum e = ((MActionEvent)ae).getEnumID();
                if (e instanceof Mode) {
                    connectionPanel.setRightTree(e == Mode.connect ? connectionPanel.getLeftTree() : e == Mode.paramconnect ? new ConfigFileModel(getCurrentView().getRootElement()) : null);
                }
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

    public static void showErrorMessage(final Exception e, final boolean printStackTrace) {
        if (printStackTrace) {
            logDomain.log(LogLevel.ERROR, "Finstruct", e);
        } else {
            logDomain.log(LogLevel.ERROR, "Finstruct", e.getMessage());
        }
        if (SwingUtilities.isEventDispatchThread()) {
            JOptionPane.showMessageDialog(null, (printStackTrace ? (e.getClass().getName() + "\n") : "") + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, (printStackTrace ? (e.getClass().getName() + "\n") : "") + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
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
            if (address == null) {
                address = JOptionPane.showInputDialog(null, ioInterface.getName() + ": Please input connection address", ec.getConnectionAddress());
            }
            if (address != null) {
                ec.connect(address, Finstruct.this.ioInterface.getNewModelHandlerInstance());
                // parent.ioInterface.addModule(ioInterface.createModule());
                EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
            } else {
                ec.managedDelete();
            }
        }
    }

    public Font getTreeFont() {
        return new JLabel().getFont().deriveFont(Font.PLAIN, 12);
    }

    @Override public void windowClosing(WindowEvent e) {
        settings.saveSettings();
        exit();
    }

    @Override
    public void addUndoBufferEntry(String string) {
    }

    @Override
    public void refreshConnectionPanelModels() {
        EventRouter.fireConnectionEvent(null, ConnectionListener.INTERFACE_UPDATED);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        if (e.isAddedPath()) {
            Object sel = e.getPath().getLastPathComponent();
            if (!(sel instanceof PortWrapperTreeNode)) {
                if (sel instanceof ModelNode) {
                    logDomain.log(LogLevel.DEBUG, getLogDescription(), "Setting view root to " + sel.toString());
                    showElement((ModelNode)sel);
                }
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
        FinstructView view = getCurrentView();
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

    /**
     * @return Finstruct persistent settings
     */
    public FinstructSettings getSettings() {
        return settings;
    }

//    @Override TODO
//    public void runtimeChange(byte changeType, final FrameworkElement element) {
//
//        // If nothing is displayed change view to any remote runtime
//        if (changeType == RuntimeListener.ADD && getCurrentView() != null && getCurrentView().getRootElement() == null) {
//            if (element.getParent().getFlag(FrameworkElementFlags.ALTERNATIVE_LINK_ROOT) &&
//                    element.getParent().getFlag(FrameworkElementFlags.NETWORK_ELEMENT) &&
//                    (element.getFlag(FrameworkElementFlags.FINSTRUCTABLE_GROUP) || element.getFlag(FrameworkElementFlags.EDGE_AGGREGATOR))) {
//                SwingUtilities.invokeLater(new Runnable() {
//
//                    @Override
//                    public void run() {
//                        connectionPanel.expandOnly(true, element);
//                        //connectionPanel.
//                        showElement(element);
//                    }
//
//                });
//            }
//        }
//    }
//
//    @Override
//    public void runtimeEdgeChange(byte changeType, AbstractPort source, AbstractPort target) {
//    }

    @Override public void treeNodesInserted(TreeModelEvent e) {}
    @Override public void treeNodesChanged(TreeModelEvent e) {}
    @Override public void treeNodesRemoved(TreeModelEvent e) {}
    @Override public void treeStructureChanged(TreeModelEvent e) {}
}
