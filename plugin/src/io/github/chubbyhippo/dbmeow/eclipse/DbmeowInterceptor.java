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

/**
 * The modal gate: a {@link VerifyKeyListener} prepended to the editor's text
 * viewer, so it sees each keystroke before the widget inserts it. Printable
 * keys and ESC feed the meow {@link Engine}; when the engine consumes the key
 * (NORMAL/MOTION/KEYPAD, or an ESC it acted on) we clear {@code event.doit} so
 * the widget never also types it — the exact contract vrapper uses
 * ({@code event.doit = !editorAdaptor.handleKey(...)}, the local vrapper
 * clone interceptor/VimInputInterceptorFactory.java:354).
 *
 * <p>Modifier chords (Ctrl/Alt/Cmd) and non-printable keys pass straight
 * through: like the siblings, only printable keys reach the modal engine, and
 * the Emacs {@code C-}/{@code M-} layer stays on the platform's own bindings.
 */
public final class DbmeowInterceptor implements VerifyKeyListener {

    private final Ctx ctx;

    DbmeowInterceptor(Ctx ctx) {
        this.ctx = ctx;
    }

    @Override
    public void verifyKey(VerifyEvent event) {
        if (!event.doit) return; // an earlier listener already consumed it

        if (event.keyCode == SWT.ESC) {
            if (Engine.escapeKey(ctx)) event.doit = false;
            return;
        }

        // leave modifier chords to the platform (C-/M- Emacs layer, shortcuts)
        int chord = SWT.CTRL | SWT.ALT | SWT.COMMAND;
        if ((event.stateMask & chord) != 0) return;

        char c = event.character;
        if (c == 0 || c < 0x20 || c == SWT.DEL) return; // non-printable

        boolean handled = Engine.handleChar(ctx, c);
        event.doit = !handled; // consumed in NORMAL; passes through in INSERT
    }
}
