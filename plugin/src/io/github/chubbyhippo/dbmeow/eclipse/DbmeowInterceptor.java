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
import io.github.chubbyhippo.dbmeow.core.Chord;
import io.github.chubbyhippo.dbmeow.core.Chords;
import io.github.chubbyhippo.dbmeow.core.Ctx;
import io.github.chubbyhippo.dbmeow.core.Engine;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;

import java.util.Map;
import java.util.function.BooleanSupplier;

public final class DbmeowInterceptor implements VerifyKeyListener {

    private static final int AVY_TIMEOUT_MS = 250;
    private static final int MODIFIER_MASK = SWT.CTRL | SWT.ALT | SWT.COMMAND;

    private static final Map<Character, String> KEY_NAMES =
            Map.ofEntries(
                    Map.entry(' ', "SPC"),
                    Map.entry('\t', "TAB"),
                    Map.entry(',', "COMMA"),
                    Map.entry('.', "PERIOD"),
                    Map.entry('/', "SLASH"),
                    Map.entry(';', "SEMICOLON"),
                    Map.entry('\'', "QUOTE"),
                    Map.entry('[', "OPEN_BRACKET"),
                    Map.entry(']', "CLOSE_BRACKET"),
                    Map.entry('\\', "BACK_SLASH"),
                    Map.entry('-', "MINUS"),
                    Map.entry('=', "EQUALS"),
                    Map.entry('`', "BACK_QUOTE"));

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

        if (AceWindowSwt.handleKey(event, ctx.ui())) return;

        if (AceClickSwt.handleKey(event, ctx.ui())) return;

        if (event.keyCode == SWT.ESC) {
            if (guarded(() -> Engine.escapeKey(ctx))) {
                event.doit = false;
                event.display.timerExec(-1, finishAvyInput);
            }
            return;
        }

        if ((event.stateMask & MODIFIER_MASK) != 0) {
            Chord chord = chordOf(event);
            if (!Chords.claims(ctx.st().mode, chord)) return;
            if (guarded(() -> Chords.dispatch(ctx, chord))) event.doit = false;
            return;
        }

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

    static Chord chordOf(VerifyEvent event) {
        String name = hostKeyName(event.keyCode);
        if (name == null) return null;
        StringBuilder spelling = new StringBuilder();
        if ((event.stateMask & SWT.CTRL) != 0) spelling.append("control ");
        if ((event.stateMask & (SWT.ALT | SWT.COMMAND)) != 0) spelling.append("alt ");
        if ((event.stateMask & SWT.SHIFT) != 0) spelling.append("shift ");
        return Chord.parse(spelling.append(name).toString());
    }

    private static String hostKeyName(int keyCode) {
        if (keyCode <= 0 || keyCode > Character.MAX_VALUE) return null;
        char key = (char) keyCode;
        String named = KEY_NAMES.get(key);
        if (named != null) return named;
        if (Character.isLetterOrDigit(key)) return String.valueOf(Character.toUpperCase(key));
        return null;
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
