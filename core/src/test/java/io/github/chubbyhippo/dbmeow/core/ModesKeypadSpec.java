// Copyright (C) 2026 Chubby Hippo
// SPDX-License-Identifier: GPL-3.0-or-later
// (see LICENSE for the full GPL-3.0-or-later text)

package io.github.chubbyhippo.dbmeow.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * State transitions: INSERT/NORMAL/MOTION/KEYPAD, escape, keypad dispatch.
 * A name-for-name port of codemeow's modesKeypad.test.ts.
 */
@DisplayName("ModesKeypadSpec")
class ModesKeypadSpec extends SpecDsl {
    @Test
    @DisplayName("given INSERT when escape then back to NORMAL")
    void givenInsertWhenEscapeThenBackToNormal() {
        given("word", "<caret>hello");
        whenKeys("i");
        thenMode(MeowMode.INSERT);
        pressEsc();
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given beacon cursors in NORMAL when escape then they collapse")
    void givenBeaconCursorsInNormalWhenEscapeThenTheyCollapse() {
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
    void givenAPendingFindWhenEscapeThenThePendingKeyIsDropped() {
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
    void givenNothingMeowRelatedWhenEscapeThenItReportsUnhandled() {
        given("word", "<caret>hello");
        assertFalse(pressEsc(), "the host may fall through to its own escape");
    }

    @Test
    @DisplayName("given a read-only document then all motions work and the modify commands are inert")
    void givenAReadOnlyDocumentThenAllMotionsWorkAndTheModifyCommandsAreInert() {
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
    void givenSpcThenKeypadOpensAndADigitBecomesTheCountForTheNextCommand() {
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
    void givenSpcXThenTheKeypadKeepsCollectingThePrefix() {
        given("word", "<caret>hello");
        whenKeys(" x");
        thenMode(MeowMode.KEYPAD);
        assertEquals("x", st.keypad.toString());
    }

    @Test
    @DisplayName("given an undefined keypad sequence then KEYPAD exits back to NORMAL")
    void givenAnUndefinedKeypadSequenceThenKeypadExitsBackToNormal() {
        // with the layout-only bundled rc the keypad table has no x prefix, so
        // the undefined-sequence exit already fires at the first key
        given("word", "<caret>hello");
        whenKeys(" x~");
        thenMode(MeowMode.NORMAL);
        thenText("hello");
    }

    @Test
    @DisplayName("given KEYPAD when escape then back to NORMAL without dispatch")
    void givenKeypadWhenEscapeThenBackToNormalWithoutDispatch() {
        given("word", "<caret>hello");
        whenKeys(" x");
        pressEsc();
        thenMode(MeowMode.NORMAL);
        thenText("hello");
    }

    @Test
    @DisplayName("given INSERT then the adapter is told to swap the cursor, and back on escape")
    void givenInsertThenTheAdapterIsToldToSwapTheCursorAndBackOnEscape() {
        // the ideameow block/bar-cursor spec, at the port seam: the adapter maps
        // these notifications to the host's bar / block cursor styles
        given("word", "<caret>hello");
        whenKeys("i");
        assertEquals(List.of(MeowMode.INSERT), ui.modes);
        pressEsc();
        assertEquals(List.of(MeowMode.INSERT, MeowMode.NORMAL), ui.modes);
    }
}
