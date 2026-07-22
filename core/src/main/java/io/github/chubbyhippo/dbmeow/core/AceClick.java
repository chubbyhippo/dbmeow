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

import java.util.ArrayList;
import java.util.List;

public final class AceClick {

    private static final String KEYS = "asdfghjkl";

    private AceClick() {}

    public static final class Session {
        private Avy.Branch node;

        private Session(Avy.Branch node) {
            this.node = node;
        }
    }

    public sealed interface Result permits Descend, Pick, NoMatch {}

    public record Descend() implements Result {}

    public record Pick(int index) implements Result {}

    public record NoMatch(char key) implements Result {}

    public static Session begin(int count) {
        if (count <= 0) return null;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < count; i++) indices.add(i);
        return new Session(Avy.tree(indices, KEYS));
    }

    public static List<UiPort.AvyLabel> labels(Session session) {
        return Avy.labels(session.node);
    }

    public static Result press(Session session, char key) {
        for (Avy.Entry entry : session.node.children()) {
            if (entry.key() != key) continue;
            if (entry.child() instanceof Avy.Leaf leaf) return new Pick(leaf.offset());
            session.node = (Avy.Branch) entry.child();
            return new Descend();
        }
        return new NoMatch(key);
    }
}
