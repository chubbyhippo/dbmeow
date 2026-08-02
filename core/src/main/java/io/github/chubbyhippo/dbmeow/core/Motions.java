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
        commands.put("meow-left", ctx -> moveChar(ctx, -ctx.state().takeCount(1)));
        commands.put("meow-right", ctx -> moveChar(ctx, ctx.state().takeCount(1)));
        commands.put("meow-next", ctx -> moveLine(ctx, ctx.state().takeCount(1)));
        commands.put("meow-prev", ctx -> moveLine(ctx, -ctx.state().takeCount(1)));
        commands.put("meow-left-expand", ctx -> moveExpand(ctx, -ctx.state().takeCount(1), 0));
        commands.put("meow-right-expand", ctx -> moveExpand(ctx, ctx.state().takeCount(1), 0));
        commands.put("meow-next-expand", ctx -> moveExpand(ctx, 0, ctx.state().takeCount(1)));
        commands.put("meow-prev-expand", ctx -> moveExpand(ctx, 0, -ctx.state().takeCount(1)));
        commands.put("meow-next-word", ctx -> wordMotion(ctx, false, ctx.state().takeCount(1)));
        commands.put("meow-next-symbol", ctx -> wordMotion(ctx, true, ctx.state().takeCount(1)));
        commands.put("meow-back-word", ctx -> wordMotion(ctx, false, -ctx.state().takeCount(1)));
        commands.put("meow-back-symbol", ctx -> wordMotion(ctx, true, -ctx.state().takeCount(1)));
        commands.put("meow-mark-word", ctx -> markWord(ctx, false));
        commands.put("meow-mark-symbol", ctx -> markWord(ctx, true));
        commands.put("meow-line", Motions::line);
        commands.put("meow-goto-line", Motions::gotoLine);
        commands.put("meow-find", ctx -> ctx.state().pending = Pending.FIND);
        commands.put("meow-till", ctx -> ctx.state().pending = Pending.TILL);
        commands.put("forward-char", ctx -> charOrExpand(ctx, ctx.state().takeCount(1)));
        commands.put("backward-char", ctx -> charOrExpand(ctx, -ctx.state().takeCount(1)));
        commands.put(
                "next-line",
                ctx -> {
                    lineOrExpand(ctx, ctx.state().takeCount(1));
                    ctx.state().lastCommand = "next-line";
                });
        commands.put(
                "previous-line",
                ctx -> {
                    lineOrExpand(ctx, -ctx.state().takeCount(1));
                    ctx.state().lastCommand = "previous-line";
                });
        commands.put(
                "move-beginning-of-line",
                ctx -> moveToOrExpand(ctx, SelType.CHAR, Motions::lineStartTarget));
        commands.put(
                "move-end-of-line",
                ctx -> moveToOrExpand(ctx, SelType.CHAR, Motions::lineEndTarget));
        commands.put(
                "back-to-indentation",
                ctx -> moveToOrExpand(ctx, SelType.CHAR, Motions::indentationTarget));
        commands.put("forward-word", ctx -> wordOrExpand(ctx, ctx.state().takeCount(1)));
        commands.put("backward-word", ctx -> wordOrExpand(ctx, -ctx.state().takeCount(1)));
        commands.put("forward-sentence", ctx -> sentenceOrExpand(ctx, ctx.state().takeCount(1)));
        commands.put("backward-sentence", ctx -> sentenceOrExpand(ctx, -ctx.state().takeCount(1)));
        commands.put("beginning-of-buffer", ctx -> bufferBoundary(ctx, true));
        commands.put("end-of-buffer", ctx -> bufferBoundary(ctx, false));
        commands.put("forward-paragraph", ctx -> paragraphOrExpand(ctx, ctx.state().takeCount(1)));
        commands.put(
                "backward-paragraph", ctx -> paragraphOrExpand(ctx, -ctx.state().takeCount(1)));
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

    private static int indentationTarget(String text, int off) {
        int line = Text.lineOfOffset(text, off);
        int end = Text.lineEnd(text, line);
        int at = Text.lineStart(text, line);
        while (at < end && isBlank(text.charAt(at))) at++;
        return at;
    }

    static boolean isBlank(char ch) {
        return ch == ' ' || ch == '\t';
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
            ctx.state().selType = type;
            ctx.state().selExpand = true;
        }
    }

    private static void wordOrExpand(Ctx ctx, int count) {
        Text.CharPredicate isWord = Text.charPred(false);
        moveToOrExpand(
                ctx,
                SelType.WORD,
                (text, off) ->
                        count >= 0
                                ? Text.Words.nextEnd(text, off, count, isWord)
                                : Text.Words.prevStart(text, off, -count, isWord));
    }

    private static void sentenceOrExpand(Ctx ctx, int count) {
        moveToOrExpand(
                ctx,
                SelType.CHAR,
                (text, off) ->
                        count >= 0
                                ? Text.nextSentenceEnd(text, off, count)
                                : Text.prevSentenceStart(text, off, -count));
    }

    private static void paragraphOrExpand(Ctx ctx, int count) {
        moveToOrExpand(
                ctx,
                SelType.CHAR,
                (text, off) ->
                        count >= 0
                                ? Text.nextParagraphEnd(text, off, count)
                                : Text.prevParagraphStart(text, off, -count));
    }

    private static void bufferBoundary(Ctx ctx, boolean top) {
        boolean counted = ctx.state().counted();
        int count = ctx.state().takeCount(1);
        moveToOrExpand(
                ctx,
                SelType.CHAR,
                (text, off) -> {
                    int len = text.length();
                    if (!counted) return top ? 0 : len;
                    int tenth = len * count / 10;
                    int raw = Text.clamp(top ? tenth : len - tenth, 0, len);
                    return nextLineStart(text, raw);
                });
    }

    private static int nextLineStart(String text, int offset) {
        if (text.isEmpty()) return 0;
        int caretLine = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        return caretLine >= Text.lineCount(text) - 1
                ? text.length()
                : Text.lineStart(text, caretLine + 1);
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
        return ctx.state().selType == SelType.CHAR
                && Selections.hasSelection(Selections.primary(ctx));
    }

    private static SelRange movedChar(int len, SelRange sel, int dx, boolean extend) {
        int active = Text.clamp(sel.active() + dx, 0, len);
        return new SelRange(extend ? sel.anchor() : active, active);
    }

    private static SelRange movedLine(
            String text, SelRange sel, int dy, boolean extend, Integer goal) {
        int caretLine = Text.lineOfOffset(text, sel.active());
        int target = caretLine + dy;
        int active;
        if (target < 0) {
            active = 0;
        } else if (target > Text.lineCount(text) - 1) {
            active = text.length();
        } else {
            int column = goal != null ? goal : sel.active() - Text.lineStart(text, caretLine);
            int bol = Text.lineStart(text, target);
            active = bol + Math.min(column, Text.lineEnd(text, target) - bol);
        }
        return new SelRange(extend ? sel.anchor() : active, active);
    }

    private static int goalColumn(Ctx ctx) {
        MeowState state = ctx.state();
        if (state.goalColumn == null
                || state.lastCommand == null
                || !VERTICAL.contains(state.lastCommand)) {
            String text = ctx.port().getText();
            int caret = Selections.primary(ctx).active();
            state.goalColumn = caret - Text.lineStart(text, Text.lineOfOffset(text, caret));
        }
        return state.goalColumn;
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
        ctx.state().selType = SelType.CHAR;
        ctx.state().selExpand = true;
    }

    private static void wordMotion(Ctx ctx, boolean symbol, int count) {
        if (count == 0) return;
        String text = ctx.port().getText();
        SelType type = wordType(symbol);
        SelRange sel = Selections.primary(ctx);
        int selStart = sel.lo();
        int selEnd = sel.hi();
        if (!(Selections.hasSelection(sel) && ctx.state().selType == type)) Selections.cancel(ctx);
        boolean extend =
                ctx.state().selExpand
                        && ctx.state().selType == type
                        && Selections.hasSelection(sel);
        int from = extend ? (count < 0 ? selStart : selEnd) : sel.active();
        int target =
                count > 0
                        ? Text.Words.nextEnd(text, from, count, Text.charPred(symbol))
                        : Text.Words.prevStart(text, from, -count, Text.charPred(symbol));
        if (target == from) return;
        int anchor =
                extend
                        ? (count < 0 ? selEnd : selStart)
                        : Text.Words.fixSelectionMark(text, target, from, Text.charPred(symbol));
        Selections.select(ctx, type, anchor, target, extend);
    }

    private static void markWord(Ctx ctx, boolean symbol) {
        boolean reversed = ctx.state().takeCount(1) < 0;
        String text = ctx.port().getText();
        int[] bounds =
                Text.Words.boundsAt(text, Selections.primary(ctx).active(), Text.charPred(symbol));
        if (bounds == null) {
            ctx.ui().hint("No word here");
            return;
        }
        int start = bounds[0];
        int end = bounds[1];
        if (reversed) Selections.select(ctx, wordType(symbol), end, start, true);
        else Selections.select(ctx, wordType(symbol), start, end, true);
        String quoted = Text.escapeRegExp(text.substring(start, end));
        String pattern = symbol ? "(?<![\\w$])" + quoted + "(?![\\w$])" : "\\b" + quoted + "\\b";
        Search.push(ctx.state(), pattern);
    }

    private static void line(Ctx ctx) {
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int count = ctx.state().takeCount(1);
        int lastLine = Text.lineCount(text) - 1;
        if (ctx.state().selType == SelType.LINE
                && ctx.state().selExpand
                && Selections.hasSelection(Selections.primary(ctx))) {
            int caretLine = Text.lineOfOffset(text, Selections.primary(ctx).active());
            if (Selections.backwardP(ctx)) {
                int target = Math.max(caretLine - Math.abs(count), 0);
                Selections.select(
                        ctx,
                        SelType.LINE,
                        Selections.mark(ctx),
                        Text.lineStart(text, target),
                        true);
            } else {
                int target = Math.min(caretLine + Math.abs(count), lastLine);
                Selections.select(
                        ctx, SelType.LINE, Selections.mark(ctx), Text.lineEnd(text, target), true);
            }
            return;
        }
        int caretLine = Text.lineOfOffset(text, Selections.primary(ctx).active());
        if (count < 0) {
            int firstLine = Math.max(caretLine + count + 1, 0);
            Selections.select(
                    ctx,
                    SelType.LINE,
                    Text.lineEnd(text, caretLine),
                    Text.lineStart(text, firstLine),
                    true);
        } else {
            int finalLine = Math.min(caretLine + count - 1, lastLine);
            Selections.select(
                    ctx,
                    SelType.LINE,
                    Text.lineStart(text, caretLine),
                    Text.lineEnd(text, finalLine),
                    true);
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
        int target = Text.clamp(parsed - 1, 0, Text.lineCount(text) - 1);
        Selections.select(
                ctx, SelType.LINE, Text.lineStart(text, target), Text.lineEnd(text, target), true);
    }

    public static void findTill(Ctx ctx, char ch, boolean till) {
        int count = ctx.state().takeCount(1);
        String text = ctx.port().getText();
        int caret = Selections.primary(ctx).active();
        int target = Text.nthCharTarget(text, ch, caret, Math.abs(count), count < 0, till);
        if (target < 0) {
            ctx.ui().hint("char not found: " + ch);
            return;
        }
        ctx.state().lastFind = ch;
        Selections.select(ctx, till ? SelType.TILL : SelType.FIND, caret, target, false);
    }
}
