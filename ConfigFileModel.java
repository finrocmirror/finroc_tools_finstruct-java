/**
 * You received this file as part of Finstruct - a tool for
 * the Finroc Framework.
 *
 * Copyright (C) 2011 Robotics Research Lab, University of Kaiserslautern
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
package org.finroc.tools.finstruct;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.finroc.core.FrameworkElement;
import org.finroc.core.parameter.ConfigFile;
import org.finroc.core.parameter.ParameterInfo;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.port.net.RemoteRuntime;
import org.finroc.tools.gui.util.treemodel.TreePortWrapper;
import org.rrlib.finroc_core_utils.log.LogLevel;
import org.rrlib.finroc_core_utils.xml.XML2WrapperException;
import org.rrlib.finroc_core_utils.xml.XMLNode;

/**
 * @author max
 *
 * Tree Model of config file
 */
public class ConfigFileModel extends DefaultTreeModel {

    /** UID */
    private static final long serialVersionUID = 3398186190365668425L;

    private final DefaultMutableTreeNode root;

    private FrameworkElement nodeContainer = new FrameworkElement("Config model node container");

    public ConfigFileModel(FrameworkElement root) {
        super(new DefaultMutableTreeNode("Config File"));
        this.root = (DefaultMutableTreeNode)getRoot();
        if (root == null || RemoteRuntime.find(root) == null) {
            initFromFile(null);
        } else {
            try {
                RemoteRuntime.find(root).getAdminInterface().getParameterInfo(root);
                initFromFile(ConfigFile.find(root));
            } catch (Exception e) {
                Finstruct.logDomain.log(LogLevel.LL_ERROR, "ConfigFileModel", e);
            }
        }
    }

    /**
     * delete model and cleanup framework elements
     */
    public void delete() {
        root.removeAllChildren();
        nodeContainer.managedDelete();
    }

    /**
     * Clear tree model
     */
    public void clear() {
        root.removeAllChildren();
        nodeContainer.managedDelete();
        nodeContainer = new FrameworkElement("Config model node container");
    }

    /**
     * Clear tree and create nodes from config file entries
     *
     * @param cf ConfigFile to init from
     */
    public void initFromFile(ConfigFile cf) {

        // clear tree
        clear();
        if (cf == null) {
            root.setUserObject("No Config File");
            return;
        }
        root.setUserObject("Config File");

        // create nodes
        XMLNode xmlRoot = cf.getRootNode();
        createNodes(root, xmlRoot, "");
        nodeContainer.init();
    }

    /**
     * Create child nodes for specified parent node
     *
     * @param parent Parent swing tree model node
     * @param xmlParent XML parent node
     */
    private void createNodes(DefaultMutableTreeNode parent, XMLNode xmlParent, String uid) {
        for (XMLNode.ConstChildIterator port = xmlParent.getChildrenBegin(); port.get() != xmlParent.getChildrenEnd(); port.next()) {
            XMLNode child = port.get();
            try {
                if (child.getName() == "node") {
                    DefaultMutableTreeNode n = new DefaultMutableTreeNode(child.getStringAttribute("name"));
                    parent.add(n);
                    String uid2 = uid + "/" + n.toString();
                    createNodes(n, child, uid2);
                } else if (child.getName() == "value") {
                    String name = child.getStringAttribute("name");
                    ConfigEntryWrapper n = new ConfigEntryWrapper(uid + "/" + name, name);
                    parent.add(n);
                }
            } catch (XML2WrapperException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @param uid Uid of config entry
     * @return Config entry (wrapper)
     */
    public ConfigEntryWrapper get(String uid) {
        return getHelper(root, uid);
    }

    /**
     * Recursive helper function for above
     *
     * @param node Current node to check
     * @param uid Uid of entry we're looking for
     * @return Entry, if it exists below node
     */
    private ConfigEntryWrapper getHelper(DefaultMutableTreeNode node, String uid) {
        if (node instanceof ConfigEntryWrapper && ((ConfigEntryWrapper)node).getUid().equals(uid)) {
            return (ConfigEntryWrapper)node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            ConfigEntryWrapper c = getHelper((DefaultMutableTreeNode)node.getChildAt(i), uid);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    class ConfigEntryWrapper extends DefaultMutableTreeNode implements TreePortWrapper {

        /** UID */
        private static final long serialVersionUID = 4774894712960992454L;

        private ConfigEntryPort port;

        private String uid;

        private ConfigEntryWrapper(String uid, String name) {
            super(name);
            this.uid = uid;
            port = new ConfigEntryPort(name);
        }

        @Override
        public String getUid() {
            return uid;
        }

        @Override
        public AbstractPort getPort() {
            return port;
        }

        @Override
        public boolean isInputPort() {
            return true;
        }

        class ConfigEntryPort extends AbstractPort {

            AbstractPort.EdgeList<AbstractPort> EMPTY_LIST = new AbstractPort.EdgeList<AbstractPort>();

            public ConfigEntryPort(String name) {
                super(new PortCreationInfo(name, nodeContainer, ParameterInfo.TYPE));
                this.initLists(EMPTY_LIST, EMPTY_LIST);
            }

            @Override
            protected void initialPushTo(AbstractPort target, boolean reverse) {
            }

            @Override
            public void notifyDisconnect() {
            }

            @Override
            protected void setMaxQueueLengthImpl(int length) {
            }

            @Override
            protected int getMaxQueueLengthImpl() {
                return 0;
            }

            @Override
            protected void clearQueueImpl() {
            }

            @Override
            public void forwardData(AbstractPort other) {
            }


        }

    }
}
