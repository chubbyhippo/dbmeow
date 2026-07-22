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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RcSpec extends SpecDsl {

    @Test
    @DisplayName("given an action mapping then it parses into a normal override")
    void actionMappingParses() {
        Rc.Config c = Rc.parse(List.of("nmap S <action>(extension.aceJump)"));
        assertEquals("extension.aceJump", c.normal.get('S').action());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given a parameterized action then the whole serialized command is kept")
    void parameterizedActionKeptWhole() {
        String id =
                "org.eclipse.ui.views.showView("
                        + "org.eclipse.ui.views.showView.viewId=org.eclipse.ui.views.BookmarkView)";
        Rc.Config c = Rc.parse(List.of("map <leader>bj <action>(" + id + ")"));
        assertEquals(id, c.keypad.get("bj").action());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given comment-only rc edits then the reload button reports no changes")
    void commentOnlyEditsNoReload() {
        Rc.setUserLines(List.of("nmap Z ,b"));
        assertTrue(RcFileState.equalTo(List.of("\" just a comment", "nmap Z ,b")));
        assertFalse(RcFileState.equalTo(List.of("nmap Q meow-goto-line")));
    }

    @Test
    @DisplayName("given a key-sequence mapping then it parses as replay keys")
    void keySequenceParsesAsReplay() {
        Rc.Config c = Rc.parse(List.of("nmap Z ,b"));
        assertEquals(",b", c.normal.get('Z').keys());
        assertTrue(c.normal.get('Z').recursive());
    }

    @Test
    @DisplayName("given nnoremap then the binding is non-recursive")
    void nnoremapNonRecursive() {
        Rc.Config c = Rc.parse(List.of("nnoremap Z ,b"));
        assertFalse(c.normal.get('Z').recursive());
    }

    @Test
    @DisplayName("given a meow command name then it parses into a command binding")
    void meowCommandNameParses() {
        Rc.Config c = Rc.parse(List.of("nmap n meow-mark-word", "nmap d ignore", "nmap Z repeat"));
        assertEquals("meow-mark-word", c.normal.get('n').command());
        assertEquals("ignore", c.normal.get('d').command());
        assertEquals("repeat", c.normal.get('Z').command());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given mmap then the binding lands in the motion map")
    void mmapLandsInMotionMap() {
        Rc.Config c = Rc.parse(List.of("mmap n meow-next", "mnoremap e k"));
        assertEquals("meow-next", c.motion.get('n').command());
        assertEquals("k", c.motion.get('e').keys());
        assertFalse(c.motion.get('e').recursive());
        assertEquals(0, c.normal.size());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given an unknown meow command then an error is collected")
    void unknownCommandCollectsError() {
        Rc.Config c = Rc.parse(List.of("nmap Z meow-frobnicate"));
        assertEquals(1, c.errors.size());
        assertTrue(c.errors.get(0).contains("meow-frobnicate"));
    }

    @Test
    @DisplayName("given leader mappings and descriptions then the keypad table extends")
    void leaderMappingsExtendKeypad() {
        givenRc(
                "map <leader>gd <action>(editor.action.revealDefinition)\ndesc <leader>g goto things");
        assertEquals("editor.action.revealDefinition", Rc.cfg().keypad.get("gd").action());
        assertEquals("goto things", Rc.cfg().keypadDesc.get("g"));
        assertEquals("editor.action.revealDefinition", Rc.keypad().get("gd").action());
        assertEquals("dbmeow.editRc", Rc.keypad().get("cm").action());
    }

    @Test
    @DisplayName("given the ideavimrc WhichKeyDesc let syntax then descriptions parse")
    void whichKeyDescLetSyntaxParses() {
        Rc.Config c =
                Rc.parse(List.of("let g:WhichKeyDesc_leader_x = \"<leader>x C-x files/buffers\""));
        assertEquals("C-x files/buffers", c.keypadDesc.get("x"));
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given set lines then which-key options apply and vim options are ignored")
    void setLinesApplyWhichKeyOptions() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "set nowhich-key",
                                "set timeoutlen=400",
                                "set clipboard+=unnamedplus",
                                "let mapleader=\" \""));
        assertEquals(false, c.whichKey);
        assertEquals(400, c.whichKeyDelayMs);
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("which-key settings layer user over bundled defaults")
    void whichKeyLayersUserOverBundled() {
        assertTrue(Rc.whichKeyEnabled());
        assertEquals(300, Rc.whichKeyDelayMs());
        givenRc("set nowhich-key\nset timeoutlen=150");
        assertFalse(Rc.whichKeyEnabled());
        assertEquals(150, Rc.whichKeyDelayMs());
    }

    @Test
    @DisplayName("given a trailing comment then it is stripped from the line")
    void trailingCommentStripped() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "nmap S <action>(extension.aceJump)   \" jump anywhere",
                                "map <leader>zz ,b            \" select the buffer"));
        assertEquals("extension.aceJump", c.normal.get('S').action());
        assertEquals(",b", c.keypad.get("zz").keys());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("the bundled default dbmeowrc defines the whole keymap")
    void bundledRcDefinesWholeKeymap() {
        Rc.Config d = Rc.defaults();
        assertEquals(List.of(), d.errors, "bundled default must parse clean");
        qwerty().forEach(
                        (key, cmd) -> {
                            if (key == 'Q') return;
                            Rc.Binding b = d.normal.get(key);
                            assertNotNull(b, "bundled layout line for '" + key + "'");
                            assertEquals(cmd, b.command(), "bundled layout line for '" + key + "'");
                        });
        assertEquals("avy-goto-line", d.normal.get('Q').command());
        assertEquals("avy-goto-char-timer", d.normal.get('S').command());
        assertEquals("meow-next", d.motion.get('j').command());
        assertEquals("meow-prev", d.motion.get('k').command());
        assertEquals("dbmeow.aceClick", d.keypad.get(" ").action());
        assertEquals("dbmeow.aceWindow", d.keypad.get("ww").action());
        assertEquals("dbmeow.aceWindow", d.keypad.get("xo").action());
        assertEquals("dbmeow.aceSwapWindow", d.keypad.get("wW").action());
        assertEquals("dbmeow.editRc", d.keypad.get("cm").action());
        assertEquals("dbmeow.reloadRc", d.keypad.get("cM").action());
        assertEquals("org.eclipse.ui.navigate.backwardHistory", d.keypad.get("m,").action());
        assertTrue(d.keypad.size() > 25, "keypad table (got " + d.keypad.size() + ")");
    }

    @Test
    @DisplayName("given bad lines then errors are collected with line numbers")
    void badLinesCollectErrors() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "frobnicate everything",
                                "nmap <Space> ,b",
                                "map <leader>1 <action>(X)",
                                "nmap Q <CR>",
                                "mmap <leader>x ,b"));
        assertEquals(5, c.errors.size());
        assertTrue(c.errors.get(0).startsWith("line 1"));
    }

    @Test
    @DisplayName("given an rc key-sequence override then the key replays through the engine")
    void keySequenceOverrideReplays() {
        given("two words", "on<caret>e two");
        givenRc("nmap Z ,b");
        whenKeys("Z");
        thenSelection("one two");
    }

    @Test
    @DisplayName("given a recursive map then the RHS expands user maps")
    void recursiveMapExpandsUserMaps() {
        given("two words", "one two<caret>");
        givenRc("nmap B ,b\nnmap Y B");
        whenKeys("Y");
        thenSelection("one two");
    }

    @Test
    @DisplayName("given nnoremap then the RHS runs the bundled default instead")
    void nnoremapRunsBundledDefault() {
        given("two words", "one two<caret>");
        givenRc("nmap B ,b\nnnoremap Z B");
        whenKeys("Z");
        thenSelection("two");
    }

    @Test
    @DisplayName("given a self-referencing map then recursion is depth-limited")
    void selfReferenceDepthLimited() {
        given("plain", "<caret>hello");
        givenRc("nmap Z Z");
        whenKeys("Z");
        thenText("hello");
    }

    @Test
    @DisplayName("given an rc keypad mapping with keys then SPC seq replays them")
    void keypadMappingReplaysKeys() {
        given("two words", "on<caret>e two");
        givenRc("map <leader>k ,b");
        whenKeys(" k");
        thenSelection("one two");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given an rc keypad mapping then it overrides the bundled entry")
    void keypadMappingOverridesBundled() {
        given("two words", "on<caret>e two");
        givenRc("map <leader>bm ,b");
        whenKeys(" bm");
        thenSelection("one two");
    }

    @Test
    @DisplayName("given a layout rebinding then the key runs the meow command")
    void layoutRebindingRunsCommand() {
        given("two words", "on<caret>e two");
        givenRc("nmap n meow-mark-word");
        whenKeys("n");
        thenSelection("one");
    }

    @Test
    @DisplayName("given ignore then the key is disabled")
    void ignoreDisablesKey() {
        given("chars", "<caret>abc");
        givenRc("nmap d ignore");
        whenKeys("d");
        thenText("abc");
    }

    @Test
    @DisplayName("given a motion rebinding then MOTION-state editors use it")
    void motionRebindingApplies() {
        given("three lines", "<caret>one\ntwo\nthree");
        givenRc("mmap n meow-next");
        st.mode = MeowMode.MOTION;
        whenKeys("n");
        assertEquals(1, caretLine());
        whenKeys("j");
        assertEquals(2, caretLine());
    }

    @Test
    @DisplayName("given repeat on another key then it repeats the last command")
    void repeatRebindingRepeatsLast() {
        given("chars", "<caret>abcdef");
        givenRc("nmap Z repeat");
        whenKeys("d");
        thenText("bcdef");
        whenKeys("Z");
        thenText("cdef");
    }

    @Test
    @DisplayName("given a mapped key when quote then the mapping repeats")
    void quoteRepeatsMappedKey() {
        given("chars", "<caret>abcdef");
        givenRc("nmap Z d");
        whenKeys("Z");
        thenText("bcdef");
        whenKeys("'");
        thenText("cdef");
    }

    @Test
    @DisplayName("given keypad entries then which-key rows show terminals and groups")
    void whichKeyShowsTerminalsAndGroups() {
        givenRc("map <leader>zz <action>(workbench.action.quickOpen)\ndesc <leader>z my group");
        List<WhichKey.Row> top = WhichKey.keypadRows("");
        assertTrue(top.stream().anyMatch(r -> r.key().equals("z") && r.label().equals("my group")));
        List<WhichKey.Row> inner = WhichKey.keypadRows("z");
        assertTrue(
                inner.stream()
                        .anyMatch(
                                r ->
                                        r.key().equals("z")
                                                && r.label().equals("workbench.action.quickOpen")));
    }

    @Test
    @DisplayName("given a terminal with a description then which-key prefers it")
    void whichKeyPrefersDescription() {
        givenRc("map <leader>zz <action>(workbench.action.quickOpen)\ndesc <leader>zz open a file");
        assertTrue(
                WhichKey.keypadRows("z").stream()
                        .anyMatch(r -> r.key().equals("z") && r.label().equals("open a file")));
    }

    @Test
    @DisplayName("given the default table then the SPC SPC entry renders as SPC")
    void spcSpcRendersAsSpc() {
        assertTrue(WhichKey.keypadRows("").stream().anyMatch(r -> r.key().equals("SPC")));
    }

    private static Map<Character, String> qwerty() {
        Map<Character, String> m = new LinkedHashMap<>();
        for (int n = 0; n <= 9; n++) m.put((char) ('0' + n), "meow-expand-" + n);
        m.put('-', "meow-negative-argument");
        m.put(';', "meow-reverse");
        m.put(',', "meow-inner-of-thing");
        m.put('.', "meow-bounds-of-thing");
        m.put('[', "meow-beginning-of-thing");
        m.put(']', "meow-end-of-thing");
        m.put('<', "meow-beginning-of-thing");
        m.put('>', "meow-end-of-thing");
        m.put('a', "meow-append");
        m.put('A', "meow-open-below");
        m.put('b', "meow-back-word");
        m.put('B', "meow-back-symbol");
        m.put('c', "meow-change");
        m.put('d', "meow-delete");
        m.put('D', "meow-backward-delete");
        m.put('e', "meow-next-word");
        m.put('E', "meow-next-symbol");
        m.put('f', "meow-find");
        m.put('g', "meow-cancel-selection");
        m.put('G', "meow-grab");
        m.put('h', "meow-left");
        m.put('H', "meow-left-expand");
        m.put('i', "meow-insert");
        m.put('I', "meow-open-above");
        m.put('j', "meow-next");
        m.put('J', "meow-next-expand");
        m.put('k', "meow-prev");
        m.put('K', "meow-prev-expand");
        m.put('l', "meow-right");
        m.put('L', "meow-right-expand");
        m.put('m', "meow-join");
        m.put('n', "meow-search");
        m.put('o', "meow-block");
        m.put('O', "meow-to-block");
        m.put('p', "meow-yank");
        m.put('q', "meow-quit");
        m.put('Q', "meow-goto-line");
        m.put('r', "meow-replace");
        m.put('R', "meow-swap-grab");
        m.put('s', "meow-kill");
        m.put('t', "meow-till");
        m.put('u', "meow-undo");
        m.put('U', "meow-undo-in-selection");
        m.put('v', "meow-visit");
        m.put('w', "meow-mark-word");
        m.put('W', "meow-mark-symbol");
        m.put('x', "meow-line");
        m.put('X', "meow-goto-line");
        m.put('y', "meow-save");
        m.put('Y', "meow-sync-grab");
        m.put('z', "meow-pop-selection");
        m.put('\'', "repeat");
        return m;
    }
}
