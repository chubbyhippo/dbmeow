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

import io.github.chubbyhippo.dbmeow.core.EditorPort;
import io.github.chubbyhippo.dbmeow.core.Rc;
import io.github.chubbyhippo.dbmeow.core.UiPort;
import io.github.chubbyhippo.dbmeow.core.WhichKey;

import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import java.util.List;
import java.util.Objects;

final class OverlayPainter implements PaintListener {

    private static final Color MATCH_BG = new Color(255, 220, 0);
    private static final Color WHICH_KEY_KEY_FG = new Color(43, 93, 178);
    private static final int MATCH_ALPHA = 96;
    private static final int GRAB_ALPHA = 128;
    private static final int OPAQUE = 255;
    private static final int MIN_FILL_WIDTH = 2;

    private final ISourceViewer viewer;
    private final StyledText text;
    private List<UiPort.AvyLabel> avyLabels = List.of();
    private List<EditorPort.OffsetRange> avyMatches = List.of();
    private EditorPort.OffsetRange grabHighlight;
    private List<Integer> expandHints = List.of();
    private String aceLabel;
    private String whichKeyTitle;
    private List<WhichKey.Row> whichKeyRows;
    private Font boldFont;
    private FontData boldBase;

    OverlayPainter(ISourceViewer viewer) {
        this.viewer = viewer;
        this.text = viewer.getTextWidget();
        text.addPaintListener(this);
    }

    void showAvyMatches(List<EditorPort.OffsetRange> matches) {
        avyMatches = matches;
        redraw();
    }

    void showAvyLabels(List<UiPort.AvyLabel> labels) {
        avyLabels = labels;
        redraw();
    }

    void clearAvy() {
        avyMatches = List.of();
        avyLabels = List.of();
        redraw();
    }

    void setGrabHighlight(EditorPort.OffsetRange range) {
        if (Objects.equals(grabHighlight, range)) return;
        grabHighlight = range;
        redraw();
    }

    void showExpandHints(List<Integer> positions) {
        expandHints = positions;
        redraw();
    }

    void clearExpandHints() {
        expandHints = List.of();
        redraw();
    }

    void showAceLabel(String label) {
        aceLabel = label;
        redraw();
    }

    void clearAceLabel() {
        if (aceLabel == null) return;
        aceLabel = null;
        redraw();
    }

    void showWhichKey(String title, List<WhichKey.Row> rows) {
        whichKeyTitle = title;
        whichKeyRows = rows;
        redraw();
    }

    void hideWhichKeyPanel() {
        if (whichKeyRows == null) return;
        whichKeyTitle = null;
        whichKeyRows = null;
        redraw();
    }

    void dispose() {
        if (!text.isDisposed()) text.removePaintListener(this);
        if (boldFont != null) {
            boldFont.dispose();
            boldFont = null;
        }
    }

    private void redraw() {
        if (!text.isDisposed()) text.redraw();
    }

    @Override
    public void paintControl(PaintEvent e) {
        if (avyLabels.isEmpty()
                && avyMatches.isEmpty()
                && grabHighlight == null
                && expandHints.isEmpty()
                && aceLabel == null
                && whichKeyRows == null) {
            return;
        }
        GC gc = e.gc;
        gc.setTextAntialias(SWT.ON);
        paintGrabHighlight(gc);
        paintMatches(gc);
        gc.setFont(boldEditorFont());
        Color labelFg = swt(Rc.overlayTextColor());
        Color avyBg = swt(Rc.overlayColor());
        for (UiPort.AvyLabel label : avyLabels) {
            paintBox(gc, label.offset(), label.label(), avyBg, labelFg);
        }
        Color hintBg = swt(Rc.expandHintColor());
        for (int i = 0; i < expandHints.size(); i++) {
            paintBox(gc, expandHints.get(i), String.valueOf((i + 1) % 10), hintBg, labelFg);
        }
        paintAceLabel(gc);
        paintWhichKey(gc);
    }

    private void paintAceLabel(GC gc) {
        if (aceLabel == null) return;
        Point extent = gc.stringExtent(aceLabel);
        gc.setBackground(swt(Rc.overlayColor()));
        gc.fillRectangle(0, 0, extent.x + 4, extent.y + 4);
        gc.setForeground(swt(Rc.overlayTextColor()));
        gc.drawString(aceLabel, 2, 2, true);
    }

