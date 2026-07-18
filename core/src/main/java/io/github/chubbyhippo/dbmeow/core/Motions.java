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

public final class Motions {
    private Motions() {}

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

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
        commands.put("meow-back-word", ctx -> wordMotion(ctx, false, -ctx.st().takeCount(1)));
        commands.put("meow-back-symbol", ctx -> wordMotion(ctx, true, -ctx.st().takeCount(1)));
        commands.put("meow-mark-word", ctx -> markWord(ctx, false));
        commands.put("meow-mark-symbol", ctx -> markWord(ctx, true));
        commands.put("meow-line", Motions::line);
        commands.put("meow-goto-line", Motions::gotoLine);
        commands.put("meow-find", ctx -> ctx.st().pending = Pending.FIND);
        commands.put("meow-till", ctx -> ctx.st().pending = Pending.TILL);
        commands.put("forward-char", ctx -> charOrExpand(ctx, ctx.st().takeCount(1)));
        commands.put("backward-char", ctx -> charOrExpand(ctx, -ctx.st().takeCount(1)));
        commands.put(
                "next-line",
                ctx -> {
                    lineOrExpand(ctx, ctx.st().takeCount(1));
                    ctx.st().lastCommand = "next-line";
                });
        commands.put(
                "previous-line",
                ctx -> {
                    lineOrExpand(ctx, -ctx.st().takeCount(1));
                    ctx.st().lastCommand = "previous-line";
                });
        commands.put(
                "move-beginning-of-line",
                ctx -> moveToOrExpand(ctx, SelType.CHAR, Motions::lineStartTarget));
        commands.put(
                "move-end-of-line",
                ctx -> moveToOrExpand(ctx, SelType.CHAR, Motions::lineEndTarget));
        commands.put("forward-word", ctx -> wordOrExpand(ctx, ctx.st().takeCount(1)));
        commands.put("backward-word", ctx -> wordOrExpand(ctx, -ctx.st().takeCount(1)));
        commands.put("forward-sentence", ctx -> sentenceOrExpand(ctx, ctx.st().takeCount(1)));
        commands.put("backward-sentence", ctx -> sentenceOrExpand(ctx, -ctx.st().takeCount(1)));
        commands.put("beginning-of-buffer", ctx -> bufferBoundary(ctx, true));
        commands.put("end-of-buffer", ctx -> bufferBoundary(ctx, false));
    }

    private interface OffsetTarget {
        int at(String text, int offset);
    }

    private static int lineStartTarget(String text, int off) {
        return Text.lineStart(text, Text.lineOfOffset(text, off));
    }

    private static int lineEndTarget(String text, int off) {
        return Text.lineEnd(text, Text.lineOfOffset(text, off));
    }

    private static void charOrExpand(Ctx ctx, int dx) {
        if (Selections.hasSelection(Selections.primary(ctx))) moveExpand(ctx, dx, 0);
        else moveChar(ctx, dx);
    }

    private static void lineOrExpand(Ctx ctx, int dy) {
        if (Selections.hasSelection(Selections.primary(ctx))) moveExpand(ctx, 0, dy);
        else moveLine(ctx, dy);
    }

    private static void moveToOrExpand(Ctx ctx, SelType type, OffsetTarget target) {
        String text = ctx.port().getText();
        boolean extend = Selections.hasSelection(Selections.primary(ctx));
        int before = Selections.primary(ctx).active();
        List<SelRange> moved = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) {
            int active = Text.clamp(target.at(text, s.active()), 0, text.length());
            moved.add(new SelRange(extend ? s.anchor() : active, active));
        }
        ctx.port().setSelections(moved);
        if (extend) {
            Selections.recordSelect(
                    ctx, type, moved.get(0).anchor(), moved.get(0).active(), true, before);
            ctx.st().selType = type;
            ctx.st().selExpand = true;
        }
    }

    private static void wordOrExpand(Ctx ctx, int n) {
        Text.CharPredicate pred = Text.charPred(false);
        moveToOrExpand(
                ctx,
                SelType.WORD,
                (text, off) ->
                        n >= 0
                                ? Text.Words.nextEnd(text, off, n, pred)
                                : Text.Words.prevStart(text, off, -n, pred));
    }

    private static void sentenceOrExpand(Ctx ctx, int n) {
        moveToOrExpand(
                ctx,
                SelType.CHAR,
                (text, off) ->
                        n >= 0
                                ? Text.nextSentenceEnd(text, off, n)
                                : Text.prevSentenceStart(text, off, -n));
    }

    private static void bufferBoundary(Ctx ctx, boolean top) {
        boolean counted = ctx.st().pendingCount != 0 || ctx.st().negative;
        int n = ctx.st().takeCount(1);
        moveToOrExpand(
                ctx,
                SelType.CHAR,
                (text, off) -> {
                    int len = text.length();
                    if (!counted) return top ? 0 : len;
                    int tenth = len * n / 10;
                    int raw = Text.clamp(top ? tenth : len - tenth, 0, len);
                    return nextLineStart(text, raw);
                });
    }

    private static int nextLineStart(String text, int offset) {
        if (text.isEmpty()) return 0;
        int ln = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        return ln >= Text.lineCount(text) - 1 ? text.length() : Text.lineStart(text, ln + 1);
    }

    private static SelType wordType(boolean symbol) {
        return symbol ? SelType.SYMBOL : SelType.WORD;
    }

    private static final Set<String> VERTICAL =
            Set.of(
                    "meow-next",
                    "meow-prev",
                    "meow-next-expand",
                    "meow-prev-expand",
                    "next-line",
                    "previous-line");

    private static boolean charSelActive(Ctx ctx) {
        return ctx.st().selType == SelType.CHAR && Selections.hasSelection(Selections.primary(ctx));
    }

    private static SelRange movedChar(int len, SelRange sel, int dx, boolean extend) {
        int active = Text.clamp(sel.active() + dx, 0, len);
        return new SelRange(extend ? sel.anchor() : active, active);
    }

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
        if (!extend && Selections.hasSelection(Selections.primary(ctx))) Selections.cancel(ctx);
        int len = ctx.port().getText().length();
        List<SelRange> moved = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) moved.add(movedChar(len, s, dx, extend));
        ctx.port().setSelections(moved);
    }

    private static void moveLine(Ctx ctx, int dy) {
        boolean extend = charSelActive(ctx);
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
    }

    private static void wordMotion(Ctx ctx, boolean symbol, int n) {
        if (n == 0) return;
        String text = ctx.port().getText();
        SelType type = wordType(symbol);
        SelRange sel = Selections.primary(ctx);
        int lo = Math.min(sel.anchor(), sel.active());
        int hi = Math.max(sel.anchor(), sel.active());
        if (!(Selections.hasSelection(sel) && ctx.st().selType == type)) Selections.cancel(ctx);
        boolean extend =
                ctx.st().selExpand && ctx.st().selType == type && Selections.hasSelection(sel);
        int from = extend ? (n < 0 ? lo : hi) : sel.active();
        int target =
                n > 0
                        ? Text.Words.nextEnd(text, from, n, Text.charPred(symbol))
                        : Text.Words.prevStart(text, from, -n, Text.charPred(symbol));
        if (target == from) return;
        int anchor =
                extend
                        ? (n < 0 ? hi : lo)
                        : Text.Words.fixSelectionMark(text, target, from, Text.charPred(symbol));
        Selections.select(ctx, type, anchor, target, extend);
    }

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

    private static void line(Ctx ctx) {
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int n = ctx.st().takeCount(1);
        int lastLine = Text.lineCount(text) - 1;
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

    public static void findTill(Ctx ctx, char ch, boolean till) {
        int n = ctx.st().takeCount(1);
        String text = ctx.port().getText();
        int caret = Selections.primary(ctx).active();
        int target = Text.nthCharTarget(text, ch, caret, Math.abs(n), n < 0, till);
        if (target < 0) {
            ctx.ui().hint("char not found: " + ch);
            return;
        }
        ctx.st().lastFind = ch;
        Selections.select(ctx, till ? SelType.TILL : SelType.FIND, caret, target, false);
    }
}
