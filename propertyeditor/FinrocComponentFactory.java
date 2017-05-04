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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.finroc.core.datatype.CoreBoolean;
import org.finroc.core.datatype.DataTypeReference;
import org.finroc.core.datatype.PortCreationList;
import org.finroc.core.datatype.XML;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.core.portdatabase.SerializationHelper;
import org.finroc.core.remote.ModelNode;
import org.finroc.core.remote.RemoteRuntime;
import org.finroc.core.remote.RemoteType;
import org.finroc.core.remote.RemoteTypes;
import org.finroc.tools.gui.util.propertyeditor.BooleanEditor;
import org.finroc.tools.gui.util.propertyeditor.ComponentFactory;
import org.finroc.tools.gui.util.propertyeditor.FieldAccessorFactory;
import org.finroc.tools.gui.util.propertyeditor.PropertiesPanel;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyAccessorAdapter;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.util.propertyeditor.PropertyListAccessor;
import org.finroc.tools.gui.util.propertyeditor.PropertyListEditor;
import org.finroc.tools.gui.util.propertyeditor.StandardComponentFactory;
import org.finroc.tools.gui.util.propertyeditor.gui.DataTypeEditor;
import org.finroc.plugins.data_types.ContainsStrings;
import org.finroc.plugins.data_types.PaintablePortData;
import org.finroc.plugins.data_types.TaskProfile;
import org.rrlib.finroc_core_utils.jc.ArrayWrapper;
import org.rrlib.finroc_core_utils.jc.container.SafeConcurrentlyIterableList;
import org.rrlib.serialization.BinarySerializable;
import org.rrlib.serialization.EnumValue;
import org.rrlib.serialization.PortDataListImpl;
import org.rrlib.serialization.Serialization;
import org.rrlib.serialization.StringInputStream;
import org.rrlib.serialization.XMLSerializable;
import org.rrlib.serialization.rtti.Copyable;
import org.rrlib.serialization.rtti.DataTypeBase;
import org.rrlib.xml.XMLDocument;
import org.xml.sax.InputSource;

/**
 * @author Max Reichardt
 *
 * Component factory for Finroc types
 */
public class FinrocComponentFactory implements ComponentFactory {

    /** Framework element that all displayed ports are child of */
    private final ModelNode commonParent;

    public FinrocComponentFactory(ModelNode commonParent) {
        this.commonParent = commonParent;
    }

