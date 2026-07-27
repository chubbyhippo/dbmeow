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

import static io.github.chubbyhippo.dbmeow.core.ToolWindowEscape.TIMEOUT_MS;
import static io.github.chubbyhippo.dbmeow.core.ToolWindowEscape.onEscape;
import static io.github.chubbyhippo.dbmeow.core.ToolWindowEscape.reset;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolWindowEscapeSpec extends SpecDsl {

    private static final String NAV_RC =
            """
            map <leader>tn meow-next
            repeat nav . meow-next
            repeat nav , meow-prev""";

    @BeforeEach
    void resetState() {
        reset();
    }

    @Test
    @DisplayName("given a single escape in a tool window then it does not jump")
    void singleEscapeNoJump() {
        assertFalse(onEscape("terminal", 1_000));
    }

    @Test
    @DisplayName("given a second escape in the same tool window within the timeout then it jumps")
    void secondEscapeSameWindowJumps() {
        onEscape("terminal", 1_000);
        assertTrue(onEscape("terminal", 1_000 + TIMEOUT_MS));
    }

    @Test
    @DisplayName("given a completed jump then the next escape starts a new pair")
    void completedJumpStartsNewPair() {
        onEscape("terminal", 1_000);
        assertTrue(onEscape("terminal", 1_100));
        assertFalse(onEscape("terminal", 1_200));
    }

    @Test
    @DisplayName("given escapes slower than the timeout then they do not pair but re-arm")
    void slowerThanTimeoutReArms() {
        onEscape("terminal", 1_000);
        assertFalse(onEscape("terminal", 1_001 + TIMEOUT_MS));
        assertTrue(onEscape("terminal", 1_200 + TIMEOUT_MS));
    }

    @Test
    @DisplayName("given escapes in different tool windows then they do not pair")
    void differentWindowsNoPair() {
        onEscape("terminal", 1_000);
        assertFalse(onEscape("list", 1_100));
        assertTrue(onEscape("list", 1_200));
    }

    @Test
    @DisplayName("given focus outside any tool window then the pair breaks")
    void focusOutsideBreaksPair() {
        onEscape("terminal", 1_000);
        assertFalse(onEscape(null, 1_100));
        assertFalse(onEscape("terminal", 1_200));
    }

    @Test
    @DisplayName("given INSERT then escape is meow's and returns to NORMAL")
    void insertEscapeIsMeows() {
        given("insert escape", "<caret>hello");
        whenKeys("i");
        thenMode(MeowMode.INSERT);
        assertTrue(pressEsc());
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given KEYPAD then escape is meow's and exits the keypad")
    void keypadEscapeIsMeows() {
        given("keypad escape", "<caret>hello");
        whenKeys(" ");
        thenMode(MeowMode.KEYPAD);
        assertTrue(pressEsc());
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given an active selection then escape is meow's and clears it")
    void selectionEscapeIsMeows() {
        given("selection escape", "<caret>hello world");
        whenKeys("w");
        assertNotNull(selectedText());
        assertTrue(pressEsc());
        assertNull(selectedText());
    }

    @Test
    @DisplayName("given an armed repeat run then escape is meow's and ends it")
    void repeatRunEscapeIsMeows() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(Engine.repeatMap);
        assertTrue(pressEsc());
        assertNull(Engine.repeatMap);
    }

    @Test
    @DisplayName("given NORMAL with nothing to cancel then escape is not meow's")
    void idleEscapeIsNotMeows() {
        given("idle escape", "<caret>hello");
        assertFalse(pressEsc());
    }
}
