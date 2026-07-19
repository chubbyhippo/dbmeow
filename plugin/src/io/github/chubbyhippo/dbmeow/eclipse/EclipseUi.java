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

import io.github.chubbyhippo.dbmeow.core.MeowMode;
import io.github.chubbyhippo.dbmeow.core.MeowState;
import io.github.chubbyhippo.dbmeow.core.Rc;
import io.github.chubbyhippo.dbmeow.core.UiPort;
import io.github.chubbyhippo.dbmeow.core.WhichKey;

import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.List;

final class EclipseUi implements UiPort {

    private final AbstractTextEditor editor;
    private final MeowState st;
    private final StyledText text;
    private final OverlayPainter painter;
    private final int insertCaretWidth;
    private final Runnable showWhichKey;
    private final IContextService contexts;
    private final IContextActivation activeContext;
    private IContextActivation normalContext;
    private String whichKeyKind;
    private String whichKeyBuffer;

    EclipseUi(AbstractTextEditor editor, MeowState st, StyledText text, OverlayPainter painter) {
        this.editor = editor;
        this.st = st;
        this.text = text;
        this.painter = painter;
        Caret caret = text.getCaret();
        this.insertCaretWidth = caret == null ? 1 : Math.max(1, caret.getSize().x);
        this.showWhichKey = this::showWhichKeyNow;
        this.contexts = editor.getSite().getService(IContextService.class);
        this.activeContext =
                contexts == null
                        ? null
                        : contexts.activateContext("io.github.chubbyhippo.dbmeow.active");
    }

    void dispose() {
        if (contexts == null) return;
        if (normalContext != null) {
            contexts.deactivateContext(normalContext);
            normalContext = null;
        }
        if (activeContext != null) contexts.deactivateContext(activeContext);
    }

    @Override
    public void hint(String text) {
        status("meow: " + text);
    }

    @Override
    public void info(String title, String body) {
        status(title + ": " + body.replace('\n', ' '));
    }

    @Override
    public String input(String prompt, String initial) {
        InputDialog dialog = new InputDialog(
                editor.getSite().getShell(),
                "dbmeow",
                prompt,
                initial == null ? "" : initial,
                null);
        return dialog.open() == Window.OK ? dialog.getValue() : null;
    }

    @Override
    public void runCommand(String id) {
        if ("dbmeow.reloadRc".equals(id)) {
            RcCommands.reload(this);
            return;
        }
        if ("dbmeow.editRc".equals(id)) {
            RcCommands.edit();
            return;
        }
        if ("dbmeow.aceWindow".equals(id)) {
            AceWindowSwt.run(editor, this);
            return;
        }
        ICommandService commands =
                PlatformUI.getWorkbench().getService(ICommandService.class);
        IHandlerService handlers =
                PlatformUI.getWorkbench().getService(IHandlerService.class);
        if (commands == null || handlers == null) {
            throw new IllegalStateException("no command services");
        }
        try {
            ParameterizedCommand command = commands.deserialize(id);
            handlers.executeCommand(command, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void scheduleWhichKey(String kind, String buffer) {
        if (!Rc.whichKeyEnabled() || text.isDisposed()) return;
        whichKeyKind = kind;
        whichKeyBuffer = buffer == null ? "" : buffer;
        text.getDisplay().timerExec(-1, showWhichKey);
        text.getDisplay().timerExec(Rc.whichKeyDelayMs(), showWhichKey);
    }

    @Override
    public void hideWhichKey() {
        whichKeyKind = null;
        if (!text.isDisposed()) text.getDisplay().timerExec(-1, showWhichKey);
        painter.hideWhichKeyPanel();
    }

    private void showWhichKeyNow() {
        String kind = whichKeyKind;
        if (kind == null || text.isDisposed()) return;
        List<WhichKey.Row> rows =
                "things".equals(kind) ? WhichKey.THINGS : WhichKey.keypadRows(whichKeyBuffer);
        String title =
                "things".equals(kind)
                        ? "thing:"
                        : whichKeyBuffer.isEmpty()
                                ? "SPC"
                                : "SPC " + String.join(" ", whichKeyBuffer.split(""));
        painter.showWhichKey(title, rows);
    }

    @Override
    public void showExpandHints(List<Integer> positions) {
        painter.showExpandHints(positions);
    }

    @Override
    public void clearExpandHints() {
        painter.clearExpandHints();
    }

    @Override
    public void showAvyMatches(
            List<io.github.chubbyhippo.dbmeow.core.EditorPort.OffsetRange> matches) {
        painter.showAvyMatches(matches);
    }

    @Override
    public void showAvyLabels(List<UiPort.AvyLabel> labels) {
        painter.showAvyLabels(labels);
    }

    @Override
    public void clearAvy() {
        painter.clearAvy();
    }

    @Override
    public void modeChanged(MeowState state) {
        refresh(state);
    }

    @Override
    public void refresh(MeowState state) {
        status("-- " + state.mode + " --");
        applyCaret(state);
        applyChordContext(state);
    }

    private void applyChordContext(MeowState state) {
        if (contexts == null) return;
        if (state.mode == MeowMode.INSERT) {
            if (normalContext != null) {
                contexts.deactivateContext(normalContext);
                normalContext = null;
            }
        } else if (normalContext == null) {
            normalContext = contexts.activateContext("io.github.chubbyhippo.dbmeow.normal");
        }
    }

    private void applyCaret(MeowState state) {
        if (text.isDisposed()) return;
        Caret caret = text.getCaret();
        if (caret == null || caret.isDisposed()) return;
        Point size = caret.getSize();
        int width = state.mode == MeowMode.INSERT ? insertCaretWidth : blockCaretWidth();
        if (size.x != width) caret.setSize(width, size.y);
    }

    private int blockCaretWidth() {
        GC gc = new GC(text);
        try {
            return Math.max(2, (int) gc.getFontMetrics().getAverageCharacterWidth());
        } finally {
            gc.dispose();
        }
    }

    private void status(String message) {
        if (editor instanceof ITextEditor) {
            editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
        }
    }
}
