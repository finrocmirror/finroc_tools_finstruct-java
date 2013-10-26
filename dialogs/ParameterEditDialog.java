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
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import org.finroc.core.finstructable.EditableInterfaces;
import org.finroc.core.parameter.StaticParameterList;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.StaticParameterAccessor;
import org.finroc.tools.gui.util.gui.MDialog;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.rrlib.finroc_core_utils.log.LogLevel;

/**
 * @author Max Reichardt
 *
 * Dialog to edit (static) parameters of framework elements
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
    private RemoteFrameworkElement element;

    /** Remote Element property list */
    private StaticParameterList elementParamList;

    /**
     * Edit element's interfaces instead of parameters?
     * (Implemented as simple flag here - because process of editing parameters and interfaces is almost the same)
     */
    private boolean editInterfaces;

    public ParameterEditDialog(Frame owner) {
        super(owner, true);
    }

    public ParameterEditDialog(JDialog owner) {
        super(owner, true);
    }

    /**
     * @param element Remote Element to edit parameters of
     * @param warnIfNoParameters Warn if no parameters could be retrieved?
     * @param editInterfaces Edit element's interfaces instead of parameters?
     */
    public void show(RemoteFrameworkElement element, boolean warnIfNoParameters, boolean editInterfaces) {
        this.editInterfaces = editInterfaces;
        setTitle((editInterfaces ? "Edit Interfaces of " : "Edit Static Parameters of ") + element.getQualifiedName('/'));
        this.element = element;
        RemoteRuntime rr = RemoteRuntime.find(element);
        if (!editInterfaces) {
            elementParamList = (StaticParameterList)rr.getAdminInterface().getAnnotation(element.getRemoteHandle(), StaticParameterList.TYPE);
        } else {
            elementParamList = ((EditableInterfaces)rr.getAdminInterface().getAnnotation(element.getRemoteHandle(), EditableInterfaces.TYPE)).
                               getStaticParameterList();
        }
        if (elementParamList != null) {
            if (elementParamList.size() > 0) {
                show(elementParamList, element);
            } else {
                pressedOk = true;
            }
        } else if (warnIfNoParameters) {
            Finstruct.showErrorMessage("Cannot get parameter list for " + element.getQualifiedLink(), false, false);
        }
    }

    /**
     * @param spl StaticParameterList to edit parameters of
     * @param fe Framework element (edited - or parent)
     */
    public void show(StaticParameterList spl, RemoteFrameworkElement fe) {

        // Create property panel
        propPanel = new PropertiesPanel(new FinrocComponentFactory(fe), new StandardComponentFactory());
        propPanel.init(StaticParameterAccessor.createForList(spl), true);
        getContentPane().add(propPanel, BorderLayout.CENTER);

        // Create buttons
        JPanel buttons = new JPanel();
        getContentPane().add(buttons, BorderLayout.SOUTH);
        cancelBack = createButton(element == null ? "Back" : "Do not change", buttons);
        if (element != null && !(getOwner() instanceof CreateModuleDialog)) {
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
                    Finstruct.logDomain.log(LogLevel.ERROR, "ParameterEditDialog", e1);
                }
            }
            if (element != null) {
                RemoteRuntime rr = RemoteRuntime.find(element);
                if (!editInterfaces) {
                    rr.getAdminInterface().setAnnotation(element.getRemoteHandle(), elementParamList);
                } else {
                    EditableInterfaces editableInterfaces = new EditableInterfaces();
                    editableInterfaces.setStaticParameterList(elementParamList);
                    rr.getAdminInterface().setAnnotation(element.getRemoteHandle(), editableInterfaces);
                }
            }

            if (e.getSource() == okCreate) {
                pressedOk = true;
                close();
            }
        }
    }
}
