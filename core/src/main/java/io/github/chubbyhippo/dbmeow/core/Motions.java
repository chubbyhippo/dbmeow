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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cursor motion and the selections it creates: char/line movement with the -expand variants,
 * word/symbol motions, meow-line, goto-line, and find/till. Every behavior here follows
 * meow-command.el, not vim intuition — see the {@link #wordMotion} doc for the
 * direction-normalization rule that makes `w` then `b` extend instead of re-mark.
 */
public final class Motions {
    private Motions() {}

    public static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-left", ctx -> moveChar(ctx, -ctx.st().takeCount(1)));
        commands.put("meow-right", ctx -> moveChar(ctx, ctx.st().takeCount(1)));
        commands.put("meow-next", ctx -> moveLine(ctx, ctx.st().takeCount(1)));
        commands.put("meow-prev", ctx -> moveLine(ctx, -ctx.st().takeCount(1)));
        commands.put("meow-left-expand", ctx -> moveExpand(ctx, -ctx.st().takeCount(1), 0));
        commands.put("meow-right-expand", ctx -> moveExpand(ctx, ctx.st().takeCount(1), 0));
        commands.put("meow-next-expand", ctx -> moveExpand(ctx, 0, ctx.st().takeCount(1)));
        commands.put("meow-prev-expand", ctx -> moveExpand(ctx, 0, -ctx.st().takeCount(1)));
        commands.put("meow-next-word", ctx -> wordMotion(ctx, false, ctx.st().takeCount(1)));
        commands.put("meow-next-symbol", ctx -> wordMotion(ctx, true, ctx.st().takeCount(1)));
        // meow-back-word = meow-next-thing with -N
        commands.put("meow-back-word", ctx -> wordMotion(ctx, false, -ctx.st().takeCount(1)));
        commands.put("meow-back-symbol", ctx -> wordMotion(ctx, true, -ctx.st().takeCount(1)));
        commands.put("meow-mark-word", ctx -> markWord(ctx, false));
        commands.put("meow-mark-symbol", ctx -> markWord(ctx, true));
        commands.put("meow-line", Motions::line);
        commands.put("meow-goto-line", Motions::gotoLine);
        commands.put("meow-find", ctx -> ctx.st().pending = Pending.FIND);
        commands.put("meow-till", ctx -> ctx.st().pending = Pending.TILL);
    }

    private static SelType wordType(boolean symbol) {
        return symbol ? SelType.SYMBOL : SelType.WORD;
    }

    /** The commands whose chains keep Emacs' temporary-goal-column alive. */
    private static final Set<String> VERTICAL =
            Set.of("meow-next", "meow-prev", "meow-next-expand", "meow-prev-expand");

    private static boolean charSelActive(Ctx ctx) {
        return ctx.st().selType == SelType.CHAR && Selections.hasSelection(Selections.primary(ctx));
    }

    /** meow-left/right run backward-char/forward-char: offsets, crossing newlines. */
    private static SelRange movedChar(int len, SelRange sel, int dx, boolean extend) {
        int active = Text.clamp(sel.active() + dx, 0, len);
        return new SelRange(extend ? sel.anchor() : active, active);
    }

    /**
     * next-line/previous-line: goal column (primary caret), own column for the rest; past the
     * first/last line the point goes to the buffer edge.
     */
    private static SelRange movedLine(
            String text, SelRange sel, int dy, boolean extend, Integer goal) {
        int ln = Text.lineOfOffset(text, sel.active());
        int target = ln + dy;
        int active;
        if (target < 0) {
            active = 0;
        } else if (target > Text.lineCount(text) - 1) {
            active = text.length();
        } else {
            int col = goal != null ? goal : sel.active() - Text.lineStart(text, ln);
            int bol = Text.lineStart(text, target);
            active = bol + Math.min(col, Text.lineEnd(text, target) - bol);
        }
        return new SelRange(extend ? sel.anchor() : active, active);
    }

    /**
     * Set (or keep) the goal column, Emacs temporary-goal-column style: it only survives while the
     * previous command was a vertical move too.
     */
    private static int goalColumn(Ctx ctx) {
        MeowState st = ctx.st();
        if (st.goalColumn == null || st.lastCommand == null || !VERTICAL.contains(st.lastCommand)) {
            String text = ctx.port().getText();
            int p = Selections.primary(ctx).active();
            st.goalColumn = p - Text.lineStart(text, Text.lineOfOffset(text, p));
        }
        return st.goalColumn;
    }

    private static void moveChar(Ctx ctx, int dx) {
        boolean extend = charSelActive(ctx);
        // meow-left/right cancel (clearing the history) only with an active region
        if (!extend && Selections.hasSelection(Selections.primary(ctx))) Selections.cancel(ctx);
        int len = ctx.port().getText().length();
        List<SelRange> moved = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) moved.add(movedChar(len, s, dx, extend));
        ctx.port().setSelections(moved);
    }

    private static void moveLine(Ctx ctx, int dy) {
        boolean extend = charSelActive(ctx);
        // meow-next/prev run meow--cancel-selection unconditionally for other types
        if (!extend) Selections.cancel(ctx);
        int goal = goalColumn(ctx);
        String text = ctx.port().getText();
        List<SelRange> sels = ctx.port().getSelections();
        List<SelRange> moved = new ArrayList<>();
        for (int i = 0; i < sels.size(); i++) {
            moved.add(movedLine(text, sels.get(i), dy, extend, i == 0 ? goal : null));
        }
        ctx.port().setSelections(moved);
    }

    /**
     * meow-left/right/next/prev-expand: (expand . char) selection through meow--select — so the
     * history is recorded — then the char/line motion.
     */
    private static void moveExpand(Ctx ctx, int dx, int dy) {
        String text = ctx.port().getText();
        Integer goal = dy != 0 ? goalColumn(ctx) : null;
        List<SelRange> sels = ctx.port().getSelections();
        int before = sels.get(0).active();
        List<SelRange> moved = new ArrayList<>();
        for (int i = 0; i < sels.size(); i++) {
            moved.add(
                    dy == 0
                            ? movedChar(text.length(), sels.get(i), dx, true)
                            : movedLine(text, sels.get(i), dy, true, i == 0 ? goal : null));
        }
        ctx.port().setSelections(moved);
        Selections.recordSelect(
                ctx, SelType.CHAR, moved.get(0).anchor(), moved.get(0).active(), true, before);
        ctx.st().selType = SelType.CHAR;
        ctx.st().selExpand = true;
        // Grab.beacon(ctx) lands here with the grab module port.
    }

    /**
     * meow-next-thing for word/symbol: when the current selection is the matching (expand . type),
     * the selection direction is normalized to the motion FIRST (meow--direction-forward/-backward)
     * — so after `w`, `e` extends from the right end and `b` extends from the left end, anchored at
     * the opposite end (meow--make-selection keeps min/max of the original region as the mark).
     * Without a matching selection: fresh (select . type) from point. No motion -> no selection
     * change.
     */
    private static void wordMotion(Ctx ctx, boolean symbol, int n) {
        if (n == 0) return;
        String text = ctx.port().getText();
        SelType type = wordType(symbol);
        SelRange sel = Selections.primary(ctx);
        int lo = Math.min(sel.anchor(), sel.active());
        int hi = Math.max(sel.anchor(), sel.active());
        // meow-next-thing: a selection of another type (or none) is cancelled
        // FIRST — meow--cancel-selection, so the chain history restarts and a
        // later z pops the null placeholder, not the foreign selection
        if (!(Selections.hasSelection(sel) && ctx.st().selType == type)) Selections.cancel(ctx);
        boolean extend =
                ctx.st().selExpand && ctx.st().selType == type && Selections.hasSelection(sel);
        int from = extend ? (n < 0 ? lo : hi) : sel.active();
        int target =
                n > 0
                        ? Text.Words.nextEnd(text, from, n, Text.charPred(symbol))
                        : Text.Words.prevStart(text, from, -n, Text.charPred(symbol));
        if (target == from) return;
        // meow--fix-thing-selection-mark: a fresh selection snaps its mark to
        // the word's own bounds — the separators between the old point and the
        // word stay OUTSIDE (e e e steps bare words)
        int anchor =
                extend
                        ? (n < 0 ? hi : lo)
                        : Text.Words.fixSelectionMark(text, target, from, Text.charPred(symbol));
        Selections.select(ctx, type, anchor, target, extend);
    }

    /**
     * meow-mark-word/-symbol: select the thing at point as (expand . type) and push its bounded
     * regexp to the search ring — why `n` works after `w`.
     */
    private static void markWord(Ctx ctx, boolean symbol) {
        boolean neg = ctx.st().takeCount(1) < 0;
        String text = ctx.port().getText();
        int[] b =
                Text.Words.boundsAt(text, Selections.primary(ctx).active(), Text.charPred(symbol));
        if (b == null) {
            ctx.ui().hint("No word here");
            return;
        }
        int s = b[0];
        int e = b[1];
        if (neg) Selections.select(ctx, wordType(symbol), e, s, true);
        else Selections.select(ctx, wordType(symbol), s, e, true);
        Search.push(ctx.st(), "\\b" + Text.escapeRegExp(text.substring(s, e)) + "\\b");
    }

    /**
     * meow-line: [bol, eol) without the newline; repeats extend in the selection's direction, a
     * negative argument reverses.
     */
    private static void line(Ctx ctx) {
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int n = ctx.st().takeCount(1);
        int lastLine = Text.lineCount(text) - 1;
        // extension needs exactly (expand . line) — a digit-expanded
        // (select . line) selection re-selects the current line instead
        if (ctx.st().selType == SelType.LINE
                && ctx.st().selExpand
                && Selections.hasSelection(Selections.primary(ctx))) {
            int caretLn = Text.lineOfOffset(text, Selections.primary(ctx).active());
            if (Selections.backwardP(ctx)) {
                int ln = Math.max(caretLn - Math.abs(n), 0);
                Selections.select(
                        ctx, SelType.LINE, Selections.mark(ctx), Text.lineStart(text, ln), true);
            } else {
                int ln = Math.min(caretLn + Math.abs(n), lastLine);
                Selections.select(
                        ctx, SelType.LINE, Selections.mark(ctx), Text.lineEnd(text, ln), true);
            }
            return;
        }
        int ln = Text.lineOfOffset(text, Selections.primary(ctx).active());
        if (n < 0) {
            int startLn = Math.max(ln + n + 1, 0);
            Selections.select(
                    ctx, SelType.LINE, Text.lineEnd(text, ln), Text.lineStart(text, startLn), true);
        } else {
            int endLn = Math.min(ln + n - 1, lastLine);
            Selections.select(
                    ctx, SelType.LINE, Text.lineStart(text, ln), Text.lineEnd(text, endLn), true);
        }
    }

    /** meow-goto-line: select the target line (expand . line) and recenter. */
    private static void gotoLine(Ctx ctx) {
        String input = ctx.ui().input("Goto line:");
        if (input == null) return;
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int parsed;
        try {
            parsed = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return;
        }
        int ln = Text.clamp(parsed - 1, 0, Text.lineCount(text) - 1);
        Selections.select(
                ctx, SelType.LINE, Text.lineStart(text, ln), Text.lineEnd(text, ln), true);
    }

    /** The second half of meow-find/meow-till, once the char arrives. */
    public static void findTill(Ctx ctx, char ch, boolean till) {
        int n = ctx.st().takeCount(1);
        String text = ctx.port().getText();
        int caret = Selections.primary(ctx).active();
        int target = Text.nthCharTarget(text, ch, caret, Math.abs(n), n < 0, till);
        if (target < 0) {
            ctx.ui().hint("char not found: " + ch);
            return;
        }
        // BEFORE the select: its expand hints preview further occurrences of
        // THIS char (a stale lastFind painted the previous find's positions)
        ctx.st().lastFind = ch;
        Selections.select(ctx, till ? SelType.TILL : SelType.FIND, caret, target, false);
    }
}
