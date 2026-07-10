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

/**
 * Structural selections: the char-thing table behind {@code , . [ ]} (see {@link Things}), bracket
 * blocks (meow-block / meow-to-block), and the join region (meow-join). The thing commands park a
 * {@link Pending} key and let the dispatcher hand the thing char to {@link #thingSelect}. Ported
 * from codemeow's structures.ts against meow-command.el and the direction rules in meow-var.el
 * (meow-thing-selection-directions).
 */
public final class Structures {
    private Structures() {}

    public static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

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
        ctx.st().pending = p;
        ctx.ui().scheduleWhichKey("things", "");
    }

    /** The second half of the thing commands, once the thing char arrives. */
    public static void thingSelect(Ctx ctx, Pending kind, char ch) {
        int off = Selections.primary(ctx).active();
        OffsetRange b =
                kind == Pending.BOUNDS ? Things.bounds(ctx, ch, off) : Things.inner(ctx, ch, off);
        if (b == null) {
            ctx.ui().hint("No thing '" + ch + "' here");
            return;
        }
        switch (kind) {
            case INNER -> Selections.select(ctx, SelType.TRANSIENT, b.start(), b.end(), false);
            // meow-thing-selection-directions: bounds select BACKWARD (caret at
            // the opening delimiter), inner forward — verified by probe
            case BOUNDS -> Selections.select(ctx, SelType.TRANSIENT, b.end(), b.start(), false);
            case BEGIN -> Selections.select(ctx, SelType.TRANSIENT, off, b.start(), false);
            case END -> Selections.select(ctx, SelType.TRANSIENT, off, b.end(), false);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ blocks

    /**
     * Smallest bracket pair strictly enclosing [s, e). Same-line quoted runs are skipped — a text
     * approximation of syntax-ppss. Returns {open, close} offsets or null.
     */
    private static int[] enclosingPair(String text, int s, int e) {
        String opens = "([{";
        String closes = ")]}";
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
        int[] best = null;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c && text.charAt(j) != '\n') {
                    if (text.charAt(j) == '\\') j++;
                    j++;
                }
                if (j < text.length() && text.charAt(j) == c) {
                    i = j + 1;
                    continue;
                }
            }
            if (opens.indexOf(c) >= 0) {
                stack.push(i);
            } else if (closes.indexOf(c) >= 0) {
                int kind = closes.indexOf(c);
                while (!stack.isEmpty()) {
                    int o = stack.pop();
                    if (opens.indexOf(text.charAt(o)) == kind) {
                        if (o < s && i + 1 >= e && (best == null || i - o < best[1] - best[0])) {
                            best = new int[] {o, i};
                        }
                        break;
                    }
                }
            }
            i++;
        }
        return best;
    }

    /**
     * meow-block: innermost pair INCLUDING delimiters; with an active block selection it expands to
     * the parent. Backward (caret at the opening delimiter) when the region is reversed XOR a
     * negative argument.
     */
    private static void block(Ctx ctx) {
        String text = ctx.port().getText();
        SelRange sel = Selections.primary(ctx);
        boolean active = ctx.st().selType == SelType.BLOCK && Selections.hasSelection(sel);
        // xor, like meow-block; read the direction BEFORE takeCount consumes it
        boolean back = Selections.backwardP(ctx) != (ctx.st().takeCount(1) < 0);
        int s = active ? Math.min(sel.anchor(), sel.active()) : sel.active();
        int e = active ? Math.max(sel.anchor(), sel.active()) : sel.active();
        int[] p = enclosingPair(text, s, e);
        if (p == null) {
            ctx.ui().hint("No enclosing block");
            return;
        }
        if (back) Selections.select(ctx, SelType.BLOCK, p[1] + 1, p[0], true);
        else Selections.select(ctx, SelType.BLOCK, p[0], p[1] + 1, true);
    }

    /**
     * meow-to-block: from point to the closing delimiter of the enclosing block (to the opening one
     * when the block selection is reversed or the argument is negative).
     */
    private static void toBlock(Ctx ctx) {
        String text = ctx.port().getText();
        boolean back =
                (ctx.st().selType == SelType.BLOCK && Selections.backwardP(ctx))
                        || ctx.st().takeCount(1) < 0;
        int caret = Selections.primary(ctx).active();
        int[] p = enclosingPair(text, caret, caret);
        if (p == null) {
            ctx.ui().hint("No enclosing block");
            return;
        }
        Selections.select(ctx, SelType.BLOCK, caret, back ? p[0] : p[1] + 1, true);
    }

    // -------------------------------------------------------------------- join

    /**
     * meow-join: select (expand . join) — end of the previous non-empty line through this line's
     * indentation (forward variant with a negative arg); killing that selection is
     * delete-indentation (see Edits).
     */
    private static void join(Ctx ctx) {
        String text = ctx.port().getText();
        if (text.isEmpty()) return;
        int n = ctx.st().takeCount(1);
        int ln = Text.lineOfOffset(text, Selections.primary(ctx).active());
        if (n >= 0) {
            int pl = ln - 1;
            while (pl >= 0 && Things.blank(text, pl)) pl--;
            if (pl < 0) return;
            int m = Text.lineEnd(text, pl);
            int p = Text.lineStart(text, ln);
            int eol = Text.lineEnd(text, ln);
            while (p < eol && Character.isWhitespace(text.charAt(p))) p++;
            Selections.select(ctx, SelType.JOIN, m, p, true);
        } else {
            int last = Text.lineCount(text) - 1;
            int nl = ln + 1;
            while (nl <= last && Things.blank(text, nl)) nl++;
            if (nl > last) return;
            int m = Text.lineEnd(text, ln);
            int p = Text.lineStart(text, nl);
            int eol = Text.lineEnd(text, nl);
            while (p < eol && Character.isWhitespace(text.charAt(p))) p++;
            Selections.select(ctx, SelType.JOIN, m, p, true);
        }
    }
}
