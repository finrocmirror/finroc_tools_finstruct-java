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

package org.finroc.finstruct.graphviz;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import org.finroc.finstruct.graphviz.Graph.Layout;

/**
 * @author max
 *
 * GraphViz vertex
 */
public class Vertex extends GraphVizElement {

    /** Rank to use (in relevant for layouts with dot only) */
    private String rank = null;

    /** Initial (fixed) position (in pixels) - null if not provided - fdp,neato only */
    private Point2D.Double fixedInitialPosition = null;

    /** Position after layout */
    private Point2D.Double layoutPosition = null;

    /** Key for above attribute */
    private String FIXED_INITIAL_POSITION_KEY = "fixedinitialpos";

    /** Size of Vertex (in points/pixels, -1 if not set) */
    private double width = -1, height = -1;

    @Override
    public void writeToDot(StringBuffer sb, Layout layout, boolean keepPositions) {
        boolean writeRank = rank != null && layout == Layout.dot;
        if (writeRank) {
            sb.append("{ rank=" + rank + "\n");
        }

        // set initial position (?)
        if (layout != Layout.dot) {
            String posAttr = null;
            if (fixedInitialPosition != null) {
                posAttr = toInch(fixedInitialPosition.x) + "," + toInch(fixedInitialPosition.y);
            } else if (keepPositions) {
                String lastInitialPos = getAttribute(FIXED_INITIAL_POSITION_KEY).toString();
                if (lastInitialPos != null) {
                    posAttr = lastInitialPos;
                } else if (layoutPosition != null) {
                    posAttr = toInch(layoutPosition.x) + "," + layoutPosition.y;
                }
            }
            if (posAttr != null) {
                posAttr += "!";
                setAttributeQuoted(FIXED_INITIAL_POSITION_KEY, posAttr);
                setAttributeQuoted("pos", posAttr);
            }
        }

        // set fixed size (?)
        if (getWidth() > 0) {
            setAttribute("shape", "box");
            setAttribute("fixedsize", "true");
            setAttribute("width", toInch(width));
            setAttribute("height", toInch(height));
        }

        // write
        sb.append("v" + getHandle() + " [");
        printAttributesForDotFile(sb);
        sb.append("];\n");

        if (writeRank) {
            sb.append("}\n");
        }
    }

    @Override
    public void processLineFromLayouter(String line, Double nullVector) {
        layoutPosition = toPoint(extractAttributeValue(line, "pos"), nullVector);
    }

    /**
     * Set vertex size
     *
     * @param width Width
     * @param height Height
     */
    public void setSize(double width, double height) {
        if (width > 0 && height > 0) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * @return Width of vertex (if previously set, otherwise -1)
     */
    public double getWidth() {
        return width;
    }

    /**
     * @return Height of vertex (if previously set, otherwise -1)
     */
    public double getHeight() {
        return height;
    }

    /**
     * Set fixed initial position (in points/pixels)
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setFixedPosition(double x, double y) {
        fixedInitialPosition = new Point2D.Double(x, y);
    }

    /**
     * @return Position after layout
     */
    public Point2D.Double getLayoutPosition() {
        return layoutPosition;
    }

    /**
     * @param rank Rank of vertex (relevant for dot layout only)
     */
    public void setRank(String rank) {
        this.rank = rank;
    }
}
