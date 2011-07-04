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
package org.finroc.tools.finstruct.dialogs;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeListener;
import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.finstructable.GroupInterface;
import org.finroc.core.finstructable.GroupInterface.DataClassification;
import org.finroc.core.finstructable.GroupInterface.PortDirection;
import org.finroc.core.parameter.StructureParameterList;
import org.finroc.core.plugin.RemoteCreateModuleAction;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.FinrocObjectAccessor;
import org.finroc.tools.gui.util.gui.MDialog;
import org.finroc.tools.gui.util.propertyeditor.FieldAccessorFactory;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.PropertyList;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.serialization.Serialization;

/**
 * @author max
 *
 * Dialog to edit (structure) parameters of framework elements
 */
public class CreateInterfacesDialog extends MDialog {

    /**
     * Task to create an interface
     */
    static class CreationTask implements Serializable {

        /** UID */
        private static final long serialVersionUID = 2945689873558975031L;

        /** Create this interface */
        boolean create = true;

        /** Name of interface to create */
        String name = "Interface X";

        /** Interface's data classification */
        GroupInterface.DataClassification dataClassification = GroupInterface.DataClassification.ANY;

        /** Interface's port direction */
        GroupInterface.PortDirection portDirection = GroupInterface.PortDirection.BOTH;

        /** Shared interface? Globally unique links ? */
        boolean shared = false, globallyUniqueLinks = false;

        /** List with ports to create */
        transient PortCreationList portCreationList;
    }

    static class CreationTasksContainer {
        PropertyList<CreationTask> tasks = new PropertyList<CreationTask>(CreationTask.class, 10);;
    }

    /** UID */
    private static final long serialVersionUID = 2276252519414637921L;

    /** Main properties panel */
    private PropertiesPanel propPanel;

    /** Buttons */
    private JButton cancel, next;

    /** Remote Element to create interfaces for */
    private FrameworkElement element;

    /** List with creation tasks */
    private final CreationTasksContainer creation = new CreationTasksContainer();

    /** Remote action for creating interfaces */
    private RemoteCreateModuleAction createInterfaceAction;

    public CreateInterfacesDialog(Frame owner) {
        super(owner, true);
    }

    /**
     * @param element Remote Element to edit parameters of
     */
    public void show(FrameworkElement element) {
        this.element = element;
        if (RemoteRuntime.find(element) == null || (!element.getFlag(CoreFlags.FINSTRUCTED)) && (!element.getFlag(CoreFlags.FINSTRUCTABLE_GROUP))) {
            Finstruct.showErrorMessage("Not a remote finstructed element", false, false);
            return;
        }

        RemoteRuntime rr = RemoteRuntime.find(element);
        for (RemoteCreateModuleAction a : rr.getAdminInterface().getRemoteModuleTypes()) {
            if (a.groupName.equals("core") || a.groupName.equals("libfinroc_core.so") && a.name.equals("Interface")) {
                createInterfaceAction = a;
                break;
            }
        }
        if (createInterfaceAction == null) {
            Finstruct.showErrorMessage("Couldn't find remote Action to create interfaces", false, false);
            return;
        }

        addTask("Sensor Input", GroupInterface.DataClassification.SENSOR_DATA, GroupInterface.PortDirection.INPUT_ONLY);
        addTask("Sensor Output", GroupInterface.DataClassification.SENSOR_DATA, GroupInterface.PortDirection.OUTPUT_ONLY);
        addTask("Controller Input", GroupInterface.DataClassification.CONTROLLER_DATA, GroupInterface.PortDirection.INPUT_ONLY);
        addTask("Controller Output", GroupInterface.DataClassification.CONTROLLER_DATA, GroupInterface.PortDirection.OUTPUT_ONLY);
        addTask("Custom Interface", GroupInterface.DataClassification.ANY, GroupInterface.PortDirection.BOTH);
        addTask("Another Interface", GroupInterface.DataClassification.ANY, GroupInterface.PortDirection.BOTH);

        // Create property panel
        propPanel = new PropertiesPanel(new StandardComponentFactory());
        propPanel.init(FieldAccessorFactory.getInstance().createAccessors(creation), true);
        getContentPane().add(propPanel, BorderLayout.CENTER);

        // Create buttons
        JPanel buttons = new JPanel();
        getContentPane().add(buttons, BorderLayout.SOUTH);
        cancel = createButton("Cancel", buttons);
        next = createButton("Next", buttons);

        // show dialog
        pack();
        setVisible(true);
    }

