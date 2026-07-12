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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmacsMotionSpec extends SpecDsl {
    @Test
    @DisplayName(
            "given no selection when forward-char then the caret moves right without selecting")
    void forwardCharMovesRight() {
        given("plain text", "<caret>hello");
        whenCommand("forward-char");
        thenCaretAt(1);
        thenNoSelection();
    }

    @Test
    @DisplayName(
            "given no selection when backward-char then the caret moves left without selecting")
    void backwardCharMovesLeft() {
        given("plain text", "he<caret>llo");
        whenCommand("backward-char");
        thenCaretAt(1);
        thenNoSelection();
    }

    @Test
    @DisplayName("given no selection when next-line then the caret moves down without selecting")
    void nextLineMovesDown() {
        given("two lines", "<caret>one\ntwo");
        whenCommand("next-line");
        assertEquals(1, caretLine());
        thenNoSelection();
    }

    @Test
    @DisplayName("given no selection when previous-line then the caret moves up without selecting")
    void previousLineMovesUp() {
        given("two lines", "one\nt<caret>wo");
        whenCommand("previous-line");
        assertEquals(0, caretLine());
        thenNoSelection();
    }

    @Test
    @DisplayName(
            "given no selection when move-beginning-of-line then the caret goes to column zero")
    void beginningOfLine() {
        given("indented line", "hel<caret>lo world");
        whenCommand("move-beginning-of-line");
        thenCaretAt(0);
        thenNoSelection();
    }

    @Test
    @DisplayName("given no selection when move-end-of-line then the caret goes to eol")
    void endOfLine() {
        given("plain text", "he<caret>llo");
        whenCommand("move-end-of-line");
        thenCaretAt(5);
        thenNoSelection();
    }

    @Test
    @DisplayName(
            "given no selection when forward-word then the caret lands at the end of the next word")
    void forwardWord() {
        given("comma separated", "<caret>word1, word2");
        whenCommand("forward-word");
        thenCaretAt(5);
        thenNoSelection();
    }

    @Test
    @DisplayName(
            "given no selection when backward-word then the caret lands at the start of the word")
    void backwardWord() {
        given("two words", "hello world<caret>");
        whenCommand("backward-word");
        thenCaretAt(6);
        thenNoSelection();
    }

    @Test
    @DisplayName("given no selection when forward-sentence then the caret lands past the sentence")
    void forwardSentence() {
        given("three sentences", "<caret>One. Two. Three.");
        whenCommand("forward-sentence");
        thenCaretAt(5);
        thenNoSelection();
    }

    @Test
    @DisplayName(
            "given no selection when backward-sentence then the caret lands at the sentence start")
    void backwardSentence() {
        given("three sentences", "One. Two. Thr<caret>ee.");
        whenCommand("backward-sentence");
        thenCaretAt(10);
        thenNoSelection();
    }

    @Test
    @DisplayName("given w then forward-char extends the selection one char forward")
    void wThenForwardCharExtends() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        whenCommand("forward-char");
        thenSelection("hello ");
        thenSelType(SelType.CHAR);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given w then backward-char shrinks the selection from its end")
    void wThenBackwardCharShrinks() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        whenCommand("backward-char");
        thenSelection("hell");
    }

    @Test
    @DisplayName("given w then next-line extends the selection down")
    void wThenNextLineExtends() {
        given("word then a second line", "<caret>hello\nworld");
        whenKeys("w");
        thenSelection("hello");
        whenCommand("next-line");
        thenSelection("hello\nworld");
    }

    @Test
    @DisplayName("given w then move-end-of-line extends the selection to eol")
    void wThenEndOfLineExtends() {
        given("three words", "<caret>hello brave world");
        whenKeys("w");
        thenSelection("hello");
        whenCommand("move-end-of-line");
        thenSelection("hello brave world");
    }

    @Test
    @DisplayName(
            "given caret mid-line when w then move-beginning-of-line extends the selection to bol")
    void wThenBeginningOfLineExtends() {
        given("three words", "hello <caret>brave world");
        whenKeys("w");
        thenSelection("brave");
        whenCommand("move-beginning-of-line");
        thenSelection("hello ");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given w then forward-word extends the word selection (chains with e)")
    void wThenForwardWordExtends() {
        given("three words", "<caret>one two three");
        whenKeys("w");
        thenSelection("one");
        whenCommand("forward-word");
        thenSelection("one two");
        thenSelType(SelType.WORD);
        whenKeys("e");
        thenSelection("one two three");
    }

    @Test
    @DisplayName("given w then forward-sentence extends the selection through the next sentence")
    void wThenForwardSentenceExtends() {
        given("two sentences", "<caret>One. Two.");
        whenKeys("w");
        thenSelection("One");
        whenCommand("forward-sentence");
        thenSelection("One. ");
        whenCommand("forward-sentence");
        thenSelection("One. Two.");
    }

    @Test
    @DisplayName(
            "given w then semicolon then forward-char shrinks from the start (reversed anchor)")
    void wThenReverseThenForwardCharShrinks() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        thenCaretAtSelectionEnd();
        whenKeys(";");
        thenCaretAtSelectionStart();
        whenCommand("forward-char");
        thenSelection("ello");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given w then semicolon then backward-char extends past the start")
    void wThenReverseThenBackwardCharExtends() {
        given("leading padding then two words", " <caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        whenKeys(";");
        thenCaretAtSelectionStart();
        whenCommand("backward-char");
        thenSelection(" hello");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given a reversed line selection then previous-line extends further up")
    void reversedLineThenPreviousLineExtends() {
        given("three lines", "one\ntwo\nth<caret>ree");
        whenKeys("x");
        whenKeys(";");
        thenCaretAtSelectionStart();
        whenCommand("previous-line");
        thenSelection("two\nthree");
    }

    @Test
    @DisplayName(
            "given beacon cursors when forward-char then every cursor extends its own selection")
    void beaconForwardCharExtendsEach() {
        given("repeats with identical trailing context", "<caret>foo. foo. foo.");
        whenKeys(",bG");
        givenCaretAt(0);
        whenKeys("w");
        thenCaretCount(3);
        whenCommand("forward-char");
        List<Integer> actives = new java.util.ArrayList<>();
        for (SelRange s : editor.sels) actives.add(s.active());
        java.util.Collections.sort(actives);
        assertEquals(List.of(4, 9, 14), actives);
    }
}
