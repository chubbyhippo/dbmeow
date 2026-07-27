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

import static io.github.chubbyhippo.dbmeow.core.Windmove.noWindowMessage;
import static io.github.chubbyhippo.dbmeow.core.Windmove.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.chubbyhippo.dbmeow.core.Windmove.Candidate;
import io.github.chubbyhippo.dbmeow.core.Windmove.Caret;
import io.github.chubbyhippo.dbmeow.core.Windmove.DiffSideView;
import io.github.chubbyhippo.dbmeow.core.Windmove.Dir;
import io.github.chubbyhippo.dbmeow.core.Windmove.Rect;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindmoveSpec extends SpecDsl {

    private static final Rect FRAME = new Rect(0, 0, 80, 25);
    private static final Rect L1 = new Rect(0, 0, 40, 12);
    private static final Rect L2 = new Rect(0, 12, 40, 6);
    private static final Rect L3 = new Rect(0, 18, 40, 6);
    private static final Rect R = new Rect(40, 0, 40, 24);

    private static List<Candidate<String>> stackedWithout(String excluded) {
        List<Candidate<String>> all =
                List.of(
                        new Candidate<>("L1", L1),
                        new Candidate<>("L2", L2),
                        new Candidate<>("L3", L3),
                        new Candidate<>("R", R));
        return all.stream().filter(candidate -> !candidate.window().equals(excluded)).toList();
    }

    @Test
    @DisplayName(
            "given a side-by-side diff then left from the modified pane crosses to the original")
    void leftFromModifiedCrosses() {
        assertEquals("dbmeow.compareSwitch", plan(Dir.LEFT, new DiffSideView(false, true, true)));
    }

    @Test
    @DisplayName(
            "given a side-by-side diff then right from the original pane crosses to the modified")
    void rightFromOriginalCrosses() {
        assertEquals("dbmeow.compareSwitch", plan(Dir.RIGHT, new DiffSideView(true, false, true)));
    }

    @Test
    @DisplayName("given the outer pane then windmove leaves the diff toward the editor")
    void outerPaneLeavesDiff() {
        assertEquals("dbmeow.focusLeftEditor", plan(Dir.LEFT, new DiffSideView(true, false, true)));
        assertEquals(
                "dbmeow.focusRightEditor", plan(Dir.RIGHT, new DiffSideView(false, true, true)));
    }

    @Test
    @DisplayName("given an inline diff then the panes are not windows")
    void inlineDiffNotWindows() {
        DiffSideView inline = new DiffSideView(false, true, false);
        assertEquals("dbmeow.focusLeftEditor", plan(Dir.LEFT, inline));
        assertEquals("dbmeow.focusRightEditor", plan(Dir.RIGHT, inline));
    }

    @Test
    @DisplayName("given up or down then it always moves between editors")
    void upDownBetweenEditors() {
        DiffSideView diff = new DiffSideView(false, true, true);
        assertEquals("dbmeow.focusAboveEditor", plan(Dir.UP, diff));
        assertEquals("dbmeow.focusBelowEditor", plan(Dir.DOWN, diff));
    }

    @Test
    @DisplayName("given no diff then windmove is the directional editor focus")
    void noDiffDirectionalFocus() {
        assertEquals("dbmeow.focusLeftEditor", plan(Dir.LEFT, null));
        assertEquals("dbmeow.focusRightEditor", plan(Dir.RIGHT, null));
        assertEquals("dbmeow.focusAboveEditor", plan(Dir.UP, null));
        assertEquals("dbmeow.focusBelowEditor", plan(Dir.DOWN, null));
    }

    @Test
    @DisplayName("given no window in the direction then the message is Emacs verbatim")
    void noWindowMessageVerbatim() {
        assertEquals("No window left from selected window", noWindowMessage(Dir.LEFT));
        assertEquals("No window down from selected window", noWindowMessage(Dir.DOWN));
    }

    @Test
    @DisplayName("given the bundled rc then SPC w hjkl dispatch windmove")
    void bundledRcWindmoveBindings() {
        Rc.Config d = Rc.defaults();
        assertEquals("dbmeow.windmoveLeft", d.keypad.get("wh").action());
        assertEquals("dbmeow.windmoveDown", d.keypad.get("wj").action());
        assertEquals("dbmeow.windmoveUp", d.keypad.get("wk").action());
        assertEquals("dbmeow.windmoveRight", d.keypad.get("wl").action());
    }

    @Test
    @DisplayName("given one two or many windows then ace-window plans self other or labels")
    void aceWindowPlansSelfOtherOrLabels() {
        assertEquals(AceWindow.Plan.NONE, AceWindow.plan(1));
        assertEquals(AceWindow.Plan.OTHER, AceWindow.plan(2));
        assertEquals(AceWindow.Plan.LABELS, AceWindow.plan(3));
        assertEquals(AceWindow.Plan.LABELS, AceWindow.plan(9));
    }

    @Test
    @DisplayName("given a stacked column when left then the window at the caret row is entered")
    void stackedColumnEntersCaretRow() {
        assertEquals("L1", Windmove.pick(Dir.LEFT, R, 1, FRAME, stackedWithout("R")));
        assertEquals("L2", Windmove.pick(Dir.LEFT, R, 14, FRAME, stackedWithout("R")));
        assertEquals("L3", Windmove.pick(Dir.LEFT, R, 23, FRAME, stackedWithout("R")));
    }

    @Test
    @DisplayName("given a visible caret then it is the reference, else the near edge plus one")
    void caretIsTheReference() {
        assertEquals(14, Windmove.reference(Dir.LEFT, R, new Caret(60, 14)));
        assertEquals(1, Windmove.reference(Dir.LEFT, R, null));
        assertEquals(1, Windmove.reference(Dir.LEFT, R, new Caret(60, 99)));
        assertEquals(60, Windmove.reference(Dir.UP, R, new Caret(60, 14)));
        assertEquals(41, Windmove.reference(Dir.UP, R, null));
    }

    @Test
    @DisplayName("given the middle of the stack then it moves in all four directions")
    void middleOfStackMovesEveryWay() {
        int caretY = 14;
        int caretX = 5;
        assertEquals("R", Windmove.pick(Dir.RIGHT, L2, caretY, FRAME, stackedWithout("L2")));
        assertEquals("L1", Windmove.pick(Dir.UP, L2, caretX, FRAME, stackedWithout("L2")));
        assertEquals("L3", Windmove.pick(Dir.DOWN, L2, caretX, FRAME, stackedWithout("L2")));
    }

    @Test
    @DisplayName("given a two by two grid then the adjacent window is picked")
    void gridPicksTheAdjacentWindow() {
        Rect a = new Rect(0, 0, 40, 12);
        Rect b = new Rect(40, 0, 40, 12);
        Rect c = new Rect(0, 12, 40, 12);
        Rect d = new Rect(40, 12, 40, 12);
        List<Candidate<String>> fromD =
                List.of(new Candidate<>("A", a), new Candidate<>("B", b), new Candidate<>("C", c));
        assertEquals("C", Windmove.pick(Dir.LEFT, d, 18, FRAME, fromD));
        assertEquals("B", Windmove.pick(Dir.UP, d, 60, FRAME, fromD));
        List<Candidate<String>> fromA =
                List.of(new Candidate<>("B", b), new Candidate<>("C", c), new Candidate<>("D", d));
        assertEquals("B", Windmove.pick(Dir.RIGHT, a, 5, FRAME, fromA));
        assertEquals("C", Windmove.pick(Dir.DOWN, a, 5, FRAME, fromA));
        assertNull(Windmove.pick(Dir.LEFT, a, 5, FRAME, fromA));
    }

    @Test
    @DisplayName("given a window covering the caret then it beats a nearer one outside the band")
    void coveringWindowBeatsNearerOutsideTheBand() {
        Rect current = new Rect(0, 100, 50, 50);
        Rect wide = new Rect(0, 0, 200, 200);
        List<Candidate<String>> candidates =
                List.of(
                        new Candidate<>("covering", new Rect(0, 20, 50, 40)),
                        new Candidate<>("nearer", new Rect(60, 95, 40, 5)));
        assertEquals("covering", Windmove.pick(Dir.UP, current, 10, wide, candidates));
    }

    @Test
    @DisplayName("given only windows outside the band then the smallest band distance wins")
    void smallestBandDistanceWins() {
        Rect current = new Rect(0, 100, 50, 50);
        Rect wide = new Rect(0, 0, 200, 200);
        List<Candidate<String>> candidates =
                List.of(
                        new Candidate<>("bandFar", new Rect(120, 50, 40, 40)),
                        new Candidate<>("bandNear", new Rect(60, 20, 40, 40)));
        assertEquals("bandNear", Windmove.pick(Dir.UP, current, 10, wide, candidates));
    }
}
