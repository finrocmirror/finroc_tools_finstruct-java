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
        sb.append("v").append(source.getHandle()).append(" -> v").append(destination.getHandle()).append(" [");
        printAttributesForDotFile(sb);
        sb.append("];\n");
    }

    @Override
    public void processLineFromLayouter(String line, Double nullVector) {
        String[] points = extractAttributeValue(line, "pos").split(" ");
        //splinePoints = new Point2D.Double[points.length];
        path = new Path2D.Double(Path2D.WIND_NON_ZERO, points.length);
        String s = points[0];
        if (s.startsWith("e,")) {
            s = s.substring(2);
        }
        Point2D.Double p = toPoint(s, nullVector);
        path.moveTo(p.x, p.y);
        for (int i = points.length - 1; i >= 1; i--) {
            p = toPoint(points[i], nullVector);
            path.lineTo(p.x, p.y);
        }
    }
}