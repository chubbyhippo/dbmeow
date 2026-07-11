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

import io.github.chubbyhippo.dbmeow.core.MeowState;
import io.github.chubbyhippo.dbmeow.core.UiPort;

import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditor;

import java.util.List;

final class EclipseUi implements UiPort {

    private final AbstractTextEditor editor;
    private final MeowState st;

    EclipseUi(AbstractTextEditor editor, MeowState st) {
        this.editor = editor;
        this.st = st;
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
    public void scheduleWhichKey(String kind, String buffer) {}

    @Override
    public void hideWhichKey() {}

    @Override
    public void showExpandHints(List<Integer> positions) {}

    @Override
    public void clearExpandHints() {}

    @Override
    public void showAvyMatches(
            List<io.github.chubbyhippo.dbmeow.core.EditorPort.OffsetRange> matches) {}

    @Override
    public void showAvyLabels(List<UiPort.AvyLabel> labels) {}

    @Override
    public void clearAvy() {}

    @Override
    public void modeChanged(MeowState state) {
        refresh(state);
    }

    @Override
    public void refresh(MeowState state) {
        status("-- " + state.mode + " --");
    }

    private void status(String message) {
        if (editor instanceof ITextEditor) {
            editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
        }
    }
}
