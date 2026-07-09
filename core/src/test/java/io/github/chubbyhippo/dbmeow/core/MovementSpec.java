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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * meow-left/right/next/prev and the -expand variants, counts, negative arg.
 * Name-for-name port of codemeow's movement.test.ts — the h/l cross-newline
 * and j/k goal-column behaviors were batch-probed against meow 1.5.0.
 */
class MovementSpec extends SpecDsl {
    @Test
    @DisplayName("given a caret when l then it moves right without selecting")
    void lMovesRight() {
        given("plain text", "<caret>hello");
        whenKeys("l");
        thenCaretAt(1);
        thenNoSelection();
    }

    @Test
    @DisplayName("given a caret when h then it moves left")
    void hMovesLeft() {
        given("plain text", "he<caret>llo");
        whenKeys("h");
        thenCaretAt(1);
        thenNoSelection();
    }

    @Test
    @DisplayName("given two lines when j then caret moves to next line")
    void jMovesToNextLine() {
        given("two lines", "<caret>one\ntwo");
        whenKeys("j");
        thenCaretLine(1);
    }

    @Test
    @DisplayName("given a count when 2 l then caret moves two chars (digit argument)")
    void countMovesTwoChars() {
        given("plain text", "<caret>hello");
        whenKeys("2l");
        thenCaretAt(2);
        thenNoSelection();
    }

    @Test
    @DisplayName("given four lines when 3 j then caret moves three lines down")
    void countMovesThreeLinesDown() {
        given("four lines", "<caret>a\nb\nc\nd");
        whenKeys("3j");
        thenCaretLine(3);
    }

    @Test
    @DisplayName("given negative argument when - 2 j then caret moves two lines up")
    void negativeArgMovesUp() {
        given("four lines", "a\nb\nc\n<caret>d");
        whenKeys("-2j");
        thenCaretLine(1);
    }

    @Test
    @DisplayName("given no selection when H then a char selection is created leftwards")
    void shiftHCreatesCharSelection() {
        given("plain text", "hel<caret>lo");
        whenKeys("H");
        thenSelection("l");
        thenSelType(SelType.CHAR);
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given a char selection when h then the selection survives and extends (meow keeps char selections)")
    void hExtendsCharSelection() {
        given("plain text", "hel<caret>lo");
        whenKeys("Hh");
        thenSelection("el");
        thenSelType(SelType.CHAR);
    }

    @Test
    @DisplayName("given a word selection when h then the selection is cancelled (only char selections survive)")
    void hCancelsWordSelection() {
        given("plain text", "<caret>hello world");
        whenKeys("w");
        thenSelection("hello");
        whenKeys("h");
        thenNoSelection();
    }

    @Test
    @DisplayName("given L J then char selection extends right and down")
    void shiftLJExtends() {
        given("two lines", "<caret>ab\ncd");
        whenKeys("LJ");
        thenSelType(SelType.CHAR);
        assertNotNull(selectedText());
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given an undefined key in NORMAL then it is swallowed and types nothing")
    void undefinedKeySwallowed() {
        given("plain text", "<caret>hello");
        whenKeys("#%");
        thenText("hello");
    }

    @Test
    @DisplayName("given the caret at bol when h then it crosses to the previous line end")
    void hCrossesToPreviousLineEnd() {
        given("two lines", "abc\n<caret>def");
        whenKeys("h");
        thenCaretAt(3);
        thenNoSelection();
    }

    @Test
    @DisplayName("given the caret at eol when l then it crosses to the next line start")
    void lCrossesToNextLineStart() {
        given("two lines", "abc<caret>\ndef");
        whenKeys("l");
        thenCaretAt(4);
    }

    @Test
    @DisplayName("given j j through a short line then the goal column is kept")
    void goalColumnKeptThroughShortLine() {
        given("short middle line", "abcd<caret>ef\nxy\nlmnopq");
        whenKeys("j");
        thenCaretAt(9); // clamped to the short line's end
        whenKeys("j");
        thenCaretAt(14); // back out to column 4
    }

    @Test
    @DisplayName("given j on the last line then the caret moves to the end of buffer")
    void jOnLastLineGoesToBufferEnd() {
        given("two lines", "ab\nc<caret>def");
        whenKeys("j");
        thenCaretAt(7);
    }

    @Test
    @DisplayName("given k on the first line then the caret moves to the beginning of buffer")
    void kOnFirstLineGoesToBufferStart() {
        given("two lines", "a<caret>bc\ndef");
        whenKeys("k");
        thenCaretAt(0);
    }
}
