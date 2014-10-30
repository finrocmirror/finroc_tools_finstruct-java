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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;

import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.RuntimeSettings;
import org.finroc.core.plugin.ConnectionListener;
import org.finroc.core.plugin.CreateExternalConnectionAction;
import org.finroc.core.plugin.ExternalConnection;
import org.finroc.core.plugin.Plugins;
import org.finroc.core.plugin.RemoteCreateModuleAction;
import org.finroc.core.port.ThreadLocalCache;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.PortWrapperTreeNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.util.Files;
import org.finroc.tools.finstruct.views.ComponentVisualization;
import org.finroc.tools.finstruct.views.Ib2cViewClassic;
import org.finroc.tools.finstruct.views.PortView;
import org.finroc.tools.finstruct.views.Profiling;
import org.finroc.tools.finstruct.views.StandardViewGraphViz;
import org.finroc.tools.gui.ConnectDialog;
import org.finroc.tools.gui.ConnectionPanel;
import org.finroc.tools.gui.GUIUiBase;
import org.finroc.tools.gui.StatusBar;
import org.finroc.tools.gui.commons.EventRouter;
import org.finroc.tools.gui.util.gui.IconManager;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MJTree;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.treemodel.InterfaceTreeModel;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Panel on the right side of finstruct graph views
 */
public class FinstructRightPanel extends JPanel implements TreeSelectionListener, ActionListener {

    /** UID */
    private static final long serialVersionUID = -6762519933079911025L;

    /** Panel containing the component library (tree with libs and CreateModuleActions) */
    private JPanel componentLibraryPanel;

    /** Panel containing component/edge properties */
    //private JPanel componentPropertyPanel;

    /** Tree in component library panel */
    private MJTree<DefaultMutableTreeNode> componentLibraryTree = new MJTree<DefaultMutableTreeNode>(DefaultMutableTreeNode.class, 1);

    /** Horizontal Split pane */
    private JSplitPane splitPane;

    /** Categories of shared libraries */
    private enum SharedLibraryCategory { Finroc_Libraries, Finroc_Plugins, Finroc_Projects, RRLibs, Other };

    /** Scroll pane for bottom panel */
    private JScrollPane bottomScrollPane = new JScrollPane();

    /** UI elements for library property panel */
    private SharedLibrary selectedLibrary;
    private JPanel libraryInfoPanel = new JPanel();
    private JLabel libraryInfoText = new JLabel("", SwingConstants.CENTER);
    private JButton libraryLoadButton = new JButton("Load Library");


    public FinstructRightPanel() {

        bottomScrollPane.setBorder(BorderFactory.createEtchedBorder());

        // Library Panel
        componentLibraryPanel = new JPanel();
        componentLibraryPanel.setBorder(BorderFactory.createEmptyBorder());
        componentLibraryPanel.setLayout(new BorderLayout());
        JScrollPane topPanel = new JScrollPane(componentLibraryTree);
        topPanel.setBorder(BorderFactory.createLineBorder(Color.gray));
        componentLibraryPanel.add(topPanel, BorderLayout.CENTER);
        componentLibraryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        componentLibraryTree.addTreeSelectionListener(this);

        // Property Panel
        //componentPropertyPanel = new JPanel();

        // Split pane
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, componentLibraryPanel, bottomScrollPane);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(5);
        splitPane.setResizeWeight(0.5);
        splitPane.resetToPreferredSizes();
        this.setLayout(new BorderLayout());
        this.add(splitPane, BorderLayout.CENTER);

        // Library info panel
        //libraryInfoPanel.setLayout(new BorderLayout());
        //libraryInfoPanel.setBorder(BorderFactory.createEtchedBorder());
        //libraryInfoPanel.setBorder(BorderFactory.createEmptyBorder());
        libraryInfoPanel.setLayout(new BoxLayout(libraryInfoPanel, BoxLayout.PAGE_AXIS));
        libraryInfoPanel.add(libraryInfoText);
        libraryInfoPanel.add(libraryLoadButton);
        libraryInfoText.setAlignmentX(Component.CENTER_ALIGNMENT);
        libraryLoadButton.addActionListener(this);
        libraryLoadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    /**
     * Called whenever root node of current view changes
     *
     * @param root Root element of view
     */
    public void setRootElement(ModelNode root) {
        componentLibraryTree.setModel(generateCreateActionModel(root));
    }

    /**
     * shared library info
     */
    class SharedLibrary implements Comparable<SharedLibrary> {
        String name; // full library name
        String shortName; // library name without prefix
        boolean loaded; // Is this a loaded library?
        ArrayList<RemoteCreateModuleAction> actions = new ArrayList<RemoteCreateModuleAction>(); // Create actions in this library (temporary)

        @Override
        public int compareTo(SharedLibrary o) {
            if (loaded == o.loaded) {
                return name.compareTo(o.name);
            }
            return loaded ? -1 : 1;
        }

        @Override
        public String toString() {
            return shortName;
        }
    }

    class SharedLibraryCategoryAndName {
        String name;
        SharedLibraryCategory category;
        public SharedLibraryCategoryAndName(String name, SharedLibraryCategory category) {
            this.name = name;
            this.category = category;
        }
    }


