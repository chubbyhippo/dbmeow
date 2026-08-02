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
import java.util.LinkedHashMap;
import java.util.Map;

public final class Structures {
    private Structures() {}

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-inner-of-thing", ctx -> pendThing(ctx, Pending.INNER));
        commands.put("meow-bounds-of-thing", ctx -> pendThing(ctx, Pending.BOUNDS));
        commands.put("meow-beginning-of-thing", ctx -> pendThing(ctx, Pending.BEGIN));
        commands.put("meow-end-of-thing", ctx -> pendThing(ctx, Pending.END));
        commands.put("meow-block", Structures::block);
        commands.put("meow-to-block", Structures::toBlock);
        commands.put("meow-join", Structures::join);
    }

    private static void pendThing(Ctx ctx, Pending p) {
        ctx.state().pending = p;
        ctx.ui().scheduleWhichKey("things", "");
    }

    public static void thingSelect(Ctx ctx, Pending kind, char ch) {
        int caret = Selections.primary(ctx).active();
        OffsetRange bounds =
                kind == Pending.BOUNDS
                        ? Things.bounds(ctx, ch, caret)
                        : Things.inner(ctx, ch, caret);
        if (bounds == null) {
            ctx.ui().hint("No thing '" + ch + "' here");
            return;
        }
        switch (kind) {
            case INNER ->
                    Selections.select(ctx, SelType.TRANSIENT, bounds.start(), bounds.end(), false);
            case BOUNDS ->
                    Selections.select(ctx, SelType.TRANSIENT, bounds.end(), bounds.start(), false);
            case BEGIN -> Selections.select(ctx, SelType.TRANSIENT, caret, bounds.start(), false);
            case END -> Selections.select(ctx, SelType.TRANSIENT, caret, bounds.end(), false);
            default -> {}
        }
    }

    private static int[] enclosingPair(String text, int selStart, int selEnd) {
        String opens = "([{";
        String closes = ")]}";
        java.util.Deque<Integer> openOffsets = new java.util.ArrayDeque<>();
        int[] best = null;
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '"' || ch == '\'' || ch == '`') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != ch && text.charAt(j) != '\n') {
                    if (text.charAt(j) == '\\') j++;
                    j++;
                }
                if (j < text.length() && text.charAt(j) == ch) {
                    i = j + 1;
                    continue;
                }
            }
            if (opens.indexOf(ch) >= 0) {
                openOffsets.push(i);
            } else if (closes.indexOf(ch) >= 0) {
                int bracketKind = closes.indexOf(ch);
                while (!openOffsets.isEmpty()) {
                    int open = openOffsets.pop();
                    if (opens.indexOf(text.charAt(open)) == bracketKind) {
                        if (open < selStart
                                && i + 1 >= selEnd
                                && (best == null || i - open < best[1] - best[0])) {
                            best = new int[] {open, i};
                        }
                        break;
                    }
                }
            }
            i++;
        }
        return best;
    }

    private static void block(Ctx ctx) {
        String text = ctx.port().getText();
        SelRange sel = Selections.primary(ctx);
        boolean active = ctx.state().selType == SelType.BLOCK && Selections.hasSelection(sel);
        boolean back = Selections.backwardP(ctx) != (ctx.state().takeCount(1) < 0);
        int selStart = active ? sel.selStart() : sel.active();
        int selEnd = active ? sel.selEnd() : sel.active();
        int[] pair = enclosingPair(text, selStart, selEnd);
        if (pair == null) {
            ctx.ui().hint("No enclosing block");
            return;
        }
        if (back) Selections.select(ctx, SelType.BLOCK, pair[1] + 1, pair[0], true);
        else Selections.select(ctx, SelType.BLOCK, pair[0], pair[1] + 1, true);
    }

    private static void toBlock(Ctx ctx) {
        String text = ctx.port().getText();
        boolean back =
                (ctx.state().selType == SelType.BLOCK && Selections.backwardP(ctx))
                        || ctx.state().takeCount(1) < 0;
        int caret = Selections.primary(ctx).active();
        int[] pair = enclosingPair(text, caret, caret);
        if (pair == null) {
            ctx.ui().hint("No enclosing block");
            return;
        }
        Selections.select(ctx, SelType.BLOCK, caret, back ? pair[0] : pair[1] + 1, true);
    }

    private static void join(Ctx ctx) {
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int count = ctx.state().takeCount(1);
        int caretLine = Text.lineOfOffset(text, Selections.primary(ctx).active());
        if (count >= 0) {
            int upper = caretLine - 1;
            while (upper >= 0 && Things.blank(text, upper)) upper--;
            if (upper < 0) return;
            selectJoin(ctx, text, upper, caretLine);
        } else {
            int last = Text.lineCount(text) - 1;
            int lower = caretLine + 1;
            while (lower <= last && Things.blank(text, lower)) lower++;
            if (lower > last) return;
            selectJoin(ctx, text, caretLine, lower);
        }
    }

    private static void selectJoin(Ctx ctx, String text, int upperLine, int lowerLine) {
        int mark = Text.lineEnd(text, upperLine);
        int point = Text.lineStart(text, lowerLine);
        int eol = Text.lineEnd(text, lowerLine);
        while (point < eol && Character.isWhitespace(text.charAt(point))) point++;
        Selections.select(ctx, SelType.JOIN, mark, point, true);
    }
}
