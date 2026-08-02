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
        MeowState state = ctx.state();
        String text = ctx.port().getText();
        SelRange sel = ctx.port().getSelections().get(0);
        if (sel.anchor() == sel.active()) return List.of();
        int caret = sel.active();
        boolean backward = caret < sel.anchor();
        List<Integer> out = new ArrayList<>();
        switch (state.selType) {
            case WORD, SYMBOL -> {
                Text.CharPredicate isWord = Text.charPred(state.selType == SelType.SYMBOL);
                int offset = caret;
                for (int step = 0; step < count; step++) {
                    offset =
                            backward
                                    ? Text.Words.prevStart(text, offset, 1, isWord)
                                    : Text.Words.nextEnd(text, offset, 1, isWord);
                    if (backward ? offset <= 0 : offset >= text.length()) break;
                    out.add(offset);
                }
            }
            case LINE -> {
                int line = Text.lineOfOffset(text, caret);
                for (int step = 0; step < count; step++) {
                    line += backward ? -1 : 1;
                    if (line < 0 || line > Text.lineCount(text) - 1) break;
                    out.add(backward ? Text.lineStart(text, line) : Text.lineEnd(text, line));
                }
            }
            case FIND, TILL -> {
                Character findChar = state.lastFind;
                if (findChar == null) return out;
                boolean till = state.selType == SelType.TILL;
                for (int nth = 1; nth <= count; nth++) {
                    int target = Text.nthCharTarget(text, findChar, caret, nth, backward, till);
                    if (target < 0) break;
                    out.add(target);
                }
            }
            default -> {}
        }
        return new ArrayList<>(new LinkedHashSet<>(out));
    }
}
