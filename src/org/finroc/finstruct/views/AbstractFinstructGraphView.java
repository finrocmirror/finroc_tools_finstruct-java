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
package org.finroc.finstruct.views;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.finroc.core.Annotatable;
import org.finroc.core.ChildIterator;
import org.finroc.core.CoreFlags;
import org.finroc.core.FinrocAnnotation;
import org.finroc.core.FrameworkElement;
import org.finroc.core.FrameworkElementTreeFilter;
import org.finroc.core.buffer.CoreInput;
import org.finroc.core.buffer.CoreOutput;
import org.finroc.core.port.AbstractPort;
import org.finroc.core.port.EdgeAggregator;
import org.finroc.core.port.PortFlags;
import org.finroc.core.port.net.NetPort;
import org.finroc.finstruct.FinstructView;

/**
 * @author max
 *
 * Base class for views displaying graphs.
 *
 * Contains various utility functions.
 */
public abstract class AbstractFinstructGraphView<V extends AbstractFinstructGraphView.VertexAnnotation, E extends AbstractFinstructGraphView.Edge> extends FinstructView {

    /** UID */
    private static final long serialVersionUID = 1516347489852848159L;

    /** Type V */
    private final Class<V> vertexClass;

    public AbstractFinstructGraphView(Class<V> vertexClass) {
        this.vertexClass = vertexClass;
    }

