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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree surface: MOTION-map dispatch on workbench trees (TreeMeow).
 * Platform-specific — no meow source of truth for a tree widget; pinned is the
 * resolution order (user maps over defaults, noremap replays skipping user
 * maps — exactly the engine's) and the four motions' translation to the tree's
 * arrow-key vocabulary. The FakeTree below implements those four command
 * semantics over a model tree. (The static keybinding table is SWT/plugin.xml
 * wiring, staged — its manifest spec has no headless analog.)
 */
class TreeMeowSpec extends SpecDsl {
    private static final class TreeNode {
        final String name;
        final TreeNode parent;
        final List<TreeNode> children = new ArrayList<>();
        boolean expanded = false;

        TreeNode(String name, TreeNode parent) {
            this.name = name;
            this.parent = parent;
        }

        TreeNode add(String childName) {
            TreeNode child = new TreeNode(childName, this);
            children.add(child);
            return child;
        }
    }

    /** The focused tree the adapter's run callback stands in front of: the four
     *  tree commands act on the model, anything else is recorded. */
    private static final class FakeTree {
        final TreeNode root = new TreeNode("root", null);
        TreeNode focus = root;
        final List<String> ran = new ArrayList<>();

        void run(String id) {
            List<TreeNode> rows = visibleRows();
            int at = rows.indexOf(focus);
            switch (id) {
                case "dbmeow.tree.focusDown" -> focus = rows.get(Math.min(at + 1, rows.size() - 1));
                case "dbmeow.tree.focusUp" -> focus = rows.get(Math.max(at - 1, 0));
                case "dbmeow.tree.collapse" -> { // fold, else go to the parent
                    if (focus.expanded) focus.expanded = false;
                    else if (focus.parent != null) focus = focus.parent;
                }
                case "dbmeow.tree.expand" -> { // unfold, else enter the first child
                    if (!focus.children.isEmpty() && !focus.expanded) focus.expanded = true;
                    else if (!focus.children.isEmpty()) focus = focus.children.get(0);
                }
                default -> ran.add(id);
            }
        }

        private List<TreeNode> visibleRows() {
            List<TreeNode> rows = new ArrayList<>();
            walk(root, rows);
            return rows;
        }

        private void walk(TreeNode n, List<TreeNode> rows) {
            rows.add(n);
            if (n.expanded) for (TreeNode c : n.children) walk(c, rows);
        }

        void select(String name) {
            focus = find(root, name);
        }

        private TreeNode find(TreeNode n, String name) {
            if (n.name.equals(name)) return n;
            for (TreeNode c : n.children) {
                TreeNode r = find(c, name);
                if (r != null) return r;
            }
            return null;
        }

        String selectedText() {
            return focus.name;
        }

        boolean isExpanded(String name) {
            TreeNode prior = focus;
            select(name);
            boolean e = focus.expanded;
            focus = prior;
            return e;
        }
    }

    /**
     * root ├── a (├── a1 └── a2) └── b — root expanded, so rows are root, a, b.
     */
    private static FakeTree givenTree() {
        FakeTree tree = new FakeTree();
        TreeNode a = tree.root.add("a");
        a.add("a1");
        a.add("a2");
        tree.root.add("b");
        tree.root.expanded = true;
        return tree;
    }

    @Test
    @DisplayName("given the bundled rc then it binds the tree keys")
    void bundledRcBindsTreeKeys() {
        Rc.Config d = Rc.defaults();
        assertEquals("meow-next", d.motion.get('j').command());
        assertEquals("meow-prev", d.motion.get('k').command());
        assertEquals("meow-left", d.motion.get('h').command());
        assertEquals("meow-right", d.motion.get('l').command());
        assertEquals("dbmeow.hideView", d.motion.get('q').action());
    }

    @Test
    @DisplayName("given a tree when j and k then the selection moves like the arrow keys")
    void jAndKMoveSelection() {
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'j');
        assertEquals("a", tree.selectedText());
        TreeMeow.dispatch(tree::run, 'j');
        assertEquals("b", tree.selectedText());
        TreeMeow.dispatch(tree::run, 'k');
        assertEquals("a", tree.selectedText());
    }

    @Test
    @DisplayName("given a collapsed node when l then it expands, and l again enters it")
    void lExpandsThenEnters() {
        FakeTree tree = givenTree();
        tree.select("a"); // collapsed
        TreeMeow.dispatch(tree::run, 'l');
        assertTrue(tree.isExpanded("a"), "l on a collapsed node expands it");
        assertEquals("a", tree.selectedText());
        TreeMeow.dispatch(tree::run, 'l');
        assertEquals("a1", tree.selectedText());
    }

    @Test
    @DisplayName("given an expanded node when h then it collapses, then goes to the parent")
    void hCollapsesThenParent() {
        FakeTree tree = givenTree();
        tree.select("a");
        tree.focus.expanded = true; // open "a"
        tree.select("a1");
        TreeMeow.dispatch(tree::run, 'h');
        assertEquals("a", tree.selectedText());
        TreeMeow.dispatch(tree::run, 'h');
        assertFalse(tree.isExpanded("a"), "h on an expanded node collapses it");
        assertEquals("a", tree.selectedText());
        TreeMeow.dispatch(tree::run, 'h');
        assertEquals("root", tree.selectedText());
    }

    @Test
    @DisplayName("given an editor-only command in the mmap then it is inert on trees")
    void editorOnlyCommandInert() {
        givenRc("mmap w meow-next-word");
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'w');
        assertEquals("root", tree.selectedText(), "a word motion has no tree meaning");
        assertTrue(tree.ran.isEmpty());
    }

    @Test
    @DisplayName("given a user mmap override then it shadows the bundled defaults")
    void userMmapOverrideShadows() {
        givenRc("mmap j ignore");
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'j');
        assertEquals("root", tree.selectedText());
    }

    @Test
    @DisplayName("given a keys mapping then the replay resolves every key through the motion map")
    void keysReplayResolvesEachKey() {
        givenRc("mmap g jj");
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'g');
        assertEquals("b", tree.selectedText());
    }

    @Test
    @DisplayName("given a noremap replay then it skips user maps like the engine")
    void noremapReplaySkipsUserMaps() {
        givenRc("mnoremap g jj\nmmap j ignore");
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'j');
        assertEquals("root", tree.selectedText(), "a user-shadowed j is inert");
        TreeMeow.dispatch(tree::run, 'g');
        assertEquals("b", tree.selectedText(), "the replay resolves j via the defaults");
    }

    @Test
    @DisplayName("given an <action> mmap then it dispatches with the tree as context")
    void actionMmapDispatches() {
        givenRc("mmap z <action>(dbmeow.test.probe)");
        FakeTree tree = givenTree();
        TreeMeow.dispatch(tree::run, 'z');
        assertEquals(List.of("dbmeow.test.probe"), tree.ran);
    }

    @Test
    @DisplayName("given defaults and user maps then boundChars merges them")
    void boundCharsMerges() {
        givenRc("mmap w meow-next-word");
        var bound = TreeMeow.boundChars();
        for (char c : "jkhlqw".toCharArray()) assertTrue(bound.contains(c), "'" + c + "' must be bound");
        assertFalse(bound.contains('z'), "unmapped letters stay native (type-to-find)");
    }

    @Test
    @DisplayName("given mmap q ignore then the key returns to the tree")
    void mmapQIgnoreReturnsToTree() {
        givenRc("mmap q ignore");
        assertFalse(TreeMeow.boundChars().contains('q'), "an ignored key leaves the shortcut set");
        assertTrue(TreeMeow.boundChars().contains('j'), "the other defaults stay");
    }
}
