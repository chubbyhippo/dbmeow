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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelectionSpec extends SpecDsl {
    @Test
    @DisplayName("given caret on a word when w then the word is marked and caret sits at its end")
    void markWord() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        thenSelType(SelType.WORD);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given caret between words when w then the next word is marked")
    void markWordBetween() {
        given("gap between words", "hello <caret> world");
        whenKeys("w");
        thenSelection("world");
    }

    @Test
    @DisplayName("given a symbol with underscore when W then the whole symbol is marked")
    void markSymbol() {
        given("snake case", "<caret>foo_bar baz");
        whenKeys("W");
        thenSelection("foo_bar");
        thenSelType(SelType.SYMBOL);
    }

    @Test
    @DisplayName("given w then W distinction - w stops at underscore boundary chars")
    void wordStopsAtUnderscore() {
        given("snake case", "<caret>foo_bar baz");
        whenKeys("w");
        thenSelection("foo");
    }

    @Test
    @DisplayName("given a bare e when pressed twice then it steps word by word (non-expandable)")
    void bareEStepsWords() {
        given("three words", "<caret>one two three");
        whenKeys("e");
        thenSelection("one");
        whenKeys("e");
        thenSelection("two");
    }

    @Test
    @DisplayName(
            "given words separated by punctuation when e e e then each selection is one bare word")
    void eeeBareWords() {
        given("comma separated", "<caret>word1, word2 word3");
        whenKeys("ee");
        thenSelection("word2");
        whenKeys("e");
        thenSelection("word3");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given b b b from the end then each selection is one bare word")
    void bbbBareWords() {
        given("comma separated", "word1, word2 word3<caret>");
        whenKeys("b");
        thenSelection("word3");
        thenCaretAtSelectionStart();
        whenKeys("bb");
        thenSelection("word1");
    }

    @Test
    @DisplayName("given e then b then the same word is re-selected backward")
    void ebReselectsBackward() {
        given("comma separated", "<caret>word1, word2 word3");
        whenKeys("eb");
        thenSelection("word1");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given a selection of another type when e then the history restarts at the cancel")
    void typeMismatchRestartsHistory() {
        given("two lines", "<caret>hello world\nnext line");
        whenKeys("x");
        thenSelection("hello world");
        whenKeys("e");
        thenSelection("next");
        whenKeys("z");
        thenNoSelection();
        thenCaretAt(11);
    }

    @Test
    @DisplayName("given w first when e then the word selection extends (meow expand-word rule)")
    void wThenEExtends() {
        given("three words", "<caret>one two three");
        whenKeys("we");
        thenSelection("one two");
        whenKeys("e");
        thenSelection("one two three");
    }

    @Test
    @DisplayName("given w then b extends the selection backward anchored at the word end")
    void wThenBExtendsBackward() {
        given("three words", "one t<caret>wo three");
        whenKeys("w");
        thenSelection("two");
        whenKeys("b");
        thenSelection("one two");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given w b then e re-normalizes forward and extends to the right")
    void wbThenERenormalizes() {
        given("three words", "one t<caret>wo three");
        whenKeys("wbe");
        thenSelection("one two three");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given W then B extends the symbol selection backward")
    void shiftWThenB() {
        given("symbols", "foo_a bar_b<caret> baz_c");
        whenKeys("W");
        thenSelection("bar_b");
        whenKeys("B");
        thenSelection("foo_a bar_b");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given caret at end when b then selects back to word beginning")
    void bAtEnd() {
        given("two words", "hello world<caret>");
        whenKeys("b");
        thenSelection("world");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given negative argument when - e then selects backward like b")
    void negativeELikeB() {
        given("two words", "hello<caret> world");
        whenKeys("-e");
        thenSelection("hello");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given E and B then symbol variants honor underscores")
    void symbolVariants() {
        given("snake case", "<caret>foo_bar baz");
        whenKeys("E");
        thenSelection("foo_bar");
        thenSelType(SelType.SYMBOL);
    }

    @Test
    @DisplayName("given x then the current line is selected without the newline")
    void lineWithoutNewline() {
        given("two lines", "li<caret>ne one\nline two");
        whenKeys("x");
        thenSelection("line one");
        thenSelType(SelType.LINE);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given a line selection when x again then it extends one line down")
    void lineExtendsDown() {
        given("three lines", "<caret>one\ntwo\nthree");
        whenKeys("xx");
        thenSelection("one\ntwo");
    }

    @Test
    @DisplayName("given a reversed line selection when x then it extends upward")
    void reversedLineExtendsUp() {
        given("three lines", "one\ntwo\nth<caret>ree");
        whenKeys("x;x");
        thenSelection("two\nthree");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName(
            "given a selection then expand hints overlay the text without inserting inline content")
    void expandHintsOverlay() {
        given("three words", "<caret>hello world again");
        whenKeys("w");
        assertTrue(ui.expandHints.size() > 0, "hint positions computed");
        assertEquals(11, ui.expandHints.get(0));
        whenKeys("g");
        assertEquals(0, ui.expandHints.size());
    }

    @Test
    @DisplayName(
            "given a find selection when the target char sits at the caret then the first hint marks it")
    void findHintAtCaret() {
        given("chars", "<caret>aXX");
        whenKeys("fX");
        assertEquals(List.of(3), ui.expandHints);
    }

    @Test
    @DisplayName("given digits after w then the selection expands by that many words")
    void digitsExpandWords() {
        given("five words", "<caret>one two three four five");
        whenKeys("w2");
        thenSelection("one two three");
    }

    @Test
    @DisplayName("given 0 after a word mark then the selection expands by ten units")
    void zeroExpandsTen() {
        given("twelve words", "<caret>a b c d e f g h i j k l");
        whenKeys("w0");
        thenSelection("a b c d e f g h i j k");
    }

    @Test
    @DisplayName("given digits after x then the selection expands by lines")
    void digitsExpandLines() {
        given("three lines", "<caret>one\ntwo\nthree");
        whenKeys("x2");
        thenSelection("one\ntwo\nthree");
    }

    @Test
    @DisplayName("given a reversed selection when digit then it expands backward")
    void reversedDigitExpandsBackward() {
        given("three lines", "one\ntwo\nthr<caret>ee");
        whenKeys("x;1");
        thenSelection("two\nthree");
    }

    @Test
    @DisplayName("given semicolon then point and mark swap (meow-reverse)")
    void semicolonReverses() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenCaretAtSelectionEnd();
        whenKeys(";");
        thenSelection("hello");
        thenCaretAtSelectionStart();
        whenKeys(";");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given goto line via minibuffer then that line is selected (meow-goto-line expands line selection)")
    void gotoLineViaMinibuffer() {
        given("three lines", "<caret>one\ntwo\nthree");
        givenMinibufferAnswers("2");
        whenKeys("X");
        thenSelection("two");
        thenSelType(SelType.LINE);
    }

    @Test
    @DisplayName("given Q then goto-line as well (QWERTY binds both Q and X)")
    void qGotoLine() {
        given("three lines", "<caret>one\ntwo\nthree");
        givenRc("nmap Q meow-goto-line");
        givenMinibufferAnswers("3");
        whenKeys("Q");
        thenSelection("three");
    }

    @Test
    @DisplayName(
            "given a selection history when z then the previous selection is restored with its type")
    void zRestoresPreviousSelection() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        whenKeys("x");
        whenKeys("z");
        thenSelection("hello");
        thenSelType(SelType.WORD);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given w then z then the caret returns to where the chain started (null placeholder)")
    void zNullPlaceholder() {
        given("two words", "he<caret>llo world");
        whenKeys("w");
        thenSelection("hello");
        whenKeys("z");
        thenNoSelection();
        thenCaretAt(2);
    }

    @Test
    @DisplayName("given g then the selection history is cleared (meow--cancel-selection)")
    void gClearsHistory() {
        given("two words", "<caret>hello world");
        whenKeys("wxg");
        whenKeys("z");
        thenNoSelection();
    }

    @Test
    @DisplayName("given a digit expand then the selection is demoted to select type")
    void digitExpandDemotes() {
        given("five words", "<caret>one two three four five");
        whenKeys("w2");
        thenSelection("one two three");
        whenKeys("e");
        thenSelection("four");
    }

    @Test
    @DisplayName("given x 2 then x re-selects the current line instead of extending")
    void digitDemotedLineReselects() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        whenKeys("x2");
        thenSelection("one\ntwo\nthree");
        whenKeys("x");
        thenSelection("three");
    }

    @Test
    @DisplayName(
            "given no history but a grab when z then the grab becomes the selection (meow-pop-grab fallback)")
    void popGrabFallback() {
        given("two words", "<caret>hello world");
        whenKeys("wG");
        st.selectionHistory.clear();
        whenKeys("z");
        thenSelection("hello");
    }

    @Test
    @DisplayName("given g then the selection is cancelled")
    void gCancels() {
        given("two words", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        whenKeys("g");
        thenNoSelection();
    }
}