    /**
     * Generates new tree model of CreateModuleActions and libraries for component library tree
     *
     * @param element (root) element to create action tree model for
     * @return Generated tree model
     */
    private TreeModel generateCreateActionModel(ModelNode element) {
        RemoteRuntime runtime = RemoteRuntime.find(element);
        if (runtime != null) {
            ArrayList<RemoteCreateModuleAction> createActions = runtime.getAdminInterface().getRemoteModuleTypes();
            List<String> loadableLibraries = runtime.getAdminInterface().getModuleLibraries();
            //DefaultMutableTreeNode root = new DefaultMutableTreeNode("Create Actions");
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Component Repository");

            DefaultMutableTreeNode[] categoryNodes = new DefaultMutableTreeNode[SharedLibraryCategory.values().length];
            HashMap<String, SharedLibrary>[] categoryMaps = new HashMap[SharedLibraryCategory.values().length];
            for (int i = 0; i < categoryNodes.length; i++) {
                categoryNodes[i] = new DefaultMutableTreeNode(SharedLibraryCategory.values()[i].toString().replace('_', ' '));
                categoryMaps[i] = new HashMap<String, SharedLibrary>();
            }

            // Add all existing components
            for (RemoteCreateModuleAction action : createActions) {
                SharedLibraryCategoryAndName categoryAndName = getSharedLibraryCategoryAndName(action.groupName);
                HashMap<String, SharedLibrary> categoryMap = categoryMaps[categoryAndName.category.ordinal()];
                SharedLibrary library = categoryMap.get(categoryAndName.name);
                if (library == null) {
                    library = new SharedLibrary();
                    library.name = action.groupName;
                    library.shortName = categoryAndName.name;
                    library.loaded = true;
                    categoryMap.put(categoryAndName.name, library);
                }
                library.actions.add(action);
            }

            // Add all loadable libraries
            for (String loadableLibrary : loadableLibraries) {
                SharedLibraryCategoryAndName categoryAndName = getSharedLibraryCategoryAndName(loadableLibrary);
                HashMap<String, SharedLibrary> categoryMap = categoryMaps[categoryAndName.category.ordinal()];
                SharedLibrary library = new SharedLibrary();
                library.name = loadableLibrary;
                library.shortName = categoryAndName.name;
                library.loaded = false;
                categoryMap.put(categoryAndName.name, library);
            }

            for (int i = 0; i < categoryNodes.length; i++) {
                ArrayList<SharedLibrary> libraries = new ArrayList<SharedLibrary>(categoryMaps[i].values());
                Collections.sort(libraries);
                for (SharedLibrary sharedLibrary : libraries) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(sharedLibrary);
                    categoryNodes[i].add(node);
                    Collections.sort(sharedLibrary.actions);
                    for (RemoteCreateModuleAction action : sharedLibrary.actions) {
                        node.add(new DefaultMutableTreeNode(action));
                    }
                }
                if (libraries.size() > 0) {
                    root.add(categoryNodes[i]);
                }
            }
            return new DefaultTreeModel(root);
        }

        return new DefaultTreeModel(new DefaultMutableTreeNode("No remote element selected"));
    }



    /**
     * @param name Library name
     * @return Category, this library belongs to
     */
    private SharedLibraryCategoryAndName getSharedLibraryCategoryAndName(String name) {
        if (name.startsWith("rrlib_")) {
            return new SharedLibraryCategoryAndName(name.substring("rrlib_".length()), SharedLibraryCategory.RRLibs);
        } else if (name.startsWith("finroc_libraries_")) {
            return new SharedLibraryCategoryAndName(name.substring("finroc_libraries_".length()), SharedLibraryCategory.Finroc_Libraries);
        } else if (name.startsWith("finroc_plugins_")) {
            return new SharedLibraryCategoryAndName(name.substring("finroc_plugins_".length()), SharedLibraryCategory.Finroc_Plugins);
        } else if (name.startsWith("finroc_projects_")) {
            return new SharedLibraryCategoryAndName(name.substring("finroc_projects_".length()), SharedLibraryCategory.Finroc_Projects);
        }
        return new SharedLibraryCategoryAndName(name, SharedLibraryCategory.Other);
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        bottomScrollPane.setViewportView(null);
        List<DefaultMutableTreeNode> selectedElements = componentLibraryTree.getSelectedObjects();
        DefaultMutableTreeNode selectedElement = selectedElements.size() == 0 ? null : selectedElements.get(0);
        if (selectedElement != null) {
            if (selectedElement.getUserObject() instanceof SharedLibrary) {

                // show shared library info
                selectedLibrary = (SharedLibrary)selectedElement.getUserObject();
                libraryInfoText.setText("<html><center><b>lib" + selectedLibrary.name + ".so</b><br>Status: " + (selectedLibrary.loaded ? "Loaded" : "Not loaded") + "</center></html>");
                libraryLoadButton.setVisible(!selectedLibrary.loaded);
                bottomScrollPane.setViewportView(libraryInfoPanel);
                this.validate();
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == libraryLoadButton) {
            System.out.println("Load Library");
        }
    }


}
