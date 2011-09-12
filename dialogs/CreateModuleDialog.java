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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.finroc.core.FrameworkElement;
import org.finroc.core.RuntimeListener;
import org.finroc.core.parameter.StructureParameterList;
import org.finroc.core.plugin.RemoteCreateModuleAction;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.util.FilteredList;

/**
 * @author max
 *
 * Create Module Dialog
 */
public class CreateModuleDialog extends MGridBagDialog implements ActionListener, CaretListener, ListSelectionListener, RuntimeListener, FilteredList.Filter<RemoteCreateModuleAction> {

    /** UID */
    private static final long serialVersionUID = -349194234472122705L;

    /** Dialog components */
    private JTextField filter, name;
    private JComboBox group;
    private FilteredList<RemoteCreateModuleAction> jlist;
    private JButton next, cancel, create, load;

    /** Parent framework element */
    private FrameworkElement parent;

    /** Last auto-set name (when clicking on list) */
    private String autoSetName = "";

    /** All Create Module actions */
    //private List<RemoteCreateModuleAction> createModuleActions;

    /** Available Create Module actions with filters applied */
    //private final ArrayList<RemoteCreateModuleAction> filteredActions = new ArrayList<RemoteCreateModuleAction>();

    /** Name of Created module */
    private String created;

    /** Created Module - set when changed event is received via runtime listener */
    private FrameworkElement createdModule;

    public CreateModuleDialog(Frame owner) {
        super(owner, true);
    }

    /**
     * Show dialog to create new module
     * (if module was created, getCreated() can be used to query name)
     *
     * @param parent to create module in
     */
    public void show(FrameworkElement parent) {
        this.parent = parent;
        setTitle("Create Module");

        // create components
        jlist = new FilteredList<RemoteCreateModuleAction>(this);
        name = new JTextField();
        name.addCaretListener(this);
        addComponent("Module name", name, 0, false);
        filter = new JTextField();
        filter.addCaretListener(jlist);
        addComponent("Type name filter", filter, 1, false);
        JPanel library = new JPanel();
        BorderLayout bl = new BorderLayout();
        bl.setHgap(4);
        library.setLayout(bl);
        group = new JComboBox();
        group.addActionListener(jlist);
        library.add(group, BorderLayout.CENTER);
        load = new JButton("Load...");
        load.addActionListener(this);
        library.add(load, BorderLayout.EAST);
        addComponent("Module library", library, 2, false);
        jlist.addListSelectionListener(this);
        jlist.setPreferredSize(new Dimension(350, 400));
        jlist.setFont(filter.getFont());
        addComponent("", jlist, 3, true);

        // create buttons
        JPanel buttons = new JPanel();
        cancel = createButton("Cancel", buttons);
        next = createButton("Next", buttons);
        create = createButton("Create & Edit", buttons);
        addComponent("", buttons, 4, false);

        // get create module actions
        updateCreateModuleActions(RemoteRuntime.find(parent).getAdminInterface().getRemoteModuleTypes());

        // show dialog
        getRootPane().setDefaultButton(create);
        pack();
        setVisible(true);

    }

    /**
     * Fill groups and list
     *
     * @param createModuleActions Current create module actions
     */
    public void updateCreateModuleActions(List<RemoteCreateModuleAction> createModuleActions) {
        if (createModuleActions == null) {
            return;
        }
        SortedSet<Object> groups = new TreeSet<Object>();
        groups.add("all");
        for (RemoteCreateModuleAction rcma : createModuleActions) {
            groups.add(rcma.groupName);
        }
        group.setModel(new DefaultComboBoxModel(groups.toArray()));
        updateButtonState();
        jlist.setElements(createModuleActions);
    }

    @Override
    public int accept(RemoteCreateModuleAction rcma) {
        String f = filter.getText();
        String g = group.getSelectedItem().toString();
        if ("all".equals(g)) {
            g = null;
        }
        if (g == null || rcma.groupName.equals(g)) {
            if (rcma.name.toLowerCase().contains(f.toLowerCase())) {
                if (rcma.name.toLowerCase().startsWith(f.toLowerCase())) {
                    return 0;
                } else {
                    return 1;
                }
            }
        }
        return -1;
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        assert(e.getSource() == name);
        updateButtonState();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == cancel) {
            close();
        } else if (e.getSource() == load) {
            RemoteRuntime rr = RemoteRuntime.find(parent);
            List<String> libs = rr.getAdminInterface().getModuleLibraries();
            if (libs != null && libs.size() > 0) {
                String load = new LoadModuleLibraryDialog((JFrame)getOwner()).show(libs);
                if (load != null) {
                    updateCreateModuleActions(rr.getAdminInterface().loadModuleLibrary(load));
                }
            } else {
                Finstruct.showErrorMessage("There don't appear to be any more modules to load", false, false);
            }
        } else if (e.getSource() == next || e.getSource() == create) {
            RemoteCreateModuleAction rcma = jlist.getSelectedValue();
            StructureParameterList spl = null;
            if (e.getSource() == next) {
                spl = rcma.parameters.instantiate();
                ParameterEditDialog ped = new ParameterEditDialog(this);
                setVisible(false);
                ped.show(spl, parent);
                if (!ped.userPressedOk()) {
                    setVisible(true);
                    return;
                }
            }

            // create module
            String[] sa = null;
            if (spl != null) {
                sa = new String[spl.size()];
                for (int i = 0; i < spl.size(); i++) {
                    sa[i] = spl.get(i).serializeValue();
                }
            }

            RemoteRuntime rr = RemoteRuntime.find(parent);

            // wait for creation and possibly open dialog for editing parameters
            synchronized (this) {

                try {
                    parent.getRuntime().addListener(this);
                    created = name.getText();
                    long moduleCreatedAt = System.currentTimeMillis();
                    if (rr.getAdminInterface().createModule(rcma, name.getText(), rr.getRemoteHandle(parent), sa)) {
                        while (createdModule == null && System.currentTimeMillis() < moduleCreatedAt + 2000) {
                            try {
                                wait(500);
                            } catch (InterruptedException e1) {
                            }
                        }

                        if (createdModule == null) {
                            Finstruct.showErrorMessage("Couldn't find & edit created element", false, false);
                        } else {

                            // possibly show edit dialog
                            new ParameterEditDialog(this).show(createdModule, false);
                        }
                    }

                } finally {
                    parent.getRuntime().removeListener(this);
                }
            }

            close();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        updateButtonState();
        if (name.getText().equals(autoSetName)) {
            autoSetName = jlist.getSelectedValue().name;
            name.setText(autoSetName);
        } else {
            autoSetName = "";
        }
    }

    /**
     * Updates button state
     */
    private void updateButtonState() {
        RemoteCreateModuleAction rcma = jlist.getSelectedValue();
        boolean b = rcma != null && name.getText().length() > 0;
        create.setEnabled(b && rcma.parameters.size() == 0);
        next.setEnabled(b && rcma.parameters.size() > 0);
    }

    @Override
    public void runtimeChange(byte changeType, final FrameworkElement element) {
        if (changeType == RuntimeListener.ADD && element.getParent() == parent) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (CreateModuleDialog.this) {
                        if (element.getDescription().equals(created)) {
                            createdModule = element;
                            CreateModuleDialog.this.notifyAll();
                        }
                    }
                }
            }).start();
        }
    }

    @Override
    public void runtimeEdgeChange(byte changeType, AbstractPort source, AbstractPort target) {}
}