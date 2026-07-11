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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * meow-insert/append/open, change, delete/backward-delete, kill (+ the kill-line and join
 * fallbacks), save, yank, replace, undo, repeat. The kill/save newline rules were probed against
 * meow 1.5.0 (see meow-semantics.md).
 */
class EditingSpec extends SpecDsl {
    @Test
    @DisplayName("given a selection when i then INSERT starts at the selection beginning")
    void selectionIStartsInsertAtBeginning() {
        given("word", "<caret>hello world");
        whenKeys("wi");
        thenMode(MeowMode.INSERT);
        thenCaretAt(0);
        thenNoSelection();
    }

    @Test
    @DisplayName("given a selection when a then INSERT starts at the selection end")
    void selectionAStartsInsertAtEnd() {
        given("word", "<caret>hello world");
        whenKeys("wa");
        thenMode(MeowMode.INSERT);
        thenCaretAt(5);
        thenNoSelection();
    }

    @Test
    @DisplayName("given no selection when i then INSERT starts at point (no cursor-position hack)")
    void noSelectionIStartsInsertAtPoint() {
        given("word", "he<caret>llo");
        whenKeys("i");
        thenMode(MeowMode.INSERT);
        thenCaretAt(2);
    }

    @Test
    @DisplayName("given INSERT mode then printable keys are not intercepted")
    void insertModePrintableNotIntercepted() {
        given("word", "<caret>hello");
        whenKeys("i");
        assertFalse(
                Engine.handleChar(ctx(), 'z'),
                "typed keys must reach the default handler in INSERT");
    }

    @Test
    @DisplayName("given A then a line opens below and INSERT starts")
    void capitalAOpensLineBelow() {
        given("one line", "ab<caret>cd");
        whenKeys("A");
        thenMode(MeowMode.INSERT);
        thenText("abcd\n");
        thenCaretAt(5);
    }

    @Test
    @DisplayName("given I then a line opens above and INSERT starts")
    void capitalIOpensLineAbove() {
        given("one line", "ab<caret>cd");
        whenKeys("I");
        thenMode(MeowMode.INSERT);
        thenText("\nabcd");
        thenCaretAt(0);
    }

    @Test
    @DisplayName("given a selection when c then it is killed into INSERT (meow-change)")
    void selectionCKilledIntoInsert() {
        given("word", "<caret>hello world");
        whenKeys("wc");
        thenText(" world");
        thenMode(MeowMode.INSERT);
        thenCaretAt(0);
    }

    @Test
    @DisplayName(
            "given no selection when c then the char at point is changed (meow-change-char fallback)")
    void noSelectionCChangeChar() {
        given("word", "a<caret>bc");
        whenKeys("c");
        thenText("ac");
        thenMode(MeowMode.INSERT);
    }

    @Test
    @DisplayName(
            "given the caret on a newline when c then the lines join (change-char takes any char)")
    void caretOnNewlineCJoins() {
        given("two lines", "ab<caret>\ncd");
        whenKeys("c");
        thenText("abcd");
        thenMode(MeowMode.INSERT);
    }

