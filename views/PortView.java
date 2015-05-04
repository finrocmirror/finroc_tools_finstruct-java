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
package org.finroc.tools.finstruct.views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.finroc.core.datatype.Timestamp;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemotePort;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.FinstructView;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.PortAccessor;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.propertyeditor.ComponentFactory;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Displays port-values and lets user manipulate them
 */
public class PortView extends FinstructView implements ActionListener {

    /** UID */
    private static final long serialVersionUID = 7231901570012922905L;

    /** Ports created for panel */
    private final ArrayList < ConnectingPortAccessor<? >> ports = new ArrayList < ConnectingPortAccessor<? >> ();

    /** Framework element that all displayed ports are child of */
    private ModelNode commonParent;

    /** reference to toolBar */
    private MToolBar toolBar;

    /** Properties Panel */
    private PortPanel propPanel;

    /** Diverse toolbar switches */
    private enum DiverseSwitches { autoUpdate, singleUpdate, apply, showTimestamps }

    /** Is the currently displayed port view drawn disconnected (due to disconnect)? */
    private boolean viewDrawnDisconnected = false;

    /** Default panel background color */
    private final Color DEFAULT_BACKGROUND_COLOR = this.getBackground();

    /** Maximum number of ports to display */
    private final int MAX_PORTS = 250;

    ///** Port description font */
    //private static final Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    @Override
    protected synchronized void rootElementChanged(XMLNode expandedElements) {
        ArrayList<RemotePort> tmpResultList = new ArrayList<RemotePort>();
        if (getRootElement() instanceof RemotePort) {
            tmpResultList.add((RemotePort)getRootElement());
        }
        getRootElement().getPortsBelow(tmpResultList);
        setLayout(new BorderLayout());

        if (tmpResultList.size() > MAX_PORTS) {
            Finstruct.showErrorMessage(tmpResultList.size() + " ports selected. Only showing the first " + MAX_PORTS + " (to preserve usability).", false, false);
            while (tmpResultList.size() > 250) {
                tmpResultList.remove(tmpResultList.size() - 1);
            }
        }

        showPorts(tmpResultList);
    }

    /**
     * Clears port view (both graphical user elements and finroc ports)
     */
    public void clear() {
        super.removeAll();

        // delete panels
        for (ConnectingPortAccessor<?> panel : ports) {
            panel.delete();
        }
        ports.clear();
    }

    /**
     * Show values of selected ports
     *
     * @param portsToShow port to show
     */
    public void showPorts(List<RemotePort> portsToShow) {
        clear();
        if (portsToShow.size() == 0) {
            return;
        }

        // determine common parent
        commonParent = (ModelNode)portsToShow.get(0).getParent();
        for (RemotePort port : portsToShow) {
            while (!port.isNodeAncestor(commonParent)) {
                commonParent = (ModelNode)commonParent.getParent();
            }
        }

        // create new panel
        for (RemotePort port : portsToShow) {

            if (FinrocComponentFactory.isTypeSupported(port.getPort().getDataType())) {
                @SuppressWarnings("rawtypes")
                ConnectingPortAccessor cpa = new ConnectingPortAccessor(port, commonParent.getQualifiedName('/'));
                ports.add(cpa);
            }
        }
        propPanel = new PortPanel(new FinrocComponentFactory(commonParent), new StandardComponentFactory());
        propPanel.setOpaque(false);
        propPanel.init(ports, true);
        add(propPanel, BorderLayout.CENTER);
        List < PropertyEditComponent<? >> components = propPanel.getComponentList();
        assert(components.size() == ports.size());
        for (int i = 0; i < ports.size(); i++) {
            ConnectingPortAccessor<?> cpa = ports.get(i);
            new ChangeForwarder(cpa, components.get(i), propPanel.timestampElements.get(i));
            cpa.init();
        }

        revalidate();
        repaint();
    }

