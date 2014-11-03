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
import java.util.EventObject;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.CellEditorListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.finroc.core.datatype.CoreBoolean;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory.CoreBooleanAdapter;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory.CoreSerializableAdapter;
import org.finroc.tools.gui.util.propertyeditor.BooleanEditor;
import org.finroc.tools.gui.util.propertyeditor.ComponentFactory;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.rrlib.serialization.BinarySerializable;
import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.Serialization;
import org.rrlib.serialization.rtti.DataTypeBase;

/**
 * @author Max Reichardt
 *
 * JTable model for property lists
 * Also provides a default table cell renderer for Finroc types
 */
@SuppressWarnings("rawtypes")
public class PropertyEditorTableModel /*extends DefaultCellEditor*/ implements TableModel { /*, TableCellRenderer*/

    /** UID */
    private static final long serialVersionUID = -6175045698165600429L;

    /** Name of first column */
    private String firstColumnName;

    /** Properties that should be shown/accessed by list */
    private ArrayList<PropertyAccessor> properties = new ArrayList<PropertyAccessor>();

    /** Property render components */
    //private ArrayList<Component> cellRenderers = new ArrayList<Component>();

    /** Property edit components */
    //private ArrayList<DefaultCellEditor> cellEditors = new ArrayList<DefaultCellEditor>();

    /** Listener list */
    private ArrayList<TableModelListener> listeners = new ArrayList<TableModelListener>();

    /** Default table cell renderer */
    private DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer();

    /**
     * @param firstColumnName Name of first column
     * @param properties Properties that should be shown/accessed by list
     */
    @SuppressWarnings("unchecked")
    public PropertyEditorTableModel(String firstColumnName, List<PropertyAccessor> rawProperties, ComponentFactory... componentFactories) {
        //super(defaultEditorComponent);
        this.firstColumnName = firstColumnName;
        for (PropertyAccessor acc : rawProperties) {
            try {
                Component renderer = null;
                DefaultCellEditor editor = null;
                if (CoreBoolean.class.isAssignableFrom(acc.getType())) {
                    properties.add(new FinrocComponentFactory.CoreBooleanAdapter((PropertyAccessor<CoreBoolean>)acc));
                } else if (acc.getType().isEnum() || acc.getType().equals(EnumValue.class)) {
                    properties.add(acc);
                    DataTypeBase type = ((EnumValue)acc.get()).getType();
                    String[] constants = new String[type.getEnumConstants().length];
                    EnumValue ev = new EnumValue(type);
                    for (int i = 0; i < constants.length; i++) {
                        ev.setIndex(i);
                        constants[i] = ev.toString();
                    }
                    JComboBox comboBox = new JComboBox(constants);
                    editor = new DefaultCellEditor(comboBox);
                } else if (BinarySerializable.class.isAssignableFrom(acc.getType()) && Serialization.isStringSerializable(acc.getType())) {
                    DataTypeBase dt = DataTypeBase.findType(acc.getType(), null);
                    properties.add(new FinrocComponentFactory.CoreSerializableAdapter((PropertyAccessor<BinarySerializable>)acc, acc.getType(), dt));
                } else {
                    continue;
                }
                //cellRenderers.add(renderer);
                //cellEditors.add(editor);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int getRowCount() {
        return properties.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return columnIndex == 0 ? firstColumnName : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? String.class : Object.class;
    }

    /**
     * @param rowIndex Row index
     * @param columnIndex Column index
     * @return Type of cell at specified position
     */
    public Class<?> getCellClass(int rowIndex, int columnIndex) {
        return columnIndex == 0 ? String.class : properties.get(rowIndex).getType();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex != 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return properties.get(rowIndex).getName();
        } else {
            try {
                return properties.get(rowIndex).get();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        try {
            properties.get(rowIndex).set(aValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }
//
//  @Override
//  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
//      Component c = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
//      if (column == 0) {
//          //c.setBackground(c.getBackground().darker());
//          return c;
//      } else {
//          return cellRenderers.get(row) != null ? cellRenderers.get(row) : c;
//      }
//  }
//
//  @Override
//  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
//      if (column == 0) {
//          return null;
//      } else {
//          if (cellEditors.get(row) == null) {
//              return super.getTableCellEditorComponent(table, value, isSelected, row, column);
//          } else {
//              DefaultCellEditor cellEditor = cellEditors.get(row);
//              Component c = cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
//              this.delegate = cellEditor.delegate;
//              return c;
//          }
//      }
//  }
}