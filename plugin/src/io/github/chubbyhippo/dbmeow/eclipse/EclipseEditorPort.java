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
import io.github.chubbyhippo.dbmeow.core.SelRange;
import io.github.chubbyhippo.dbmeow.core.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.custom.StyledText;

import java.util.ArrayList;
import java.util.List;

final class EclipseEditorPort implements EditorPort {

    private final ITextViewer viewer;
    private final org.eclipse.ui.texteditor.AbstractTextEditor editor;

    EclipseEditorPort(org.eclipse.jface.text.source.ISourceViewer viewer,
                      org.eclipse.ui.texteditor.AbstractTextEditor editor) {
        this.viewer = viewer;
        this.editor = editor;
    }

    @Override
    public String getText() {
        IDocument doc = viewer.getDocument();
        return doc == null ? "" : doc.get();
    }

    @Override
    public List<SelRange> getSelections() {
        StyledText w = viewer.getTextWidget();
        int caretModel = widgetToModel(w.getCaretOffset());
        org.eclipse.swt.graphics.Point sel = w.getSelectionRange();
        int start = widgetToModel(sel.x);
        int end = widgetToModel(sel.x + sel.y);
        List<SelRange> out = new ArrayList<>();
        if (sel.y == 0) {
            out.add(new SelRange(caretModel, caretModel));
        } else if (caretModel <= start) {
            out.add(new SelRange(end, start));
        } else {
            out.add(new SelRange(start, end));
        }
        return out;
    }

    @Override
    public void setSelections(List<SelRange> sels) {
        if (sels.isEmpty()) return;
        SelRange p = sels.get(0);
        StyledText w = viewer.getTextWidget();
        int anchorW = widgetSafe(w, modelToWidget(p.anchor()));
        int activeW = widgetSafe(w, modelToWidget(p.active()));
        if (anchorW < 0 || activeW < 0) return;
        w.setSelectionRange(anchorW, activeW - anchorW);
        w.showSelection();
    }

    private static int widgetSafe(StyledText w, int offset) {
        if (offset < 0) return -1;
        int max = w.getCharCount();
        int clamped = Math.min(offset, max);
        if (clamped > 0
                && clamped < max
                && w.getTextRange(clamped - 1, 2).equals("\r\n")) {
            return clamped - 1;
        }
        return clamped;
    }

    @Override
    public void edit(List<TextEdit> edits) {
        IDocument doc = viewer.getDocument();
        if (doc == null) return;
        List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort((a, b) -> Integer.compare(b.start(), a.start()));
        IRewriteTarget rewrite =
                viewer instanceof ITextViewerExtension ext ? ext.getRewriteTarget() : null;
        if (rewrite != null) rewrite.beginCompoundChange();
        try {
            for (TextEdit e : ordered) {
                doc.replace(e.start(), e.end() - e.start(), e.text());
            }
        } catch (BadLocationException ignored) {
        } finally {
            if (rewrite != null) rewrite.endCompoundChange();
        }
    }

    @Override
    public boolean isWritable() {
        return editor.isEditable();
    }

    @Override
    public LineRange visibleLineRange() {
        int top = viewer.getTopIndex();
        int bottom = viewer.getBottomIndex();
        if (top < 0 || bottom < 0) return null;
        return new LineRange(top, bottom);
    }

    @Override
    public void undo() {
        if (viewer instanceof ITextOperationTarget target
                && target.canDoOperation(ITextOperationTarget.UNDO)) {
            target.doOperation(ITextOperationTarget.UNDO);
        }
    }

    @Override
    public void closeEditor() {
        editor.getSite().getPage().closeEditor(editor, true);
    }

    @Override
    public OffsetRange symbolRangeAt(int offset) {
        return null;
    }

    private int widgetToModel(int widgetOffset) {
        if (viewer instanceof ITextViewerExtension5 ext) {
            return ext.widgetOffset2ModelOffset(widgetOffset);
        }
        return widgetOffset;
    }

    private int modelToWidget(int modelOffset) {
        if (viewer instanceof ITextViewerExtension5 ext) {
            return ext.modelOffset2WidgetOffset(modelOffset);
        }
        return modelOffset;
    }
}
