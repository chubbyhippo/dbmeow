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

public final class Selections {
    private Selections() {}

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        for (int n = 0; n <= 9; n++) {
            final int digit = n;
            commands.put("meow-expand-" + n, ctx -> expandOrCount(ctx, digit));
        }
        commands.put("meow-reverse", Selections::reverse);
        commands.put("meow-cancel-selection", Selections::cancelAll);
        commands.put("meow-pop-selection", Selections::pop);
    }

    private static final int MAX_SELECTION_HISTORY = 200;
    private static final int DIGIT_ZERO_EXPAND = 10;

    private static final Set<SelType> EXPANDABLE =
            Set.of(
                    SelType.CHAR,
                    SelType.WORD,
                    SelType.SYMBOL,
                    SelType.LINE,
                    SelType.FIND,
                    SelType.TILL);

    public static SelRange primary(Ctx ctx) {
        return ctx.port().getSelections().get(0);
    }

    public static boolean hasSelection(SelRange sel) {
        return sel.anchor() != sel.active();
    }

    public static boolean backwardP(Ctx ctx) {
        SelRange sel = primary(ctx);
        return hasSelection(sel) && sel.active() < sel.anchor();
    }

    public static int mark(Ctx ctx) {
        SelRange sel = primary(ctx);
        return hasSelection(sel) ? sel.anchor() : sel.active();
    }

    public static void recordSelect(
            Ctx ctx, SelType type, int anchor, int active, boolean expand, int posBefore) {
        MeowState st = ctx.st();
        SavedSelection prev =
                st.lastSelection != null
                        ? st.lastSelection
                        : new SavedSelection(null, false, posBefore, posBefore);
        SavedSelection head = st.selectionHistory.peekLast();
        if (head == null || !head.equals(prev)) st.selectionHistory.addLast(prev);
        while (st.selectionHistory.size() > MAX_SELECTION_HISTORY)
            st.selectionHistory.removeFirst();
        st.lastSelection = new SavedSelection(type, expand, anchor, active);
    }

    public static void select(Ctx ctx, SelType type, int markOff, int point, boolean expand) {
        select(ctx, type, markOff, point, expand, true);
    }

    public static void select(
            Ctx ctx, SelType type, int markOff, int point, boolean expand, boolean push) {
        MeowState st = ctx.st();
        int len = ctx.port().getText().length();
        int m = Text.clamp(markOff, 0, len);
        int p = Text.clamp(point, 0, len);
        List<SelRange> sels = ctx.port().getSelections();
        if (push) recordSelect(ctx, type, m, p, expand, sels.get(0).active());
        else st.lastSelection = new SavedSelection(type, expand, m, p);
        st.selType = type;
        st.selExpand = expand;
        List<SelRange> next = new ArrayList<>(sels);
        next.set(0, new SelRange(m, p));
        ctx.port().setSelections(next);
        Grab.beacon(ctx);
        ctx.ui().showExpandHints(Hints.expandHintPositions(ctx));
    }

    public static void resetSelectionMemory(MeowState st) {
        st.selectionHistory.clear();
        st.lastSelection = null;
    }

    public static void collapse(Ctx ctx) {
        List<SelRange> sels = new ArrayList<>(ctx.port().getSelections());
        sels.set(0, new SelRange(sels.get(0).active(), sels.get(0).active()));
        ctx.port().setSelections(sels);
        ctx.st().selType = SelType.NONE;
        ctx.st().selExpand = false;
    }

    public static void cancel(Ctx ctx) {
        collapse(ctx);
        resetSelectionMemory(ctx.st());
    }

    public static void cancelAll(Ctx ctx) {
        List<SelRange> sels = ctx.port().getSelections();
        if (sels.size() > 1) ctx.port().setSelections(List.of(sels.get(0)));
        cancel(ctx);
    }

    private static void reverse(Ctx ctx) {
        SelRange sel = primary(ctx);
        if (!hasSelection(sel)) return;
        List<SelRange> sels = new ArrayList<>(ctx.port().getSelections());
        sels.set(0, new SelRange(sel.active(), sel.anchor()));
        ctx.port().setSelections(sels);
    }

    private static void pop(Ctx ctx) {
        MeowState st = ctx.st();
        if (hasSelection(primary(ctx))) {
            SavedSelection entry = st.selectionHistory.pollLast();
            if (entry == null) return;
            if (entry.type() == null) {
                List<SelRange> sels = new ArrayList<>(ctx.port().getSelections());
                sels.set(0, new SelRange(entry.active(), entry.active()));
                ctx.port().setSelections(sels);
                cancel(ctx);
                ctx.ui().hint("No previous selection");
            } else {
                select(ctx, entry.type(), entry.anchor(), entry.active(), entry.expand(), false);
            }
        } else if (!Grab.pop(ctx)) {
            ctx.ui().hint("No previous selection");
        }
    }

    private static void expandOrCount(Ctx ctx, int n) {
        MeowState st = ctx.st();
        if (hasSelection(primary(ctx)) && EXPANDABLE.contains(st.selType)) {
            expand(ctx, n == 0 ? DIGIT_ZERO_EXPAND : n);
        } else {
            st.pendingCount = st.pendingCount * 10 + n;
        }
    }

    private static void expand(Ctx ctx, int n) {
        MeowState st = ctx.st();
        String text = ctx.port().getText();
        boolean back = backwardP(ctx);
        int caret = primary(ctx).active();
        int target;
        switch (st.selType) {
            case CHAR -> target = caret + (back ? -n : n);
            case WORD, SYMBOL -> {
                Text.CharPredicate p = Text.charPred(st.selType == SelType.SYMBOL);
                target =
                        back
                                ? Text.Words.prevStart(text, caret, n, p)
                                : Text.Words.nextEnd(text, caret, n, p);
            }
            case LINE -> {
                int ln = Text.lineOfOffset(text, caret);
                target =
                        back
                                ? Text.lineStart(text, Math.max(ln - n, 0))
                                : Text.lineEnd(text, Math.min(ln + n, Text.lineCount(text) - 1));
            }
            case FIND, TILL -> {
                Character ch = st.lastFind;
                if (ch == null) return;
                int t = Text.nthCharTarget(text, ch, caret, n, back, st.selType == SelType.TILL);
                if (t < 0) return;
                target = t;
            }
            default -> {
                return;
            }
        }
        select(ctx, st.selType, mark(ctx), target, false);
    }
}
