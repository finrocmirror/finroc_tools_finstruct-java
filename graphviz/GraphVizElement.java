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

package org.finroc.tools.finstruct.graphviz;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

import org.rrlib.finroc_core_utils.jc.log.LogDefinitions;
import org.rrlib.finroc_core_utils.log.LogDomain;

/**
 * @author max
 *
 * Base class for major elements in a GraphViz graph
 */
public abstract class GraphVizElement {

    /** Attribute Map */
    protected HashMap<String, Object> attributes = new HashMap<String, Object>();

    /** Unique handle of GraphViz element (initialized as soon as added to graph) */
    private int handle = -1;

    /** Key of attribute that contains finroc handle */
    public final String HANDLE_KEY = "finstructhandle";

    /** Log Domain to use */
    public LogDomain logDomain = LogDefinitions.finroc.getSubDomain("finstruct").getSubDomain("graphviz");

    /**
     * @param name Name/key of attribute
     * @return Current attribute value (null if attribute doesn't exist)
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Sets an attribute
     *
     * @param name Name/key of attribute
     * @param value Attribute value (toString(), will be called to get output string)
     * @return The previous value stored under this key (null if none)
     */
    public Object setAttribute(String name, Object value) {
        return attributes.put(name, value);
    }

    /**
     * Sets an attribute (Same as above, adds quotes to value, however)
     *
     * @param name Name/key of attribute
     * @param value Attribute value (toString(), will be called to get output string)
     * @return The previous value stored under this key (null if none)
     */
    public Object setAttributeQuoted(String name, String value) {
        return attributes.put(name, "\"" + value + "\"");
    }

    /**
     * Removes an attribute
     *
     * @param name Name/key of attribute
     * @return The value stored under this key until now (null if none)
     */
    public Object remove(String name) {
        return attributes.remove(name);
    }

    /**
     * @param handle Element's unique handle in graph
     */
    void setHandle(int handle) {
        assert(this.handle < 0);
        this.handle = handle;
        attributes.put(HANDLE_KEY, "" + handle);
    }

    /**
     * @return Element's unique handle in graph (can be used for lookup in graph)
     */
    public int getHandle() {
        return handle;
    }

    /**
     * Write element to dot file
     *
     * @param sb StringBuffer to write to
     * @param layout Layout that is used
     * @param keepPositions Keep node positions?
     * (if position is provided, this is used anyway - if this is true, the position of the last
     * layout run is kept; this doesn't work with dot though)
     */
    public abstract void writeToDot(StringBuffer sb, Graph.Layout layout, boolean keepPositions);

    /**
     * Converts value in pixels to value in inches (to write to .dot file)
     *
     * @param d Value in pixels/points
     * @return Value in Inches as string
     */
    public static String toInch(double d) {
        return ("" + (d / 72.0)).replace(',', '.');
    }

    /** Constant: Point (0,0) */
    private static final Point2D.Double NULL_POINT = new Point2D.Double(0, 0);

    /**
     * @param s String in the form 12345,12345
     * @return Point with these x and y coordinates
     */
    public static Point2D.Double toPoint(String s) {
        return toPoint(s, NULL_POINT);
    }

    /**
     * @param s String in the form 12345,12345
     * @param nullVector Null vector (subtracted from result)
     * @return Point with these x and y coordinates
     */
    public static Point2D.Double toPoint(String s, Point2D.Double nullVector) {
        assert(s.contains(","));
        String[] parts = s.split(",");
        return new Point2D.Double(Double.parseDouble(parts[0]) - nullVector.x, Double.parseDouble(parts[1]) - nullVector.y);
    }

    /**
     * @param sb StringBuffer to write to
     */
    public void printAttributesForDotFile(StringBuffer sb) {
        boolean first = true;
        for (Map.Entry<String, Object> e : attributes.entrySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(e.getKey()).append("=").append(e.getValue().toString());
        }
    }

    /**
     * Extract attribute from graphviz output line
     * (quite verbose, but efficient implementation)
     *
     * @param line Output Line
     * @param key Key of attribute
     * @return Attribute value (without any quotes)
     */
    public static String extractAttributeValue(String line, String key) {
        boolean inAttrList = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inAttrList) {
                if (c == '[') {
                    inAttrList = true;
                }
                continue;
            }

            // process next attribute
            if (Character.isLetter(c)) {
                if (line.regionMatches(i, key + "=", 0, key.length() + 1)) {
                    int start = i + key.length() + 1;
                    boolean quoted = line.charAt(start) == '"';
                    if (quoted) {
                        start++;
                    }
                    for (i = start; i < line.length(); i++) {
                        c = line.charAt(i);
                        if ((quoted && c == '"') || ((!quoted) && (c == ']' || c == ','))) {
                            return line.substring(start, i);
                        }
                    }
                } else { // advance to next "," or "]"
                    boolean quoted = false;
                    while (true) {
                        i++;
                        c = line.charAt(i);
                        if (c == '"') {
                            quoted = !quoted;
                        }
                        if ((!quoted) && c == ']') {
                            return null;
                        }
                        if ((!quoted) && c == ',') {
                            break;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Process line returned by layout program (usually extracts positions etc.)
     *
     * @param line Line
     * @param nullVector Null vector (usually subtracted from positions)
     */
    public abstract void processLineFromLayouter(String line, Point2D.Double nullVector);

    /**
     * (only called by Graph.clear())
     *
     * Resets handle so that object can be reused in another graph
     */
    void resetHandle() {
        handle = -1;
    }
}
