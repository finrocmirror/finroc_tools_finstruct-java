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

import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.gui.util.treemodel.InterfaceTreeModel;

/**
 * @author Max Reichardt
 *
 * Finstruct tree Model for interfaces.
 * Contains worker thread that queries additional information from remote runtime.
 */
public class FinstructInterfaceTreeModel extends InterfaceTreeModel implements Runnable, TreeModelListener {

    public FinstructInterfaceTreeModel() {
        Thread workerThread = new Thread(this);
        workerThread.setDaemon(true);
        workerThread.setName("Finstruct Worker Thread");
        workerThread.start();
        super.addTreeModelListener(this);
    }

    @Override
    public void run() {
        while (true) {
            RemoteFrameworkElement element = frameworkElementsToCheckForInterface.poll();
            if (element != null) {
                try {
                    RemoteRuntime runtime = RemoteRuntime.find(element);
                    if (runtime.getParent() != null) {
                        element.getEditableInterfaces();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
            }
        }
    }

    /** Elements that worker thread should check for editable interfaces */
    private final ConcurrentLinkedQueue<RemoteFrameworkElement> frameworkElementsToCheckForInterface = new ConcurrentLinkedQueue<RemoteFrameworkElement>();

    @Override
    public void treeNodesChanged(TreeModelEvent e) {}
    @Override
    public void treeNodesRemoved(TreeModelEvent e) {}

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        Object parent = e.getPath()[e.getPath().length - 1];
        for (int i : e.getChildIndices()) {
            processSubtree((ModelNode)this.getChild(parent, i));
        }
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
        processSubtree((ModelNode)e.getPath()[e.getPath().length - 1]);
    }

    /**
     * Do some postprocessing on newly inserted nodes and subtrees
     *
     * @param node Inserted node
     */
    private void processSubtree(ModelNode node) {
        if (node instanceof RemoteFrameworkElement && ((RemoteFrameworkElement)node).isComponent()) {
            RemoteRuntime r = RemoteRuntime.find((RemoteFrameworkElement)node);
            if (r != null && r.getSerializationInfo().getRevision() == 0) {
                frameworkElementsToCheckForInterface.add((RemoteFrameworkElement)node);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            processSubtree(node.getChildAt(i));
        }
    }

    /**
     * Update/resolve URI connector connection partners using current model.
     * When model changes, this needs to be computed again (suggestion: do before redraw)
     * May only be called by thread that builds remote model
     */
    public void updateUriConnectors() {
        // TODO: make this more general when required (handle runtimes that are not two levels below root)
        for (int i = 0; i < getRoot().getChildCount(); i++) {
            ModelNode transportRoot = getRoot().getChildAt(i);
            for (int j = 0; j < transportRoot.getChildCount(); j++) {
                ModelNode node = transportRoot.getChildAt(j);
                if (node instanceof RemoteRuntime) {
                    ((RemoteRuntime)node).updateUriConnectors();
                }
            }
        }
    }

}
