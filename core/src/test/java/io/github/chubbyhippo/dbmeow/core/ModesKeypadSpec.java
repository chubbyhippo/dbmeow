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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        whenKeys("l");
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
        given("two lines", "<caret>one\ntwo");
        givenReadOnly();
        whenKeys("j");
        assertEquals(1, caretLine());
        whenKeys("kw");
        thenSelection("one");
        whenKeys("s");
        thenText("one\ntwo");
        thenSelection("one");
        whenKeys("y");
        thenClipboard("one");
        whenKeys("d");
        whenKeys("p");
        thenText("one\ntwo");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName(
            "given INSERT when the keypad action fires then a keypad command returns to INSERT")
    void keypadActionReturnsToInsert() {
        given("word", "ab<caret>cd");
        givenRc("map <leader>zz meow-left");
        whenKeys("i");
        thenMode(MeowMode.INSERT);
        Engine.enterKeypad(ctx());
        thenMode(MeowMode.KEYPAD);
        whenKeys("zz");
        thenMode(MeowMode.INSERT);
        thenCaretAt(1);
    }

    @Test
    @DisplayName("given INSERT when the keypad action then escape then back to INSERT")
    void keypadActionEscapeBackToInsert() {
        given("word", "<caret>hello");
        whenKeys("i");
        Engine.enterKeypad(ctx());
        thenMode(MeowMode.KEYPAD);
        pressEsc();
        thenMode(MeowMode.INSERT);
    }

    @Test
    @DisplayName("given NORMAL when the keypad action fires then KEYPAD round-trips to NORMAL")
    void keypadActionRoundTripsToNormal() {
        given("word", "<caret>hello");
        Engine.enterKeypad(ctx());
        thenMode(MeowMode.KEYPAD);
        pressEsc();
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
    @DisplayName("given a keypad action entry then the host command runs")
    void keypadActionEntryRunsHostCommand() {
        given("word", "<caret>hello");
        whenKeys(" xs");
        thenMode(MeowMode.NORMAL);
        assertEquals(List.of("org.eclipse.ui.file.save"), ui.ran);
    }

    @Test
    @DisplayName("given INSERT then the adapter is told to swap the cursor, and back on escape")
    void insertSwapsCursorAndBack() {
        given("word", "<caret>hello");
        whenKeys("i");
        assertEquals(List.of(MeowMode.INSERT), ui.modes);
        pressEsc();
        assertEquals(List.of(MeowMode.INSERT, MeowMode.NORMAL), ui.modes);
    }

    @Test
    @DisplayName("given the bundled defaults then SPC m exposes the M- motion and edit layer")
    void bundledDefaultsExposeMetaLayerOnSpcM() {
        Map<String, Rc.Binding> keypad = Rc.keypad();
        assertEquals("backward-sentence", keypad.get("ma").command());
        assertEquals("backward-word", keypad.get("mb").command());
        assertEquals("capitalize-word", keypad.get("mc").command());
        assertEquals("kill-word", keypad.get("md").command());
        assertEquals("forward-sentence", keypad.get("me").command());
        assertEquals("forward-word", keypad.get("mf").command());
        assertEquals("downcase-word", keypad.get("ml").command());
        assertEquals("upcase-word", keypad.get("mu").command());
        assertEquals("beginning-of-buffer", keypad.get("m<").command());
        assertEquals("end-of-buffer", keypad.get("m>").command());
        assertEquals("backward-paragraph", keypad.get("m{").command());
        assertEquals("forward-paragraph", keypad.get("m}").command());
    }

    @Test
    @DisplayName("given the SPC m keypad then a meta word motion runs and returns to NORMAL")
    void spcMMetaWordMotionRunsAndReturnsToNormal() {
        given("two words", "<caret>hello world");
        whenKeys(" mf");
        assertTrue(editor.sels.get(0).active() > 0, "caret advanced");
        thenMode(MeowMode.NORMAL);
    }
}
