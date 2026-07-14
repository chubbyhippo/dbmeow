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

import io.github.chubbyhippo.dbmeow.core.Ctx;
import io.github.chubbyhippo.dbmeow.core.Engine;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.AbstractTextEditor;

public final class DbmeowChordHandler extends AbstractHandler {

    private static final String PREFIX = "io.github.chubbyhippo.dbmeow.";
    private static final String EMACS = "emacs.";

    @Override
    public Object execute(ExecutionEvent event) {
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        if (!(part instanceof AbstractTextEditor editor)) return null;
        Ctx ctx = InterceptorManager.INSTANCE.ctxOf(editor);
        if (ctx == null) return null;
        String id = event.getCommand().getId();
        if (!id.startsWith(PREFIX)) return null;
        String name = id.substring(PREFIX.length());
        try {
            if (name.equals("keypad")) {
                Engine.enterKeypad(ctx);
            } else if (name.startsWith(EMACS)) {
                Engine.runEmacsMotion(ctx, name.substring(EMACS.length()));
            }
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            ctx.ui().hint("error — " + reason);
        }
        return null;
    }
}
