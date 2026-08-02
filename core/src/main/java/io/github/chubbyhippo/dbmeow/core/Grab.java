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

package io.github.chubbyhippo.dbmeow.core;

import io.github.chubbyhippo.dbmeow.core.EditorPort.OffsetRange;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Grab {
    private Grab() {}

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-grab", Grab::grab);
        commands.put("meow-sync-grab", Grab::sync);
        commands.put("meow-swap-grab", Grab::swap);
    }

    private static final int MAX_BEACONS = 500;

    public static void clear(Ctx ctx) {
        ctx.state().grab = null;
        ctx.ui().setGrabHighlight(null);
    }

    private static void set(Ctx ctx, int start, int end) {
        ctx.state().grab = new OffsetRange(start, end);
        ctx.ui().setGrabHighlight(end > start ? new OffsetRange(start, end) : null);
    }

    public static void adjustForEdits(MeowState state, List<TextEdit> edits) {
        OffsetRange g = state.grab;
        if (g == null) return;
        int grabStart = g.start();
        int grabEnd = g.end();
        List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(TextEdit::start).reversed());
        for (TextEdit e : ordered) {
            int delta = e.text().length() - (e.end() - e.start());
            if (grabStart >= e.end()) {
                grabStart += delta;
                grabEnd += delta;
            } else {
                if (grabEnd >= e.end()) grabEnd += delta;
                else if (grabEnd > e.start()) grabEnd = e.start();
                if (grabStart > e.start()) grabStart = e.start();
            }
        }
        if (grabEnd < grabStart) grabEnd = grabStart;
        state.grab = new OffsetRange(grabStart, grabEnd);
    }

    private static void grab(Ctx ctx) {
        clear(ctx);
        SelRange sel = Selections.primary(ctx);
        if (Selections.hasSelection(sel)) {
            set(ctx, sel.lo(), sel.hi());
        }
        Selections.cancel(ctx);
    }

    private static void sync(Ctx ctx) {
        SelRange sel = Selections.primary(ctx);
        if (!Selections.hasSelection(sel)) {
            ctx.ui().hint("meow-sync-grab needs a selection");
            return;
        }
        clear(ctx);
        set(ctx, sel.lo(), sel.hi());
        Selections.cancel(ctx);
    }

    private static void swap(Ctx ctx) {
        if (Edits.blockedReadOnly(ctx)) return;
        MeowState state = ctx.state();
        OffsetRange g = state.grab;
        SelRange sel = Selections.primary(ctx);
        if (g == null) {
            ctx.ui().hint("No grab");
            return;
        }
        if (!Selections.hasSelection(sel)) {
            ctx.ui().hint("meow-swap-grab needs a selection");
            return;
        }
        int grabStart = g.start();
        int grabEnd = g.end();
        int selStart = sel.lo();
        int selEnd = sel.hi();
        if (Math.max(grabStart, selStart) < Math.min(grabEnd, selEnd)
                && !(grabStart == selStart && grabEnd == selEnd)) {
            ctx.ui().hint("Selection overlaps the grab");
            return;
        }
        String text = ctx.port().getText();
        String grabText = text.substring(grabStart, grabEnd);
        String selText = text.substring(selStart, selEnd);
        state.grab = null;
        ctx.port()
                .edit(
                        List.of(
                                new TextEdit(selStart, selEnd, grabText),
                                new TextEdit(grabStart, grabEnd, selText)));
        if (grabStart <= selStart) {
            int delta = selText.length() - (grabEnd - grabStart);
            set(ctx, grabStart, grabStart + selText.length());
            int caret = selStart + delta + grabText.length();
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        } else {
            int delta = grabText.length() - (selEnd - selStart);
            set(ctx, grabStart + delta, grabStart + delta + selText.length());
            int caret = selStart + grabText.length();
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        }
        state.selType = SelType.NONE;
    }

    public static boolean pop(Ctx ctx) {
        OffsetRange g = ctx.state().grab;
        if (g == null) return false;
        int start = g.start();
        int end = g.end();
        clear(ctx);
        Selections.select(ctx, SelType.TRANSIENT, start, end, false);
        return true;
    }

    public static void beacon(Ctx ctx) {
        MeowState state = ctx.state();
        OffsetRange g = state.grab;
        if (g == null || g.end() <= g.start()) return;
        SelRange sel = Selections.primary(ctx);
        if (!Selections.hasSelection(sel)) return;
        int selStart = sel.lo();
        int selEnd = sel.hi();
        if (selStart < g.start() || selEnd > g.end() || selEnd == selStart) return;
        String text = ctx.port().getText();
        List<SelRange> sels = new ArrayList<>();
        switch (state.selType) {
            case WORD, SYMBOL, VISIT, FIND, TILL, CHAR -> {
                String selText = text.substring(selStart, selEnd);
                if (selText.trim().isEmpty()) return;
                boolean bounded = state.selType == SelType.WORD || state.selType == SelType.SYMBOL;
                String pat =
                        bounded
                                ? "\\b" + Text.escapeRegExp(selText) + "\\b"
                                : Text.escapeRegExp(selText);
                Matcher m;
                try {
                    m = Pattern.compile(pat).matcher(text.substring(g.start(), g.end()));
                } catch (RuntimeException e) {
                    return;
                }
                int rlen = g.end() - g.start();
                int added = 0;
                int from = 0;
                while (from <= rlen && m.find(from)) {
                    int matchStart = m.start();
                    int matchEnd = m.end();
                    if (matchEnd == matchStart) {
                        from = matchEnd + 1;
                        continue;
                    }
                    int s0 = g.start() + matchStart;
                    int e0 = g.start() + matchEnd;
                    if (s0 != selStart) {
                        sels.add(new SelRange(s0, e0));
                        if (++added >= MAX_BEACONS) break;
                    }
                    from = matchEnd;
                }
                if (sels.isEmpty()) return;
                sels.add(0, new SelRange(selStart, selEnd));
            }
            case LINE -> {
                int first = Text.lineOfOffset(text, g.start());
                int last = Text.lineOfOffset(text, Math.max(g.end() - 1, g.start()));
                if (last <= first) return;
                for (int line = first; line <= last; line++) {
                    sels.add(new SelRange(Text.lineStart(text, line), Text.lineEnd(text, line)));
                }
            }
            default -> {
                return;
            }
        }
        ctx.port().setSelections(sels);
    }
}