    /**
     * add interface creation task - if interface doesn't already exist
     *
     * @param name Interface name
     * @param dataClass Interface's data classification
     * @param dir Interface's port direction
     */
    private void addTask(String name, DataClassification dataClass, PortDirection dir) {
        if (element.getChild(name) != null) {
            return;
        }
        CreationTask task = new CreationTask();
        task.name = name;
        task.dataClassification = dataClass;
        task.portDirection = dir;
        creation.tasks.add(task);
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == cancel) {
            close();
        } else if (e.getSource() == next) {

            // apply changes
            for (PropertyEditComponent<?> wpec : propPanel.getComponentList()) {
                try {
                    wpec.applyChanges();
                } catch (Exception e1) {
                    Finstruct.logDomain.log(LogLevel.LL_ERROR, "ParameterEditDialog", e1);
                }
            }

            // show next dialog
            if (new PortsDialog().showDialog()) {
                close();
            }
        }
    }

    /**
     * Dialog to edit ports of created interfaces
     */
    public class PortsDialog extends MDialog implements RuntimeListener {

        /** UID */
        private static final long serialVersionUID = -7549055844293711845L;

        /** Buttons */
        private JButton cancel, create;

        /** Main properties panel */
        private PropertiesPanel propPanel;

        /** Close initial dialog? */
        private volatile boolean closeInitial;

        /** Number of modules that need port list set */
        private int setPortListCount;

        public PortsDialog() {
            super(CreateInterfacesDialog.this, true);
        }

        /**
         * Show dialog
         *
         * @return Close initial dialog?
         */
        public boolean showDialog() {

            // Create property panel
            propPanel = new PropertiesPanel(new FinrocComponentFactory(element), new StandardComponentFactory());
            ArrayList < PropertyAccessor<? >> accessors = new ArrayList < PropertyAccessor<? >> ();
            for (CreationTask task : creation.tasks) {
                if (task.create) {
                    for (CreationTask task2 : creation.tasks) {
                        if ((task2 != task && task.name.equals(task2.name)) || element.getChild(task.name) != null) {
                            Finstruct.showErrorMessage("Please select unique names for new interfaces", false, false);
                            return false;
                        }
                    }
                    if (task.portCreationList == null) {
                        task.portCreationList = new PortCreationList();
                    }
                    accessors.add(new FinrocObjectAccessor(task.name + " ports", task.portCreationList));
                }
            }
            if (accessors.size() == 0) {
                Finstruct.showErrorMessage("Please select at least one action to create", false, false);
                return false;
            }
            propPanel.init(accessors, true);
            getContentPane().add(propPanel, BorderLayout.CENTER);

            // Create buttons
            JPanel buttons = new JPanel();
            getContentPane().add(buttons, BorderLayout.SOUTH);
            cancel = createButton("Cancel", buttons);
            createButton("Back", buttons);
            create = createButton("Create", buttons);

            // show dialog
            pack();
            setVisible(true);

            return closeInitial;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == cancel) {
                closeInitial = true;
            } else if (e.getSource() == create) {

                // apply changes
                for (PropertyEditComponent<?> wpec : propPanel.getComponentList()) {
                    try {
                        wpec.applyChanges();
                    } catch (Exception e1) {
                        Finstruct.logDomain.log(LogLevel.LL_ERROR, "ParameterEditDialog", e1);
                    }
                }

                setPortListCount = 0;
                RemoteRuntime rr = RemoteRuntime.find(element);

                // Create interfaces (create & wait to apply port list changes)
                synchronized (this) {

                    try {
                        element.getRuntime().addListener(this);

                        // Create modules
                        for (CreationTask task : creation.tasks) {
                            if (task.create) {
                                String[] params = new String[4];
                                params[0] = "" + task.dataClassification.ordinal();
                                params[1] = "" + task.portDirection.ordinal();
                                params[2] = "" + task.shared;
                                params[3] = "" + task.globallyUniqueLinks;
                                if (rr.getAdminInterface().createModule(createInterfaceAction, task.name, rr.getRemoteHandle(element), params) && task.portCreationList.getSize() > 0) {
                                    setPortListCount++;
                                }
                            }
                        }

                        if (setPortListCount > 0) {

                            // wait for all port lists to be set

                            long start = System.currentTimeMillis();
                            while (System.currentTimeMillis() < (start + 2000) && setPortListCount > 0) {
                                try {
                                    this.wait(500);
                                } catch (InterruptedException e1) {
                                }
                            }

                            if (setPortListCount > 0) {
                                Finstruct.showErrorMessage("Could not set ports list of " + setPortListCount + " interfaces", false, false);
                            }
                        }
                    } finally {
                        element.getRuntime().removeListener(this);
                    }

                }

                closeInitial = true;
            }
            close();
        }

        @Override
        public void runtimeChange(byte changeType, final FrameworkElement element) {
            if (changeType == RuntimeListener.ADD && element.getParent() == CreateInterfacesDialog.this.element) {

                new Thread(new Runnable() {

                    @Override
                    public void run() {

                        System.out.println("Started thread for " + element.getQualifiedName());
                        synchronized (PortsDialog.this) {

                            System.out.println("Started2 thread for " + element.getQualifiedName());

                            for (final CreationTask task : creation.tasks) {
                                if (element.getDescription().equals(task.name) && task.portCreationList.getSize() > 0) {

                                    ThreadLocalCache.get();
                                    // okay, the port list of this element needs to be set
                                    try {
                                        RemoteRuntime rr = RemoteRuntime.find(element);
                                        int handle = rr.getRemoteHandle(element);
                                        StructureParameterList elementParamList = (StructureParameterList)rr.getAdminInterface().getAnnotation(handle, StructureParameterList.TYPE);
                                        elementParamList.get(0).set(Serialization.serialize(task.portCreationList));
                                        rr.getAdminInterface().setAnnotation(handle, elementParamList);

                                        setPortListCount--;
                                        PortsDialog.this.notifyAll();
                                    } catch (Exception e) {
                                        Finstruct.showErrorMessage("Failed to set annotation list", true, true);
                                    }
                                }
                            }
                            return;
                        }
                    }
                }).start();
            }
        }

        @Override
        public void runtimeEdgeChange(byte changeType, AbstractPort source, AbstractPort target) {}
    }
}