    /**
     * @param fe Framework Element
     * @return Output interface?
     */
    public static boolean isOutputOnlyInterface(FrameworkElement fe) {
        boolean one = false;
        boolean all = true;
        if (isInterface(fe)) {
            ChildIterator ci = new ChildIterator(fe);
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                if (next.isPort()) {
                    boolean output = next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= output;
                    all &= output;
                }
            }
        }
        return (one & all) || (all & fe.getDescription().toLowerCase().contains("output") && (!fe.getDescription().toLowerCase().contains("input")));
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
            FrameworkElement next = null;
            while ((next = ci.next()) != null) {
                if (next.isPort()) {
                    boolean input = !next.getFlag(PortFlags.IS_OUTPUT_PORT);
                    one |= input;
                    all &= input;
                }
            }
        }
        return (one & all) || (all & fe.getDescription().toLowerCase().contains("input") && (!fe.getDescription().toLowerCase().contains("output")));
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
     * Returns parent of framework element that is displayed in graph
     *
     * @param fe Framework element whose parent to search for
     * @return Parent
     */
    public GetParentResult getParentInGraph(FrameworkElement fe) {
        GetParentResult result = new GetParentResult();
        for (int i = 0; i < fe.getLinkCount(); i++) {
            FrameworkElement current = fe;
            FrameworkElement parent = fe.getLink(i).getParent();
            while (parent != null) {
                if (current.getFlag(CoreFlags.EDGE_AGGREGATOR)) {
                    result.dataTypeFlags |= current.getAllFlags();
                }
                if (parent == getRootElement()) {
                    result.parent = current;
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
    public List<FrameworkElement> getVertices(FrameworkElement root) {
        ChildIterator ci = new ChildIterator(root);
        ArrayList<FrameworkElement> result = new ArrayList<FrameworkElement>();
        if (!root.isReady()) {
            return result;
        }

        // add vertices
        FrameworkElement next = null;
        while ((next = ci.next()) != null) {
            VertexAnnotation ann = getAnnotation(next);
            if (ann != null) {
                ann.reset();
            }
            result.add(next);
        }

        // mark special vertices
        for (int controller = 0; controller <= 1; controller++) {
            for (int output = 0; output <= 1; output++) {
                ci.reset();
                next = null;
                FrameworkElement found = null;
                int foundCount = 0;

                while ((next = ci.next()) != null) {
                    if ((controller == 1 ? isControllerInterface(next) : isSensorInterface(next)) && (output == 1 ? isOutputOnlyInterface(next) : isInputOnlyInterface(next))) {
                        found = next;
                        foundCount++;
                    }
                }

                if (foundCount == 1) {
                    VertexAnnotation ann = getOrCreateAnnotation(found);
                    ann.specialNode = controller == 1 ? (output == 1 ? SpecialNode.ControllerOutput : SpecialNode.ControllerInput) : (output == 1 ? SpecialNode.SensorOutput : SpecialNode.SensorInput);
                    if (controller == 0) {
                        result.remove(ann.frameworkElement);
                        result.add(0, ann.frameworkElement);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Returns all framework element below root that should be displayed
     *
     * @param root Root
     * @return List with edges (start and endpoints) are set to vertices returned by method above
     */
    public Collection<E> getEdges(final FrameworkElement root) {
        final HashMap<E, E> result = new HashMap<E, E>();
        if (!root.isReady()) {
            return new ArrayList<E>();
        }
        FrameworkElementTreeFilter.Callback cb = new FrameworkElementTreeFilter.Callback() {
            @SuppressWarnings("unchecked")
            @Override
            public void treeFilterCallback(FrameworkElement fe) {
                if (fe.isChildOf(root)) {
                    NetPort np = ((AbstractPort)fe).asNetPort();
                    for (FrameworkElement feDest : np.getRemoteEdgeDestinations()) {
                        GetParentResult src = getParentInGraph(fe);
                        GetParentResult dest = getParentInGraph(feDest);
                        if (src.parent != null && dest.parent != null && src.parent != dest.parent) {
                            E eNew = (E)createEdgeInstance(src.parent, dest.parent);
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
        filter.traverseElementTree(root, cb, new StringBuilder());
        return result.values();
    }

    /**
     * @author max
     *
     * Holds result for method above
     */
    public class GetParentResult {
        FrameworkElement parent;
        int dataTypeFlags;
    }

    /**
     * Edge in View
     */
    public static class Edge extends Annotatable implements Serializable {

        /** UID */
        private static final long serialVersionUID = 7311395657703973551L;

        /** Data type flags (to find out whether it's sensor or controller data only) */
        public int dataTypeFlags;

        /** Source and destination vertex of edge */
        private FrameworkElement source, destination;

        public Edge() {}

        @Override
        public boolean equals(Object other) {
            if (other instanceof Edge) {
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
        public FrameworkElement getSource() {
            return source;
        }

        /**
         * @return Destination vertex of edge
         */
        public FrameworkElement getDestination() {
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
            if (isSensorData()) {
                return Color.YELLOW;
            }
            if (isControllerData()) {
                return Color.RED;
            }
            return Color.BLACK;
        }
    }

    /**
     * Enum for interface types that need to be treated specially
     */
    public enum SpecialNode { SensorInput, SensorOutput, ControllerInput, ControllerOutput }

    /**
     * Annotation for every Finroc FrameworkElement that is currently displayed in graph
     */
    public static class VertexAnnotation extends FinrocAnnotation {

        /** Marks special interfaces */
        private SpecialNode specialNode;

        /** Finroc element that this vertex represents */
        protected final FrameworkElement frameworkElement;

        /**
         * @param fe Finroc element that this vertex represents
         */
        public VertexAnnotation(FrameworkElement fe) {
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
        }

        @Override
        public void initDataType() {
        }

        @Override
        public void deserialize(CoreInput is) {
            throw new RuntimeException("Unsupported");
        }

        @Override
        public void serialize(CoreOutput os) {
            throw new RuntimeException("Unsupported");
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
            if (specialNode == SpecialNode.SensorInput || specialNode == SpecialNode.SensorOutput) {
                return Color.yellow;
            } else if (specialNode == SpecialNode.ControllerInput || specialNode == SpecialNode.ControllerOutput) {
                return Color.red;
            }
            return Color.blue;
        }

        /**
         * @return Color for text
         */
        public Color getTextColor() {
            return getColor() == Color.blue ? Color.white : Color.black;
        }
    }

    /**
     * @param fe Framework Element
     * @return VertexAnnotation associated with framework element (null if none exists)
     */
    public V getAnnotation(FrameworkElement fe) {
        return fe.getAnnotation(vertexClass);
    }

    /**
     * @param fe Framework Element
     * @return VertexAnnotation associated with framework element (creates one if none exists)
     */
    @SuppressWarnings("unchecked")
    public V getOrCreateAnnotation(FrameworkElement fe) {
        V ann = getAnnotation(fe);
        if (ann == null) {
            ann = (V)createVertexInstance(fe);
            fe.addAnnotation(ann);
        }
        return ann;
    }

    /**
     * Create framework element annotation instance for provided framework element
     * (may be overridden)
     *
     * @param fe framework element
     * @return Instance - needs to be a subclass of VertexAnnotation
     */
    protected VertexAnnotation createVertexInstance(FrameworkElement fe) {
        return new VertexAnnotation(fe);
    }

    /**
     * Create edge instance for provided framework elements
     * (may be overridden)
     *
     * @param source Edge source
     * @param dest Edge destination
     * @return Instance - needs to be a subclass of VertexAnnotation
     */
    protected Edge createEdgeInstance(FrameworkElement source, FrameworkElement dest) {
        return new Edge();
    }
}