    /**
     * @param type Type to check
     * @return True if type is supported by Finroc component factory
     * (e.g. a component for displaying and possibly editing is available)
     */
    public static boolean isTypeSupported(DataTypeBase dt) {
        if (FinrocTypeInfo.isCCType(dt) || FinrocTypeInfo.isStdType(dt) || ((dt instanceof RemoteType) && ((RemoteType)dt).isAdaptable())) {
            Class<?> type = dt.getJavaClass();
            if (type != null) {
                return (type.equals(PortCreationList.class) || DataTypeReference.class.equals(type)
                        || PaintablePortData.class.isAssignableFrom(type) || XML.class.isAssignableFrom(type) || type.isEnum() || type.equals(TaskProfile.List.class)
                        || type.equals(EnumValue.class) || CoreBoolean.class.isAssignableFrom(type) || type.equals(PortDataListImpl.class)
                        || (BinarySerializable.class.isAssignableFrom(type) && (Serialization.isStringSerializable(type) || Serialization.isXmlSerializable(type))));
            }
        }
        return false;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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
            wpec.setPreferredSize(new Dimension(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width, 200));
            acc = new PortCreationListAdapter((PropertyAccessor<PortCreationList>)acc);
        } else if (DataTypeReference.class.equals(type)) {
            RemoteRuntime rr = RemoteRuntime.find(commonParent);
            ArrayList<DataTypeReference> types = new ArrayList<DataTypeReference>();
            if (rr == null) { // use local data types
                for (short i = 0; i < DataTypeBase.getTypeCount(); i++) {
                    DataTypeBase dt = DataTypeBase.getType(i);
                    if (dt != null) {
                        types.add(new DataTypeReference(dt));
                    }
                }
            } else {
                SafeConcurrentlyIterableList<RemoteTypes.Entry> remoteTypes = rr.getRemoteTypes().getTypes();
                ArrayWrapper<RemoteTypes.Entry> iterable = remoteTypes.getIterable();
                for (int i = 0, n = iterable.size(); i < n; i++) {
                    RemoteTypes.Entry entry = iterable.get(i);
                    if (entry != null && entry.getLocalDataType() != null) {
                        types.add(new DataTypeReference(entry.getLocalDataType()));
                    }
                }
            }
            wpec = new DataTypeEditor(types.toArray(new DataTypeReference[0]), null, panel);
            //acc = new CoreSerializableAdapter((PropertyAccessor<BinarySerializable>)acc, type, DataTypeReference.TYPE);
        } else if (PaintablePortData.class.isAssignableFrom(type)) {
            wpec = new PaintableViewer();
        } else if (XML.class.isAssignableFrom(type)) {
            wpec = new XMLEditor();
        } else if (type.isEnum() || type.equals(EnumValue.class)) {
            wpec = new EnumEditor();
        } else if (CoreBoolean.class.isAssignableFrom(type)) {
            wpec = new BooleanEditor();
            acc = new CoreBooleanAdapter((PropertyAccessor<CoreBoolean>)acc);
        } else if (type.equals(TaskProfile.List.class)) {
            wpec = new TaskProfileViewer(RemoteRuntime.find(commonParent));
        } else if (type.equals(PortDataListImpl.class)) {
            wpec = new CoreSerializableDefaultEditor(type);
            acc = new PortDataListAdapter((PropertyAccessor<PortDataListImpl>)acc);
        } else if (BinarySerializable.class.isAssignableFrom(type) && Serialization.isStringSerializable(type)) {
            DataTypeBase dt = DataTypeBase.findType(acc.getType(), null);
            wpec = new CoreSerializableDefaultEditor(type);
            acc = new CoreSerializableAdapter((PropertyAccessor<BinarySerializable>)acc, type, dt);
        } else if ((!ContainsStrings.class.isAssignableFrom(type)) && BinarySerializable.class.isAssignableFrom(type) && Serialization.isXmlSerializable(type)) {
            DataTypeBase dt = DataTypeBase.findType(acc.getType(), null);
            wpec = new XMLEditor();
            acc = new CoreSerializableXMLAdapter((PropertyAccessor<BinarySerializable>)acc, type, dt);
        }

