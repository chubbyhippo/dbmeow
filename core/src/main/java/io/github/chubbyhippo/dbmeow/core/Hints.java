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

/**
 * meow's expand hints: after an expandable selection (word/symbol/line/
 * find/till), digit labels mark where 1-9 and 0 (=10) would take the
 * selection. The core computes the offsets; the adapter renders them as
 * decorations and removes them after meow-expand-hint-remove-delay (1 s)
 * or on the next key, whichever comes first.
 */
public final class Hints {
    private Hints() {
    }

    public static List<Integer> expandHintPositions(Ctx ctx) {
        return expandHintPositions(ctx, 10);
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
                    i = backward
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
                int i = caret;
                for (int k = 0; k < count; k++) {
                    int next = backward
                            ? Text.lastIndexOfChar(text, c, i - 1)
                            : Text.indexOfChar(text, c, i + 1);
                    if (next < 0) break;
                    out.add(st.selType == SelType.TILL
                            ? next
                            : Math.min(next + 1, text.length()));
                    i = next;
                }
            }
            default -> {
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(out));
    }
}
