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
package org.finroc.tools.finstruct;

import java.io.File;
import java.util.ArrayList;

import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLDocument;
import org.rrlib.xml.XMLException;
import org.rrlib.xml.XMLNode;

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

    /** Size of connection history */
    private static final int CONNECTION_HISTORY_SIZE = 8;

    private static final String CONNECTION_HISTORY_XML_NAME = "connection_history";

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
                        Log.log(LogLevel.WARNING, this, "Could not parse settings file. Creating new one. ", e);
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
        for (XMLNode child : root.children()) {
            if (child.getName().equals(name)) {
                return child;
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
                Log.log(LogLevel.ERROR, this, "Could not save settings file. Reason: ", e);
            }
        }
    }

    /**
     * @return Connection History
     */
    public ArrayList<String> getConnectionHistory() {
        ArrayList<String> result = new ArrayList<String>();
        try {
            XMLNode historyNode = getTopLevelNode(CONNECTION_HISTORY_XML_NAME);
            for (XMLNode child : historyNode.children()) {
                result.add(child.getTextContent());
            }
        } catch (Exception e) {
            Log.log(LogLevel.ERROR, this, e);
        }
        return result;
    }

    /**
     * @param newConnection Connection to add to connection history
     */
    public void addToConnectionHistory(String newConnection) {
        ArrayList<String> connectionHistory = getConnectionHistory();
        if (connectionHistory.size() > 0 && connectionHistory.get(0).equals(newConnection)) {
            return;
        }
        connectionHistory.remove(newConnection);
        connectionHistory.add(0, newConnection);
        while (connectionHistory.size() > CONNECTION_HISTORY_SIZE) {
            connectionHistory.remove(connectionHistory.size() - 1);
        }

        XMLNode historyNode = getTopLevelNode(CONNECTION_HISTORY_XML_NAME);
        historyNode.getParent().removeChildNode(historyNode);
        historyNode = getTopLevelNode(CONNECTION_HISTORY_XML_NAME);
        for (String connection : connectionHistory) {
            try {
                historyNode.addChildNode("connection").setContent(connection);
            } catch (XMLException e) {
                Log.log(LogLevel.ERROR, this, e);
            }
        }
        saveSettings();
    }

}
