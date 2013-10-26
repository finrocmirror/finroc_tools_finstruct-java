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

package org.finroc.tools.finstruct.graphviz;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import org.finroc.plugins.data_types.util.BezierSpline;
import org.finroc.tools.finstruct.graphviz.Graph.Layout;

/**
 * @author Max Reichardt
 *
 * Edge in graphviz graph
 */

public class Edge extends GraphVizElement {

    /** Vertices at which edge begins and ends */
    private final Vertex source, destination;

    /** Path containing Points to draw spline with (as returned by layouter) */
    private BezierSpline path;
    //private Point2D.Double[] splinePoints;

    /** Reverse edge while layouting with dot? */
    private boolean reversedInDotLayout;

    public Edge(Vertex source, Vertex destination) {
        this.source = source;
        this.destination = destination;
    }

    /**
     * @return Path containing Points to draw spline with (as returned by layouter)
     */
    public Path2D.Double getPath() {
        return path;
    }

    /**
     * @return Vertex at which edge begins
     */
    public Vertex getSource() {
        return source;
    }

    /**
     * @return Vertex at which edge ends
     */
    public Vertex getDestination() {
        return destination;
    }

    @Override
    public void writeToDot(StringBuffer sb, Layout layout, boolean keepPositions) {
        int srcHandle = reversedInDotLayout ? destination.getHandle() : source.getHandle();
        int destHandle = reversedInDotLayout ? source.getHandle() : destination.getHandle();
        sb.append("v").append(srcHandle).append(" -> v").append(destHandle).append(" [");
        printAttributesForDotFile(sb);
        sb.append("];\n");
    }

    @Override
    public void processLineFromLayouter(String line, Double nullVector) {

        // parse points
        String[] points = extractAttributeValue(line, "pos").split(" ");
        Point2D.Double[] splinePoints = new Point2D.Double[points.length];
        String s = points[0];
        if (s.startsWith("e,")) {
            s = s.substring(2);
        }
        splinePoints[0] = toPoint(s, nullVector);
        for (int i = points.length - 1; i >= 1; i--) {
            splinePoints[points.length - i] = toPoint(points[i], nullVector);
        }

        path = new BezierSpline(splinePoints);
    }

    /**
     * @return Reverse edge while layouting with dot?
     */
    public boolean isReversedInDotLayout() {
        return reversedInDotLayout;
    }

    /**
     * @param reversedInDotLayout Reverse edge while layouting with dot?
     */
    public void setReversedInDotLayout(boolean reversedInDotLayout) {
        this.reversedInDotLayout = reversedInDotLayout;
    }
}
