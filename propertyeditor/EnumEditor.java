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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.naming.OperationNotSupportedException;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import org.finroc.tools.gui.FinrocGUI;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.rtti.DataTypeBase;
import org.rrlib.finroc_core_utils.serialization.EnumValue;

/**
 * @author Max Reichardt
 *
 * Editor with ComboBox
 */
public class EnumEditor extends PropertyEditComponent<EnumValue> {

    /** UID */
    private static final long serialVersionUID = -613623582568285L;

    protected JComboBox jcmb;
    protected DataTypeBase dataType;

    @Override
    protected void createAndShow() {
        try {
            jcmb = new JComboBox();
            valueUpdated(getCurWidgetValue());
        } catch (Exception e) {
            FinrocGUI.logDomain.log(LogLevel.LL_ERROR, toString(), e);
        }
        jcmb.setPreferredSize(new Dimension(TEXTFIELDWIDTH, jcmb.getPreferredSize().height));
        add(jcmb, BorderLayout.WEST);
        jcmb.setEnabled(isModifiable());
    }

    @Override
    public void createAndShowMinimal(EnumValue object) throws OperationNotSupportedException {
        try {
            jcmb = new JComboBox();
            valueUpdated(object);
        } catch (Exception e) {
            FinrocGUI.logDomain.log(LogLevel.LL_ERROR, toString(), e);
        }
        add(jcmb);
        jcmb.setEnabled(isModifiable());
    }

    @Override
    public EnumValue getCurEditorValue() {
        if (dataType == null) {
            return null;
        }
        EnumValue ev = new EnumValue(dataType);
        ev.set(jcmb.getSelectedIndex());
        return ev;
    }

    @Override
    protected void valueUpdated(EnumValue t) {
        if (t == null) {
            jcmb.setModel(new DefaultComboBoxModel());
            return;
        }
        if (t.getType() != dataType) {
            dataType = t.getType();
            String[] constants = new String[dataType.getEnumConstants().length];
            EnumValue ev = new EnumValue(t.getType());
            for (int i = 0; i < constants.length; i++) {
                ev.set(i);
                constants[i] = ev.toString();
            }
            jcmb.setModel(new DefaultComboBoxModel(constants));
        }
        jcmb.setSelectedIndex(t.getOrdinal());
    }
}
