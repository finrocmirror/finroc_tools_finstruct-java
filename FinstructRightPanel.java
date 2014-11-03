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

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.AEADBadTagException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
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
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.finroc.core.RuntimeEnvironment;
import org.finroc.core.RuntimeSettings;
import org.finroc.core.datatype.CoreString;
import org.finroc.core.parameter.ConstructorParameters;
import org.finroc.core.parameter.StaticParameterList;
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
import org.finroc.plugins.data_types.util.BoundsExtractingGraphics2D;
import org.finroc.tools.finstruct.dialogs.ParameterEditDialog;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.FinrocObjectAccessor;
import org.finroc.tools.finstruct.propertyeditor.PropertyEditorTable;
import org.finroc.tools.finstruct.propertyeditor.PropertyEditorTableModel;
import org.finroc.tools.finstruct.propertyeditor.StaticParameterAccessor;
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
import org.finroc.tools.gui.commons.fastdraw.BufferedConvexSVG;
import org.finroc.tools.gui.commons.fastdraw.SVG;
import org.finroc.tools.gui.util.gui.IconManager;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.gui.MJTree;
import org.finroc.tools.gui.util.gui.MToolBar;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.finroc.tools.gui.util.treemodel.InterfaceTreeModel;
import org.finroc.tools.gui.widgets.LED;
import org.rrlib.logging.Log;
import org.rrlib.logging.LogLevel;
import org.rrlib.xml.XMLNode;

/**
 * @author Max Reichardt
 *
 * Panel on the right side of finstruct graph views
 */
public class FinstructRightPanel extends JPanel implements TreeSelectionListener, ActionListener, TreeModelListener {

    /** UID */
    private static final long serialVersionUID = -6762519933079911025L;

    /** Reference to finstruct window */
    private FinstructWindow parent;

    /** Did panel register as tree model listener yet? */
    private boolean treeModelListenerRegistered = false;

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

    /** Current root element */
    private ModelNode rootElement;

    /** Scroll pane for bottom panel */
    private JScrollPane bottomScrollPane = new JScrollPane();

    /** UI elements for library property panel */
    private SharedLibrary selectedLibrary;
    private JPanel libraryInfoPanel = new JPanel();
    private JLabel libraryInfoText = new JLabel("", SwingConstants.CENTER);
    private JButton libraryLoadButton = new JButton("Load Library");

    /** UI elements for component creation panel */
    private RemoteCreateModuleAction selectedCreateAction;
    private JPanel componentCreationPanel = new JPanel();
    private JLabel componentInfoText = new JLabel(); //"", SwingConstants.CENTER);
    private PropertyEditorTable componentParameters = new PropertyEditorTable();
    private JButton componentCreateButton = new JButton("Create");
    private JLabel componentDescription = new JLabel(); //"", SwingConstants.CENTER);
    private CoreString componentCreateName = new CoreString();
    private ConstructorParameters componentConstructorParameters;

    /** If this is not null - and an element with this name is added, switches to view of this element */
    private String createdElementToSwitchTo = null;

    public FinstructRightPanel(FinstructWindow parent) {
        this.parent = parent;

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
        componentLibraryTree.setRootVisible(false);
        componentLibraryTree.setCellRenderer(new ComponentLibraryTreeCellRenderer());

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

        // Component creation panel
        componentCreationPanel.setLayout(new BorderLayout());
        JPanel componentCreationTopPanel = new JPanel();
        componentCreationTopPanel.setLayout(new BorderLayout());
        componentCreationTopPanel.add(componentInfoText, BorderLayout.CENTER);
        componentCreationTopPanel.add(componentCreateButton, BorderLayout.EAST);
        componentCreationPanel.add(componentCreationTopPanel, BorderLayout.NORTH);
        componentCreationPanel.add(new JScrollPane(componentParameters), BorderLayout.CENTER);
        componentCreationPanel.add(componentDescription, BorderLayout.SOUTH);
        componentCreationPanel.setBorder(BorderFactory.createEtchedBorder());
        componentInfoText.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        componentCreationPanel.setMinimumSize(new Dimension(100, 100));
        /*componentInfoText.setAlignmentX(Component.LEFT_ALIGNMENT);
        componentParameters.setAlignmentX(Component.LEFT_ALIGNMENT);
        componentCreateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        componentDescription.setAlignmentX(Component.LEFT_ALIGNMENT);*/
    }

    /**
     * Called when window containing this panel is closed
     */
    public void destroy() {
        if (treeModelListenerRegistered) {
            parent.finstruct.getIoInterface().removeTreeModelListener(this);
        }
    }

