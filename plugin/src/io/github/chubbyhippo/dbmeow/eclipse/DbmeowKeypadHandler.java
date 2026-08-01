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

public final class DbmeowKeypadHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) {
        IEditorPart part = HandlerUtil.getActiveEditor(event);
        if (!(part instanceof AbstractTextEditor editor)) return null;
        Ctx ctx = InterceptorManager.INSTANCE.ctxOf(editor);
        if (ctx == null) return null;
        try {
            Engine.enterKeypad(ctx);
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            ctx.ui().hint("error — " + reason);
        }
        return null;
    }
}
