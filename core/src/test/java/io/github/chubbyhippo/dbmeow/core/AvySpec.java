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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvySpec extends SpecDsl {
    private void timeout() {
        Avy.finishInput(ctx());
    }

    @Test
    @DisplayName("given S with input matching many places then labels select the jump target")
    void sInputManyPlacesLabelsSelect() {
        given("repeats", "<caret>foo bar foo baz foo");
        whenKeys("S");
        whenKeys("fo");
        timeout();
        whenKeys("s");
        thenCaretAt(8);
        assertNull(st.avy, "session ends after the jump");
    }

    @Test
    @DisplayName("given a single candidate then avy jumps immediately (avy-single-candidate-jump)")
    void singleCandidateJumpsImmediately() {
        given("words", "<caret>alpha beta gamma");
        whenKeys("S");
        whenKeys("gam");
        timeout();
        thenCaretAt(11);
        assertNull(st.avy);
    }

    @Test
    @DisplayName("given no candidates then the session ends where it started")
    void noCandidatesEndsWhereStarted() {
        given("words", "<caret>alpha beta");
        whenKeys("S");
        whenKeys("zz");
        timeout();
        thenCaretAt(0);
        assertNull(st.avy);
        whenKeys("l");
        thenCaretAt(1);
    }

    @Test
    @DisplayName("given matching is case-insensitive (avy-case-fold-search)")
    void matchingCaseInsensitive() {
        given("mixed case", "<caret>Foo bar fOO");
        whenKeys("S");
        whenKeys("foo");
        timeout();
        whenKeys("s");
        thenCaretAt(8);
    }

    @Test
    @DisplayName("given an active selection then the avy jump extends it (avy-action-goto)")
    void activeSelectionJumpExtends() {
        given("words", "<caret>hello world again");
        whenKeys("w");
        whenKeys("S");
        whenKeys("aga");
        timeout();
        thenSelection("hello world ");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given a bad selection key then avy stays active (avy-handler-default)")
    void badSelectionKeyStaysActive() {
        given("repeats", "<caret>xx xx xx");
        whenKeys("S");
        whenKeys("xx");
        timeout();
        whenKeys("z");
        assertNotNull(st.avy);
        whenKeys("d");
        thenCaretAt(6);
    }

    @Test
    @DisplayName(
            "given more candidates than keys then leading keys stay single and the last key hosts a subtree")
    void moreCandidatesThanKeysSubtree() {
        given("ten es", "<caret>e e e e e e e e e e");
        whenKeys("S");
        whenKeys("e");
        timeout();
        whenKeys("l");
        assertNotNull(st.avy);
        whenKeys("s");
        thenCaretAt(18);
    }

    @Test
    @DisplayName("given escape during an avy session then it cancels in place")
    void escapeCancelsInPlace() {
        given("words", "<caret>foo foo foo");
        whenKeys("S");
        whenKeys("foo");
        timeout();
        assertNotNull(st.avy);
        assertTrue(pressEsc());
        assertNull(st.avy);
        thenCaretAt(0);
    }

    @Test
    @DisplayName("given Q then visible lines are labeled and a key jumps to that line")
    void qLabelsVisibleLinesJump() {
        given("four lines", "one\ntwo\nthr<caret>ee\nfour");
        whenKeys("Q");
        assertNotNull(st.avy);
        whenKeys("f");
        thenCaretAt(14);
        assertNull(st.avy);
    }

    @Test
    @DisplayName("given Q then a digit switches to the goto-line number prompt")
    void qDigitSwitchesToNumberPrompt() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenMinibufferAnswers("3");
        whenKeys("Q3");
        thenCaretAt(8);
        assertNull(st.avy);
    }

    @Test
    @DisplayName("the avy-subdiv distribution matches avy 0-5-0")
    void avySubdivDistribution() {
        assertArrayEquals(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1}, Avy.subdiv(9, 9));
        assertArrayEquals(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 2}, Avy.subdiv(10, 9));
        assertArrayEquals(new int[] {1, 1, 1, 1, 9, 9, 9, 9, 9}, Avy.subdiv(49, 9));
        assertArrayEquals(new int[] {9, 9, 9, 9, 9, 9, 9, 9, 9}, Avy.subdiv(81, 9));
    }
}
