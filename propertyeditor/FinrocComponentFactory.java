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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.datatype.CoreBoolean;
import org.finroc.core.datatype.DataTypeReference;
import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.datatype.XML;
import org.finroc.core.portdatabase.SerializationHelper;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.tools.gui.util.propertyeditor.BooleanEditor;
import org.finroc.tools.gui.util.propertyeditor.ComboBoxEditor;
import org.finroc.tools.gui.util.propertyeditor.ComponentFactory;
import org.finroc.tools.gui.util.propertyeditor.FieldAccessorFactory;
import org.finroc.tools.gui.util.propertyeditor.ObjectCloner;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessorAdapter;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.PropertyListAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyListEditor;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.finroc.plugins.data_types.ContainsStrings;
import org.finroc.plugins.data_types.PaintablePortData;
import org.rrlib.finroc_core_utils.rtti.DataTypeBase;
import org.rrlib.finroc_core_utils.serialization.EnumValue;
import org.rrlib.finroc_core_utils.serialization.RRLibSerializable;
import org.rrlib.finroc_core_utils.serialization.Serialization;
import org.rrlib.finroc_core_utils.serialization.StringInputStream;

/**
 * @author max
 *
 * Component factory for Finroc types
 */
public class FinrocComponentFactory implements ComponentFactory {

    /** Framework element that all displayed ports are child of */
    private final ModelNode commonParent;

    static {
        TypedObjectCloner.register();
    }

    public FinrocComponentFactory(ModelNode commonParent) {
        this.commonParent = commonParent;
    }

    @SuppressWarnings( { "rawtypes", "unchecked" })
    @Override
    public PropertyEditComponent<?> createComponent(PropertyAccessor<?> acc, PropertiesPanel panel) throws Exception {
        Class<?> type = acc.getType();
        PropertyEditComponent wpec = null;

        /*if (TypedObjectList.class.isAssignableFrom(type)) {
            wpec = new PropertyListEditor(new FinrocComponentFactory(commonParent), new StandardComponentFactory());
            acc = new TypedObjectListAdapter((PropertyAccessor<TypedObjectList>)acc);
        } else*/
        if (type.equals(PortCreationList.class)) {
            wpec = new PropertyListEditor(panel, new FinrocComponentFactory(commonParent), new StandardComponentFactory());
            wpec.setPreferredSize(new Dimension(800, 200));
            acc = new PortCreationListAdapter((PropertyAccessor<PortCreationList>)acc);
        } else if (DataTypeReference.class.equals(type)) {
            RemoteRuntime rr = RemoteRuntime.find(commonParent);
            ArrayList<String> types = new ArrayList<String>();
            if (rr == null) { // use local data types
                for (short i = 0; i < DataTypeBase.getTypeCount(); i++) {
                    DataTypeBase dt = DataTypeBase.getType(i);
                    if (dt != null) {
                        types.add(dt.getName());
                    }
                }
            } else {
                types.addAll(rr.getRemoteTypes().getRemoteTypeNames());
            }
            wpec = new ComboBoxEditor<String>(types.toArray(new String[0]));
            acc = new CoreSerializableAdapter((PropertyAccessor<RRLibSerializable>)acc, type, DataTypeReference.TYPE);
        } else if (PaintablePortData.class.isAssignableFrom(type)) {
            wpec = new PaintableViewer();
        } else if (XML.class.isAssignableFrom(type)) {
            wpec = new XMLEditor();
        } else if (type.isEnum() || type.equals(EnumValue.class)) {
            wpec = new EnumEditor();
        } else if (CoreBoolean.class.isAssignableFrom(type)) {
            wpec = new BooleanEditor();
            acc = new CoreBooleanAdapter((PropertyAccessor<CoreBoolean>)acc);
        } else if (RRLibSerializable.class.isAssignableFrom(type) && (!ContainsStrings.class.isAssignableFrom(type))) {
            DataTypeBase dt = DataTypeBase.findType(acc.getType());
            wpec = new CoreSerializableDefaultEditor(type);
            acc = new CoreSerializableAdapter((PropertyAccessor<RRLibSerializable>)acc, type, dt);
        }

        if (wpec != null) {
            wpec.init(acc);
        }
        return wpec;
    }

    /**
     * Allows using CoreSerializables in TextEditor
     */
    public static class CoreSerializableAdapter extends PropertyAccessorAdapter<RRLibSerializable, String> {

        /** Expected finroc class of property */
        private final Class<?> finrocClass;

        /** Finroc DataType of property - if available */
        private final DataTypeBase dataType;

        public CoreSerializableAdapter(PropertyAccessor<RRLibSerializable> wrapped, Class<?> finrocClass, DataTypeBase dataType) {
            super(wrapped, String.class);
            this.finrocClass = finrocClass;
            this.dataType = dataType;
        }

        @Override
        public void set(String s) throws Exception {
            if (dataType != null && RRLibSerializable.class.isAssignableFrom(finrocClass)) {
                DataTypeBase dt = SerializationHelper.getTypedStringDataType(dataType, s);
                RRLibSerializable buffer = (RRLibSerializable)dt.createInstance();
                SerializationHelper.typedStringDeserialize(buffer, s);
                wrapped.set(buffer);
            } else {
                RRLibSerializable buffer = (RRLibSerializable)finrocClass.newInstance(); /*(RRLibSerializable)JavaOnlyPortDataFactory.rawCreate(finrocClass);*/
                buffer.deserialize(new StringInputStream(s));
                wrapped.set(buffer);
            }
        }

