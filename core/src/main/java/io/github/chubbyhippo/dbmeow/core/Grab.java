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

/**
 * meow-grab / swap-grab / sync-grab — the secondary-selection stand-in — plus
 * the BEACON multi-cursor approximation. A name-for-name port of codemeow's
 * grab.ts, against meow-command.el (see meow-semantics.md). The beacon LOGIC
 * ports and is tested headless over FakeEditor (which can hold several carets);
 * the SWT adapter renders only the primary caret (single-caret StyledText) but
 * still applies the multi-range edit — see the README for that adapter note.
 */
public final class Grab {
    private Grab() {
    }

    public static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-grab", Grab::grab);
        commands.put("meow-sync-grab", Grab::sync);
        commands.put("meow-swap-grab", Grab::swap);
    }

    public static void clear(Ctx ctx) {
        ctx.st().grab = null;
    }

    private static void set(Ctx ctx, int start, int end) {
        ctx.st().grab = new OffsetRange(start, end);
    }

    /**
     * Keep the grab tracking core-applied edits, like a range marker would:
     * edits before it shift it, edits inside it grow/shrink it.
     */
    public static void adjustForEdits(MeowState st, List<TextEdit> edits) {
        OffsetRange g = st.grab;
        if (g == null) return;
        int gs = g.start();
        int ge = g.end();
        List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(TextEdit::start).reversed());
        for (TextEdit e : ordered) {
            int delta = e.text().length() - (e.end() - e.start());
            if (gs >= e.end()) {
                gs += delta;
                ge += delta;
            } else {
                if (ge >= e.end()) ge += delta;
                else if (ge > e.start()) ge = e.start();
                if (gs > e.start()) gs = e.start();
            }
        }
        if (ge < gs) ge = gs;
        st.grab = new OffsetRange(gs, ge);
    }

    /** meow-grab: region -> secondary selection; with NO region the grab is
     *  cancelled instead (meow 1.5.0 body, despite its docstring). */
    private static void grab(Ctx ctx) {
        clear(ctx);
        SelRange sel = Selections.primary(ctx);
        if (Selections.hasSelection(sel)) {
            set(ctx, Math.min(sel.anchor(), sel.active()), Math.max(sel.anchor(), sel.active()));
        }
        Selections.cancel(ctx);
    }

    /** meow-sync-grab: secondary := region; selection cancelled. */
    private static void sync(Ctx ctx) {
        SelRange sel = Selections.primary(ctx);
        if (!Selections.hasSelection(sel)) {
            ctx.ui().hint("meow-sync-grab needs a selection");
            return;
        }
        clear(ctx);
        set(ctx, Math.min(sel.anchor(), sel.active()), Math.max(sel.anchor(), sel.active()));
        Selections.cancel(ctx);
    }

    /** meow-swap-grab: exchange region and secondary text; the secondary stays
     *  at its location holding the swapped-in text. */
    private static void swap(Ctx ctx) {
        if (Edits.blockedReadOnly(ctx)) return; // swap-grab edits both regions
        MeowState st = ctx.st();
        OffsetRange g = st.grab;
        SelRange sel = Selections.primary(ctx);
        if (g == null) {
            ctx.ui().hint("No grab");
            return;
        }
        if (!Selections.hasSelection(sel)) {
            ctx.ui().hint("meow-swap-grab needs a selection");
            return;
        }
        int gs = g.start();
        int ge = g.end();
        int ss = Math.min(sel.anchor(), sel.active());
        int se = Math.max(sel.anchor(), sel.active());
        if (Math.max(gs, ss) < Math.min(ge, se) && !(gs == ss && ge == se)) {
            ctx.ui().hint("Selection overlaps the grab");
            return;
        }
        String text = ctx.port().getText();
        String grabText = text.substring(gs, ge);
        String selText = text.substring(ss, se);
        st.grab = null; // replaced wholesale below; skip marker adjustment
        ctx.port().edit(List.of(
                new TextEdit(ss, se, grabText),
                new TextEdit(gs, ge, selText)));
        if (gs <= ss) {
            int delta = selText.length() - (ge - gs);
            set(ctx, gs, gs + selText.length());
            int caret = ss + delta + grabText.length();
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        } else {
            int delta = grabText.length() - (se - ss);
            set(ctx, gs + delta, gs + delta + selText.length());
            int caret = ss + grabText.length();
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        }
        st.selType = SelType.NONE;
    }

    /** meow-pop-grab, the pop-selection fallback: grab becomes the selection. */
    public static boolean pop(Ctx ctx) {
        OffsetRange g = ctx.st().grab;
        if (g == null) return false;
        int start = g.start();
        int end = g.end();
        clear(ctx);
        Selections.select(ctx, SelType.TRANSIENT, start, end, false);
        return true;
    }

    /**
     * BEACON: with a grab active, creating a selection inside it drops a
     * cursor+selection on every similar range in the grab, so a following edit
     * (change/delete/…) hits them all — meow's kmacro replay, done with
     * multiple carets. Invoked from the selection primitive, so every selecting
     * command participates. The SWT adapter shows only the primary caret, but
     * the multi-range edit still applies.
     */
    public static void beacon(Ctx ctx) {
        MeowState st = ctx.st();
        OffsetRange g = st.grab;
        if (g == null || g.end() <= g.start()) return;
        SelRange sel = Selections.primary(ctx);
        if (!Selections.hasSelection(sel)) return;
        int ss = Math.min(sel.anchor(), sel.active());
        int se = Math.max(sel.anchor(), sel.active());
        if (ss < g.start() || se > g.end() || se == ss) return;
        String text = ctx.port().getText();
        List<SelRange> sels = new ArrayList<>();
        switch (st.selType) {
            case WORD, SYMBOL, VISIT, FIND, TILL, CHAR -> {
                String selText = text.substring(ss, se);
                if (selText.trim().isEmpty()) return;
                boolean bounded = st.selType == SelType.WORD || st.selType == SelType.SYMBOL;
                String pat = bounded
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
                    int rs = m.start();
                    int re = m.end();
                    if (re == rs) {
                        from = re + 1;
                        continue;
                    }
                    int s0 = g.start() + rs;
                    int e0 = g.start() + re;
                    if (s0 != ss) {
                        sels.add(new SelRange(s0, e0));
                        if (++added >= 500) break;
                    }
                    from = re;
                }
                if (sels.isEmpty()) return;
                sels.add(0, new SelRange(ss, se)); // the original stays primary
            }
            case LINE -> {
                int first = Text.lineOfOffset(text, g.start());
                int last = Text.lineOfOffset(text, Math.max(g.end() - 1, g.start()));
                if (last <= first) return;
                for (int ln = first; ln <= last; ln++) {
                    sels.add(new SelRange(Text.lineStart(text, ln), Text.lineEnd(text, ln)));
                }
            }
            default -> {
                return;
            }
        }
        ctx.port().setSelections(sels);
    }
}