    /**
     * Called whenever root node of current view changes
     *
     * @param root Root element of view
     */
    public void setRootElement(ModelNode root) {
        rootElement = root;
        createdElementToSwitchTo = null;
        componentLibraryTree.storeExpandedElements();
        componentLibraryTree.setModel(generateCreateActionModel(root));
        componentLibraryTree.restoreExpandedElements();
    }

    /**
     * Show properties of specified element in bottom view
     *
     * @param element Element whose properties to show
     */
    private void showElement(RemoteFrameworkElement element) {
        // TODO Auto-generated method stub

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
            @SuppressWarnings("unchecked")
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

    @SuppressWarnings("rawtypes")
    @Override
    public void valueChanged(TreeSelectionEvent e) {
        bottomScrollPane.setViewportView(null);
        componentConstructorParameters = null;
        createdElementToSwitchTo = null;
        List<DefaultMutableTreeNode> selectedElements = componentLibraryTree.getSelectedObjects();
        DefaultMutableTreeNode selectedElement = selectedElements.size() == 0 ? null : selectedElements.get(0);
        if (selectedElement != null) {
            if (selectedElement.getUserObject() instanceof SharedLibrary) {

                // show shared library info
                selectedLibrary = (SharedLibrary)selectedElement.getUserObject();
                libraryInfoText.setText("<html><center><b>lib" + selectedLibrary.name + ".so</b><br>Status: " + (selectedLibrary.loaded ? "Loaded" : "Not loaded") + "</center></html>");
                libraryLoadButton.setVisible(!selectedLibrary.loaded);
                bottomScrollPane.setViewportView(libraryInfoPanel);
                splitPane.setBottomComponent(bottomScrollPane);
                this.validate();

            } else if (selectedElement.getUserObject() instanceof RemoteCreateModuleAction) {

                // show creation info
                selectedCreateAction = (RemoteCreateModuleAction)selectedElement.getUserObject();
                componentInfoText.setText("<html><b>" + simpleHtmlEscape(selectedCreateAction.name) + "</b></html>");

                // unique component name by default
                componentCreateName.set(selectedCreateAction.name);
                int i = 0;
                while (rootElement.getChildByName(componentCreateName.toString()) != null) {
                    i++;
                    componentCreateName.set(selectedCreateAction.name + " (" + i + ")");
                }
                componentConstructorParameters = selectedCreateAction.parameters != null ? selectedCreateAction.parameters.instantiate() : null;

                // Create property accessor list
                ArrayList<PropertyAccessor> propertyEditList = new ArrayList<PropertyAccessor>();
                propertyEditList.add(new FinrocObjectAccessor("Name", componentCreateName));
                if (componentConstructorParameters != null) {
                    propertyEditList.addAll(StaticParameterAccessor.createForList(componentConstructorParameters));
                }

                // complete view initialization
                PropertyEditorTableModel tableModel = new PropertyEditorTableModel("Parameter", propertyEditList, new FinrocComponentFactory(rootElement), new StandardComponentFactory());
                componentParameters.setModel(tableModel);
                //componentParameters.setDefaultRenderer(Object.class, tableModel);
                //componentParameters.setDefaultEditor(Object.class, tableModel);
                ((DefaultCellEditor)componentParameters.getDefaultEditor(Object.class)).setClickCountToStart(1);
                //bottomScrollPane.setViewportView(componentCreationPanel);
                splitPane.setBottomComponent(componentCreationPanel);
                this.validate();
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == libraryLoadButton) {
            RemoteRuntime runtime = RemoteRuntime.find(rootElement);
            if (runtime != null) {
                try {
                    TreePath selectedPath = componentLibraryTree.getSelectionPath();
                    runtime.getAdminInterface().loadModuleLibrary(selectedLibrary.name);
                    componentLibraryTree.storeExpandedElements();
                    componentLibraryTree.setModel(generateCreateActionModel(rootElement));
                    componentLibraryTree.restoreExpandedElements();
                    TreePath newSelectedPath = componentLibraryTree.findPath(selectedPath.getPath());
                    if (newSelectedPath != null) {
                        componentLibraryTree.expandPath(newSelectedPath);
                        componentLibraryTree.setSelectionPath(newSelectedPath);
                    }
                } catch (Exception ex) {
                    Finstruct.showErrorMessage(ex, true);
                }
            } else {
                Finstruct.showErrorMessage("Cannot load library: connection to remote runtime lost", false, false);
            }
        } else if (e.getSource() == componentCreateButton) {
            RemoteRuntime runtime = RemoteRuntime.find(rootElement);
            if (runtime != null) {
                if (!treeModelListenerRegistered) {
                    parent.finstruct.getIoInterface().addTreeModelListener(this);
                    treeModelListenerRegistered = true;
                }

                try {
                    // wait for creation and possibly open dialog for editing parameters
                    String error = runtime.getAdminInterface().createModule(selectedCreateAction, componentCreateName.toString(), ((RemoteFrameworkElement)rootElement).getRemoteHandle(), componentConstructorParameters);
                    if (error.length() > 0) {
                        Finstruct.showErrorMessage("Error creating module: " + error, false, false);
                    } else {
                        createdElementToSwitchTo = componentCreateName.toString();
                    }
                } catch (Exception exception) {
                    Finstruct.showErrorMessage("Exception creating module: " + exception.getMessage(), false, false);
                }
            } else {
                Finstruct.showErrorMessage("Cannot create module: connection to remote runtime lost", false, false);
            }
        }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        if (createdElementToSwitchTo != null && e.getTreePath().getLastPathComponent() == parent) {
            for (Object o : e.getChildren()) {
                if (o instanceof RemoteFrameworkElement && o.toString().equals(createdElementToSwitchTo)) {
                    showElement((RemoteFrameworkElement)o);
                    return;
                }
            }
        }
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {}

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {}

    @Override
    public void treeStructureChanged(TreeModelEvent e) {}

    /**
     * Tree cell renderer for component library tree
     */
    public class ComponentLibraryTreeCellRenderer extends DefaultTreeCellRenderer {

        /** UID */
        private static final long serialVersionUID = 146146803489028912L;

        /** Height of tree icons */
        private final int iconHeight;

        /** Icon for shared libraries that have not been loaded yet */
        private ImageIcon notLoadedLibrary;

        /** Icon for components */
        private ImageIcon componentIcon;

        private ComponentLibraryTreeCellRenderer() {

            // Generate (transparent) icon for not loaded libraries
            Icon standardIcon = this.getOpenIcon();
            iconHeight = standardIcon.getIconHeight();
            BufferedImage notLoadedLibraryImage = new BufferedImage(standardIcon.getIconWidth(), standardIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D notLoadedLibraryImageGraphics = notLoadedLibraryImage.createGraphics();
            notLoadedLibraryImageGraphics.setColor(new Color(255, 255, 255, 0));
            notLoadedLibraryImageGraphics.fillRect(0, 0, notLoadedLibraryImage.getWidth(), notLoadedLibraryImage.getHeight());
            notLoadedLibraryImageGraphics.setComposite(AlphaComposite.SrcOver.derive(0.25f));
            standardIcon.paintIcon(componentLibraryTree, notLoadedLibraryImageGraphics, 0, 0);
            notLoadedLibraryImageGraphics.dispose();
            notLoadedLibrary = new ImageIcon(notLoadedLibraryImage);

            // Create component icon
            componentIcon = new ImageIcon(Finstruct.class.getResource("icon/module-max.png"));
            // with SVG we get aliasing effects
            /*try {
                SVG componentIconSVG = SVG.createInstance(Finstruct.class.getResource("icon/module-max.svg"), true);
                double zoom = ((double)iconHeight) / componentIconSVG.getBounds().getHeight();
                BufferedImage componentIconImage = new BufferedImage((int)Math.ceil(zoom * componentIconSVG.getBounds().getWidth()), iconHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D componentIconImageGraphics = componentIconImage.createGraphics();
                componentIconImageGraphics.setClip(0, 0, componentIconImage.getWidth(),  componentIconImage.getHeight());
                componentIconImageGraphics.setColor(new Color(255, 255, 255, 0));
                componentIconImageGraphics.fillRect(0, 0, componentIconImage.getWidth(), componentIconImage.getHeight());
                componentIconImageGraphics.scale(zoom, zoom);
                //componentIconImageGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                //componentIconImageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                componentIconImageGraphics.translate(-componentIconSVG.getBounds().getMinX(), -componentIconSVG.getBounds().getMinY());
                componentIconSVG.paint(componentIconImageGraphics, null);
                componentIconImageGraphics.dispose();
                componentIcon = new ImageIcon(componentIconImage);
            } catch (Exception e) {
                e.printStackTrace();
            }*/
            //setLeafIcon(componentIcon);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,  row, hasFocus);
            if (leaf && component instanceof JLabel) {
                ((JLabel)component).setIcon((((DefaultMutableTreeNode)value).getUserObject() instanceof SharedLibrary) ? notLoadedLibrary : componentIcon);
            }
            return component;
        }
    }

    /**
     * Performs simple escaping for html string
     * (could be moved to some more central place. However, this is not the cleanest way to do this - but sufficient here)
     *
     * @param toEscape String to escape
     * @return Escaped string
     */
    public static String simpleHtmlEscape(String toEscape) {
        return toEscape.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replaceAll("'", "&apos;");
    }
    /*public class ComponentCreateTableModel extends PropertyEditorTableModel {


    }*/
}
