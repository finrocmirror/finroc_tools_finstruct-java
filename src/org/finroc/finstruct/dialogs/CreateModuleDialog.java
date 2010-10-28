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
package org.finroc.finstruct.dialogs;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.finroc.core.FrameworkElement;
import org.finroc.core.parameter.StructureParameterList;
import org.finroc.core.plugin.RemoteCreateModuleAction;
import org.finroc.core.port.net.RemoteRuntime;

/**
 * @author max
 *
 * Create Module Dialog
 */
public class CreateModuleDialog extends JDialog implements ActionListener, CaretListener, ListSelectionListener {

    /** UID */
    private static final long serialVersionUID = -349194234472122705L;

    /** Dialog components */
    private JTextField filter, name;
    private JComboBox group;
    private JList jlist;
    private JButton next, cancel, create;

    /** Parent framework element */
    private FrameworkElement parent;

    /** All Create Module actions */
    private List<RemoteCreateModuleAction> createModuleActions;

    /** Available Create Module actions with filters applied */
    private final ArrayList<RemoteCreateModuleAction> filteredActions = new ArrayList<RemoteCreateModuleAction>();

    /** Data model listeners */
    private final ArrayList<ListDataListener> listener = new ArrayList<ListDataListener>();

    /** Contains created module name - after dialog closed */
    private volatile String created = null;

    /** Timestamp when module was created */
    private volatile long moduleCreatedAt = 0;

    /** Gridbag constraints for layout */
    private final GridBagConstraints gbc = new GridBagConstraints();

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

        // create panels
        JPanel main = new JPanel();
        main.setLayout(new GridBagLayout());
        JPanel buttons = new JPanel();
        getContentPane().add(main);

        // create components
        name = new JTextField();
        name.addCaretListener(this);
        addComponent(main, "Module name", name, 0, false);
        filter = new JTextField();
        filter.addCaretListener(this);
        addComponent(main, "Type name filter", filter, 1, false);
        group = new JComboBox();
        group.addActionListener(this);
        addComponent(main, "Module group filter", group, 2, false);
        jlist = new JList();
        jlist.addListSelectionListener(this);
        jlist.setPreferredSize(new Dimension(200, 400));
        jlist.setFont(filter.getFont());
        addComponent(main, "", jlist, 3, true);

        // create buttons
        cancel = new JButton("Cancel");
        cancel.addActionListener(this);
        buttons.add(cancel);
        next = new JButton("Next");
        next.addActionListener(this);
        buttons.add(next);
        create = new JButton("Create & Edit");
        create.addActionListener(this);
        buttons.add(create);
        addComponent(main, "", buttons, 4, false);

        // get create module actions
        createModuleActions = RemoteRuntime.find(parent).getAdminInterface().getRemoteModuleTypes();

        // fill groups and list
        SortedSet<Object> groups = new TreeSet<Object>();
        groups.add("all");
        for (RemoteCreateModuleAction rcma : createModuleActions) {
            groups.add(rcma.groupName);
        }
        group.setModel(new DefaultComboBoxModel(groups.toArray()));
        updateListModel();
        updateButtonState();
        jlist.setModel(new Model());
        jlist.setSelectedIndex(0);

        // show dialog
        getRootPane().setDefaultButton(create);
        pack();
        setVisible(true);

    }

    /**
     * Add component to dialog
     *
     * @param panel Panel to add to
     * @param label Label
     * @param c Component to add
     * @param i Component index
     * @param resizeY Resize in Y direction - when dialog ist resized?
     */
    private void addComponent(JPanel panel, String label, JComponent c, int i, boolean resizeY) {
        gbc.gridy = i;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.00001;
        gbc.weighty = resizeY ? 0.2 : 0.00001;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.NONE;
        JLabel jl = new JLabel(label);
        panel.add(jl, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 0.2;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(c, gbc);
    }

    /**
     * updates list according to filter
     */
    private void updateListModel() {
        SortedSet<RemoteCreateModuleAction> front = new TreeSet<RemoteCreateModuleAction>();
        SortedSet<RemoteCreateModuleAction> back = new TreeSet<RemoteCreateModuleAction>();
        String f = filter.getText();
        String g = group.getSelectedItem().toString();
        if ("all".equals(g)) {
            g = null;
        }
        for (RemoteCreateModuleAction rcma : createModuleActions) {
            if (g == null || rcma.groupName.equals(g)) {
                if (rcma.name.toLowerCase().contains(f.toLowerCase())) {
                    if (rcma.name.toLowerCase().startsWith(f.toLowerCase())) {
                        front.add(rcma);
                    } else {
                        back.add(rcma);
                    }
                }
            }
        }
        ArrayList<RemoteCreateModuleAction> newList = new ArrayList<RemoteCreateModuleAction>();
        newList.addAll(front);
        newList.addAll(back);

        // compare lists - and return if nothing changed
        if (newList.size() == filteredActions.size()) {
            boolean diff = false;
            for (int i = 0; i < newList.size(); i++) {
                if (!newList.get(i).equals(filteredActions.get(i))) {
                    diff = true;
                    break;
                }
            }
            if (!diff) {
                return;
            }
        }

        filteredActions.clear();
        filteredActions.addAll(newList);

        // notify listeners
        for (ListDataListener l : listener) {
            l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, filteredActions.size()));
        }
        if (filteredActions.size() > 0) {
            jlist.setSelectedIndex(0);
        }
    }


    @Override
    public void caretUpdate(CaretEvent e) {
        if (e.getSource() == filter) {
            updateListModel();
        } else if (e.getSource() == name) {
            updateButtonState();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == group) {
            updateListModel();
        } else if (e.getSource() == cancel) {
            close();
        } else if (e.getSource() == next || e.getSource() == create) {
            RemoteCreateModuleAction rcma = (RemoteCreateModuleAction)jlist.getSelectedValue();
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
            created = parent.getQualifiedLink() + "/" + name.getText();
            moduleCreatedAt = System.currentTimeMillis();
            rr.getAdminInterface().createModule(rcma, name.getText(), rr.getRemoteHandle(parent), sa);

            close();
        }
    }

    /**
     * Close dialog
     */
    private void close() {
        setVisible(false);
        getRootPane().removeAll();
        dispose();
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        updateButtonState();
    }

    /**
     * Updates button state
     */
    private void updateButtonState() {
        RemoteCreateModuleAction rcma = (RemoteCreateModuleAction)jlist.getSelectedValue();
        boolean b = rcma != null && name.getText().length() > 0;
        create.setEnabled(b && rcma.parameters.size() == 0);
        next.setEnabled(b && rcma.parameters.size() > 0);
    }

    /**
     * @return created module name - after dialog closed (null if creation was cancelled)
     */
    public String getCreated() {
        return created;
    }

    /**
     * @return Timestamp when module was created
     */
    public long getModuleCreatedAt() {
        return moduleCreatedAt;
    }

    /**
     * List model for JList
     */
    private class Model implements ListModel {

        @Override
        public int getSize() {
            return filteredActions.size();
        }

        @Override
        public Object getElementAt(int index) {
            return filteredActions.get(index);
        }

        @Override
        public void addListDataListener(ListDataListener l) {
            listener.add(l);
        }

        @Override
        public void removeListDataListener(ListDataListener l) {
            listener.remove(l);
        }
    }
}