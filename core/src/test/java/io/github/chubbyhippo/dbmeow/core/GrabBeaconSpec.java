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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * meow-grab, swap-grab, sync-grab, and the multi-cursor BEACON approximation.
 * A name-for-name port of codemeow's grabBeacon.test.ts. The beacon LOGIC runs
 * headless here over FakeEditor; the SWT adapter renders only the primary caret
 * (single-caret StyledText) but still applies the multi-range edit.
 */
class GrabBeaconSpec extends SpecDsl {
    @Test
    @DisplayName("given a selection when G then it becomes the grab and the selection is cancelled")
    void selectionGBecomesGrab() {
        given("word", "<caret>hello world");
        whenKeys("wG");
        thenNoSelection();
        assertNotNull(st.grab);
        assertEquals(0, st.grab.start());
        assertEquals(5, st.grab.end());
    }

    @Test
    @DisplayName("given a grab and a selection elsewhere when R then the two texts swap (meow-swap-grab)")
    void swapGrabSwapsTexts() {
        given("three words", "<caret>one two three");
        whenKeys("wG");
        givenCaretAt(8);
        whenKeys("w");
        thenSelection("three");
        whenKeys("R");
        thenText("three two one");
        thenNoSelection();
        assertEquals("three", editor.getText().substring(st.grab.start(), st.grab.end()),
                "grab now holds the swapped-in text");
    }

    @Test
    @DisplayName("given no selection when G then an existing grab is cancelled (meow 1.5.0 body)")
    void noSelectionGCancelsGrab() {
        given("word", "<caret>hello world");
        whenKeys("wG");
        assertNotNull(st.grab);
        whenKeys("G"); // no selection now: meow-grab cancels the secondary
        assertNull(st.grab);
    }

    @Test
    @DisplayName("given no grab when R then nothing changes")
    void noGrabRNothing() {
        given("word", "<caret>hello");
        whenKeys("wR");
        thenText("hello");
        thenSelection("hello");
    }

    @Test
    @DisplayName("given a selection overlapping the grab when R then the swap is refused")
    void overlappingRRefused() {
        given("three words", "<caret>one two three");
        whenKeys("weG"); // grab "one two" [0,7)
        givenCaretAt(4);
        whenKeys("fr"); // selection [4,12) overlaps the grab
        whenKeys("R");
        thenText("one two three");
    }

    @Test
    @DisplayName("given Y then the grab is re-synced to the current selection (meow-sync-grab)")
    void syncGrabResyncs() {
        given("two words", "<caret>hello world");
        whenKeys("wG"); // grab "hello"
        givenCaretAt(6);
        whenKeys("wY"); // sync to "world"
        thenNoSelection();
        assertEquals(6, st.grab.start());
        assertEquals(11, st.grab.end());
    }

    @Test
    @DisplayName("given a grab when marking a word inside it then a cursor lands on every occurrence (BEACON)")
    void beaconWordOccurrences() {
        given("repeats", "<caret>foo bar foo baz foo");
        whenKeys(",bG"); // grab the whole buffer
        givenCaretAt(0);
        whenKeys("w");
        thenCaretCount(3);
    }

    @Test
    @DisplayName("given beacon cursors when c then all occurrences change together")
    void beaconChangeTogether() {
        given("repeats", "<caret>foo bar foo baz foo");
        whenKeys(",bG");
        givenCaretAt(0);
        whenKeys("wc");
        thenText(" bar  baz ");
        thenMode(MeowMode.INSERT);
        thenCaretCount(3);
    }

    @Test
    @DisplayName("given a grab when x inside it then a cursor lands on every line (line beacon)")
    void beaconLineOccurrences() {
        given("three lines", "<caret>a\nb\nc");
        whenKeys(",bG");
        givenCaretAt(0);
        whenKeys("x");
        thenCaretCount(3);
    }

    @Test
    @DisplayName("given beacon cursors when g then they collapse to one")
    void beaconCollapseOnG() {
        given("repeats", "<caret>foo bar foo baz foo");
        whenKeys(",bG");
        givenCaretAt(0);
        whenKeys("w");
        thenCaretCount(3);
        whenKeys("g");
        thenCaretCount(1);
        thenNoSelection();
    }

    @Test
    @DisplayName("given a selection outside the grab then no beacon cursors appear")
    void noBeaconOutsideGrab() {
        given("repeats", "<caret>foo bar foo");
        whenKeys("wG"); // grab only the first foo
        givenCaretAt(8);
        whenKeys("w"); // select the last foo — outside the grab
        thenCaretCount(1);
    }
}
