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
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.finroc.core.FrameworkElementFlags;
import org.finroc.core.datatype.CoreString;
import org.finroc.core.parameter.ConstructorParameters;
import org.finroc.core.parameter.StaticParameterList;
import org.finroc.core.plugin.RemoteCreateModuleAction;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemotePort;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteType;
import org.finroc.tools.finstruct.propertyeditor.ConnectingPortAccessor;
import org.finroc.tools.finstruct.propertyeditor.FinrocComponentFactory;
import org.finroc.tools.finstruct.propertyeditor.FinrocObjectAccessor;
import org.finroc.tools.finstruct.propertyeditor.PortAccessor;
import org.finroc.tools.finstruct.propertyeditor.PropertyEditorTable;
import org.finroc.tools.finstruct.propertyeditor.PropertyEditorTableModel;
import org.finroc.tools.finstruct.propertyeditor.StaticParameterAccessor;
import org.finroc.tools.finstruct.views.AbstractGraphView;
import org.finroc.tools.finstruct.views.StandardViewGraphViz;
import org.finroc.tools.gui.util.gui.MJTree;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.rrlib.serialization.rtti.DataTypeBase;

/**
 * @author Max Reichardt
 *
 * Panel on the right side of finstruct graph views
 */
public class FinstructRightPanel extends JPanel implements TreeSelectionListener, ActionListener, TreeModelListener, Comparator<Object>, PortAccessor.Listener, PropertyEditorTableModel.PropertySetListener {

    /** UID */
    private static final long serialVersionUID = -6762519933079911025L;

    /** Preferred width of right panel */
    private static final int PREFERRED_WIDTH = 200;

    /** Reference to finstruct window */
    private FinstructWindow parent;

    /** Did panel register as tree model listener yet? */
    private boolean treeModelListenerRegistered = false;

    /** Panel containing the component library (tree with libs and CreateModuleActions) */
    private JPanel componentLibraryPanel;

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

    /** UI elements for component properties */
    private RemoteFrameworkElement selectedComponent;
    private StaticParameterList selectedComponentStaticParameterList;
    private JPanel componentPropertyPanel = new JPanel();
    private JLabel componentPropertyPanelText = new JLabel();
    private PropertyEditorTable componentProperties = new PropertyEditorTable();
    private JButton componentDeleteButton = new JButton("Delete");
    private JComboBox<Object> componentPropertySelection = new JComboBox<Object>();
    private final ArrayList<ConnectingPortAccessor<?>> componentPropertyAccessPorts = new ArrayList<ConnectingPortAccessor<?>> ();

    /** If this is not null - and an element with this name is added, switches to view of this element */
    private String createdElementToSwitchTo = null;

    /** Empty bottom panel */
    private JPanel emptyBottomPanel = new JPanel();

    public FinstructRightPanel(FinstructWindow parent) {
        this.parent = parent;

        bottomScrollPane.setBorder(BorderFactory.createEtchedBorder());
        emptyBottomPanel.setBorder(BorderFactory.createEtchedBorder());

        // Library Panel
        componentLibraryPanel = new JPanel();
        componentLibraryPanel.setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
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
        componentCreationPanel.setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
        componentCreateButton.addActionListener(this);

        // Component property panel
        componentPropertyPanel.setLayout(new BorderLayout());
        JPanel componentPropertyTopPanel = new JPanel();
        componentPropertyTopPanel.setLayout(new BorderLayout());
        componentPropertyTopPanel.add(componentPropertyPanelText, BorderLayout.CENTER);
        componentPropertyTopPanel.add(componentDeleteButton, BorderLayout.EAST);
        componentPropertyTopPanel.add(componentPropertySelection, BorderLayout.SOUTH);
        componentPropertyPanel.add(componentPropertyTopPanel, BorderLayout.NORTH);
        componentPropertyPanel.add(new JScrollPane(componentProperties), BorderLayout.CENTER);
        componentPropertyPanel.setBorder(BorderFactory.createEtchedBorder());
        componentPropertyPanelText.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        componentPropertyPanel.setMinimumSize(new Dimension(100, 100));
        componentPropertyPanel.setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
        componentPropertySelection.addActionListener(this);
        componentDeleteButton.addActionListener(this);
    }

