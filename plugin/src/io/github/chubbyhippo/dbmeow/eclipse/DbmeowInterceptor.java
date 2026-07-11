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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;

public final class DbmeowInterceptor implements VerifyKeyListener {

    private final Ctx ctx;

    DbmeowInterceptor(Ctx ctx) {
        this.ctx = ctx;
    }

    @Override
    public void verifyKey(VerifyEvent event) {
        if (!event.doit) return;

        if (event.keyCode == SWT.ESC) {
            if (Engine.escapeKey(ctx)) event.doit = false;
            return;
        }

        int chord = SWT.CTRL | SWT.ALT | SWT.COMMAND;
        if ((event.stateMask & chord) != 0) return;

        char c = event.character;
        if (c == 0 || c < 0x20 || c == SWT.DEL) return;

        boolean handled = Engine.handleChar(ctx, c);
        event.doit = !handled;
    }
}