        @Override
        public String get() throws Exception {
            RRLibSerializable cs = wrapped.get();
            if (cs == null) {
                return "";
            }
            if (dataType != null && RRLibSerializable.class.isAssignableFrom(finrocClass)) {
                return SerializationHelper.typedStringSerialize(dataType, cs, dataType);
            } else {
                return Serialization.serialize(cs);
            }
        }
    }

//    /**
//     * Allows using TypedObjectList in PropertyListEditor
//     */
//    @SuppressWarnings("rawtypes")
//    public static class TypedObjectListAdapter extends PropertyAccessorAdapter<TypedObjectList, TypedObjectListAdapter.TypedObjectListAccessor> {
//
//        /** Expected finroc class of property */
//        //private final Class<?> finrocClass;
//
//        /** Finroc DataType of property - if available */
//        //private final DataType dataType;
//
//        public TypedObjectListAdapter(PropertyAccessor<TypedObjectList> wrapped/*, Class<?> finrocClass, DataType dataType*/) {
//            super(wrapped, TypedObjectListAccessor.class);
//            /*this.finrocClass = finrocClass;
//            this.dataType = dataType;*/
//        }
//
//        @Override
//        public TypedObjectListAccessor get() throws Exception {
//            return new TypedObjectListAccessor(wrapped.get());
//        }
//
//        @Override
//        public void set(TypedObjectListAccessor newValue) throws Exception {
//            wrapped.set(newValue.wrapped);
//        }
//
//        /** Wrapper for TypedObjectLists */
//        public class TypedObjectListAccessor implements PropertyListAccessor, ObjectCloner.Cloneable {
//
//            /** Wrapped TypedObjectList */
//            private final TypedObjectList wrapped;
//
//            private TypedObjectListAccessor(TypedObjectList wrapped) {
//                this.wrapped = wrapped;
//            }
//
//            @Override
//            public boolean equals(Object o) {
//                return (o instanceof TypedObjectListAccessor && wrapped.equals(((TypedObjectListAccessor)o).wrapped));
//            }
//
//            @Override
//            public Object clone() {
//                return new TypedObjectListAccessor(ObjectCloner.clone(wrapped));
//            }
//
//            @Override
//            public Class getElementType() {
//                return wrapped.getElementType().getJavaClass();
//            }
//
//            @Override
//            public int getMaxEntries() {
//                return Integer.MAX_VALUE;
//            }
//
//            @Override
//            public int size() {
//                return wrapped.getSize();
//            }
//
//            @Override
//            public Object get(int index) {
//                if (wrapped instanceof CCDataList) {
//                    return ((CCDataList)wrapped).getWithoutExtraLock(index);
//                } else {
//                    return ((PortDataList)wrapped).getWithoutExtraLock(index);
//                }
//            }
//
//            @SuppressWarnings("unchecked")
//            @Override
//            public List getElementAccessors(Object element) {
//                ArrayList result = new ArrayList();
//                result.add(new FinrocObjectAccessor((TypedObject)element));
//                return result;
//            }
//
//            @Override
//            public void addElement() {
//                wrapped.setSize(wrapped.getSize() + 1, wrapped.getElementType());
//            }
//
//            @Override
//            public void removeElement(int index) {
//                wrapped.removeElement(index);
//            }
//
//        }
//    }

    /**
     * Allows using TypedObjectList in PropertyListEditor
     */
    public static class PortCreationListAdapter extends PropertyAccessorAdapter<PortCreationList, PortCreationListAdapter.PortCreationListAccessor> {

        public PortCreationListAdapter(PropertyAccessor<PortCreationList> wrapped) {
            super(wrapped, PortCreationListAccessor.class);
        }

        @Override
        public PortCreationListAccessor get() throws Exception {
            return new PortCreationListAccessor(wrapped.get());
        }

        @Override
        public void set(PortCreationListAccessor newValue) throws Exception {
            wrapped.set(newValue.wrapped);
        }

        /** Wrapper for TypedObjectLists */
        public static class PortCreationListAccessor implements PropertyListAccessor<PortCreationList.Entry>, ObjectCloner.Cloneable {

            /** Wrapped TypedObjectList */
            private final PortCreationList wrapped;

            private PortCreationListAccessor(PortCreationList wrapped) {
                assert(wrapped != null);
                this.wrapped = wrapped;
            }

            @Override
            public Object clone() {
                return new PortCreationListAccessor(ObjectCloner.clone(wrapped));
            }

            @Override
            public boolean equals(Object o) {
                return (o instanceof PortCreationListAccessor && wrapped.equals(((PortCreationListAccessor)o).wrapped));
            }

            @Override
            public Class<PortCreationList.Entry> getElementType() {
                return PortCreationList.Entry.class;
            }

            @Override
            public int getMaxEntries() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int size() {
                return wrapped.getSize();
            }

            @Override
            public PortCreationList.Entry get(int index) {
                return wrapped.getEntry(index);
            }

            @Override
            public List < PropertyAccessor<? >> getElementAccessors(PortCreationList.Entry element) {
                return FieldAccessorFactory.getInstance().createAccessors(element);
            }

            @Override
            public void addElement() {
                wrapped.addElement();
            }

            @Override
            public void removeElement(int index) {
                wrapped.removeElement(index);
            }

        }
    }

    public class CoreBooleanAdapter extends PropertyAccessorAdapter<CoreBoolean, Boolean> {

        public CoreBooleanAdapter(PropertyAccessor<CoreBoolean> wrapped) {
            super(wrapped, Boolean.class);
        }

        @Override
        public Boolean get() throws Exception {
            CoreBoolean b = wrapped.get();
            return b == null ? false : b.get();
        }

        @Override
        public void set(Boolean newValue) throws Exception {
            wrapped.set(new CoreBoolean(newValue));
        }

    }

}
