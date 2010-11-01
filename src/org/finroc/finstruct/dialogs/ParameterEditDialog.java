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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import org.finroc.core.FrameworkElement;
import org.finroc.core.parameter.StructureParameterList;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.finstruct.Finstruct;
import org.finroc.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.finstruct.propertyeditor.StructureParameterAccessor;
import org.finroc.gui.util.gui.MDialog;
import org.finroc.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.gui.util.propertyeditor.StandardComponentFactory;
import org.finroc.log.LogLevel;

/**
 * @author max
 *
 * Dialog to edit (structure) parameters of framework elements
 */
public class ParameterEditDialog extends MDialog implements ActionListener {

    /** UID */
    private static final long serialVersionUID = 1508066944645377137L;

    /** Main properties panel */
    private PropertiesPanel propPanel;

    /** Did user close this dialog by pressing "ok"? */
    private boolean pressedOk = false;

    /** Buttons */
    private JButton cancelBack, apply, okCreate;

    /** Remote Element to edit parameters of */
    private FrameworkElement element;

    /** Remote Element property list */
    private StructureParameterList elementParamList;

    public ParameterEditDialog(Frame owner) {
        super(owner, true);
    }

    public ParameterEditDialog(JDialog owner) {
        super(owner, true);
    }

    /**
     * @param element Remote Element to edit parameters of
     * @param warnIfNoParameters Warn if no parameters could be retrieved?
     */
    public void show(FrameworkElement element, boolean warnIfNoParameters) {
        this.element = element;
        RemoteRuntime rr = RemoteRuntime.find(element);
        elementParamList = (StructureParameterList)rr.getAdminInterface().getAnnotation(rr.getRemoteHandle(element), StructureParameterList.TYPE);
        if (elementParamList != null) {
            show(elementParamList, element);
        } else if (warnIfNoParameters) {
            Finstruct.showErrorMessage("Cannot get parameter list for " + element.getQualifiedLink(), false, false);
        }
    }

    /**
     * @param spl StructureParameterList to edit parameters of
     * @param fe Framework element (edited - or parent)
     */
    public void show(StructureParameterList spl, FrameworkElement fe) {

        // Create property panel
        propPanel = new PropertiesPanel(new FinrocComponentFactory(fe), new StandardComponentFactory());
        propPanel.init(StructureParameterAccessor.createForList(spl), true);
        getContentPane().add(propPanel, BorderLayout.CENTER);

        // Create buttons
        JPanel buttons = new JPanel();
        getContentPane().add(buttons, BorderLayout.SOUTH);
        cancelBack = createButton(element == null ? "Back" : "Cancel", buttons);
        if (element != null) {
            apply = createButton("Apply", buttons);
        }
        okCreate = createButton(element == null ? "Create" : "Apply & Close", buttons);

        // show dialog
        pack();
        setVisible(true);
    }

    /**
     * @return Did user close this dialog by pressing "ok"?
     */
    public boolean userPressedOk() {
        return pressedOk;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == cancelBack) {
            close();
        } else if (e.getSource() == apply || e.getSource() == okCreate) {

            // apply changes
            for (PropertyEditComponent<?> wpec : propPanel.getComponentList()) {
                try {
                    wpec.applyChanges();
                } catch (Exception e1) {
                    Finstruct.logDomain.log(LogLevel.LL_ERROR, "ParameterEditDialog", e1);
                }
            }
            if (element != null) {
                RemoteRuntime rr = RemoteRuntime.find(element);
                rr.getAdminInterface().setAnnotation(rr.getRemoteHandle(element), elementParamList);
            }

            if (e.getSource() == okCreate) {
                pressedOk = true;
                close();
            }
        }
    }
}