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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The repeat transient — Emacs repeat-mode, ported (init.el's transient repeat maps, repeat.el read
 * from Emacs 30.2 source). Rc `repeat` groups make multi-key entries tap-to-continue: dispatching
 * any binding whose TARGET is a group member arms the group (target identity, like the repeat-map
 * symbol property; the entering key needn't be a member — repeat-check-key 'no), then member keys
 * re-dispatch their targets and any other key or ESC ends the run and keeps its normal meaning
 * (set-transient-map fall-through — never swallowed, no timeout).
 */
@DisplayName("RepeatSpec")
class RepeatSpec extends SpecDsl {
    /**
     * A keypad nav entry plus a repeat group over the same targets; the members deliberately sit on
     * `.`/`,` — meow's bounds/inner-of-thing — to pin that a live run shadows them and a finished
     * run gives them back.
     */
    private static final String NAV_RC =
            """
            map <leader>tn meow-next
            repeat nav . meow-next
            repeat nav , meow-prev""";

    // ------------------------------------------------------------- parsing

    @Test
    @DisplayName("given repeat lines then named groups parse with their member targets")
    void givenRepeatLinesThenNamedGroupsParseWithTheirMemberTargets() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "repeat nav . meow-next",
                                "repeat nav , meow-prev",
                                "repeat zoom i <action>(org.eclipse.ui.edit.text.zoomIn)"));
        assertEquals("meow-next", c.repeat.get("nav").get('.').command());
        assertEquals("meow-prev", c.repeat.get("nav").get(',').command());
        assertEquals("org.eclipse.ui.edit.text.zoomIn", c.repeat.get("zoom").get('i').action());
        assertTrue(c.errors.isEmpty());
    }

    @Test
    @DisplayName("given a repeat line with a bad target then an error is collected")
    void givenARepeatLineWithABadTargetThenAnErrorIsCollected() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "repeat nav . meow-frobnicate", // misspelled command
                                "repeat nav")); // group and key but no target
        assertEquals(2, c.errors.size());
        assertTrue(c.errors.get(0).contains("meow-frobnicate"));
    }

    @Test
    @DisplayName("given a repeat key that is not a single printable key then an error is collected")
    void givenARepeatKeyThatIsNotASinglePrintableKeyThenAnErrorIsCollected() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "repeat nav ab meow-next", // two keys
                                "repeat nav <Space> meow-next")); // SPC is the keypad key
        assertEquals(2, c.errors.size());
    }

    @Test
    @DisplayName("given home rc repeat lines then they layer per key over the bundled group")
    void givenHomeRcRepeatLinesThenTheyLayerPerKeyOverTheBundledGroup() {
        givenRc("repeat error , meow-prev\nrepeat error e <action>(org.eclipse.ui.file.save)");
        Map<Character, Rc.Binding> g = Rc.repeatGroups().get("error");
        // bundled default beneath
        assertEquals("org.eclipse.ui.navigate.next", g.get('.').action());
        assertEquals("meow-prev", g.get(',').command()); // the user override
        assertEquals("org.eclipse.ui.file.save", g.get('e').action()); // the extension
    }

    @Test
    @DisplayName("given a repeat member bound to ignore then the key is given back")
    void givenARepeatMemberBoundToIgnoreThenTheKeyIsGivenBack() {
        givenRc("repeat zoom o ignore");
        Map<Character, Rc.Binding> g = Rc.repeatGroups().get("zoom");
        assertFalse(g.containsKey('o'));
        assertEquals("org.eclipse.ui.edit.text.zoomIn", g.get('i').action()); // the rest stays
    }

    @Test
    @DisplayName("the bundled default dbmeowrc declares the init el repeat groups")
    void theBundledDefaultDbmeowrcDeclaresTheInitElRepeatGroups() {
        // init.el parity within verified Eclipse ids: flymake -> error
        // (annotation nav), text-scale -> zoom (no reset id exists); the
        // siblings' change/expand groups have no DBeaver-SQL analog
        Map<String, Map<Character, Rc.Binding>> d = Rc.defaults().repeat;
        assertEquals("org.eclipse.ui.navigate.next", d.get("error").get('.').action());
        assertEquals("org.eclipse.ui.navigate.previous", d.get("error").get(',').action());
        assertEquals(Set.of('i', '=', 'o', '-'), d.get("zoom").keySet());
        assertNull(d.get("change"));
        assertNull(d.get("expand"));
    }

    @Test
    @DisplayName("given a repeat line edit then the reload button sees a change")
    void givenARepeatLineEditThenTheReloadButtonSeesAChange() {
        // the reload surface compares the PARSED config — repeat groups are
        // part of it, so editing one must light it up
        Rc.setUserLines(List.of("nmap Z ,b"));
        assertFalse(RcFileState.equalTo(List.of("nmap Z ,b", "repeat nav . meow-next")));
    }

    // ------------------------------------------------------------ dispatch

    @Test
    @DisplayName(
            "given a keypad nav entry in a repeat group then tapping the members keeps walking")
    void givenAKeypadNavEntryInARepeatGroupThenTappingTheMembersKeepsWalking() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn"); // SPC t n -> meow-next, arms the nav group
        thenCaretLine(1);
        whenKeys("."); // member: re-dispatches meow-next, re-arms
        thenCaretLine(2);
        whenKeys(".");
        thenCaretLine(3);
        whenKeys(","); // the other member walks back
        thenCaretLine(2);
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given a normal key bound to a member target then it arms the same run")
    void givenANormalKeyBoundToAMemberTargetThenItArmsTheSameRun() {
        // membership is the TARGET, not the key that ran it — Emacs puts
        // repeat-map on the command symbol, so every binding of it arms
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys("j"); // bundled-default j = meow-next, a nav member by identity
        thenCaretLine(1);
        whenKeys(".");
        thenCaretLine(2);
    }

    @Test
    @DisplayName("given a non-member key then the run ends and the key keeps its normal meaning")
    void givenANonMemberKeyThenTheRunEndsAndTheKeyKeepsItsNormalMeaning() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(st.repeatMap);
        whenKeys("w"); // not a member: falls through to meow-mark-word
        thenSelection("two");
        assertNull(st.repeatMap);
    }

    @Test
    @DisplayName("given the run over then the member keys mean their normal commands again")
    void givenTheRunOverThenTheMemberKeysMeanTheirNormalCommandsAgain() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        whenKeys("x"); // ends the run (meow-line)
        thenSelection("two");
        whenKeys("."); // meow-bounds-of-thing again, waiting for its thing key
        assertEquals(Pending.BOUNDS, st.pending);
        thenCaretLine(1); // and no nav happened
    }

    @Test
    @DisplayName("given escape then the run ends")
    void givenEscapeThenTheRunEnds() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(st.repeatMap);
        pressEsc();
        assertNull(st.repeatMap);
        whenKeys(".");
        assertEquals(Pending.BOUNDS, st.pending);
        thenCaretLine(1);
    }

    @Test
    @DisplayName("given SPC during a run then the keypad still opens")
    void givenSpcDuringARunThenTheKeypadStillOpens() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        whenKeys(" tn"); // SPC is not a member: run ends, keypad works as ever
        thenCaretLine(2);
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given a digit during a run then it falls through as a count")
    void givenADigitDuringARunThenItFallsThroughAsACount() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        thenCaretLine(1);
        whenKeys("2j"); // 2 ends the run and counts the next command
        thenCaretLine(3);
    }

    @Test
    @DisplayName("given a run then the armed keys are the group members")
    void givenARunThenTheArmedKeysAreTheGroupMembers() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(st.repeatMap);
        assertEquals(Set.of('.', ','), st.repeatMap.keySet());
        whenKeys("w");
        assertNull(st.repeatMap);
    }
}
