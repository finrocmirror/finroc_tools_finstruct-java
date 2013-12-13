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
import java.awt.Dimension;
import java.io.StringReader;

import javax.naming.OperationNotSupportedException;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.finroc.core.datatype.XML;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.rrlib.xml.XMLDocument;
import org.xml.sax.InputSource;

/**
 * @author Max Reichardt
 *
 * TODO add syntax highlighting
 */
public class XMLEditor extends PropertyEditComponent<XML> {

    /** UID */
    private static final long serialVersionUID = 2486687318726499512L;

    private JTextArea jta;

    protected void createAndShow() throws Exception {
        jta = new JTextArea();
        jta.setMinimumSize(new Dimension(TEXTFIELDWIDTH, 100));
        //jta.setPreferredSize(new Dimension(TEXTFIELDWIDTH, 100));
        valueUpdated(getCurWidgetValue());
        JPanel jp = new JPanel();
        jp.setBorder(BorderFactory.createTitledBorder(""/*getPropertyName()*/));
        jp.setLayout(new BorderLayout());
        jp.setPreferredSize(new Dimension(TEXTFIELDWIDTH, 100));
        jp.add(new JScrollPane(jta), BorderLayout.CENTER);
        add(jp, BorderLayout.CENTER);
        jta.setEnabled(isModifiable());
    }

    @Override
    public void createAndShowMinimal(XML s) throws OperationNotSupportedException {
        throw new OperationNotSupportedException();
    }

    @Override
    public XML getCurEditorValue() throws Exception {
        new XMLDocument(new InputSource(new StringReader(jta.getText())), false); // throws exception if invalid XML
        XML xml = new XML();
        xml.set(jta.getText());
        return xml;
    }

    @Override
    protected void valueUpdated(XML t) {
        jta.setText(t == null ? "" : t.toString());
    }

    @Override
    public boolean isResizable() {
        return true;
    }
}
