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

import io.github.chubbyhippo.dbmeow.core.Windmove.DiffSideView;
import io.github.chubbyhippo.dbmeow.core.Windmove.Dir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindmoveSpec extends SpecDsl {
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
}
