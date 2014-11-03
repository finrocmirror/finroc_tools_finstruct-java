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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.Component;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.rtti.DataTypeBase;

/**
 * @author Max Reichardt
 *
 * JTable specialized for editing property lists
 */
public class PropertyEditorTable extends JTable {

    /** UID */
    private static final long serialVersionUID = 2377331321872181111L;

    /** Data model (same as in base class, but more specific type) */
    private PropertyEditorTableModel model;

    @SuppressWarnings("rawtypes")
    public PropertyEditorTable() {
        super(new PropertyEditorTableModel("", new ArrayList<PropertyAccessor>()));
    }

    /**
     * Create combo box model for specified enum type
     *
     * @param type Enum type
     * @return Combo box model
     */
    private static DefaultComboBoxModel<String> getEnumComboBoxModel(DataTypeBase type) {
        String[] constants = new String[type.getEnumConstants().length];
        EnumValue ev = new EnumValue(type);
        for (int i = 0; i < constants.length; i++) {
            ev.setIndex(i);
            constants[i] = ev.toString();
        }
        return new DefaultComboBoxModel<String>(constants);
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 0) {
            return super.getCellRenderer(row, column);
        } else {
            Class<?> type = model.getCellClass(row, column);
            if (type.isEnum() || type.equals(EnumValue.class)) {
                return new EnumRenderer(((EnumValue)model.getValueAt(row, column)).getType());
            } else {
                return super.getDefaultRenderer(type);
            }
        }
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
        if (column == 0) {
            return super.getCellEditor(row, column);
        } else {
            Class<?> type = model.getCellClass(row, column);
            if (type.isEnum() || type.equals(EnumValue.class)) {
                return new EnumEditor(((EnumValue)model.getValueAt(row, column)).getType());
            } else {
                return super.getDefaultEditor(type);
            }
        }
    }

    @Override
    public PropertyEditorTableModel getModel() {
        return model;
    }

    @Override
    public void setModel(TableModel dataModel) {
        if (dataModel instanceof PropertyEditorTableModel) {
            model = (PropertyEditorTableModel)dataModel;
            super.setModel(dataModel);

            for (int i = 0; i < model.getRowCount(); i++) {
                int height = this.getCellRenderer(i, 1).getTableCellRendererComponent(this, null, false, false, i, 1).getPreferredSize().height;
                this.setRowHeight(i, Math.max(this.getRowHeight(), height - 2));
            }
        } else {
            throw new RuntimeException("Model must be PropertyEditorTableModel");
        }
    }

    protected static class EnumRenderer extends JComboBox<String> implements TableCellRenderer {

        private static final long serialVersionUID = 3769138604085550020L;

        public EnumRenderer(DataTypeBase enumType) {
            super(getEnumComboBoxModel(enumType));
            this.setBorder(BorderFactory.createEmptyBorder());
            //this.setOpaque(false);
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            /*if (isSelected) {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }*/
            this.setSelectedIndex(value == null ? -1 : ((EnumValue)value).getIndex());

            /*if (hasFocus) {
                setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
            } else {
                setBorder(noFocusBorder);
            }*/
            return this;
        }
    }

    protected static class EnumEditor extends DefaultCellEditor {

        private static final long serialVersionUID = 3596025009074973294L;

        DataTypeBase enumType;

        public EnumEditor(DataTypeBase enumType) {
            super(new JComboBox<String>(getEnumComboBoxModel(enumType)));
            this.enumType = enumType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object getCellEditorValue() {
            EnumValue ev = new EnumValue(enumType);
            ev.setIndex(((JComboBox<String>)getComponent()).getSelectedIndex());
            return ev;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JComboBox<String> component = ((JComboBox<String>)getComponent());
            component.setSelectedIndex(((EnumValue)value).getIndex());
            return component;
        }
    }

}
