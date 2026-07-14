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

import io.github.chubbyhippo.dbmeow.core.EditorPort.LineRange;
import io.github.chubbyhippo.dbmeow.core.EditorPort.OffsetRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Avy {
    private Avy() {}

    private static final String KEYS = "asdfghjkl";

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("avy-goto-char-timer", Avy::startCharTimer);
        commands.put("avy-goto-line", Avy::startGotoLine);
    }

    sealed interface AvyNode permits Leaf, Branch {}

    record Leaf(int offset) implements AvyNode {}

    record Branch(List<Entry> children) implements AvyNode {}

    record Entry(char key, AvyNode child) {}

    private static final double SUBDIV_LOG_EPSILON = 1e-6;

    public static int[] subdiv(int n, int b) {
        int p = (int) Math.floor(Math.log(n) / Math.log(b) + SUBDIV_LOG_EPSILON) - 1;
        int x1 = 1;
        for (int i = 0; i < p; i++) x1 *= b;
        int x2 = b * x1;
        int delta = n - x2;
        int n2 = (int) Math.floor((double) delta / (x2 - x1));
        int n1 = b - n2 - 1;
        int[] out = new int[b];
        int idx = 0;
        for (int i = 0; i < n1; i++) out[idx++] = x1;
        out[idx++] = n - n1 * x1 - n2 * x2;
        for (int i = 0; i < n2; i++) out[idx++] = x2;
        return out;
    }

    static Branch tree(List<Integer> candidates, String keys) {
        List<Entry> children = new ArrayList<>();
        if (candidates.size() < keys.length()) {
            for (int i = 0; i < candidates.size(); i++) {
                children.add(new Entry(keys.charAt(i), new Leaf(candidates.get(i))));
            }
            return new Branch(children);
        }
        List<Integer> rest = candidates;
        int[] sizes = subdiv(candidates.size(), keys.length());
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            List<Integer> taken = new ArrayList<>(rest.subList(0, size));
            rest = new ArrayList<>(rest.subList(size, rest.size()));
            children.add(
                    new Entry(
                            keys.charAt(i),
                            size == 1 ? new Leaf(taken.get(0)) : tree(taken, keys)));
        }
        return new Branch(children);
    }

    static List<UiPort.AvyLabel> labels(Branch node) {
        List<UiPort.AvyLabel> out = new ArrayList<>();
        walk(node, "", out);
        return out;
    }

    private static void walk(AvyNode n, String path, List<UiPort.AvyLabel> out) {
        if (n instanceof Leaf leaf) {
            out.add(new UiPort.AvyLabel(leaf.offset(), path));
        } else if (n instanceof Branch b) {
            for (Entry e : b.children()) walk(e.child(), path + e.key(), out);
        }
    }

    public static final class AvySession {
        enum Phase {
            COLLECTING,
            SELECTING
        }

        Phase phase = Phase.COLLECTING;
        final StringBuilder input = new StringBuilder();
        Branch node = null;
        final boolean gotoLine;

        AvySession(boolean gotoLine) {
            this.gotoLine = gotoLine;
        }
    }

    private static void startCharTimer(Ctx ctx) {
        cancel(ctx);
        ctx.st().avy = new AvySession(false);
    }

    private static void startGotoLine(Ctx ctx) {
        cancel(ctx);
        AvySession session = new AvySession(true);
        ctx.st().avy = session;
        String text = ctx.port().getText();
        int[] fl = visibleLines(ctx);
        List<Integer> candidates = new ArrayList<>();
        for (int ln = fl[0]; ln <= fl[1]; ln++) candidates.add(Text.lineStart(text, ln));
        toSelecting(ctx, session, candidates);
    }

    public static void key(Ctx ctx, char c) {
        AvySession session = ctx.st().avy;
        if (session == null) return;
        if (session.phase == AvySession.Phase.COLLECTING) collect(ctx, session, c);
        else select(ctx, session, c);
    }

    private static void collect(Ctx ctx, AvySession session, char c) {
        session.input.append(c);
        int len = session.input.length();
        List<OffsetRange> ranges = new ArrayList<>();
        for (int start : matches(ctx, session.input.toString())) {
            ranges.add(new OffsetRange(start, start + len));
        }
        ctx.ui().showAvyMatches(ranges);
    }

    public static boolean awaitingTimeout(MeowState st) {
        return st.avy != null
                && st.avy.phase == AvySession.Phase.COLLECTING
                && st.avy.input.length() > 0;
    }

    public static void finishInput(Ctx ctx) {
        AvySession session = ctx.st().avy;
        if (session == null || session.phase != AvySession.Phase.COLLECTING) return;
        List<Integer> candidates = matches(ctx, session.input.toString());
        if (candidates.isEmpty()) {
            cancel(ctx);
            ctx.ui().hint("zero candidates");
        } else if (candidates.size() == 1) {
            cancel(ctx);
            jump(ctx, candidates.get(0));
        } else {
            toSelecting(ctx, session, candidates);
        }
    }

    private static void toSelecting(Ctx ctx, AvySession session, List<Integer> candidates) {
        ctx.ui().clearAvy();
        session.phase = AvySession.Phase.SELECTING;
        session.node = tree(candidates, KEYS);
        ctx.ui().showAvyLabels(labels(session.node));
    }

    private static void select(Ctx ctx, AvySession session, char c) {
        if (session.gotoLine && c >= '0' && c <= '9') {
            cancel(ctx);
            String input = ctx.ui().input("Goto line:", String.valueOf(c));
            if (input == null) return;
            int n;
            try {
                n = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                return;
            }
            String text = ctx.port().getText();
            int ln = Math.min(Math.max(n - 1, 0), Text.lineCount(text) - 1);
            jump(ctx, Text.lineStart(text, ln));
            return;
        }
        Branch node = session.node;
        if (node == null) return;
        AvyNode child = null;
        for (Entry e : node.children()) {
            if (e.key() == c) {
                child = e.child();
                break;
            }
        }
        if (child == null) {
            ctx.ui().hint("No such candidate: " + c);
        } else if (child instanceof Leaf leaf) {
            cancel(ctx);
            jump(ctx, leaf.offset());
        } else {
            session.node = (Branch) child;
            ctx.ui().showAvyLabels(labels((Branch) child));
        }
    }

    private static void jump(Ctx ctx, int offset) {
        SelRange sel = Selections.primary(ctx);
        if (Selections.hasSelection(sel)) {
            ctx.port().setSelections(List.of(new SelRange(Selections.mark(ctx), offset)));
        } else {
            ctx.port().setSelections(List.of(new SelRange(offset, offset)));
        }
    }

    public static void cancel(Ctx ctx) {
        if (ctx.st().avy != null) ctx.ui().clearAvy();
        ctx.st().avy = null;
    }

    private static int[] visibleLines(Ctx ctx) {
        int total = Text.lineCount(ctx.port().getText());
        LineRange vis = ctx.port().visibleLineRange();
        if (vis == null) return new int[] {0, total - 1};
        return new int[] {
            Text.clamp(vis.first(), 0, total - 1), Text.clamp(vis.last(), 0, total - 1)
        };
    }

    private static List<Integer> matches(Ctx ctx, String input) {
        if (input.isEmpty()) return List.of();
        String text = ctx.port().getText();
        int[] fl = visibleLines(ctx);
        int from = Text.lineStart(text, fl[0]);
        int to = Text.lineEnd(text, fl[1]);
        String haystack = text.toLowerCase();
        String needle = input.toLowerCase();
        List<Integer> out = new ArrayList<>();
        int i = from;
        while (i <= to - needle.length()) {
            if (haystack.startsWith(needle, i)) {
                out.add(i);
                i += needle.length();
            } else {
                i++;
            }
        }
        return out;
    }
}