    @Override
    public void initMenuAndToolBar(JMenuBar menuBar, MToolBar toolBar) {
        this.toolBar = toolBar;
        toolBar.addToggleButton(new MAction(DiverseSwitches.autoUpdate, "system-upgrade-ubuntu.png", "Auto Update", this), true);
        toolBar.add(new MAction(DiverseSwitches.singleUpdate, "reload-ubuntu.png", "Single Update", this));
        toolBar.add(new MAction(DiverseSwitches.apply, "gtk-apply-ubuntu.png", "Apply", this));
        toolBar.addToggleButton(new MAction(DiverseSwitches.showTimestamps, "clock-ubuntu.png", "Show Timestamps", this), true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        try {
            if (ae instanceof MActionEvent) {
                @SuppressWarnings("rawtypes")
                Enum e = ((MActionEvent)ae).getEnumID();
                if (e == DiverseSwitches.autoUpdate) {
                    for (ConnectingPortAccessor<?> port : ports) {
                        port.setAutoUpdate(toolBar.isSelected(DiverseSwitches.autoUpdate));
                    }
                } else if (e == DiverseSwitches.singleUpdate && (!toolBar.isSelected(DiverseSwitches.autoUpdate))) {
                    for (ConnectingPortAccessor<?> port : ports) {
                        ((ChangeForwarder)port.getListener()).initialValueRetrieve = true;
                        port.setAutoUpdate(true);
                    }
                } else if (e == DiverseSwitches.apply && propPanel != null) {
                    for (PropertyEditComponent<?> comp : propPanel.getComponentList()) {
                        comp.applyChanges();
                    }
                } else if (e == DiverseSwitches.showTimestamps && propPanel != null) {
                    for (JLabel label : propPanel.timestampElements) {
                        label.setVisible(toolBar.isSelected(DiverseSwitches.showTimestamps));
                    }
                }
            }
        } catch (Exception e) {
            Log.log(LogLevel.ERROR, this, e);
            JOptionPane.showMessageDialog(null, e.getClass().getName() + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public synchronized void paint(Graphics g) {
        viewDrawnDisconnected = !isConnectedToRootNode();
        setBackground(viewDrawnDisconnected ? new Color(255, 200, 190) : DEFAULT_BACKGROUND_COLOR);
        super.paint(g);
    }

    @Override
    protected void updateView() {
        if (getRootElement() != null) {
            boolean disconnected = !isConnectedToRootNode();
            boolean repaint = disconnected != viewDrawnDisconnected;
            if (disconnected) {
                tryReconnectingToRootNode();
                repaint |= isConnectedToRootNode();
            }
            if (repaint) {
                repaint();
            }
        }
    }

    /**
     * Customized PropertiesPanel for Port view.
     * Includes optional text field for time stamps
     */
    private class PortPanel extends PropertiesPanel {

        /** UID */
        private static final long serialVersionUID = -6085731965441695840L;

        /** All timestamp fields */
        private ArrayList<JLabel> timestampElements = new ArrayList<JLabel>();

        /** Gridbag contraints for timestamp */
        private final GridBagConstraints gbc = new GridBagConstraints();

        public PortPanel(ComponentFactory... componentFactories) {
            super(componentFactories);
            gbc.gridx = 2;
        }

        @Override
        protected void addComponent(PropertyEditComponent<?> comp, int index, boolean labelAlignmentLeft) {
            super.addComponent(comp, index, labelAlignmentLeft);
            gbc.gridy = index;
            JLabel timestampElement = new JLabel();
            timestampElement.setVisible(toolBar.isSelected(DiverseSwitches.showTimestamps));
            timestampElements.add(timestampElement);
            add(timestampElement, gbc);
        }
    }

    /**
     * Forwards port changes to property edit component
     */
    private class ChangeForwarder implements PortAccessor.Listener, Runnable {

        /** Property edit component to forward change to */
        private final PropertyEditComponent<?> component;

        /** Port to forward events from */
        private final ConnectingPortAccessor<?> port;

        /** Element to display timestamp in */
        private final JLabel timestampElement;

        /** initially true - until first value is retrieved from server */
        protected boolean initialValueRetrieve = true;

        /** Buffer to store timestamp in */
        private final Timestamp timestampBuffer = new Timestamp();

        public ChangeForwarder(ConnectingPortAccessor<?> cpa, PropertyEditComponent<?> component, JLabel timestampElement) {
            this.port = cpa;
            this.component = component;
            this.timestampElement = timestampElement;
            cpa.setListener(this);
        }

        @Override
        public void portChanged() {
            SwingUtilities.invokeLater(this);
        }

        @Override
        public void run() {
            //System.out.println("Running for " + component.toString());
            ThreadLocalCache.get();
            boolean aa = toolBar.isSelected(DiverseSwitches.autoUpdate);
            boolean upd = initialValueRetrieve;
            if (aa || upd) {
                try {
                    component.updateValue();
                    port.getTimestamp(timestampBuffer);
                    timestampElement.setText(timestampBuffer.toString());
                } catch (Exception e) {
                    Log.log(LogLevel.ERROR, this, e);
                }
            }
            if (initialValueRetrieve) {
                initialValueRetrieve = false;
                if (!aa) {
                    port.setAutoUpdate(false);
                }
            }
        }
    }

    @Override
    public void storeViewConfiguration(XMLNode node) {
    }

    @Override
    protected void destroy() {
        clear();
    }
}

