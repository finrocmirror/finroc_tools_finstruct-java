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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.finroc.core.FinrocAnnotation;
import org.finroc.core.FrameworkElement;
import org.finroc.core.parameter.ConfigFile;
import org.finroc.core.parameter.ParameterInfo;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.PortCreationInfo;
import org.finroc.core.remote.HasUid;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapperTreeNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLException;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Tree Model of config file
 */
public class ConfigFileModel extends DefaultTreeModel {

    /** UID */
    private static final long serialVersionUID = 3398186190365668425L;

    private final DefaultMutableTreeNode root;

    private FrameworkElement nodeContainer = new FrameworkElement("Config model node container");

    public ConfigFileModel(ModelNode root) {
        super(new DefaultMutableTreeNode("Config File"));
        this.root = (DefaultMutableTreeNode)getRoot();
        if (root == null || RemoteRuntime.find(root) == null || (!(root instanceof RemoteFrameworkElement))) {
            initFromFile(null);
        } else {
            try {
                RemoteRuntime.find(root).getAdminInterface().getParameterInfo((RemoteFrameworkElement)root);
                initFromFile(findConfigFile((RemoteFrameworkElement)root));
            } catch (Exception e) {
                Log.log(LogLevel.ERROR, this, e);
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
        for (XMLNode port : xmlParent.children()) {
            XMLNode child = port;
            try {
                String slashedUid = (uid.length() == 0 ? "" : (uid + "/"));
                if (child.getName() == "node") {
                    DefaultMutableTreeNode n = new DefaultMutableTreeNode(child.getStringAttribute("name"));
                    parent.add(n);
                    String uid2 = slashedUid + n.toString();
                    createNodes(n, child, uid2);
                } else if (child.getName() == "value") {
                    String name = child.getStringAttribute("name");
                    ConfigEntryWrapper n = new ConfigEntryWrapper(slashedUid + name, name);
                    parent.add(n);
                }
            } catch (XMLException e) {
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
        if (node instanceof ConfigEntryWrapper && (((ConfigEntryWrapper)node).getUid().equals(uid) || ("/" + ((ConfigEntryWrapper)node).getUid()).equals(uid))) {
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

    /**
     * Find ConfigFile which specified element is configured from
     *
     * @param element Element
     * @return ConfigFile - or null if none could be found
     */
    public static ConfigFile findConfigFile(ModelNode element) {
        FinrocAnnotation ann = (element instanceof RemoteFrameworkElement) ? ((RemoteFrameworkElement)element).getAnnotation(ConfigFile.TYPE) : null;
        if (ann != null && ((ConfigFile)ann).isActive() == true) {
            return (ConfigFile)ann;
        }
        Object parent = element.getParent();
        if (parent != null && parent instanceof RemoteFrameworkElement) {
            return findConfigFile((RemoteFrameworkElement)parent);
        }
        return null;
    }

    class ConfigEntryWrapper extends DefaultMutableTreeNode implements PortWrapperTreeNode, HasUid {

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

        @Override
        public boolean isProxy() {
            return false;
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
