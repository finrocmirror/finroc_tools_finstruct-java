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

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import org.finroc.finstruct.graphviz.Graph.Layout;

/**
 * @author max
 *
 * Edge in graphviz graph
 */

public class Edge extends GraphVizElement {

    /** Vertices at which edge begins and ends */
    private final Vertex source, destination;

    /** Path containing Points to draw spline with (as returned by layouter) */
    private Path2D.Double path;
    //private Point2D.Double[] splinePoints;

    /** Reverse edge while layouting with dot? */
    private boolean reversedInDotLayout;

    /** Parameter T for spline curves */
    private static final double T = 0;

    /** Precalculated helper variables for spline curves */
    private static final double X = (1 - T) / 4;
    private static final double Y = (1 + T) / 2;
    private static final double Z = (1 - T) / 2;

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
        Point2D.Double[] splinePoints = new Point2D.Double[points.length + 2];
        String s = points[0];
        if (s.startsWith("e,")) {
            s = s.substring(2);
        }
        splinePoints[0] = toPoint(s, nullVector);
        splinePoints[1] = splinePoints[0]; // duplicate first point
        for (int i = points.length - 1; i >= 1; i--) {
            splinePoints[points.length + 1 - i] = toPoint(points[i], nullVector);
        }
        splinePoints[points.length + 1] = splinePoints[points.length]; // duplicate last point

        // create path
        path = new Path2D.Double(Path2D.WIND_NON_ZERO, points.length);
        path.moveTo(splinePoints[0].x, splinePoints[0].y);
        for (int i = 0; i < points.length - 1; i++) {
            Point2D.Double p1 = splinePoints[i + 1];
            Point2D.Double p2 = splinePoints[i + 2];
            Point2D.Double p3 = splinePoints[i + 3];
            double b1x = Y * p1.x + Z * p2.x;
            double b1y = Y * p1.y + Z * p2.y;
            double b2x = Z * p1.x + Y * p2.x;
            double b2y = Z * p1.y + Y * p2.y;
            double b3x = X * (p1.x + p3.x) + Y * p2.x;
            double b3y = X * (p1.y + p3.y) + Y * p2.y;
            path.curveTo(b1x, b1y, b2x, b2y, b3x, b3y);
        }
        Point2D.Double last = splinePoints[splinePoints.length - 1];
        path.lineTo(last.x, last.y);
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