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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * State transitions: INSERT/NORMAL/MOTION/KEYPAD, escape, keypad dispatch. A name-for-name port of
 * codemeow's modesKeypad.test.ts.
 */
class ModesKeypadSpec extends SpecDsl {
    @Test
    @DisplayName("given INSERT when escape then back to NORMAL")
    void escapeExitsInsert() {
        given("word", "<caret>hello");
        whenKeys("i");
        thenMode(MeowMode.INSERT);
        pressEsc();
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given beacon cursors in NORMAL when escape then they collapse")
    void escapeCollapsesBeaconCursors() {
        given("repeats", "<caret>foo bar foo");
        whenKeys(",bG");
        givenCaretAt(0);
        whenKeys("w");
        thenCaretCount(2);
        pressEsc();
        thenCaretCount(1);
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given a pending find when escape then the pending key is dropped")
    void escapeDropsPendingFind() {
        given("word", "<caret>hello");
        whenKeys("f");
        assertNotNull(st.pending);
        pressEsc();
        assertNull(st.pending);
        whenKeys("l"); // 'l' must act as a motion again, not as the find target
        thenCaretAt(1);
    }

    @Test
    @DisplayName("given nothing meow-related when escape then it reports unhandled")
    void escapeReportsUnhandled() {
        given("word", "<caret>hello");
        assertFalse(pressEsc(), "the host may fall through to its own escape");
    }

    @Test
    @DisplayName(
            "given a read-only document then all motions work and the modify commands are inert")
    void readOnlyGatesModifyCommands() {
        // like Emacs: a read-only buffer stays in NORMAL — motions, selections
        // and save all work; only text changes gate (meow--allow-modify-p)
        given("two lines", "<caret>one\ntwo");
        givenReadOnly();
        whenKeys("j");
        assertEquals(1, caretLine());
        whenKeys("kw");
        thenSelection("one");
        whenKeys("s"); // meow-kill: gated silently — nothing at all happens
        thenText("one\ntwo");
        thenSelection("one");
        whenKeys("y"); // meow-save is a copy, not a modification: it works
        thenClipboard("one");
        whenKeys("d"); // meow-delete: Emacs' "Buffer is read-only" — inert
        whenKeys("p"); // meow-yank: same
        thenText("one\ntwo");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given SPC then KEYPAD opens and a digit becomes the count for the next command")
    void keypadDigitBecomesCount() {
        given("four lines", "<caret>a\nb\nc\nd");
        whenKeys(" ");
        thenMode(MeowMode.KEYPAD);
        whenKeys("3");
        thenMode(MeowMode.NORMAL);
        whenKeys("j");
        assertEquals(3, caretLine());
    }

    @Test
    @DisplayName("given SPC x then the keypad keeps collecting the prefix")
    void keypadCollectsPrefix() {
        given("word", "<caret>hello");
        whenKeys(" x");
        thenMode(MeowMode.KEYPAD);
        assertEquals("x", st.keypad.toString());
    }

    @Test
    @DisplayName("given an undefined keypad sequence then KEYPAD exits back to NORMAL")
    void undefinedKeypadSequenceExits() {
        // with the layout-only bundled rc the keypad table has no x prefix, so
        // the undefined-sequence exit already fires at the first key
        given("word", "<caret>hello");
        whenKeys(" x~");
        thenMode(MeowMode.NORMAL);
        thenText("hello");
    }

    @Test
    @DisplayName("given KEYPAD when escape then back to NORMAL without dispatch")
    void escapeCancelsKeypad() {
        given("word", "<caret>hello");
        whenKeys(" x");
        pressEsc();
        thenMode(MeowMode.NORMAL);
        thenText("hello");
    }

    @Test
    @DisplayName("given INSERT then the adapter is told to swap the cursor, and back on escape")
    void insertSwapsCursorAndBack() {
        // the ideameow block/bar-cursor spec, at the port seam: the adapter maps
        // these notifications to the host's bar / block cursor styles
        given("word", "<caret>hello");
        whenKeys("i");
        assertEquals(List.of(MeowMode.INSERT), ui.modes);
        pressEsc();
        assertEquals(List.of(MeowMode.INSERT, MeowMode.NORMAL), ui.modes);
    }
}
