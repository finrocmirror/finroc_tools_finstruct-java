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
package org.finroc.tools.finstruct;

import java.io.File;

import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.xml.XMLDocument;
import org.rrlib.finroc_core_utils.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Finstruct's persistent settings stored as XML
 */
public class FinstructSettings {

    /** Settings file name */
    private static final String SETTINGSFILE = System.getProperty("user.home") + File.separator + ".finstruct";

    /** Settings XML file */
    private XMLDocument document;

    /**
     * @return FinstructSettings XML document. Loads or creates it if necessary.
     */
    private XMLDocument getSettings() {
        if (document == null) {
            try {
                if (new File(SETTINGSFILE).exists()) {
                    try {
                        document = new XMLDocument(SETTINGSFILE, false);
                        return document;
                    } catch (Exception e) {
                        Finstruct.logDomain.log(LogLevel.LL_WARNING, "FinstructSettings", "Could not parse settings file. Creating new one. ", e);
                        document = new XMLDocument();
                        document.addRootNode("finstruct-settings");
                    }
                } else {
                    document = new XMLDocument();
                    document.addRootNode("finstruct-settings");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return document;
    }

    /**
     * Returns top-level node with specified name (e.g. "bookmarks").
     * Creates this node if it does not exist.
     *
     * @param name Name of node
     * @return Node. Possibly existing node (with existing entries) - or newly created node.
     */
    public XMLNode getTopLevelNode(String name) {
        XMLNode root = getSettings().getRootNode();
        for (XMLNode.ConstChildIterator child = root.getChildrenBegin(); child.get() != null; child.next()) {
            if (child.get().getName().equals(name)) {
                return child.get();
            }
        }
        return root.addChildNode(name);
    }

    /**
     * Saves Settings to hard disk
     */
    public void saveSettings() {
        if (document != null) {
            try {
                document.writeToFile(SETTINGSFILE, true);
            } catch (Exception e) {
                Finstruct.logDomain.log(LogLevel.LL_ERROR, "FinstructSettings", "Could not save settings file. Reason: ", e);
            }
        }
    }
}
