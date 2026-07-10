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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Double-ESC in a tool window (ToolWindowEscape) — ideameow's ToolWindowEscapeSpec, ported name for
 * name. Platform-specific (no meow source of truth): the pinned behavior is the pure pairing state
 * machine the adapter's escape bindings feed; the re-emission of a lone first press is adapter
 * wiring, outside the core.
 */
class ToolWindowEscapeSpec {
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
}
