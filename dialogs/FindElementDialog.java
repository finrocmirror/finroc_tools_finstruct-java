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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.finroc.core.remote.ModelNode;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.util.FilteredList;

/**
 * @author max
 *
 * Dialog to find element
 */
public class FindElementDialog extends MGridBagDialog implements FilteredList.Filter<FindElementDialog.WrappedFrameworkElement> {

    /** UID */
    private static final long serialVersionUID = 9130805331511729849L;

    /** Buttons */
    private JButton showInTree, showParent, show;
    private JTextField filter;
    private FilteredList<WrappedFrameworkElement> list;

    /** Finstruct reference */
    private Finstruct finstruct;

    public FindElementDialog(Frame owner, boolean modal) {
        super(owner, modal);
    }

    /**
     * Show dialog to find element
     */
    public void show(Finstruct fs) {
        setTitle("Find Element");
        this.finstruct = fs;
        JPanel main = new JPanel();
        main.setLayout(new GridBagLayout());

        // create components
        list = new FilteredList<WrappedFrameworkElement>(this);
        filter = new JTextField();
        filter.addCaretListener(list);
        addComponent("Element name filter", filter, 1, false);
        list.setPreferredSize(new Dimension(700, 600));
        list.setFont(filter.getFont());
        list.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        addComponent("Element", list, 3, true);

        // create buttons
        JPanel buttons = new JPanel();
        showInTree = createButton("Expand in Tree", buttons);
        showParent = createButton("Show parent", buttons);
        show = createButton("Show", buttons);
        addComponent("", buttons, 4, false);

        // create list model
        ArrayList<WrappedFrameworkElement> elements = new ArrayList<WrappedFrameworkElement>();
        for (ModelNode node : finstruct.getIoInterface().getRoot().getNodesBelow(null)) {
            elements.add(new WrappedFrameworkElement(node));
        }
        list.setElements(elements);

        // show dialog
        getRootPane().setDefaultButton(show);
        pack();
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        WrappedFrameworkElement node = list.getSelectedValue();
        if (node == null || node.fe == null) {
            return;
        }

        if (e.getSource() == showInTree) {
            finstruct.getConnectionPanel().expandOnly(true, node.fe);
        } else if (e.getSource() == showParent && node.fe.getParent() != null) {
            finstruct.showElement(node.fe.getParent());
        } else if (e.getSource() == show) {
            finstruct.showElement(node.fe);
        }
    }

    @Override
    public int accept(WrappedFrameworkElement t) {
        String f = filter.getText().toLowerCase();
        ModelNode fe = t.fe;
        if (fe.getName().toLowerCase().startsWith(f)) {
            return 0;
        } else if (fe.getName().toLowerCase().contains(f)) {
            return 1;
        } else if (fe.getQualifiedName('/').toLowerCase().contains(f)) {
            return 2;
        }
        return -1;
    }

    /**
     * Wraps framework elements for use in filtered list
     */
    public class WrappedFrameworkElement implements Comparable<WrappedFrameworkElement> {

        /** Wrapped framework element */
        private final ModelNode fe;

        private WrappedFrameworkElement(ModelNode fe) {
            this.fe = fe;
        }

        @Override
        public int compareTo(WrappedFrameworkElement o) {
            return fe.getQualifiedName('/').compareTo(o.fe.getQualifiedName('/'));
        }

        public String toString() {
            String qname = fe.getParent().getQualifiedName('/');
            if (qname.length() > 0) {
                return fe.getName() + "    (" + qname.substring(1) + ")";
            } else {
                return fe.getName();
            }
        }
    }
}
