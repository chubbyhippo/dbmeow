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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Meow for workbench trees — MOTION state ported to the one surface a database tool navigates
 * without a text editor (Database Navigator, project explorer, result grids, ...). Like special
 * buffers in Emacs, a tree answers to the MOTION map (the rc's `mmap` lines): the four motion
 * commands translate to the tree widget's own arrow-key vocabulary, `<action>(...)` bindings
 * dispatch against the focused tree, and every unbound key keeps its native meaning. Same
 * resolution order as the engine (user maps over bundled defaults; a noremap replay skips user
 * maps; depth-guarded). The SWT wiring (one gated key per bound char) is staged; the dispatch logic
 * is here.
 */
public final class TreeMeow {
    private TreeMeow() {}

    /**
     * The four motion commands with a native tree meaning → the tree widget's arrow-key vocabulary
     * (down/up move, collapse folds else goes to the parent, expand unfolds else enters the first
     * child — the JTree ActionMap contract the siblings pin). Values are dbmeow tree commands the
     * SWT adapter maps; every other meow command needs a text buffer, inert here.
     */
    private static final Map<String, String> LIST_MOTIONS =
            Map.of(
                    "meow-next", "dbmeow.tree.focusDown",
                    "meow-prev", "dbmeow.tree.focusUp",
                    "meow-left", "dbmeow.tree.collapse",
                    "meow-right", "dbmeow.tree.expand");

    /**
     * Every char the MOTION map binds (defaults + ~/.dbmeowrc) minus effective ignores — the tree
     * shortcut set.
     */
    public static Set<Character> boundChars() {
        Set<Character> all = new HashSet<>(Rc.defaults().motion.keySet());
        all.addAll(Rc.cfg().motion.keySet());
        Set<Character> out = new HashSet<>();
        for (char c : all) {
            Rc.Binding b = Rc.cfg().motion.get(c);
            if (b == null) b = Rc.defaults().motion.get(c);
            if (b != null && !"ignore".equals(b.command())) out.add(c);
        }
        return out;
    }

    /**
     * Resolve one key against the MOTION map and run it through [run] — the focused tree's command
     * executor.
     */
    public static void dispatch(Consumer<String> run, char c) {
        dispatch(run, c, false, 0);
    }

    /** The engine's layering + replay-depth guard, over the tree surface. */
    public static void dispatch(Consumer<String> run, char c, boolean noremap, int depth) {
        Rc.Binding b = noremap ? null : Rc.cfg().motion.get(c);
        if (b == null) b = Rc.defaults().motion.get(c);
        if (b == null) return;
        if (b.command() != null) {
            String listCommand = LIST_MOTIONS.get(b.command());
            if (listCommand != null) run.accept(listCommand);
            return;
        }
        if (b.action() != null) {
            run.accept(b.action());
            return;
        }
        if (b.keys() == null || depth >= 8) return;
        for (char k : b.keys().toCharArray()) {
            dispatch(run, k, noremap || !b.recursive(), depth + 1);
        }
    }
}