    /**
     * Called when window containing this panel is closed
     */
    public void destroy() {
        clearBottomPanel();
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
     * Clears and resets all variables related to bottom panel contents
     * (also removes any created ports)
     */
    private void clearBottomPanel() {
        for (ConnectingPortAccessor<?> componentPropertyAccessPort : componentPropertyAccessPorts) {
            componentPropertyAccessPort.delete();
        }
        splitPane.setBottomComponent(emptyBottomPanel);
        componentPropertyAccessPorts.clear();
        bottomScrollPane.setViewportView(null);
        componentConstructorParameters = null;
        createdElementToSwitchTo = null;
        selectedComponent = null;
        selectedComponentStaticParameterList = null;
    }

    /**
     * Show properties of specified element in bottom view
     *
     * @param element Element whose properties to show
     */
    public void showElement(RemoteFrameworkElement element) {
        int dividerLocation = splitPane.getDividerLocation();
        clearBottomPanel();
        selectedComponent = element;

        RemoteRuntime runtime = RemoteRuntime.find(element);
        if (runtime != null) {
            // TODO: transform to asynchronous call in order to increase GUI responsiveness (furthermore, we can be sure that all subelements have been added)
            selectedComponentStaticParameterList = (StaticParameterList)runtime.getAdminInterface().getAnnotation(element.getRemoteHandle(), StaticParameterList.TYPE);
            if (selectedComponentStaticParameterList != null) {
                for (int i = 0; i < selectedComponentStaticParameterList.size(); i++) {
                    selectedComponentStaticParameterList.get(i).resetChanged();
                    //System.out.println(i + ":" + selectedComponentStaticParameterList.get(i).hasChanged());
                }
            }

            // initialize GUI elements
            componentPropertyPanelText.setText("<html><b>" + simpleHtmlEscape(selectedComponent.getName()) + "</b><br>" + (((element.getFlags() & FrameworkElementFlags.FINSTRUCTED) != 0) ? "(Created via Finstruct/XML)" : "(Created in C++ code)") + "</html>"); // TODO: if create action is known, we can show info about component type etc.

            // init combo box
            String lastComboBoxSelection = (componentPropertySelection.getSelectedItem() == null) ? null : componentPropertySelection.getSelectedItem().toString();
            ArrayList<Object> comboBoxElements = new ArrayList<Object>();
            comboBoxElements.add("All");
            int parameterCount = selectedComponentStaticParameterList != null ? selectedComponentStaticParameterList.size() : 0;
            for (int i = 0; i < element.getChildCount(); i++) {
                if (element.getChildAt(i) instanceof RemoteFrameworkElement) {
                    RemoteFrameworkElement childElement = (RemoteFrameworkElement)element.getChildAt(i);
                    if (AbstractGraphView.isParameters(childElement)) {
                        parameterCount += childElement.getChildCount();
                    } else if (AbstractGraphView.isInterface(childElement)) {
                        comboBoxElements.add(childElement);
                    }
                }
            }
            Object selectedItem = null;
            if (parameterCount > 0) {
                selectedItem = "Parameters";
                comboBoxElements.add(selectedItem);
            }
            Collections.sort(comboBoxElements, this);
            if (lastComboBoxSelection != null) {
                for (Object comboBoxElement : comboBoxElements) {
                    if (comboBoxElement.toString().equals(lastComboBoxSelection)) {
                        selectedItem = comboBoxElement;
                        break;
                    }
                }
            }
            if (selectedItem == null) {
                selectedItem = comboBoxElements.size() > 1 ? comboBoxElements.get(1) : comboBoxElements.get(0); // Only select 'All' if there's no other choice
            }
            componentPropertySelection.setModel(new DefaultComboBoxModel<Object>(comboBoxElements.toArray()));
            componentPropertySelection.setSelectedItem(selectedItem);

            // Create property accessor list
            showComponentProperties(selectedItem);

            // complete view initialization
            splitPane.setBottomComponent(componentPropertyPanel);
            this.validate();
        }
        splitPane.setDividerLocation(dividerLocation);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void propertySet(PropertyAccessor property) {
        if (selectedComponentStaticParameterList != null) {
            boolean changed = false;
            for (int i = 0; i < selectedComponentStaticParameterList.size(); i++) {
                changed |= selectedComponentStaticParameterList.get(i).hasChanged();
                selectedComponentStaticParameterList.get(i).resetChanged();
            }
            if (changed) {
                RemoteRuntime runtime = RemoteRuntime.find(rootElement);
                if (runtime != null) {
                    runtime.getAdminInterface().setAnnotation(selectedComponent.getRemoteHandle(), selectedComponentStaticParameterList);
                } else {
                    Finstruct.showErrorMessage("Cannot set static parameter: connection to remote runtime lost", false, false);
                }
            }
        }
    }

    /**
     * Called whenever componentPropertySelection combo box value changes.
     * Displays selected properties.
     *
     * @param selectedItem Selected property group
     */
    @SuppressWarnings("rawtypes")
    private void showComponentProperties(Object selectedItem) {
        // Clear any old port accessors
        for (ConnectingPortAccessor<?> componentPropertyAccessPort : componentPropertyAccessPorts) {
            componentPropertyAccessPort.delete();
        }
        componentPropertyAccessPorts.clear();

        ArrayList<PropertyAccessor> propertyEditList = getComponentProperties(selectedItem, false);
        PropertyEditorTableModel tableModel = new PropertyEditorTableModel("Name", propertyEditList, new FinrocComponentFactory(rootElement), new StandardComponentFactory());
        tableModel.addPropertySetListener(this);
        componentProperties.setModel(tableModel);
        ((DefaultCellEditor)componentProperties.getDefaultEditor(Object.class)).setClickCountToStart(1);
    }

    /**
     * Retrieves all component properties accessors of specified property group
     *
     * @param selectedItem Selected property group
     * @return List with property accessors
     */
    @SuppressWarnings("rawtypes")
    private ArrayList<PropertyAccessor> getComponentProperties(Object selectedItem, boolean prefix) {
        ArrayList<PropertyAccessor> result = new ArrayList<PropertyAccessor>();
        if (selectedItem.toString().equals("All")) {
            for (int i = 1; i < componentPropertySelection.getModel().getSize(); i++) {
                result.addAll(getComponentProperties(componentPropertySelection.getModel().getElementAt(i), true));
            }
        } else if (selectedItem instanceof RemoteFrameworkElement) {
            RemoteFrameworkElement interfaceElement = (RemoteFrameworkElement)selectedItem;
            for (int i = 0; i < interfaceElement.getChildCount(); i++) {
                if (interfaceElement.getChildAt(i) instanceof RemotePort) {
                    RemotePort port = (RemotePort)interfaceElement.getChildAt(i);
                    DataTypeBase type = port.getPort().getDataType();
                    if (FinrocTypeInfo.isCCType(type) || FinrocTypeInfo.isStdType(type) || ((type instanceof RemoteType) && ((RemoteType)type).isAdaptable())) {
                        ConnectingPortAccessor portAccess = new ConnectingPortAccessor((RemotePort)interfaceElement.getChildAt(i), prefix ? interfaceElement.getParent().getQualifiedName('/') : interfaceElement.getQualifiedName('/'));
                        componentPropertyAccessPorts.add(portAccess);
                        result.add(portAccess);
                        portAccess.setListener(this);
                        portAccess.setAutoUpdate(true);
                        portAccess.init();
                    }
                }
            }
        } else if (selectedItem.toString().equals("Parameters")) {
            if (selectedComponentStaticParameterList != null) {
                result.addAll(StaticParameterAccessor.createForList(selectedComponentStaticParameterList, prefix ? "Parameters/" : ""));
            }
            ModelNode parameters = selectedComponent.getChildByName("Parameters");
            if (parameters != null) {
                result.addAll(getComponentProperties(parameters, prefix));
            }
        }
        return result;
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
        clearBottomPanel();
        List<DefaultMutableTreeNode> selectedElements = componentLibraryTree.getSelectedObjects();
        DefaultMutableTreeNode selectedElement = selectedElements.size() == 0 ? null : selectedElements.get(0);
        int dividerLocation = splitPane.getDividerLocation();
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
        splitPane.setDividerLocation(dividerLocation);
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
                        Finstruct.showErrorMessage("Error creating component: " + error, false, false);
                    } else {
                        createdElementToSwitchTo = componentCreateName.toString();
                    }
                } catch (Exception exception) {
                    Finstruct.showErrorMessage("Exception creating component: " + exception.getMessage(), false, false);
                }
            } else {
                Finstruct.showErrorMessage("Cannot create component: connection to remote runtime lost", false, false);
            }
        } else if (e.getSource() == componentPropertySelection) {
            showComponentProperties(componentPropertySelection.getSelectedItem());
        } else if (e.getSource() == componentDeleteButton) {
            RemoteRuntime runtime = RemoteRuntime.find(rootElement);
            if (runtime != null) {
                runtime.getAdminInterface().deleteElement(selectedComponent.getRemoteHandle());
                clearBottomPanel();
                if (parent.getCurrentView() instanceof StandardViewGraphViz) {
                    ((StandardViewGraphViz)parent.getCurrentView()).refreshViewAfter(500);
                }
            } else {
                Finstruct.showErrorMessage("Cannot delete component: connection to remote runtime lost", false, false);
            }
        }
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        if (createdElementToSwitchTo != null && e.getTreePath().getLastPathComponent() == rootElement) {
            for (Object o : e.getChildren()) {
                if (o instanceof RemoteFrameworkElement && o.toString().equals(createdElementToSwitchTo)) {
                    showElement((RemoteFrameworkElement)o);
                    if (parent.getCurrentView() instanceof StandardViewGraphViz) {
                        ((StandardViewGraphViz)parent.getCurrentView()).relayout(false);
                    }
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

    // To string comparison for property selection combo box
    @Override
    public int compare(Object o1, Object o2) {
        return o1.toString().compareTo(o2.toString());
    }

    @Override
    public void portChanged() {
        componentProperties.repaint();
    }
}
