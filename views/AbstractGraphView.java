/**
 * You received this file as part of Finstruct - a tool for
 * the Finroc Framework.
 *
 * Copyright (C) 2010 Robotics Research Lab, University of Kaiserslautern
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
package org.finroc.tools.finstruct.views;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JComponent;

import org.finroc.core.Annotatable;
import org.finroc.core.FrameworkElement.ChildIterator;
import org.finroc.core.CoreFlags;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTags;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.EdgeAggregator;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.net.NetPort;
import org.finroc.core.portdatabase.FinrocTypeInfo;
import org.finroc.tools.finstruct.FinstructConnectionPanel;
import org.finroc.tools.finstruct.FinstructView;

/**
 * @author max
 *
 * Base class for views displaying graphs.
 *
 * Contains various utility functions.
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractGraphView<V extends AbstractGraphView.Vertex, E extends AbstractGraphView.Edge> extends FinstructView {

    /** UID */
    private static final long serialVersionUID = 1516347489852848159L;

    /** Reference to connectionPanel */
    protected FinstructConnectionPanel connectionPanel;

    /**
     * @param fe Framework Element
     * @return Output interface?
     */
    public static boolean isOutputOnlyInterface(FrameworkElement fe) {
        boolean one = false;
        boolean all = true;
        if (isInterface(fe)) {
            ChildIterator ci = new ChildIterator(fe);
            AbstractPort next = null;
            while ((next = ci.nextPort()) != null) {
                if (!FinrocTypeInfo.isMethodType(next.getDataType(), true)) {
                    boolean output = next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= output;
                    all &= output;
                }
            }
        }
        return (one & all) || (all & fe.getName().toLowerCase().contains("output") && (!fe.getName().toLowerCase().contains("input")));
    }

    /**
     * @param fe Framework Element
     * @return Input interface?
     */
    public static boolean isInputOnlyInterface(FrameworkElement fe) {
        boolean one = false;
        boolean all = true;
        if (isInterface(fe)) {
            ChildIterator ci = new ChildIterator(fe);
            AbstractPort next = null;
            while ((next = ci.nextPort()) != null) {
                if (!FinrocTypeInfo.isMethodType(next.getDataType(), true)) {
                    boolean input = !next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= input;
                    all &= input;
                }
            }
        }
        return (one & all) || (all & fe.getName().toLowerCase().contains("input") && (!fe.getName().toLowerCase().contains("output")));
    }

    /**
     * @param fe Framework Element
     * @return Is framework Element a sensor interface?
     */
    public static boolean isSensorInterface(FrameworkElement fe) {
        return isInterface(fe) && fe.getFlag(EdgeAggregator.SENSOR_DATA) && (!fe.getFlag(EdgeAggregator.CONTROLLER_DATA));
    }

    /**
     * @param fe Framework Element
     * @return Is framework Element a Controller interface?
     */
    public static boolean isControllerInterface(FrameworkElement fe) {
        return isInterface(fe) && fe.getFlag(EdgeAggregator.CONTROLLER_DATA) && (!fe.getFlag(EdgeAggregator.SENSOR_DATA));
    }

    /**
     * @param fe Framework Element
     * @return Is framework element an interface
     */
    public static boolean isInterface(FrameworkElement fe) {
        return fe.getFlag(CoreFlags.EDGE_AGGREGATOR) && fe.getFlag(EdgeAggregator.IS_INTERFACE);
    }

    /**
     * @param fe Framework element
     * @return Is parameter node?
     */
    public static boolean isParameters(FrameworkElement fe) {
        if (fe.getName().equalsIgnoreCase("Parameter") || fe.getName().equalsIgnoreCase("Parameters")) {
            return hasOnlyPortChildren(fe, true);
        }
        return false;
    }

    /**
     * @param fe Framework element
     * @param onNoChildrenReturn Value to return if framework element has no children
     * @return Are all children of framework element ports?
     */
    public static boolean hasOnlyPortChildren(FrameworkElement fe, boolean onNoChildrenReturn) {
        ChildIterator ci = new ChildIterator(fe);
        FrameworkElement next = null;
        boolean hasChildren = false;
        while ((next = ci.next()) != null) {
            if (!next.isPort()) {
                return false;
            }
            hasChildren = true;
        }
        return hasChildren ? true : onNoChildrenReturn;
    }


    /**
     * Returns parent of framework element that is displayed in graph
     *
     * @param lookup Lookup table for out vertices
     * @param fe Framework element whose parent to search for
     * @return Parent
     */
    public GetParentResult getParentInGraph(HashMap<FrameworkElement, V> lookup, FrameworkElement fe) {
        GetParentResult result = new GetParentResult();
        for (int i = 0; i < fe.getLinkCount(); i++) {
            FrameworkElement current = fe;
            FrameworkElement parent = fe.getLink(i).getParent();
            while (parent != null) {
                if (current.getFlag(CoreFlags.EDGE_AGGREGATOR)) {
                    result.dataTypeFlags |= current.getAllFlags();
                }
                V v = lookup.get(current);
                if (v != null) {
                    result.parent = v;
                    return result;
                }
                current = parent;
                parent = current.getParent();
            }
        }
        return result;
    }

    /**
     * Returns all framework element below root that should be displayed
     *
     * @param root Root
     * @return List
     */
    @SuppressWarnings("unchecked")
    public List<V> getVertices(FrameworkElement root) {
        ChildIterator ci = new ChildIterator(root);
        ArrayList<V> result = new ArrayList<V>();
        if (!root.isReady()) {
            return result;
        }

        // add vertices
        FrameworkElement next = null;
        while ((next = ci.next()) != null) {
            V vertex = createVertexInstance(next);
            if (vertex != null) {
                result.add(vertex);
            }
        }

        // mark special vertices
        for (int controller = 0; controller <= 1; controller++) {
            for (int output = 0; output <= 1; output++) {
                V found = null;
                int foundCount = 0;

                for (V v : result) {
                    FrameworkElement next2 = v.frameworkElement;
                    if ((controller == 1 ? isControllerInterface(next2) : isSensorInterface(next2)) && (output == 1 ? isOutputOnlyInterface(next2) : isInputOnlyInterface(next2))) {
                        found = v;
                        foundCount++;
                    }
                }

                if (foundCount == 1) {
                    found.specialNode = controller == 1 ? (output == 1 ? SpecialNode.ControllerOutput : SpecialNode.ControllerInput) : (output == 1 ? SpecialNode.SensorOutput : SpecialNode.SensorInput);
                    if (controller == 0) {
                        result.remove(found);
                        result.add(0, found);
                    }
                }
            }
        }

        // mark groups
        for (final V v : result) {
            // we have a group, if framework element is tagged as such
            if (FrameworkElementTags.isTagged(v.frameworkElement, "group")) {
                v.setGroup(true);
            }
        }

        return result;
    }

    /**
     * Returns all framework element below root that should be displayed
     *
     * @param root Root
     * @param allVertices All Vertices that could be connected in graph
     * @return List with edges (start and endpoints) are set to vertices returned by method above
     */
    public Collection<E> getEdges(final FrameworkElement root, Collection<V> allVertices) {
        final TreeMap<E, E> result = new TreeMap<E, E>();
        if (!root.isReady()) {
            return new ArrayList<E>();
        }
        final HashMap<FrameworkElement, V> lookup = new HashMap<FrameworkElement, V>();
        for (V v : allVertices) {
            lookup.put(v.frameworkElement, v);
        }
        FrameworkElementTreeFilter.Callback<Boolean> cb = new FrameworkElementTreeFilter.Callback<Boolean>() {
            @SuppressWarnings("unchecked")
            @Override
            public void treeFilterCallback(FrameworkElement fe, Boolean unused) {
                if (fe.isChildOf(root)) {
                    NetPort np = ((AbstractPort)fe).asNetPort();
                    for (FrameworkElement feDest : np.getRemoteEdgeDestinations()) {
                        GetParentResult src = getParentInGraph(lookup, fe);
                        GetParentResult dest = getParentInGraph(lookup, feDest);
                        if (src.parent != null && dest.parent != null && src.parent != dest.parent) {
                            E eNew = createEdgeInstance(src.parent, dest.parent);
                            eNew.source = src.parent;
                            eNew.destination = dest.parent;
                            E e = result.get(eNew);
                            if (e == null) {
                                e = eNew;
                                result.put(eNew, eNew);
                            }
                            e.dataTypeFlags |= src.dataTypeFlags | dest.dataTypeFlags;
                        }
                    }
                }
            }
        };
        FrameworkElementTreeFilter filter = new FrameworkElementTreeFilter(CoreFlags.IS_PORT | CoreFlags.STATUS_FLAGS, CoreFlags.IS_PORT | CoreFlags.READY | CoreFlags.PUBLISHED);
        filter.traverseElementTree(root, cb, null);
        return result.values();
    }

    /**
     * @param v Vertex to get color for
     * @return Base color to draw vertex in
     */
    public Color getVertexColor(Vertex v) {
        if (v.specialNode == SpecialNode.SensorInput || v.specialNode == SpecialNode.SensorOutput) {
            return Color.yellow;
        } else if (v.specialNode == SpecialNode.ControllerInput || v.specialNode == SpecialNode.ControllerOutput) {
            return Color.red;
        }
        return v.isGroup() ? DARK_BLUE : Color.blue;
    }

    /**
     * @param e Edge to get color for
     * @return Base color to draw edge in
     */
    public Color getEdgeColor(Edge e) {
        if (e.isSensorData()) {
            return Color.YELLOW;
        }
        if (e.isControllerData()) {
            return Color.RED;
        }
        return Color.BLACK;
    }

    /**
     * @author max
     *
     * Holds result for method above
     */
    public class GetParentResult {
        V parent;
        int dataTypeFlags;
    }

    /**
     * Edge in View
     */
    public class Edge extends Annotatable implements Serializable, Comparable<Edge> {

        /** UID */
        private static final long serialVersionUID = 7311395657703973551L;

        /** Data type flags (to find out whether it's sensor or controller data only) */
        public int dataTypeFlags;

        /** Source and destination vertex of edge */
        Vertex source, destination;

        public Edge() {}

        public Edge(Vertex s, Vertex d) {
            source = s;
            destination = d;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object other) {
            if (other instanceof AbstractGraphView.Edge) {
                Edge e = (Edge)other;
                return source == e.source && destination == e.destination;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return source.hashCode() - destination.hashCode();
        }

        /**
         * @return Source vertex of edge
         */
        public Vertex getSource() {
            return source;
        }

        /**
         * @return Destination vertex of edge
         */
        public Vertex getDestination() {
            return destination;
        }

        /**
         * @return Does edge transport controller data (only)?
         */
        public boolean isControllerData() {
            return (dataTypeFlags & (EdgeAggregator.SENSOR_DATA | EdgeAggregator.CONTROLLER_DATA)) == EdgeAggregator.CONTROLLER_DATA;
        }

        /**
         * @return Does edge transport sensor data (only)?
         */
        public boolean isSensorData() {
            return (dataTypeFlags & (EdgeAggregator.SENSOR_DATA | EdgeAggregator.CONTROLLER_DATA)) == EdgeAggregator.SENSOR_DATA;
        }

        /**
         * @return Edge color
         */
        public Color getColor() {
            return getEdgeColor(this);
        }

        @Override
        public int compareTo(Edge o) {
            int sh = source.frameworkElement.getHandle();
            int dh = destination.frameworkElement.getHandle();
            int osh = o.source.frameworkElement.getHandle();
            int dsh = o.destination.frameworkElement.getHandle();
            if (sh < osh) {
                return -1;
            } else if (sh > osh) {
                return 1;
            }
            if (dh < dsh) {
                return -1;
            } else if (dh > dsh) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Enum for interface types that need to be treated specially
     */
    public enum SpecialNode { SensorInput, SensorOutput, ControllerInput, ControllerOutput }

    /** Dark blue color */
    public final static Color DARK_BLUE = Color.BLUE.darker().darker();

    /**
     * Vertex for every Finroc FrameworkElement that is currently displayed in graph
     */
    public class Vertex {

        /** Marks special interfaces */
        protected SpecialNode specialNode;

        /** Finroc element that this vertex represents */
        protected final FrameworkElement frameworkElement;

        /** Is this vertex a (expandable) group? */
        private boolean isGroup;

        /**
         * @param fe Finroc element that this vertex represents
         */
        public Vertex(FrameworkElement fe) {
            frameworkElement = fe;
        }

        /**
         * @return Finroc element that this vertex represents
         */
        public FrameworkElement getFinrocElement() {
            return frameworkElement;
        }

        /**
         * Reset special interface info
         */
        public void reset() {
            specialNode = null;
            isGroup = false;
        }

        /**
         * @return Should node have fixed position in graph?
         */
        public boolean hasFixedPos() {
            return specialNode != null;
        }

        /**
         * @return If node has fixed position: left-most or right-most?
         */
        public boolean onRight() {
            return specialNode == SpecialNode.ControllerInput || specialNode == SpecialNode.ControllerOutput;
        }

        /**
         * @return If node has fixed position: bottom-most or top-most?
         */
        public boolean atBottom() {
            return specialNode == SpecialNode.SensorInput || specialNode == SpecialNode.ControllerOutput;
        }

        /**
         * @return Node color in graph
         */
        public Color getColor() {
            return getVertexColor(this);
        }

        /**
         * @return Color for text
         */
        public Color getTextColor() {
            return getColor() == Color.yellow || getColor() == Color.red ? Color.black : Color.white;
        }

        /**
         * @return Is this vertex a (expandable) group?
         */
        public boolean isGroup() {
            return isGroup;
        }

        /**
         * @param b Is this vertex a (expandable) group?
         */
        public void setGroup(boolean b) {
            isGroup = b;
        }
    }

//    /**
//     * @param fe Framework Element
//     * @return VertexAnnotation associated with framework element (null if none exists)
//     */
//    public V getAnnotation(FrameworkElement fe) {
//        return fe.getAnnotation(vertexClass);
//    }
//
//    /**
//     * @param fe Framework Element
//     * @return VertexAnnotation associated with framework element (creates one if none exists)
//     */
//    @SuppressWarnings("unchecked")
//    public V getOrCreateAnnotation(FrameworkElement fe) {
//        V ann = getAnnotation(fe);
//        if (ann == null) {
//            ann = (V)createVertexInstance(fe);
//            fe.addAnnotation(ann);
//        }
//        return ann;
//    }

    /**
     * Create framework element annotation instance for provided framework element
     * (may be overridden)
     *
     * @param fe framework element
     * @return Instance - needs to be a subclass of VertexAnnotation
     */
    @SuppressWarnings("unchecked")
    protected V createVertexInstance(FrameworkElement fe) {
        return (V)new Vertex(fe);
    }

    /**
     * Create edge instance for provided framework elements
     * (may be overridden)
     *
     * @param source Edge source
     * @param dest Edge destination
     * @return Instance - needs to be a subclass of VertexAnnotation
     */
    @SuppressWarnings("unchecked")
    protected E createEdgeInstance(V source, V dest) {
        return (E)new Edge(source, dest);
    }

    @Override
    public JComponent initLeftPanel(FinstructConnectionPanel connectionPanel) {
        this.connectionPanel = connectionPanel;
        return super.initLeftPanel(connectionPanel);
    }
}
