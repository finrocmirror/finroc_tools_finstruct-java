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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;

import javax.naming.OperationNotSupportedException;

import org.finroc.tools.gui.WidgetUI;
import org.finroc.tools.gui.util.gui.MAction;
import org.finroc.tools.gui.util.gui.MActionEvent;
import org.finroc.tools.gui.util.propertyeditor.PropertyEditComponent;
import org.finroc.tools.gui.widgets.GeometryRenderer;
import org.finroc.plugins.data_types.Blittable;
import org.finroc.plugins.data_types.HasBlittable;
import org.finroc.plugins.data_types.PaintablePortData;

public class PaintableViewer extends PropertyEditComponent<PaintablePortData> {

    /** UID */
    private static final long serialVersionUID = 7615759489323538266L;

    private enum ExtraAction { ZoomToFit }

    class Viewer extends GeometryRenderer {

        /** UID */
        private static final long serialVersionUID = 4258877685901525064L;

        @Override
        protected WidgetUI createWidgetUI() {
            return new ViewerUI();
        }

        class ViewerUI extends GeometryRendererUI {

            /** UID */
            private static final long serialVersionUID = -207863307631544369L;

            /** Object currently viewed */
            private PaintablePortData currentlyViewed;

            public void show(PaintablePortData p) {
                currentlyViewed = p;

                // set scaling factor so that things fit
                if (p != null) {
                    Viewer.this.invertYAxis = (p instanceof Blittable) || (p instanceof HasBlittable);
                    if (toolbar.isSelected(ExtraAction.ZoomToFit)) {
                        Rectangle2D b = p.getBounds();
                        if (b != null) {
                            double zoomX = (getBounds().getWidth() - 60) / b.getWidth();
                            double zoomY = (getBounds().getHeight() - 30) / b.getHeight();
                            zoom = Math.min(zoomX, zoomY);
                            translationX = -b.getCenterX();
                            translationY = -b.getCenterY();
                            updateRulers();
                            repaint();
                        }
                    } else {
                        repaint();
                    }
                }
            }

            ViewerUI() {
                toolbar.addToggleButton(new MAction(ExtraAction.ZoomToFit, "zoom-page_oo.png", "Zoom to fit", this), true);
                toolbar.setSelected(ExtraAction.ZoomToFit);
            }

            @Override
            public void drawGeometries(Graphics2D g2d) {
                if (currentlyViewed != null) {
                    currentlyViewed.paint(g2d, null);
                }
            }

            @Override
            public void actionPerformed(ActionEvent ae) {
                Enum<?> e = ((MActionEvent)ae).getEnumID();
                if (e instanceof ExtraAction) {
                    show(currentlyViewed);
                    return;
                }
                super.actionPerformed(ae);
            }



        }
    }

    private Viewer.ViewerUI viewerUI;

    @Override
    protected void createAndShow() throws Exception {
        Viewer viewer = new Viewer();
        viewer.setBounds(new Rectangle(0, 0, 640, 400));
        viewerUI = (Viewer.ViewerUI)viewer.createUI();
        viewerUI.setPreferredSize(new Dimension(640, 400));
        valueUpdated(getCurWidgetValue());
        add(viewerUI, BorderLayout.WEST);
    }

    @Override
    public void createAndShowMinimal(PaintablePortData b) throws OperationNotSupportedException {
        Viewer viewer = new Viewer();
        viewer.setBounds(new Rectangle(0, 0, 320, 200));
        viewerUI = (Viewer.ViewerUI)viewer.createUI();
        viewerUI.setPreferredSize(new Dimension(320, 200));
        valueUpdated(b);
        add(viewerUI);
    }

    @Override
    public PaintablePortData getCurEditorValue() {
        return viewerUI.currentlyViewed;
    }

    @Override
    protected void valueUpdated(PaintablePortData p) {
        viewerUI.show(p);
    }

}
