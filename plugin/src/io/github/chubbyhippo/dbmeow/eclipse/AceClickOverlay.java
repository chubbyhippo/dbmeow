// Copyright (C) 2026 Chubby Hippo
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along
// with this program. If not, see <https://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.chubbyhippo.dbmeow.eclipse;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.widgets.Shell;

import java.util.List;

final class AceClickOverlay {

    record Badge(Rectangle displayBounds, String label) {}

    private static final Color BADGE_BG = new Color(229, 43, 80);
    private static final Color BADGE_FG = new Color(255, 255, 255);
    private static final int PADDING = 2;

    private final Shell parent;
    private final Shell shell;
    private Font boldFont;
    private FontData boldBase;
    private Region region;
    private List<Badge> badges = List.of();

    AceClickOverlay(Shell parent) {
        this.parent = parent;
        this.shell = new Shell(parent, SWT.NO_TRIM | SWT.ON_TOP | SWT.NO_FOCUS);
        this.shell.setBackground(BADGE_BG);
        this.shell.addPaintListener(this::paint);
    }

    void show(List<Badge> badges) {
        if (shell.isDisposed()) return;
        this.badges = badges;
        Rectangle area = parent.getBounds();
        shell.setBounds(area);
        applyRegion(area);
        shell.setVisible(true);
        shell.redraw();
        shell.update();
    }

    void dispose() {
        if (!shell.isDisposed()) shell.dispose();
        if (region != null) {
            region.dispose();
            region = null;
        }
        if (boldFont != null) {
            boldFont.dispose();
            boldFont = null;
        }
    }

    private void applyRegion(Rectangle area) {
        Region next = new Region(shell.getDisplay());
        GC gc = new GC(shell);
        gc.setFont(boldFont());
        try {
            for (Badge badge : badges) {
                Point extent = gc.stringExtent(badge.label());
                next.add(
                        new Rectangle(
                                badge.displayBounds().x - area.x,
                                badge.displayBounds().y - area.y,
                                extent.x + PADDING * 2,
                                extent.y + PADDING * 2));
            }
        } finally {
            gc.dispose();
        }
        shell.setRegion(next);
        if (region != null) region.dispose();
        region = next;
    }

    private void paint(PaintEvent e) {
        Rectangle area = shell.getBounds();
        GC gc = e.gc;
        gc.setTextAntialias(SWT.ON);
        gc.setFont(boldFont());
        gc.setBackground(BADGE_BG);
        gc.setForeground(BADGE_FG);
        for (Badge badge : badges) {
            Point extent = gc.stringExtent(badge.label());
            int x = badge.displayBounds().x - area.x;
            int y = badge.displayBounds().y - area.y;
            gc.fillRectangle(x, y, extent.x + PADDING * 2, extent.y + PADDING * 2);
            gc.drawString(badge.label(), x + PADDING, y + PADDING, true);
        }
    }

    private Font boldFont() {
        FontData base = parent.getFont().getFontData()[0];
        if (boldFont == null || !base.equals(boldBase)) {
            if (boldFont != null) boldFont.dispose();
            boldBase = base;
            boldFont =
                    new Font(
                            parent.getDisplay(),
                            new FontData(base.getName(), base.getHeight(), base.getStyle() | SWT.BOLD));
        }
        return boldFont;
    }
}
