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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RcParser {
    private RcParser() {}

    private static final Pattern ACTION_RE =
            Pattern.compile("^<action>\\(([\\w.\\-$(),=]+)\\)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHICHKEY_LET_RE =
            Pattern.compile("^let\\s+g:WhichKeyDesc\\w*\\s*=\\s*\"(.+)\"$");
    private static final Pattern TRAILING_COMMENT_RE = Pattern.compile("\\s\"");

    static Rc.Config parse(List<String> lines) {
        Rc.Config c = new Rc.Config();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            final int lineNo = i + 1;
            Consumer<String> err = msg -> c.errors.add("line " + lineNo + ": " + msg);

            if (line.isEmpty() || line.startsWith("\"") || line.startsWith("#")) continue;

            Matcher wk = WHICHKEY_LET_RE.matcher(line);
            if (wk.matches()) {
                parseDescBody(c, wk.group(1), err);
                continue;
            }

            Matcher cut = TRAILING_COMMENT_RE.matcher(line);
            if (cut.find()) line = line.substring(0, cut.start()).stripTrailing();
            if (line.isEmpty()) continue;

            String[] split = line.split("\\s+", 2);
            String cmd = split[0];
            String rest = split.length > 1 ? split[1].trim() : "";
            switch (cmd) {
                case "let" -> {}
                case "cmap", "cnoremap" -> {}
                case "set" -> parseSet(c, rest);
                case "desc" -> parseDescBody(c, rest, err);
                case "map", "noremap", "nmap", "nnoremap", "mmap", "mnoremap" ->
                        parseMap(c, cmd, rest, err);
                case "repeat" -> parseRepeat(c, rest, err);
                default -> err.accept("unknown command '" + cmd + "'");
            }
        }
        return c;
    }

    private static void parseSet(Rc.Config c, String rest) {
        if (rest.equals("which-key")) {
            c.whichKey = true;
        } else if (rest.equals("nowhich-key")) {
            c.whichKey = false;
        } else if (rest.startsWith("timeoutlen")) {
            String eq = rest.contains("=") ? rest.substring(rest.indexOf('=') + 1).trim() : "";
            Integer n =
                    !eq.isEmpty()
                            ? parseIntOrNull(eq)
                            : parseIntOrNull(
                                    rest.split("\\s+").length > 1 ? rest.split("\\s+")[1] : "");
            if (n != null && n >= 0) c.whichKeyDelayMs = n;
        }
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void parseDescBody(Rc.Config c, String body, Consumer<String> err) {
        if (!body.startsWith("<leader>")) {
            err.accept("descriptions must start with <leader>: " + body);
            return;
        }
        String after = body.substring("<leader>".length());
        String seqToken = after.split("\\s", 2)[0];
        String desc = after.substring(seqToken.length()).trim();
        String seq = parseKeys(seqToken, err);
        if (seq == null) return;
        if (seq.isEmpty()) {
            err.accept("empty key sequence in description: " + body);
            return;
        }
        c.keypadDesc.put(seq, desc);
    }

    private static void parseMap(Rc.Config c, String cmd, String rest, Consumer<String> err) {
        String[] parts = rest.split("\\s+", 2);
        if (parts.length < 2 || parts[0].isEmpty()) {
            err.accept(cmd + " needs a key and a target");
            return;
        }
        String lhs = parts[0];
        String rhs = parts[1].trim();
        boolean recursive = cmd.equals("map") || cmd.equals("nmap") || cmd.equals("mmap");
        boolean motion = cmd.equals("mmap") || cmd.equals("mnoremap");

        Rc.Binding binding = parseTarget(rhs, recursive, cmd + " " + rest, err);
        if (binding == null) return;

        if (lhs.startsWith("<leader>")) {
            if (motion) {
                err.accept(cmd + " cannot define keypad entries; use map <leader>...");
                return;
            }
            String seq = parseKeys(lhs.substring("<leader>".length()), err);
            if (seq == null) return;
            if (seq.isEmpty()) {
                err.accept("<leader> alone cannot be mapped");
            } else if ("0123456789?/".indexOf(seq.charAt(0)) >= 0) {
                err.accept(
                        "keypad "
                                + seq.charAt(0)
                                + " is reserved (digit argument / cheatsheet / describe)");
            } else {
                c.keypad.put(seq, binding);
            }
            return;
        }

        String keys = parseKeys(lhs, err);
        if (keys == null) return;
        if (keys.length() != 1) {
            err.accept(
                    (motion ? "motion" : "normal")
                            + "-mode key must be a single printable key: "
                            + lhs);
        } else if (keys.equals(" ")) {
            err.accept("SPC is the keypad key and cannot be remapped");
        } else {
            (motion ? c.motion : c.normal).put(keys.charAt(0), binding);
        }
    }

    private static Rc.Binding parseTarget(
            String rhs, boolean recursive, String errContext, Consumer<String> err) {
        Matcher am = ACTION_RE.matcher(rhs);
        if (am.matches()) return new Rc.Binding(am.group(1), null, null, recursive);
        if (Registry.COMMANDS.containsKey(rhs)) return new Rc.Binding(null, null, rhs, recursive);
        if (rhs.startsWith("meow-")) {
            err.accept("unknown meow command '" + rhs + "'");
            return null;
        }
        String keys = parseKeys(rhs.replaceAll("\\s+", ""), err);
        if (keys == null) return null;
        if (keys.isEmpty()) {
            err.accept("empty target in '" + errContext + "'");
            return null;
        }
        return new Rc.Binding(null, keys, null, recursive);
    }

    private static void parseRepeat(Rc.Config c, String rest, Consumer<String> err) {
        String[] parts = rest.split("\\s+", 3);
        if (parts.length < 3) {
            err.accept("repeat needs a group, a member key and a target");
            return;
        }
        String group = parts[0];
        String keyToken = parts[1];
        String key = parseKeys(keyToken, err);
        if (key == null) return;
        if (key.length() != 1) {
            err.accept("repeat member key must be a single printable key: " + keyToken);
        } else if (key.equals(" ")) {
            err.accept("SPC is the keypad key and cannot be a repeat member");
        } else {
            Rc.Binding binding = parseTarget(parts[2].trim(), true, "repeat " + rest, err);
            if (binding == null) return;
            c.repeat.computeIfAbsent(group, k -> new LinkedHashMap<>()).put(key.charAt(0), binding);
        }
    }

    private static String parseKeys(String s, Consumer<String> err) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '<') {
                int close = s.indexOf('>', i);
                if (close < 0) {
                    out.append(ch);
                    i++;
                    continue;
                }
                String token = s.substring(i + 1, close).toLowerCase();
                if (token.equals("space")) {
                    out.append(' ');
                } else if (token.equals("lt")) {
                    out.append('<');
                } else {
                    err.accept(
                            "unsupported key token "
                                    + s.substring(i, close + 1)
                                    + " (only printable keys reach the meow engine)");
                    return null;
                }
                i = close + 1;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }
}
