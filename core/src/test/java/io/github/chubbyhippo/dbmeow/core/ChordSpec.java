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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChordSpec extends SpecDsl {

    @Test
    @DisplayName("given the host spelling then it normalizes to the same chord as the Emacs one")
    void hostSpellingNormalizes() {
        assertEquals(Chord.parse("C-f"), Chord.parse("control F"));
        assertEquals(Chord.parse("M-b"), Chord.parse("alt B"));
        assertEquals(Chord.parse("C-M-x"), Chord.parse("control alt X"));
        assertFalse(Chord.parse("control F").shift());
        assertTrue(Chord.parse("C-F").shift());
    }

    @Test
    @DisplayName("given SPC or TAB as the key name then the chord parses like Emacs writes it")
    void namedKeysParse() {
        assertEquals(new Chord(false, true, false, ' '), Chord.parse("M-SPC"));
        assertEquals(Chord.parse("M-SPC"), Chord.parse("alt SPACE"));
        assertEquals(new Chord(true, false, false, '\t'), Chord.parse("C-TAB"));
        assertNull(Chord.parse("SPC"));
    }

    @Test
    @DisplayName("given a cmap line then it parses into a chord binding")
    void cmapParsesIntoChordBinding() {
        Rc.Config c = Rc.parse(List.of("cmap control F forward-char"));
        assertEquals(List.of(), c.errors);
        Rc.Binding binding = c.chords.get(new Chord(true, false, false, 'f'));
        assertNotNull(binding);
        assertEquals("forward-char", binding.target());
    }

    @Test
    @DisplayName("given a cmap with no modifier or a bad keystroke then errors are collected")
    void badChordsCollectErrors() {
        Rc.Config c = Rc.parse(List.of("cmap kj forward-char", "cmap control forward-char"));
        assertEquals(2, c.errors.size());
        assertTrue(c.errors.get(0).contains("not a chord"));
        assertTrue(c.errors.get(1).contains("not a chord"));
        assertTrue(c.chords.isEmpty());
    }

    @Test
    @DisplayName("given a pressed chord event then bindingFor resolves it and plain keys do not")
    void bindingForResolvesChordsOnly() {
        givenRc("cmap C-f forward-char");
        assertNotNull(Chords.bindingFor(Chord.parse("C-f")));
        assertNull(Chords.bindingFor(Chord.parse("f")));
        assertNull(Chords.bindingFor(null));
    }

    @Test
    @DisplayName("given shift alone then it is not a chord but Ctrl and Alt-Shift are")
    void shiftAloneIsNotAChord() {
        assertNull(Chord.parse("S-f"));
        assertNull(Chord.parse("shift F"));
        assertNotNull(Chord.parse("C-f"));
        assertNotNull(Chord.parse("alt shift E"));
        assertTrue(Chord.parse("alt shift E").shift());
    }

    @Test
    @DisplayName(
            "given NORMAL or MOTION then a mapped chord is claimed but INSERT and KEYPAD are not")
    void claimsInNormalAndMotionOnly() {
        givenRc("cmap C-f forward-char");
        assertTrue(Chords.claims(MeowMode.NORMAL, Chord.parse("C-f")));
        assertTrue(Chords.claims(MeowMode.MOTION, Chord.parse("C-f")));
        assertFalse(Chords.claims(MeowMode.INSERT, Chord.parse("C-f")));
        assertFalse(Chords.claims(MeowMode.KEYPAD, Chord.parse("C-f")));
        assertFalse(Chords.claims(MeowMode.NORMAL, Chord.parse("C-q")));
    }

    @Test
    @DisplayName("given an unmapped chord then it is handed back rather than swallowed")
    void unmappedChordPassesThrough() {
        given("plain text", "<caret>hello");
        givenRc("");
        assertFalse(Chords.dispatch(ctx(), Chord.parse("C-q")));
        thenCaretAt(0);
    }

    @Test
    @DisplayName("given both spellings of a punctuation chord then they collapse to one binding")
    void punctuationSpellingsCollapse() {
        assertEquals(Chord.parse("M-<"), Chord.parse("alt shift COMMA"));
        assertEquals(Chord.parse("M->"), Chord.parse("alt shift PERIOD"));
        assertEquals(Chord.parse("M-{"), Chord.parse("alt shift OPEN_BRACKET"));
        assertEquals(Chord.parse("C-/"), Chord.parse("control SLASH"));
        assertEquals(Chord.parse("C-_"), Chord.parse("control shift MINUS"));
        assertEquals(Chord.parse("M-^"), Chord.parse("alt shift 6"));
    }
}
