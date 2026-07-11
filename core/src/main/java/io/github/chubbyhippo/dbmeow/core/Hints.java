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
import java.util.LinkedHashSet;
import java.util.List;

public final class Hints {
    private Hints() {}

    private static final int EXPAND_DIGIT_COUNT = 10;

    public static List<Integer> expandHintPositions(Ctx ctx) {
        return expandHintPositions(ctx, EXPAND_DIGIT_COUNT);
    }

    public static List<Integer> expandHintPositions(Ctx ctx, int count) {
        MeowState st = ctx.st();
        String text = ctx.port().getText();
        SelRange sel = ctx.port().getSelections().get(0);
        if (sel.anchor() == sel.active()) return List.of();
        int caret = sel.active();
        boolean backward = caret < sel.anchor();
        List<Integer> out = new ArrayList<>();
        switch (st.selType) {
            case WORD, SYMBOL -> {
                Text.CharPredicate pred = Text.charPred(st.selType == SelType.SYMBOL);
                int i = caret;
                for (int k = 0; k < count; k++) {
                    i =
                            backward
                                    ? Text.Words.prevStart(text, i, 1, pred)
                                    : Text.Words.nextEnd(text, i, 1, pred);
                    if (backward ? i <= 0 : i >= text.length()) break;
                    out.add(i);
                }
            }
            case LINE -> {
                int ln = Text.lineOfOffset(text, caret);
                for (int k = 0; k < count; k++) {
                    ln += backward ? -1 : 1;
                    if (ln < 0 || ln > Text.lineCount(text) - 1) break;
                    out.add(backward ? Text.lineStart(text, ln) : Text.lineEnd(text, ln));
                }
            }
            case FIND, TILL -> {
                Character c = st.lastFind;
                if (c == null) return out;
                boolean till = st.selType == SelType.TILL;
                for (int k = 1; k <= count; k++) {
                    int t = Text.nthCharTarget(text, c, caret, k, backward, till);
                    if (t < 0) break;
                    out.add(t);
                }
            }
            default -> {}
        }
        return new ArrayList<>(new LinkedHashSet<>(out));
    }
}
