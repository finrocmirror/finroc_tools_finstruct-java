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
package org.finroc.tools.finstruct.actions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.finstructable.EditableInterfaces;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteFrameworkElement;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.finstruct.Finstruct;
import org.rrlib.serialization.Serialization;


/**
 * @author Max Reichardt
 *
 * Action to add ports to component interface
 */
public class AddPortsToInterfaceAction extends FinstructAction {

    /**
     * @param remoteComponent Component whose interface to edit
     * @throws Throws Exception if remoteComponent has no editable interfaces
     */
    public AddPortsToInterfaceAction(RemoteFrameworkElement remoteComponent) throws Exception {
        link = remoteComponent.getQualifiedName(LINK_SEPARATOR);
        editableInterfaces = remoteComponent.getEditableInterfaces();
        if (editableInterfaces == null) {
            throw new Exception("Component '" + link + "' has no editable interfaces");
        }
        undoAction = false;
    }

    @Override
    public String getDescriptionForEditMenu() {
        return "Add ports to interface " + getReadableLinkForMenu(link);
    }

    @Override
    protected void executeImplementation() throws Exception {
        if (portsToAdd.isEmpty()) {
            return;
        }
        ModelNode node = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(link, LINK_SEPARATOR);
        if (!(node instanceof RemoteFrameworkElement)) {
            throw new Exception("Cannot find remote framework element '" + link + "'");
        }
        RemoteRuntime runtime = RemoteRuntime.find(node);
        if (runtime == null) {
            throw new Exception("Cannot find remote runtime for element '" + link + "'");
        }
        if (undoAction) {
            runtime.getAdminInterface().setAnnotation(((RemoteFrameworkElement)node).getRemoteHandle(), originalInterface);
            return;
        }

        originalInterface = ((RemoteFrameworkElement)node).getEditableInterfacesObject(); // TODO: optimization: could be done asynchronously
        EditableInterfaces newInterface = Serialization.deepCopy(originalInterface);

        // Add ports to interfaces
        for (Map.Entry<String, ArrayList<PortCreationList.Entry>> entry : portsToAdd.entrySet()) {
            PortCreationList interfaceList = null;
            for (int i = 0; i < newInterface.getStaticParameterList().size(); i++) {
                if (newInterface.getStaticParameterList().get(i).getName().equals(entry.getKey())) {
                    interfaceList = (PortCreationList)newInterface.getStaticParameterList().get(i).valPointer().getData();
                    break;
                }
            }
            if (interfaceList == null) {
                throw new Exception("No interface named '" + entry.getKey() + "' in component '" + link + "'");
            }

            for (PortCreationList.Entry addPort : entry.getValue()) {
                // check whether interface already contains port
                for (int i = 0; i < interfaceList.getSize(); i++) {
                    if (interfaceList.getEntry(i).name.equals(addPort.name)) {
                        throw new Exception("Port with name '" + addPort.name + "' already exists in interface for component '" + link + "'");
                    }
                }
                PortCreationList.Entry newEntry = interfaceList.addElement();
                newEntry.name = addPort.name;
                newEntry.type = addPort.type;
                newEntry.createOptions = addPort.createOptions;
            }

        }

        // Commit to remote runtime
        runtime.getAdminInterface().setAnnotation(((RemoteFrameworkElement)node).getRemoteHandle(), newInterface);
    }

    @Override
    protected String checkSuccessImplementation() {
        if (undoAction) {
            return "";
        }

        ModelNode node = Finstruct.getInstance().getIoInterface().getChildByQualifiedName(link, LINK_SEPARATOR);
        if (!(node instanceof RemoteFrameworkElement)) {
            return "Cannot find remote framework element '" + link + "'";
        }
        ArrayList<RemoteFrameworkElement> currentInterfaces = ((RemoteFrameworkElement)node).getEditableInterfaces();
        if (currentInterfaces == null) {
            return "Interfaces disappeared";
        }

        for (Map.Entry<String, ArrayList<PortCreationList.Entry>> entry : portsToAdd.entrySet()) {
            RemoteFrameworkElement element = null;
            for (RemoteFrameworkElement currentInterface : currentInterfaces) {
                if (currentInterface.getName().equals(entry.getKey())) {
                    element = currentInterface;
                    break;
                }
            }
            if (element == null) {
                return null;
            }

            for (PortCreationList.Entry addPort : entry.getValue()) {
                // check whether interface already contains port
                boolean found = false;
                for (int i = 0; i < element.getChildCount(); i++) {
                    if (element.getChildAt(i).getName().equals(addPort.name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return null;
                }
            }
        }
        return "";
    }

    @Override
    protected FinstructAction getUndoActionImplementation() {
        return new AddPortsToInterfaceAction(link, originalInterface);
    }

    /**
     * @return Qualified name of remote component
     */
    public String getComponentLink() {
        return link;
    }

    /**
     * @param interfaceName Name of component interface
     * @return List with ports to add for this interface (is created if it does not exist yet). Can be used to add/remove entries.
     * @throws Exception if no editable interface with specified name exists
     */
    public ArrayList<PortCreationList.Entry> getPortsToAdd(String interfaceName) throws Exception {
        ArrayList<PortCreationList.Entry> result = portsToAdd.get(interfaceName);
        if (result == null) {
            RemoteFrameworkElement found = null;
            for (RemoteFrameworkElement element : editableInterfaces) {
                if (element.getName().equals(interfaceName)) {
                    found = element;
                    break;
                }
            }
            if (found == null) {
                throw new Exception("Element '" + link + "' has no interface with name '" + interfaceName + "'");
            }
            result = new ArrayList<PortCreationList.Entry>();
            portsToAdd.put(interfaceName, result);
        }
        return result;
    }

    /**
     * @return Number of ports created by this action
     */
    public int getNewPortCount() {
        int count = 0;
        for (Map.Entry<String, ArrayList<PortCreationList.Entry>> entry : portsToAdd.entrySet()) {
            count += entry.getValue().size();
        }
        return count;
    }

//  /**
//   * @return The new version of the interface (to be edited/adapted)
//   */
//  public EditableInterfaces getEditableInterfaces() {
//      return newInterface;
//  }
//
//  /**
//   * @param Name of single interface to edit
//   * @return The new version of the single interface (to be edited/adapted). Null if component has no such interface.
//   */
//  public PortCreationList getInterface(String name) {
//      for (int i = 0; i < newInterface.getStaticParameterList().size(); i++) {
//          if (newInterface.getStaticParameterList().get(i).getName().equals(name)) {
//              return (PortCreationList)newInterface.getStaticParameterList().get(i).valPointer().getData();
//          }
//      }
//      return null;
//  }

    // private constructor for undo action
    private AddPortsToInterfaceAction(String link, EditableInterfaces originalInterface) {
        this.link = link;
        this.originalInterface = new EditableInterfaces();
        this.editableInterfaces = null;
        undoAction = true;
    }

    /** Qualified name of component */
    private final String link;

    /** Editable interfaces of component */
    private final ArrayList<RemoteFrameworkElement> editableInterfaces;

    /** Ports to add to interfaces */
    private final Map<String, ArrayList<PortCreationList.Entry>> portsToAdd = new HashMap<String, ArrayList<PortCreationList.Entry>>();

    /** Original interface (for undo) */
    private EditableInterfaces originalInterface;

    /** Undo action for add ports */
    private final boolean undoAction;
}