    private void paintWhichKey(GC gc) {
        List<WhichKey.Row> rows = whichKeyRows;
        if (rows == null || rows.isEmpty()) return;
        int rowsPerColumn = 12;
        gc.setFont(text.getFont());
        int lineHeight = gc.getFontMetrics().getHeight() + 2;
        Rectangle area = text.getClientArea();
        int visibleRows = Math.min(rowsPerColumn, rows.size());
        int panelHeight = (visibleRows + 1) * lineHeight + 10;
        int top = area.height - panelHeight;
        gc.setBackground(text.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        gc.fillRectangle(0, top, area.width, panelHeight);
        Color fg = text.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
        gc.setForeground(fg);
        gc.setFont(boldEditorFont());
        gc.drawString(whichKeyTitle == null ? "" : whichKeyTitle, 8, top + 5, true);
        gc.setFont(text.getFont());
        int x = 8;
        for (int column = 0; column * rowsPerColumn < rows.size(); column++) {
            List<WhichKey.Row> slice =
                    rows.subList(
                            column * rowsPerColumn,
                            Math.min((column + 1) * rowsPerColumn, rows.size()));
            int keyWidth = 0;
            int labelWidth = 0;
            for (WhichKey.Row row : slice) {
                keyWidth = Math.max(keyWidth, gc.stringExtent(row.key()).x);
                labelWidth = Math.max(labelWidth, gc.stringExtent(row.label()).x);
            }
            int columnWidth = keyWidth + 12 + labelWidth + 28;
            if (column > 0 && x + columnWidth > area.width) break;
            for (int i = 0; i < slice.size(); i++) {
                WhichKey.Row row = slice.get(i);
                int y = top + 5 + (i + 1) * lineHeight;
                gc.setForeground(WHICH_KEY_KEY_FG);
                gc.drawString(row.key(), x, y, true);
                gc.setForeground(fg);
                gc.drawString(row.label(), x + keyWidth + 12, y, true);
            }
            x += columnWidth;
        }
    }

    private void paintGrabHighlight(GC gc) {
        if (grabHighlight == null) return;
        fillRanges(gc, List.of(grabHighlight), swt(Rc.grabColor()), GRAB_ALPHA);
    }

    private void paintMatches(GC gc) {
        if (avyMatches.isEmpty()) return;
        fillRanges(gc, avyMatches, MATCH_BG, MATCH_ALPHA);
    }

    private void fillRanges(GC gc, List<EditorPort.OffsetRange> ranges, Color bg, int alpha) {
        gc.setBackground(bg);
        gc.setAlpha(alpha);
        for (EditorPort.OffsetRange range : ranges) {
            int start = widgetOffset(range.start());
            int end = widgetOffset(range.end());
            if (start < 0 || end < start) continue;
            fillSpan(gc, start, end);
        }
        gc.setAlpha(OPAQUE);
    }

    private void fillSpan(GC gc, int start, int end) {
        int firstLine = text.getLineAtOffset(start);
        int lastLine = text.getLineAtOffset(end);
        for (int line = firstLine; line <= lastLine; line++) {
            int spanStart = Math.max(start, text.getOffsetAtLine(line));
            int spanEnd = line == lastLine ? end : endOfLine(line);
            Point from = text.getLocationAtOffset(spanStart);
            Point to = text.getLocationAtOffset(spanEnd);
            int width = line == lastLine ? to.x - from.x : Math.max(to.x - from.x, MIN_FILL_WIDTH);
            gc.fillRectangle(
                    from.x,
                    from.y,
                    Math.max(width, MIN_FILL_WIDTH),
                    text.getLineHeight(spanStart));
        }
    }

    private int endOfLine(int line) {
        return text.getOffsetAtLine(line) + text.getLine(line).length();
    }

    private void paintBox(GC gc, int modelOffset, String label, Color bg, Color fg) {
        int offset = widgetOffset(modelOffset);
        if (offset < 0) return;
        Point loc = text.getLocationAtOffset(offset);
        int height = text.getLineHeight(offset);
        Point extent = gc.stringExtent(label);
        gc.setBackground(bg);
        gc.fillRectangle(loc.x, loc.y, extent.x + 3, height);
        gc.setForeground(fg);
        gc.drawString(label, loc.x + 1, loc.y + (height - extent.y) / 2, true);
    }

    private static Color swt(Rc.Rgb c) {
        return new Color(c.r(), c.g(), c.b());
    }

    private int widgetOffset(int modelOffset) {
        int offset =
                viewer instanceof ITextViewerExtension5 ext5
                        ? ext5.modelOffset2WidgetOffset(modelOffset)
                        : modelOffset;
        return offset >= 0 && offset <= text.getCharCount() ? offset : -1;
    }

    private Font boldEditorFont() {
        FontData base = text.getFont().getFontData()[0];
        if (boldFont == null || !base.equals(boldBase)) {
            if (boldFont != null) boldFont.dispose();
            boldBase = base;
            FontData bold = new FontData(base.getName(), base.getHeight(), base.getStyle() | SWT.BOLD);
            boldFont = new Font(text.getDisplay(), bold);
        }
        return boldFont;
    }
}
