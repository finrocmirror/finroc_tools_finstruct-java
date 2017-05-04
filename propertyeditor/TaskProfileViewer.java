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

import java.awt.BorderLayout;
import java.awt.Color;

import javax.naming.OperationNotSupportedException;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.plugins.data_types.TaskProfile;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;

/**
 * @author Max Reichardt
 *
 * Editor component for viewing task profiles available with '--profiling' option
 */
public class TaskProfileViewer extends PropertyEditComponent<TaskProfile.List> {

    /** UID */
    private static final long serialVersionUID = -613623582568285L;

    private JTable jtable;
    private TaskProfile.List currentProfile;
    private static final String[] columnNames = new String[] { "Name", "Last", "Average", "Max", "Total" };
    private RemoteRuntime runtime;
    private static final Color colorTotal = Color.LIGHT_GRAY;
    private static final Color[] classificationColors = new Color[] { ConnectionPanel.sensorInterfaceColor, ConnectionPanel.controllerInterfaceColor, ConnectionPanel.rightBackgroundColor };
    static class Renderer extends DefaultTableCellRenderer {

        @Override
        public void setValue(Object value) {
            super.setValue(value);
            setBackground(((ColoredString)value).color);
        }
    }
    private static final Renderer renderer = new Renderer();

    private class ColoredString {

        String string;
        Color color;

        ColoredString(String string, Color color) {
            this.string = string;
            this.color = color;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    public TaskProfileViewer(RemoteRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void createAndShow() {
        try {
            jtable = new JTable();
            jtable.setAutoCreateRowSorter(true);
            jtable.setDefaultRenderer(Object.class, renderer);
            valueUpdated(getCurWidgetValue());
        } catch (Exception e) {
            Log.log(LogLevel.ERROR, this, e);
        }
        //jtable.setMinimumSize(new Dimension(300, 200));
        add(new JScrollPane(jtable), BorderLayout.CENTER);
    }

    @Override
    public void createAndShowMinimal(TaskProfile.List object) throws OperationNotSupportedException {
        try {
            jtable = new JTable();
            jtable.setAutoCreateRowSorter(true);
            jtable.setDefaultRenderer(Object.class, renderer);
            valueUpdated(object);
        } catch (Exception e) {
            Log.log(LogLevel.ERROR, this, e);
        }
        add(jtable);
    }

    @Override
    public TaskProfile.List getCurEditorValue() {
        return currentProfile;
    }

    @Override
    protected void valueUpdated(TaskProfile.List t) {
        if (t == null) {
            jtable.setModel(new DefaultTableModel(columnNames, 0));
            return;
        }
        Object[][] data = new Object[t.size()][];
        for (int i = 0; i < t.size(); i++) {
            TaskProfile taskProfile = t.get(i);
            data[i] = new Object[columnNames.length];
            Color color = i == 0 ? colorTotal : classificationColors[taskProfile.taskClassification.ordinal()];
            ColoredString name = new ColoredString("Total", color);
            data[i][0] = name;
            if (i > 0) {
                RemoteFrameworkElement element = runtime.elementLookup.get(taskProfile.handle);
                if (element.isInterface()) {
                    element = (RemoteFrameworkElement)element.getParent();
                }
                name.string = element == null ? "Unknown" : element.getQualifiedLink();
            }
            data[i][1] = new ColoredString(taskProfile.lastExecutionDuration.toString(), color);
            data[i][2] = new ColoredString(taskProfile.averageExecutionDuration.toString(), color);
            data[i][3] = new ColoredString(taskProfile.maxExecutionDuration.toString(), color);
            data[i][4] = new ColoredString(taskProfile.totalExecutionDuration.toString(), color);
        }
        jtable.setModel(new DefaultTableModel(data, columnNames));
        jtable.setShowHorizontalLines(true);
        jtable.setCellSelectionEnabled(false);
    }
}