        if (wpec != null) {
            wpec.init(acc);
        }
        return wpec;
    }

    /**
     * Allows using CoreSerializables in TextEditor
     */
    public static class CoreSerializableAdapter extends PropertyAccessorAdapter<BinarySerializable, String> {

        /** Expected finroc class of property */
        private final Class<?> finrocClass;

        /** Finroc DataType of property - if available */
        private final DataTypeBase dataType;

        public CoreSerializableAdapter(PropertyAccessor<BinarySerializable> wrapped, Class<?> finrocClass, DataTypeBase dataType) {
            super(wrapped, String.class);
            this.finrocClass = finrocClass;
            this.dataType = dataType;
        }

        @Override
        public void set(String s) throws Exception {
            if (dataType != null && BinarySerializable.class.isAssignableFrom(finrocClass)) {
                DataTypeBase dt = SerializationHelper.getTypedStringDataType(dataType, s);
                BinarySerializable buffer = (BinarySerializable)dt.createInstance();
                SerializationHelper.typedStringDeserialize(buffer, s);
                wrapped.set(buffer);
            } else {
                wrapped.set((BinarySerializable)new StringInputStream(s).readObject(finrocClass));
            }
        }

        @Override
        public String get() throws Exception {
            BinarySerializable cs = wrapped.get();
            if (cs == null) {
                return "";
            }
            if (dataType != null && BinarySerializable.class.isAssignableFrom(finrocClass)) {
                return SerializationHelper.typedStringSerialize(dataType, cs, dataType);
            } else {
                return Serialization.serialize(cs);
            }
        }
    }

    /**
     * Allows using CoreSerializables in TextEditor
     */
    public static class CoreSerializableXMLAdapter extends PropertyAccessorAdapter<BinarySerializable, XML> {

        /** Expected finroc class of property */
        //private final Class<?> finrocClass;

        /** Finroc DataType of property - if available */
        private final DataTypeBase dataType;

        public CoreSerializableXMLAdapter(PropertyAccessor<BinarySerializable> wrapped, Class<?> finrocClass, DataTypeBase dataType) {
            super(wrapped, XML.class);
            //this.finrocClass = finrocClass;
            this.dataType = dataType;
        }

        @Override
        public void set(XML s) throws Exception {
            BinarySerializable buffer = (BinarySerializable)dataType.createInstance();
            XMLDocument d = new XMLDocument(new InputSource(new StringReader(s.toString())), false);
            ((XMLSerializable)buffer).deserialize(d.getRootNode());
            wrapped.set(buffer);
        }

        @Override
        public XML get() throws Exception {
            BinarySerializable cs = wrapped.get();
            XMLDocument doc = new XMLDocument();
            doc.addRootNode("value");
            XML result = new XML();
            if (cs != null) {
                //doc.getRootNode().setAttribute("type", dataType.getName());
                ((XMLSerializable)cs).serialize(doc.getRootNode());
            }
            result.set(doc.getRootNode().getXMLDump(true));
            return result;
        }
    }


    /**
     * Allows using CoreSerializables in TextEditor
     */
    @SuppressWarnings("rawtypes")
    public static class PortDataListAdapter extends PropertyAccessorAdapter<PortDataListImpl, String> {

        /** Data type of list elements */
        private DataTypeBase elementType;

        public PortDataListAdapter(PropertyAccessor<PortDataListImpl> wrapped) {
            super(wrapped, String.class);
        }

        @Override
        public void set(String s) throws Exception {
            if (elementType == null) {
                throw new Exception("Unknown element type");
            }
            PortDataListImpl list = new PortDataListImpl(elementType);
            String[] strings = s.trim().split("\n");
            list.resize(strings.length);
            for (int i = 0; i < strings.length; i++) {
                Serialization.deepCopy(new StringInputStream(strings[i]).readObject(elementType.getJavaClass()), list.get(i));
            }
            wrapped.set(list);
        }

        @Override
        public String get() throws Exception {
            PortDataListImpl list = wrapped.get();
            if (list == null) {
                return "";
            }
            elementType = list.getElementType();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(Serialization.serialize(list.get(i)));
            }
            return sb.toString();
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
        public static class PortCreationListAccessor implements PropertyListAccessor<PortCreationList.Entry>, Copyable<PortCreationListAccessor> {

            /** Wrapped TypedObjectList */
            private PortCreationList wrapped;

            private PortCreationListAccessor(PortCreationList wrapped) {
                assert(wrapped != null);
                this.wrapped = wrapped;
            }

            public PortCreationListAccessor() {} // only for deepCopy.newInstance() to succeed

            @Override
            public void copyFrom(PortCreationListAccessor source) {
                this.wrapped = Serialization.deepCopy(source.wrapped);
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

            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public List < PropertyAccessor<? >> getElementAccessors(PortCreationList.Entry element) {
                List < PropertyAccessor<? >> accs = FieldAccessorFactory.getInstance().createAccessors(element);
                PropertyAccessor createOptionFlagAccessor = accs.remove(accs.size() - 1);
                if ((wrapped.getSelectableCreateOptions() & PortCreationList.CREATE_OPTION_SHARED) != 0) {
                    accs.add(1, new PortCreationEntryFlagAccessor(createOptionFlagAccessor, PortCreationList.CREATE_OPTION_SHARED));
                }
                if ((wrapped.getSelectableCreateOptions() & PortCreationList.CREATE_OPTION_OUTPUT) != 0) {
                    accs.add(1, new PortCreationEntryFlagAccessor(createOptionFlagAccessor, PortCreationList.CREATE_OPTION_OUTPUT));
                }
                return accs;
            }

            @Override
            public void addElement() {
                wrapped.addElement();
            }

            @Override
            public void removeElement(int index) {
                wrapped.removeElement(index);
            }

            public static class PortCreationEntryFlagAccessor extends PropertyAccessorAdapter<Byte, Boolean> {

                final byte flag;

                public PortCreationEntryFlagAccessor(PropertyAccessor<Byte> wrapped, byte flag) {
                    super(wrapped, Boolean.class);
                    this.flag = flag;
                }

                @Override
                public Boolean get() throws Exception {
                    return (wrapped.get() & flag) != 0;
                }

                @Override
                public void set(Boolean newValue) throws Exception {
                    byte flags = wrapped.get();
                    if (newValue) {
                        flags |= flag;
                    } else {
                        flags &= ~flag;
                    }
                    wrapped.set(flags);
                }

                @Override
                public String getName() {
                    return flag == PortCreationList.CREATE_OPTION_OUTPUT ? "Output" : "Shared";
                }
            }
        }
    }

    public static class CoreBooleanAdapter extends PropertyAccessorAdapter<CoreBoolean, Boolean> {

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
