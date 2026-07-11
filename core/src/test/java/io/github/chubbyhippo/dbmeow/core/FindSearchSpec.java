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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * meow-find, meow-till (+expand), meow-visit, meow-search. The find-replaces-word and search-wrap
 * behaviors were probed against meow 1.5.0 (see meow-semantics.md).
 */
class FindSearchSpec extends SpecDsl {
    private int selMin() {
        SelRange s = editor.sels.get(0);
        return Math.min(s.anchor(), s.active());
    }

    @Test
    @DisplayName("given f X then selects from point through the char inclusive")
    void fXSelectsThroughInclusive() {
        given("marker text", "<caret>abcXdef");
        whenKeys("fX");
        thenSelection("abcX");
        thenSelType(SelType.FIND);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given t X then selects up to but excluding the char")
    void tXSelectsExcluding() {
        given("marker text", "<caret>abcXdef");
        whenKeys("tX");
        thenSelection("abc");
        thenSelType(SelType.TILL);
    }

    @Test
    @DisplayName(
            "given w then f X then a fresh find selection runs from the word end through the char")
    void wThenFXFreshFind() {
        given("comma separated", "w<caret>ord1, word2 word3");
        whenKeys("w");
        thenSelection("word1");
        whenKeys("f3");
        // probed (meow 1.5.0): find REPLACES the word selection with
        // (select . find) from the old point — region [6,19)
        thenSelection(", word2 word3");
        thenSelType(SelType.FIND);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given w then t X then the till selection stops before the char")
    void wThenTXTillStops() {
        given("comma separated", "w<caret>ord1, word2 word3");
        whenKeys("wt3");
        thenSelection(", word2 word"); // probed: region [6,18)
        thenSelType(SelType.TILL);
    }

    @Test
    @DisplayName("given a count when 2 f a then the second occurrence is reached")
    void countTwoFA() {
        given("repeating", "<caret>xaxaxa");
        whenKeys("2fa");
        thenSelection("xaxa");
    }

    @Test
    @DisplayName("given a find selection when digit then it expands to the next occurrence")
    void findDigitExpands() {
        given("repeating", "<caret>xaxaxa");
        whenKeys("fa1");
        thenSelection("xaxa");
        whenKeys("1");
        thenSelection("xaxaxa");
    }

    @Test
    @DisplayName("given the char is absent when f then nothing changes")
    void findAbsentCharNoChange() {
        given("plain", "<caret>hello");
        whenKeys("fZ");
        thenNoSelection();
        thenCaretAt(0);
    }

    @Test
    @DisplayName("given negative argument when - f then finds backward")
    void negativeFindsBackward() {
        given("repeating", "xabc<caret>def");
        whenKeys("-fa");
        thenSelection("abc");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given w then n repeats the pushed word search forward (meow-search)")
    void wThenNSearchForward() {
        given("repeats", "<caret>foo bar foo baz foo");
        whenKeys("w"); // marks foo, pushes \bfoo\b
        whenKeys("n");
        thenSelection("foo");
        assertEquals(8, selMin());
    }

    @Test
    @DisplayName("given repeated n then the search wraps at the end of the buffer")
    void repeatedNWraps() {
        given("repeats", "<caret>foo bar foo");
        whenKeys("wnn"); // second n has no match ahead -> wraps to the first foo
        assertEquals(0, selMin());
        thenSelection("foo");
    }

    @Test
    @DisplayName("given a reversed selection when n then the search goes backward")
    void reversedNSearchBackward() {
        given("repeats", "foo bar <caret>foo bar foo");
        whenKeys("w"); // marks middle foo
        whenKeys(";"); // reverse: direction now backward
        whenKeys("n");
        assertEquals(0, selMin());
        thenSelection("foo");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName(
            "given a selection that does not match the pattern when n then the selection text becomes the pattern")
    void nonMatchingSelectionBecomesPattern() {
        given("repeats", "foo <caret>bar foo bar");
        st.searchHistory.add("zzz");
        whenKeys(",e"); // transient symbol selection "bar" — doesn't match zzz
        whenKeys("n"); // meow-search adopts the region as the new pattern
        thenSelection("bar");
        assertEquals(12, selMin());
    }

    @Test
    @DisplayName("given no pattern and no selection when n then nothing is selected")
    void nNoPatternNothing() {
        given("plain", "<caret>hello");
        whenKeys("n");
        thenNoSelection();
    }

    @Test
    @DisplayName("given visit with minibuffer input then the first match after point is selected")
    void visitFirstMatchAfterPoint() {
        given("repeats", "<caret>alpha beta gamma beta");
        givenMinibufferAnswers("beta");
        whenKeys("v");
        thenSelection("beta");
        assertEquals(6, selMin());
        thenSelType(SelType.VISIT);
    }

    @Test
    @DisplayName("given visit then n continues to the next match")
    void visitThenNContinues() {
        given("repeats", "<caret>alpha beta gamma beta");
        givenMinibufferAnswers("beta");
        whenKeys("vn");
        assertEquals(17, selMin());
    }
}
