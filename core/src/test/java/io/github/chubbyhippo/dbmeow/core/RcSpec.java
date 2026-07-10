// Copyright (C) 2026 Chubby Hippo
// SPDX-License-Identifier: GPL-3.0-or-later
// (see LICENSE for the full GPL-3.0-or-later text)

package io.github.chubbyhippo.dbmeow.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ~/.dbmeowrc parsing, nmap/mmap/map dispatch (including relayouting the meow keys themselves), and
 * which-key rows. A name-for-name port of codemeow's rc.test.ts.
 */
@DisplayName("RcSpec")
class RcSpec extends SpecDsl {
    // ------------------------------------------------------------------ parsing

    @Test
    @DisplayName("given an action mapping then it parses into a normal override")
    void givenAnActionMappingThenItParsesIntoANormalOverride() {
        Rc.Config c = Rc.parse(List.of("nmap S <action>(extension.aceJump)"));
        assertEquals("extension.aceJump", c.normal.get('S').action());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given a parameterized action then the whole serialized command is kept")
    void givenAParameterizedActionThenTheWholeSerializedCommandIsKept() {
        String id =
                "org.eclipse.ui.views.showView("
                        + "org.eclipse.ui.views.showView.viewId=org.eclipse.ui.views.BookmarkView)";
        Rc.Config c = Rc.parse(List.of("map <leader>bj <action>(" + id + ")"));
        assertEquals(id, c.keypad.get("bj").action());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given comment-only rc edits then the reload button reports no changes")
    void givenCommentOnlyRcEditsThenTheReloadButtonReportsNoChanges() {
        // the reload surface compares the PARSED config (IdeaVim's
        // VimRcFileState hashes the parsed Script the same way) — formatting
        // and comment edits never demand a reload
        Rc.setUserLines(List.of("nmap Z ,b"));
        assertTrue(RcFileState.equalTo(List.of("\" just a comment", "nmap Z ,b")));
        assertFalse(RcFileState.equalTo(List.of("nmap Q meow-goto-line")));
    }

    @Test
    @DisplayName("given a key-sequence mapping then it parses as replay keys")
    void givenAKeySequenceMappingThenItParsesAsReplayKeys() {
        Rc.Config c = Rc.parse(List.of("nmap Z ,b"));
        assertEquals(",b", c.normal.get('Z').keys());
        assertTrue(c.normal.get('Z').recursive());
    }

    @Test
    @DisplayName("given nnoremap then the binding is non-recursive")
    void givenNnoremapThenTheBindingIsNonRecursive() {
        Rc.Config c = Rc.parse(List.of("nnoremap Z ,b"));
        assertFalse(c.normal.get('Z').recursive());
    }

    @Test
    @DisplayName("given a meow command name then it parses into a command binding")
    void givenAMeowCommandNameThenItParsesIntoACommandBinding() {
        Rc.Config c = Rc.parse(List.of("nmap n meow-mark-word", "nmap d ignore", "nmap Z repeat"));
        assertEquals("meow-mark-word", c.normal.get('n').command());
        assertEquals("ignore", c.normal.get('d').command());
        assertEquals("repeat", c.normal.get('Z').command());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given mmap then the binding lands in the motion map")
    void givenMmapThenTheBindingLandsInTheMotionMap() {
        Rc.Config c = Rc.parse(List.of("mmap n meow-next", "mnoremap e k"));
        assertEquals("meow-next", c.motion.get('n').command());
        assertEquals("k", c.motion.get('e').keys());
        assertFalse(c.motion.get('e').recursive());
        assertEquals(0, c.normal.size());
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given an unknown meow command then an error is collected")
    void givenAnUnknownMeowCommandThenAnErrorIsCollected() {
        Rc.Config c = Rc.parse(List.of("nmap Z meow-frobnicate"));
        assertEquals(1, c.errors.size());
        assertTrue(c.errors.get(0).contains("meow-frobnicate"));
    }

    @Test
    @DisplayName("given leader mappings and descriptions then the keypad table extends")
    void givenLeaderMappingsAndDescriptionsThenTheKeypadTableExtends() {
        givenRc(
                "map <leader>gd <action>(editor.action.revealDefinition)\ndesc <leader>g goto things");
        assertEquals("editor.action.revealDefinition", Rc.cfg().keypad.get("gd").action());
        assertEquals("goto things", Rc.cfg().keypadDesc.get("g"));
        assertEquals("editor.action.revealDefinition", Rc.keypad().get("gd").action());
        // codemeow additionally asserts the bundled defaults stay beneath
        // (Rc.keypad().get("b")): deferred — the bundled .dbmeowrc carries no
        // keypad table yet.
    }

    @Test
    @DisplayName("given the ideavimrc WhichKeyDesc let syntax then descriptions parse")
    void givenTheIdeavimrcWhichKeyDescLetSyntaxThenDescriptionsParse() {
        Rc.Config c =
                Rc.parse(List.of("let g:WhichKeyDesc_leader_x = \"<leader>x C-x files/buffers\""));
        assertEquals("C-x files/buffers", c.keypadDesc.get("x"));
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("given set lines then which-key options apply and vim options are ignored")
    void givenSetLinesThenWhichKeyOptionsApplyAndVimOptionsAreIgnored() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "set nowhich-key",
                                "set timeoutlen=400",
                                "set clipboard+=unnamedplus", // pasted from .ideavimrc: ignored
                                "let mapleader=\" \""));
        assertEquals(false, c.whichKey);
        assertEquals(400, c.whichKeyDelayMs);
        assertEquals(List.of(), c.errors);
    }

    @Test
    @DisplayName("which-key settings layer user over bundled defaults")
    void whichKeySettingsLayerUserOverBundledDefaults() {
        // empty user config: the bundled file's `set which-key` / timeoutlen=300
        assertTrue(Rc.whichKeyEnabled());
        assertEquals(300, Rc.whichKeyDelayMs());
        givenRc("set nowhich-key\nset timeoutlen=150");
        assertFalse(Rc.whichKeyEnabled());
        assertEquals(150, Rc.whichKeyDelayMs());
    }

    @Test
    @DisplayName("given a trailing comment then it is stripped from the line")
    void givenATrailingCommentThenItIsStrippedFromTheLine() {
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
    @DisplayName("given bad lines then errors are collected with line numbers")
    void givenBadLinesThenErrorsAreCollectedWithLineNumbers() {
        Rc.Config c =
                Rc.parse(
                        List.of(
                                "frobnicate everything", // unknown command
                                "nmap <Space> ,b", // SPC is reserved
                                "map <leader>1 <action>(X)", // keypad digits are reserved
                                "nmap Q <CR>", // unsupported key token
                                "mmap <leader>x ,b")); // keypad entries are mode-independent
        assertEquals(5, c.errors.size());
        assertTrue(c.errors.get(0).startsWith("line 1"));
    }

    // ------------------------------------------------------------------ dispatch

    @Test
    @DisplayName("given an rc key-sequence override then the key replays through the engine")
    void givenAnRcKeySequenceOverrideThenTheKeyReplaysThroughTheEngine() {
        given("two words", "on<caret>e two");
        givenRc("nmap Z ,b");
        whenKeys("Z");
        thenSelection("one two");
    }

    @Test
    @DisplayName("given a recursive map then the RHS expands user maps")
    void givenARecursiveMapThenTheRhsExpandsUserMaps() {
        given("two words", "one two<caret>");
        givenRc("nmap B ,b\nnmap Y B");
        whenKeys("Y");
        thenSelection("one two"); // Y -> user B -> whole buffer
    }

    @Test
    @DisplayName("given nnoremap then the RHS runs the bundled default instead")
    void givenNnoremapThenTheRhsRunsTheBundledDefaultInstead() {
        given("two words", "one two<caret>");
        givenRc("nmap B ,b\nnnoremap Z B");
        whenKeys("Z");
        thenSelection("two"); // bundled-default B = back-symbol, not the user map
    }

    @Test
    @DisplayName("given a self-referencing map then recursion is depth-limited")
    void givenASelfReferencingMapThenRecursionIsDepthLimited() {
        given("plain", "<caret>hello");
        givenRc("nmap Z Z");
        whenKeys("Z"); // must terminate via the depth guard
        thenText("hello");
    }

    @Test
    @DisplayName("given an rc keypad mapping with keys then SPC seq replays them")
    void givenAnRcKeypadMappingWithKeysThenSpcSeqReplaysThem() {
        given("two words", "on<caret>e two");
        givenRc("map <leader>k ,b");
        whenKeys(" k");
        thenSelection("one two");
        thenMode(MeowMode.NORMAL);
    }

    @Test
    @DisplayName("given an rc keypad mapping then it overrides the bundled entry")
    void givenAnRcKeypadMappingThenItOverridesTheBundledEntry() {
        given("two words", "on<caret>e two");
        givenRc("map <leader>bm ,b"); // overrides the bundled SPC b m (addBookmark)
        whenKeys(" bm");
        thenSelection("one two");
    }

    @Test
    @DisplayName("given a layout rebinding then the key runs the meow command")
    void givenALayoutRebindingThenTheKeyRunsTheMeowCommand() {
        given("two words", "on<caret>e two");
        givenRc("nmap n meow-mark-word"); // bundled-default n = meow-search
        whenKeys("n");
        thenSelection("one");
    }

    @Test
    @DisplayName("given ignore then the key is disabled")
    void givenIgnoreThenTheKeyIsDisabled() {
        given("chars", "<caret>abc");
        givenRc("nmap d ignore");
        whenKeys("d");
        thenText("abc");
    }

    @Test
    @DisplayName("given a motion rebinding then MOTION-state editors use it")
    void givenAMotionRebindingThenMotionStateEditorsUseIt() {
        // read-only documents stay in NORMAL these days (like Emacs read-only
        // buffers); the mmap table applies to the MOTION state proper
        given("three lines", "<caret>one\ntwo\nthree");
        givenRc("mmap n meow-next");
        st.mode = MeowMode.MOTION;
        whenKeys("n");
        assertEquals(1, caretLine());
        whenKeys("j"); // the default motion keys stay underneath
        assertEquals(2, caretLine());
    }

    @Test
    @DisplayName("given repeat on another key then it repeats the last command")
    void givenRepeatOnAnotherKeyThenItRepeatsTheLastCommand() {
        given("chars", "<caret>abcdef");
        givenRc("nmap Z repeat");
        whenKeys("d");
        thenText("bcdef");
        whenKeys("Z");
        thenText("cdef");
    }

    @Test
    @DisplayName("given a mapped key when quote then the mapping repeats")
    void givenAMappedKeyWhenQuoteThenTheMappingRepeats() {
        given("chars", "<caret>abcdef");
        givenRc("nmap Z d");
        whenKeys("Z");
        thenText("bcdef");
        whenKeys("'");
        thenText("cdef");
    }

    // ------------------------------------------------------------------ which-key

    @Test
    @DisplayName("given keypad entries then which-key rows show terminals and groups")
    void givenKeypadEntriesThenWhichKeyRowsShowTerminalsAndGroups() {
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
    void givenATerminalWithADescriptionThenWhichKeyPrefersIt() {
        givenRc("map <leader>zz <action>(workbench.action.quickOpen)\ndesc <leader>zz open a file");
        assertTrue(
                WhichKey.keypadRows("z").stream()
                        .anyMatch(r -> r.key().equals("z") && r.label().equals("open a file")));
    }

    @Test
    @DisplayName("given the default table then the SPC SPC entry renders as SPC")
    void givenTheDefaultTableThenTheSpcSpcEntryRendersAsSpc() {
        assertTrue(WhichKey.keypadRows("").stream().anyMatch(r -> r.key().equals("SPC")));
    }
}
