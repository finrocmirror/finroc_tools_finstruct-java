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
package org.finroc.tools.finstruct.dialogs;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.finroc.tools.gui.util.gui.MDialog;

/**
 * @author max
 *
 * MDialog based on GridBagLayout. On the left we have labels - on the right the respective components.
 */
public abstract class MGridBagDialog extends MDialog {

    /** UID */
    private static final long serialVersionUID = -8604199647417835758L;

    /** Gridbag constraints for layout */
    private final GridBagConstraints gbc = new GridBagConstraints();

    /** Main panel with grid bag layout */
    protected final JPanel mainPanel = new JPanel();

    public MGridBagDialog(Dialog owner, boolean modal) {
        super(owner, modal);
        mainPanel.setLayout(new GridBagLayout());
        getContentPane().add(mainPanel);
    }

    public MGridBagDialog(Frame owner, boolean modal) {
        super(owner, modal);
        mainPanel.setLayout(new GridBagLayout());
        getContentPane().add(mainPanel);
    }

    /**
     * Add component to dialog
     *
     * @param label Label
     * @param c Component to add
     * @param i Component index
     * @param resizeY Resize in Y direction - when dialog ist resized?
     */
    protected void addComponent(String label, JComponent c, int i, boolean resizeY) {
        gbc.gridy = i;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.00001;
        gbc.weighty = resizeY ? 0.2 : 0.00001;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.NONE;
        JLabel jl = new JLabel(label);
        mainPanel.add(jl, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 0.2;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(c, gbc);
    }
}
