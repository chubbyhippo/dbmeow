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

import io.github.chubbyhippo.dbmeow.core.Avy;
import io.github.chubbyhippo.dbmeow.core.Ctx;
import io.github.chubbyhippo.dbmeow.core.Engine;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;

import java.util.function.BooleanSupplier;

public final class DbmeowInterceptor implements VerifyKeyListener {

    private static final int AVY_TIMEOUT_MS = 250;

    private final Ctx ctx;
    private final Runnable finishAvyInput;

    DbmeowInterceptor(Ctx ctx) {
        this.ctx = ctx;
        this.finishAvyInput =
                () -> {
                    if (Avy.awaitingTimeout(ctx.st())) Avy.finishInput(ctx);
                };
    }

    @Override
    public void verifyKey(VerifyEvent event) {
        if (!event.doit) return;

        if (event.keyCode == SWT.ESC) {
            if (guarded(() -> Engine.escapeKey(ctx))) {
                event.doit = false;
                event.display.timerExec(-1, finishAvyInput);
            }
            return;
        }

        int chord = SWT.CTRL | SWT.ALT | SWT.COMMAND;
        if ((event.stateMask & chord) != 0) return;

        char c = event.character;
        if (c == 0 || c < 0x20 || c == SWT.DEL) return;

        boolean handled = guarded(() -> Engine.handleChar(ctx, c));
        event.doit = !handled;
        if (handled) {
            event.display.timerExec(-1, finishAvyInput);
            if (Avy.awaitingTimeout(ctx.st())) {
                event.display.timerExec(AVY_TIMEOUT_MS, finishAvyInput);
            }
        }
    }

    private boolean guarded(BooleanSupplier engineCall) {
        try {
            return engineCall.getAsBoolean();
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            ctx.ui().hint("error — " + reason);
            return true;
        }
    }
}
