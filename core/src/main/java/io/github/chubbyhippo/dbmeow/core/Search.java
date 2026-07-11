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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Search {
    private Search() {}

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-search", Search::search);
        commands.put("meow-visit", Search::visit);
    }

    private static final int MAX_SEARCH_HISTORY = 50;

    public static void push(MeowState st, String pattern) {
        st.searchHistory.removeIf(p -> p.equals(pattern));
        st.searchHistory.add(pattern);
        while (st.searchHistory.size() > MAX_SEARCH_HISTORY) st.searchHistory.remove(0);
    }

    private record Match(int start, int end) {}

    private static boolean fullyMatches(String pattern, String s) {
        try {
            return Pattern.compile("^(?:" + pattern + ")$").matcher(s).matches();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<Match> allMatches(String text, String pattern) {
        Pattern re;
        try {
            re = Pattern.compile(pattern);
        } catch (RuntimeException e) {
            re = Pattern.compile(Pattern.quote(pattern));
        }
        List<Match> out = new ArrayList<>();
        Matcher m = re.matcher(text);
        int from = 0;
        while (from <= text.length() && m.find(from)) {
            if (m.end() == m.start()) {
                from = m.end() + 1;
                continue;
            }
            out.add(new Match(m.start(), m.end()));
            from = m.end();
        }
        return out;
    }

    private static void search(Ctx ctx) {
        MeowState st = ctx.st();
        SelRange sel = Selections.primary(ctx);
        String pattern =
                st.searchHistory.isEmpty()
                        ? null
                        : st.searchHistory.get(st.searchHistory.size() - 1);
        if (Selections.hasSelection(sel)) {
            String selText =
                    ctx.port()
                            .getText()
                            .substring(
                                    Math.min(sel.anchor(), sel.active()),
                                    Math.max(sel.anchor(), sel.active()));
            if (!selText.isEmpty() && (pattern == null || !fullyMatches(pattern, selText))) {
                pattern = Text.escapeRegExp(selText);
                push(st, pattern);
            }
        }
        if (pattern == null) {
            ctx.ui().hint("No search pattern");
            return;
        }
        searchWith(ctx, pattern, st.takeCount(1) < 0 || Selections.backwardP(ctx));
    }

    private static void visit(Ctx ctx) {
        boolean backward = ctx.st().takeCount(1) < 0;
        String input = ctx.ui().input("Visit (regexp):");
        if (input == null || input.isEmpty()) return;
        String pattern = input;
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException e) {
            pattern = Text.escapeRegExp(input);
        }
        push(ctx.st(), pattern);
        searchWith(ctx, pattern, backward);
    }

    private static void searchWith(Ctx ctx, String pattern, boolean backward) {
        int caret = Selections.primary(ctx).active();
        List<Match> matches = allMatches(ctx.port().getText(), pattern);
        Match m = null;
        if (!backward) {
            for (Match x : matches) {
                if (x.start() >= caret) {
                    m = x;
                    break;
                }
            }
            if (m == null && !matches.isEmpty()) m = matches.get(0);
        } else {
            for (Match x : matches) {
                if (x.end() <= caret) m = x;
            }
            if (m == null && !matches.isEmpty()) m = matches.get(matches.size() - 1);
        }
        if (m == null) {
            ctx.ui().hint("No match: " + pattern);
            return;
        }
        if (!backward) {
            Selections.select(ctx, SelType.VISIT, m.start(), m.end(), false);
        } else {
            Selections.select(ctx, SelType.VISIT, m.end(), m.start(), false);
        }
    }
}