    @Test
    @DisplayName("given the caret at end of buffer when c then nothing happens, not even INSERT")
    void caretAtEndOfBufferCNothing() {
        given("word", "ab<caret>");
        whenKeys("c");
        thenText("ab");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given U then undo runs only with an active region (undo-in-selection is gated)")
    void capitalUUndoGatedByRegion() {
        given("word", "<caret>hello");
        whenKeys("U");
        assertEquals(0, editor.undoCount, "no region: no undo");
        whenKeys("wU");
        assertEquals(1, editor.undoCount, "with a region it undoes");
    }

    @Test
    @DisplayName("given a selection when d then it is deleted without touching the clipboard")
    void selectionDDeletedNoClipboard() {
        given("word", "<caret>hello world");
        givenClipboard("KEEP");
        whenKeys("wd");
        thenText(" world");
        thenClipboard("KEEP");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName(
            "given no selection when d then the char at point is deleted (delete-char fallback)")
    void noSelectionDDeleteChar() {
        given("word", "a<caret>bc");
        whenKeys("d");
        thenText("ac");
    }

    @Test
    @DisplayName("given D then the char before point is deleted (meow-backward-delete)")
    void capitalDBackwardDelete() {
        given("word", "ab<caret>c");
        whenKeys("D");
        thenText("ac");
        thenCaretAt(1);
    }

    @Test
    @DisplayName("given a selection when s then it is killed to the clipboard (meow-kill)")
    void selectionSKillToClipboard() {
        given("word", "<caret>hello world");
        whenKeys("ws");
        thenText(" world");
        thenClipboard("hello");
    }

    @Test
    @DisplayName("given no selection when s then kill-line takes over (meow-C-k fallback)")
    void noSelectionSKillLine() {
        given("two lines", "he<caret>llo\nworld");
        whenKeys("s");
        thenText("he\nworld");
        thenClipboard("llo");
    }

    @Test
    @DisplayName("given the caret at eol when s then the newline is killed (kill-line joins)")
    void caretAtEolSKillsNewline() {
        given("two lines", "he<caret>\nworld");
        whenKeys("s");
        thenText("heworld");
    }

    @Test
    @DisplayName(
            "given a join selection when s then the lines join with a single space (fixup-whitespace)")
    void joinSelectionSJoinsSingleSpace() {
        given("indented continuation", "one\n  t<caret>wo");
        whenKeys("ms");
        thenText("one two");
        thenCaretAt(3);
    }

    @Test
    @DisplayName("given a join before a closing bracket then no space is inserted")
    void joinBeforeClosingBracketNoSpace() {
        given("hanging paren", "f(x\n  <caret>)");
        whenKeys("ms");
        thenText("f(x)");
    }

    @Test
    @DisplayName(
            "given y then the selection is copied and cancelled (kill-ring-save deactivates the mark)")
    void yCopiesAndCancels() {
        given("word", "<caret>hello world");
        whenKeys("wy");
        thenText("hello world");
        thenClipboard("hello");
        thenNoSelection();
        thenCaretAt(5);
    }

    @Test
    @DisplayName(
            "given a line selection when y then the newline is copied and the caret lands past it")
    void lineSelectionYCopiesNewline() {
        given("two lines", "o<caret>ne\ntwo");
        whenKeys("xy");
        thenText("one\ntwo");
        thenClipboard("one\n");
        thenNoSelection();
        thenCaretAt(4);
    }

    @Test
    @DisplayName("given x x then y then both lines are copied with the trailing newline")
    void twoLinesYCopiesTrailingNewline() {
        given("three lines", "o<caret>ne\ntwo\nthree");
        whenKeys("xxy");
        thenText("one\ntwo\nthree");
        thenClipboard("one\ntwo\n");
        thenNoSelection();
        thenCaretAt(8);
    }

    @Test
    @DisplayName("given a line selection when s then the whole line goes including its newline")
    void lineSelectionSWholeLineWithNewline() {
        given("three lines", "o<caret>ne\ntwo\nthree");
        whenKeys("xs");
        thenText("two\nthree");
        thenClipboard("one\n");
        thenCaretAt(0);
    }

    @Test
    @DisplayName(
            "given a reversed line selection when s then the newline stays (backward selections kill as-is)")
    void reversedLineSelectionSNewlineStays() {
        given("three lines", "one\nt<caret>wo\nthree");
        whenKeys("x;s");
        thenText("one\n\nthree");
        thenClipboard("two");
    }

    @Test
    @DisplayName("given the last line when s then there is no newline to take")
    void lastLineSNoNewline() {
        given("two lines", "one\nt<caret>wo");
        whenKeys("xs");
        thenText("one\n");
        thenClipboard("two");
    }

    @Test
    @DisplayName(
            "given p then the clipboard is inserted at point with the caret after it (meow-yank)")
    void pYanksClipboard() {
        given("word", "<caret>hello");
        givenClipboard("XY");
        whenKeys("p");
        thenText("XYhello");
        thenCaretAt(2);
    }

    @Test
    @DisplayName(
            "given r then the selection is replaced by the clipboard which stays intact (meow-replace)")
    void rReplacesWithClipboard() {
        given("word", "<caret>hello world");
        givenClipboard("XY");
        whenKeys("wr");
        thenText("XY world");
        thenClipboard("XY");
        thenNoSelection();
    }

    @Test
    @DisplayName("given r without a selection then nothing happens")
    void rWithoutSelectionNothing() {
        given("word", "<caret>hello");
        givenClipboard("XY");
        whenKeys("r");
        thenText("hello");
    }

    @Test
    @DisplayName("given u then the selection is cancelled first (meow-undo)")
    void uCancelsSelectionFirst() {
        given("word", "<caret>hello world");
        whenKeys("wu");
        thenNoSelection();
    }

    @Test
    @DisplayName("given x x then repeated u past the undo stack then nothing blows up")
    void repeatedUPastStackNoCrash() {
        // IntelliJ's UndoAction fails a platform assertion when performed
        // while disabled; nothing like that exists here: the EditorPort.undo
        // is a plain call and an exhausted stack is a silent no-op, so every
        // press just dispatches it safely.
        given("three lines", "<caret>one\ntwo\nthree");
        whenKeys("xx");
        whenKeys("uuuuuu");
        thenText("one\ntwo\nthree");
        assertEquals(6, editor.undoCount, "undo dispatched on every press");
    }

    @Test
    @DisplayName("given quote then the last command repeats")
    void quoteRepeatsLastCommand() {
        given("chars", "<caret>abcdef");
        whenKeys("d");
        thenText("bcdef");
        whenKeys("'");
        thenText("cdef");
    }

    @Test
    @DisplayName("given quote after a two-key command then the whole unit repeats")
    void quoteAfterTwoKeyRepeatsWholeUnit() {
        given("markers", "<caret>xaxaxa");
        whenKeys("fa");
        thenSelection("xa");
        whenKeys("'");
        thenSelection("xa");
        assertEquals(
                2,
                Math.min(editor.sels.get(0).anchor(), editor.sels.get(0).active()),
                "repeat replayed f+a from the new point");
    }

    @Test
    @DisplayName("given quote after finding a quote char then the find repeats")
    void quoteAfterFindingQuoteRepeatsFind() {
        // a quote as a pending argument is part of the repeat unit; only the
        // repeat *command* is excluded from it
        given("quotes", "<caret>a'b'c");
        whenKeys("f'");
        thenSelection("a'");
        whenKeys("'");
        thenSelection("b'");
    }
}
