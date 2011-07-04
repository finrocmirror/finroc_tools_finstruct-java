/**
 * You received this file as part of FinGUI - a universal
 * (Web-)GUI editor for Robotic Systems.
 *
 * Copyright (C) 2007-2010 Max Reichardt
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
package org.finroc.tools.finstruct.propertyeditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import javax.naming.OperationNotSupportedException;

import org.finroc.tools.gui.WidgetUI;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.widgets.GeometryRenderer;
import org.finroc.plugins.data_types.PaintablePortData;

public class PaintableViewer extends PropertyEditComponent<PaintablePortData> {

    /** UID */
    private static final long serialVersionUID = 7615759489323538266L;

    class Viewer extends GeometryRenderer {

        /** UID */
        private static final long serialVersionUID = 4258877685901525064L;

        /** Object currently viewed */
        private PaintablePortData currentlyViewed;

        public void show(PaintablePortData p) {
            currentlyViewed = p;

            // set scaling factor so that things fit
            if (p != null) {
                Rectangle2D b = p.getBounds();
                if (b != null) {
                    double zoomX = (viewer.getBounds().getWidth() - 60) / b.getWidth();
                    double zoomY = (viewer.getBounds().getHeight() - 30) / b.getHeight();
                    zoom = Math.min(zoomX, zoomY);
                    translationX = -b.getCenterX();
                    translationY = -b.getCenterY();
                    repaint();
                }
            }
        }

        @Override
        public void drawGeometries(Graphics2D g2d) {
            if (currentlyViewed != null) {
                currentlyViewed.paint(g2d);
            }
        }
    }

    private Viewer viewer;

    @Override
    protected void createAndShow() throws Exception {
        viewer = new Viewer();
        viewer.setBounds(new Rectangle(0, 0, 640, 400));
        WidgetUI wui = viewer.createUI();
        wui.setPreferredSize(new Dimension(640, 400));
        valueUpdated(getCurWidgetValue());
        add(wui, BorderLayout.WEST);
    }

    @Override
    public void createAndShowMinimal(PaintablePortData b) throws OperationNotSupportedException {
        viewer = new Viewer();
        viewer.setBounds(new Rectangle(0, 0, 320, 200));
        WidgetUI wui = viewer.createUI();
        wui.setPreferredSize(new Dimension(320, 200));
        valueUpdated(b);
        add(wui);
    }

    @Override
    public PaintablePortData getCurEditorValue() {
        return viewer.currentlyViewed;
    }

    @Override
    protected void valueUpdated(PaintablePortData p) {
        viewer.show(p);
    }

}
