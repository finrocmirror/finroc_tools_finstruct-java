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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.gui.util.gui.MDialog;

/**
 * @author Max Reichardt
 *
 * Dialog to choose a module library to load
 */
public class LoadModuleLibraryDialog extends MDialog implements MouseListener {

    /** UID */
    private static final long serialVersionUID = 9130805331512378849L;

    /** Buttons */
    private JButton load, cancel;
    private JList<String> list;
    private String selected;

    /** Double-Click-detection */
    private long lastClick = 0;
    private String lastSelected = "";

    public LoadModuleLibraryDialog(MDialog owner) {
        super(owner, true);
    }

    public LoadModuleLibraryDialog(JFrame owner) {
        super(owner, true);
    }


    /**
     * Show dialog
     */
    public String show(List<String> moduleLibraries) {
        setTitle("Load Module Library");
        JPanel main = new JPanel();
        main.setLayout(new BorderLayout());
        main.setBorder(new EmptyBorder(10, 10, 10, 10));

        // create components
        list = new JList<String>(moduleLibraries.toArray(new String[0]));
        //list.setPreferredSize(new Dimension(350, 550));
        list.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        list.addMouseListener(this);
        JScrollPane js = new JScrollPane(list);
        js.setPreferredSize(new Dimension(380, 550));
        main.add(js, BorderLayout.CENTER);

        // create buttons
        JPanel buttons = new JPanel();
        cancel = createButton("Cancel", buttons);
        load = createButton("Load", buttons);
        main.add(buttons, BorderLayout.SOUTH);

        // show dialog
        getRootPane().getContentPane().add(main);
        getRootPane().setDefaultButton(load);
        pack();
        selected = null;
        setVisible(true);
        return selected;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == load) {
            selected = list.getSelectedValue().toString();
            close();
        } else if (e.getSource() == cancel) {
            selected = null;
            close();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {
        if (System.currentTimeMillis() - lastClick < Finstruct.DOUBLE_CLICK_DELAY && lastSelected == list.getSelectedValue().toString()) {
            selected = list.getSelectedValue().toString();
            close();
        } else {
            lastClick = System.currentTimeMillis();
            lastSelected = list.getSelectedValue().toString();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
