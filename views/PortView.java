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
package org.finroc.tools.finstruct.views;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.tools.finstruct.FinstructView;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.PortAccessor;
import org.finroc.tools.finstruct.propertyeditor.UnknownTypePortAccessor;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.rrlib.finroc_core_utils.log.LogLevel;

/**
 * @author max
 *
 * Displays port-values and lets user manipulate them
 */
public class PortView extends FinstructView implements FrameworkElementTreeFilter.Callback<Boolean>, ActionListener {

    /** UID */
    private static final long serialVersionUID = 7231901570012922905L;

    /** temporary list for rootElementChanged() function */
    private final ArrayList<AbstractPort> tmpResultList = new ArrayList<AbstractPort>();

    /** Ports created for panel */
    private final ArrayList < ConnectingPortAccessor<? >> ports = new ArrayList < ConnectingPortAccessor<? >> ();

    /** Framework element that all displayed ports are child of */
    private FrameworkElement commonParent;

    /** reference to toolBar */
    private MToolBar toolBar;

    /** Properties Panel */
    private PropertiesPanel propPanel;

    /** Diverse toolbar switches */
    private enum DiverseSwitches { autoUpdate, singleUpdate, apply }

    ///** Port description font */
    //private static final Font FONT = new JLabel().getFont().deriveFont(Font.PLAIN);

    @Override
    protected synchronized void rootElementChanged(ArrayList<FrameworkElement> expandedElements) {
        tmpResultList.clear();
        FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(CoreFlags.STATUS_FLAGS | CoreFlags.IS_PORT, CoreFlags.READY | CoreFlags.PUBLISHED | CoreFlags.IS_PORT);
        filter.traverseElementTree(getRootElement(), this, null);
        setLayout(new BorderLayout());
        showPorts(tmpResultList);
    }

    @Override
    public void treeFilterCallback(FrameworkElement fe, Boolean unused) {
        assert(fe instanceof AbstractPort);
        tmpResultList.add((AbstractPort)fe);
    }

    /**
     * Show values of selected ports
     *
     * @param portsToShow port to show
     */
    public void showPorts(List<AbstractPort> portsToShow) {
        super.removeAll();

        // delete panels
        for (ConnectingPortAccessor<?> panel : ports) {
            panel.delete();
        }
        ports.clear();
        if (portsToShow.size() == 0) {
            return;
        }

        // determine common parent
        commonParent = portsToShow.get(0).getParent();
        for (AbstractPort port : portsToShow) {
            while (!port.isChildOf(commonParent)) {
                commonParent = commonParent.getParent();
            }
        }

        // create new panel
        for (AbstractPort port : portsToShow) {
            if (FinrocTypeInfo.isCCType(port.getDataType()) || FinrocTypeInfo.isStdType(port.getDataType())) {
                @SuppressWarnings("rawtypes")
                ConnectingPortAccessor cpa = new ConnectingPortAccessor(port, commonParent.getQualifiedLink());
                ports.add(cpa);
            } else if (FinrocTypeInfo.get(port.getDataType()).getType() == FinrocTypeInfo.Type.UNKNOWN_CC || FinrocTypeInfo.get(port.getDataType()).getType() == FinrocTypeInfo.Type.UNKNOWN_STD && port.asNetPort() != null) {
                @SuppressWarnings("rawtypes")
                ConnectingPortAccessor cpa = new UnknownTypePortAccessor(port.asNetPort(), commonParent.getQualifiedLink());
                ports.add(cpa);
            }
        }
        propPanel = new PropertiesPanel(new FinrocComponentFactory(commonParent), new StandardComponentFactory());
        propPanel.init(ports, true);
        add(propPanel, BorderLayout.CENTER);
        List < PropertyEditComponent<? >> components = propPanel.getComponentList();
        assert(components.size() == ports.size());
        for (int i = 0; i < ports.size(); i++) {
            ConnectingPortAccessor<?> cpa = ports.get(i);
            new ChangeForwarder(cpa, components.get(i));
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
                }
            }
        } catch (Exception e) {
            PropertiesPanel.logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
            JOptionPane.showMessageDialog(null, e.getClass().getName() + "\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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

        /** initially true - until first value is retrieved from server */
        protected boolean initialValueRetrieve = true;

        public ChangeForwarder(ConnectingPortAccessor<?> cpa, PropertyEditComponent<?> component) {
            this.port = cpa;
            this.component = component;
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
                } catch (Exception e) {
                    logDomain.log(LogLevel.LL_ERROR, getLogDescription(), e);
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
    public Collection <? extends FrameworkElement > getExpandedElementsForHistory() {
        return null;
    }
}
