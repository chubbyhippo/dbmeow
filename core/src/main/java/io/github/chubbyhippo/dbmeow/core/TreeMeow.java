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

public final class TreeMeow {
    private TreeMeow() {}

    private static final Map<String, String> LIST_MOTIONS =
            Map.of(
                    "meow-next", "dbmeow.tree.focusDown",
                    "meow-prev", "dbmeow.tree.focusUp",
                    "meow-left", "dbmeow.tree.collapse",
                    "meow-right", "dbmeow.tree.expand");

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

    public static void dispatch(Consumer<String> run, char c) {
        dispatch(run, c, false, 0);
    }

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
