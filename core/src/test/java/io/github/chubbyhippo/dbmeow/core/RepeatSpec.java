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

class RepeatSpec extends SpecDsl {
    private static final String NAV_RC =
            """
            map <leader>tn meow-next
            repeat nav . meow-next
            repeat nav , meow-prev""";

    @Test
    @DisplayName("given repeat lines then named groups parse with their member targets")
    void repeatLinesParseGroups() {
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
    void badTargetCollectsError() {
        Rc.Config c = Rc.parse(List.of("repeat nav . meow-frobnicate", "repeat nav"));
        assertEquals(2, c.errors.size());
        assertTrue(c.errors.get(0).contains("meow-frobnicate"));
    }

    @Test
    @DisplayName("given a repeat key that is not a single printable key then an error is collected")
    void badRepeatKeyCollectsError() {
        Rc.Config c = Rc.parse(List.of("repeat nav ab meow-next", "repeat nav <Space> meow-next"));
        assertEquals(2, c.errors.size());
    }

    @Test
    @DisplayName("given home rc repeat lines then they layer per key over the bundled group")
    void homeRcLayersOverBundledGroup() {
        givenRc("repeat error , meow-prev\nrepeat error e <action>(org.eclipse.ui.file.save)");
        Map<Character, Rc.Binding> g = Rc.repeatGroups().get("error");
        assertEquals("org.eclipse.ui.navigate.next", g.get('.').action());
        assertEquals("meow-prev", g.get(',').command());
        assertEquals("org.eclipse.ui.file.save", g.get('e').action());
    }

    @Test
    @DisplayName("given a repeat member bound to ignore then the key is given back")
    void ignoreGivesMemberBack() {
        givenRc("repeat zoom o ignore");
        Map<Character, Rc.Binding> g = Rc.repeatGroups().get("zoom");
        assertFalse(g.containsKey('o'));
        assertEquals("org.eclipse.ui.edit.text.zoomIn", g.get('i').action());
    }

    @Test
    @DisplayName("the bundled default dbmeowrc declares the init el repeat groups")
    void bundledRcDeclaresRepeatGroups() {
        Map<String, Map<Character, Rc.Binding>> d = Rc.defaults().repeat;
        assertEquals("org.eclipse.ui.navigate.next", d.get("error").get('.').action());
        assertEquals("org.eclipse.ui.navigate.previous", d.get("error").get(',').action());
        assertEquals(Set.of('i', '=', 'o', '-'), d.get("zoom").keySet());
        assertNull(d.get("change"));
        assertNull(d.get("expand"));
    }

    @Test
    @DisplayName("given the bundled rc then the tab repeat group cycles editor tabs")
    void bundledRcTabGroupCyclesTabs() {
        Map<Character, Rc.Binding> g = Rc.defaults().repeat.get("tab");
        assertEquals("org.eclipse.ui.window.nextEditor", g.get('n').action());
        assertEquals("org.eclipse.ui.window.previousEditor", g.get('p').action());
        assertEquals("org.eclipse.ui.window.nextEditor", g.get('.').action());
        assertEquals("org.eclipse.ui.window.previousEditor", g.get(',').action());
        assertEquals(Set.of('n', 'p', '.', ','), g.keySet());
    }

    @Test
    @DisplayName("given a repeat line edit then the reload button sees a change")
    void repeatLineEditLightsReload() {
        Rc.setUserLines(List.of("nmap Z ,b"));
        assertFalse(RcFileState.equalTo(List.of("nmap Z ,b", "repeat nav . meow-next")));
    }

    @Test
    @DisplayName(
            "given a keypad nav entry in a repeat group then tapping the members keeps walking")
    void memberTapsKeepWalking() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        thenCaretLine(1);
        whenKeys(".");
        thenCaretLine(2);
        whenKeys(".");
        thenCaretLine(3);
        whenKeys(",");
        thenCaretLine(2);
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given a normal key bound to a member target then it arms the same run")
    void targetIdentityArmsRun() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys("j");
        thenCaretLine(1);
        whenKeys(".");
        thenCaretLine(2);
    }

    @Test
    @DisplayName("given a non-member key then the run ends and the key keeps its normal meaning")
    void nonMemberEndsRunAndFallsThrough() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(Engine.repeatMap);
        whenKeys("w");
        thenSelection("two");
        assertNull(Engine.repeatMap);
    }

    @Test
    @DisplayName("given the run over then the member keys mean their normal commands again")
    void memberKeysRestoredAfterRun() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        whenKeys("x");
        thenSelection("two");
        whenKeys(".");
        assertEquals(Pending.BOUNDS, st.pending);
        thenCaretLine(1);
    }

    @Test
    @DisplayName("given escape then the run ends")
    void escapeEndsRun() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(Engine.repeatMap);
        pressEsc();
        assertNull(Engine.repeatMap);
        whenKeys(".");
        assertEquals(Pending.BOUNDS, st.pending);
        thenCaretLine(1);
    }

    @Test
    @DisplayName("given SPC during a run then the keypad still opens")
    void keypadOpensDuringRun() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        whenKeys(" tn");
        thenCaretLine(2);
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given a digit during a run then it falls through as a count")
    void digitFallsThroughAsCount() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        thenCaretLine(1);
        whenKeys("2j");
        thenCaretLine(3);
    }

    @Test
    @DisplayName("given a run then a member tap continues after an editor switch")
    void memberTapContinuesAfterEditorSwitch() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        thenCaretLine(1);
        st = new MeowState();
        whenKeys(".");
        thenCaretLine(2);
    }

    @Test
    @DisplayName("given a run then the armed keys are the group members")
    void armedKeysAreGroupMembers() {
        given("four lines", "<caret>one\ntwo\nthree\nfour");
        givenRc(NAV_RC);
        whenKeys(" tn");
        assertNotNull(Engine.repeatMap);
        assertEquals(Set.of('.', ','), Engine.repeatMap.keySet());
        whenKeys("w");
        assertNull(Engine.repeatMap);
    }

    @Test
    @DisplayName(
            "given the bundled rc then SPC x z repeats the last command and bare z keeps repeating"
                    + " like Emacs C-x z")
    void spcXzRepeatsAndBareZContinues() {
        given("delete run", "<caret>aaaaa");
        whenKeys("d");
        thenText("aaaa");
        whenKeys(" xz");
        thenText("aaa");
        whenKeys("z");
        thenText("aa");
        whenKeys("z");
        thenText("a");
        thenMode(MeowMode.NORMAL);
    }
}
