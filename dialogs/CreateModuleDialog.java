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
package org.finroc.tools.finstruct.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import org.finroc.core.remote.RemoteCreateAction;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteStaticParameterList;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.util.FilteredList;
import org.finroc.tools.gui.util.treemodel.InterfaceTreeModel;
import org.rrlib.serialization.Register;

/**
 * @author Max Reichardt
 *
 * Create Module Dialog
 */
public class CreateModuleDialog extends MGridBagDialog implements ActionListener, CaretListener, ListSelectionListener, FilteredList.Filter<RemoteCreateAction>, ListDataListener, TreeModelListener {

    /** UID */
    private static final long serialVersionUID = -349194234472122705L;

    /** Dialog components */
    private JTextField filter, name;
    private JComboBox<Object> group;
    private FilteredList<RemoteCreateAction> jlist;
    private JButton next, cancel, create, load;

    /** Parent framework element */
    private RemoteFrameworkElement parent;

    /** Last auto-set name (when clicking on list) */
    private String autoSetName = "";

    /** All Create Module actions */
    //private List<RemoteCreateModuleAction> createModuleActions;

    /** Available Create Module actions with filters applied */
    //private final ArrayList<RemoteCreateModuleAction> filteredActions = new ArrayList<RemoteCreateModuleAction>();

    /** Name of Created module */
    private String created;

    /** Created Module - set when changed event is received via runtime listener */
    private RemoteFrameworkElement createdModule;

    /** Tree model of remote framework elements */
    private InterfaceTreeModel treeModel;

    public CreateModuleDialog(Frame owner) {
        super(owner, true);
    }

    /**
     * Show dialog to create new module
     * (if module was created, getCreated() can be used to query name)
     *
     * @param parent to create module in
     */
    public void show(RemoteFrameworkElement parent, InterfaceTreeModel treeModel) {
        this.parent = parent;
        this.treeModel = treeModel;
        setTitle("Create Module");

        // create components
        jlist = new FilteredList<>(this);
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
        group = new JComboBox<>();
        group.addActionListener(jlist);
        library.add(group, BorderLayout.CENTER);
        load = new JButton("Load...");
        load.addActionListener(this);
        library.add(load, BorderLayout.EAST);
        addComponent("Module library", library, 2, false);
        jlist.addListSelectionListener(this);
        jlist.setFont(filter.getFont());
        JScrollPane jlistScrollPane = new JScrollPane(jlist);
        jlistScrollPane.setPreferredSize(new Dimension(425, 400));
        addComponent("", jlistScrollPane, 3, true);
        jlist.getModel().addListDataListener(this);

        // create buttons
        JPanel buttons = new JPanel();
        cancel = createButton("Cancel", buttons);
        next = createButton("Next", buttons);
        create = createButton("Create & Edit", buttons);
        addComponent("", buttons, 4, false);

        // get create module actions
        Register<RemoteCreateAction> createActions = RemoteRuntime.find(parent).getCreateActions();
        updateCreateModuleActions(createActions);

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
    public void updateCreateModuleActions(Register<RemoteCreateAction> createModuleActions) {
        if (createModuleActions == null) {
            return;
        }
        SortedSet<Object> groups = new TreeSet<Object>();
        ArrayList<RemoteCreateAction> actions = new ArrayList<>(createModuleActions.size());
        groups.add("all");
        for (int i = 0, n = createModuleActions.size(); i < n; i++) {
            RemoteCreateAction action = createModuleActions.get(i);
            groups.add(action.getGroupName());
            actions.add(action);
        }
        group.setModel(new DefaultComboBoxModel<Object>(groups.toArray()));
        updateButtonState();
        jlist.setElements(actions);
    }

    @Override
    public int accept(RemoteCreateAction rcma) {
        String f = filter.getText();
        String g = group.getSelectedItem().toString();
        if ("all".equals(g)) {
            g = null;
        }
        if (g == null || rcma.getGroupName().equals(g)) {
            if (rcma.getName().toLowerCase().contains(f.toLowerCase())) {
                if (rcma.getName().toLowerCase().startsWith(f.toLowerCase())) {
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
                    updateCreateModuleActions(rr.loadModuleLibrary(load));
                }
            } else {
                Finstruct.showErrorMessage("There don't appear to be any more modules to load", false, false);
            }
        } else if (e.getSource() == next || e.getSource() == create) {
            RemoteCreateAction rcma = jlist.getSelectedValue();
            RemoteStaticParameterList spl = null;
            if (e.getSource() == next) {
                spl = rcma.getParameters().instantiate();
                ParameterEditDialog ped = new ParameterEditDialog(this);
                setVisible(false);
                ped.show(spl, parent);
                if (!ped.userPressedOk()) {
                    setVisible(true);
                    return;
                }
            }

            RemoteRuntime rr = RemoteRuntime.find(parent);

            // wait for creation and possibly open dialog for editing parameters
            String error = "";

            boolean timerActive = false;
            try {
                treeModel.addTreeModelListener(this);
                created = name.getText();
                error = rr.getAdminInterface().createModule(rcma, name.getText(), parent.getRemoteHandle(), spl);
                if (error.length() == 0) {
                    Timer timer = new Timer(2000, this);
                    timer.setRepeats(false);
                    timer.start();
                    timerActive = true;
                }

            } finally {
                if (!timerActive) {
                    treeModel.removeTreeModelListener(this);
                }
            }

            if (error.length() != 0) {
                Finstruct.showErrorMessage("Error creating module: " + error, false, false);
            }
        } else if (e.getSource() instanceof Timer) {
            if (createdModule == null) {
                Finstruct.showErrorMessage("Couldn't find & edit created element", false, false);
                treeModel.removeTreeModelListener(this);
            }
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        updateButtonState();
        if (jlist.getSelectedValue() == null) {
            return;
        } else if (name.getText().equals(autoSetName)) {
            autoSetName = jlist.getSelectedValue().getName();
            name.setText(autoSetName);
        } else {
            autoSetName = "";
        }
    }

    /**
     * Updates button state
     */
    private void updateButtonState() {
        RemoteCreateAction rcma = jlist.getSelectedValue();
        boolean b = rcma != null && name.getText().length() > 0;
        boolean hasParameters = b && rcma.getParameters() != null && rcma.getParameters().size() > 0;
        create.setEnabled(b && (!hasParameters));
        next.setEnabled(b && hasParameters);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        valueChanged(null);
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        valueChanged(null);
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        valueChanged(null);
    }


    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        if (e.getTreePath().getLastPathComponent() == parent) {
            for (Object o : e.getChildren()) {
                if (o instanceof RemoteFrameworkElement && o.toString().equals(created)) {
                    createdModule = (RemoteFrameworkElement)o;
                    treeModel.removeTreeModelListener(this);

                    // possibly show edit dialog
                    try {
                        new ParameterEditDialog(this).show(createdModule, false, false);
                    } catch (Exception exception) {
                        Finstruct.showErrorMessage(exception, true);
                    }
                    close();
                    return;
                }
            }
        }
    }
    @Override public void treeNodesChanged(TreeModelEvent e) {}
    @Override public void treeNodesRemoved(TreeModelEvent e) {}
    @Override public void treeStructureChanged(TreeModelEvent e) {}
}
