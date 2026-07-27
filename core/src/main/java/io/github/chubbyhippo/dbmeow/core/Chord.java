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

import java.util.Locale;
import java.util.Map;

public record Chord(boolean ctrl, boolean alt, boolean shift, char key) {

    private static final Map<String, Character> PLAIN_KEYS =
            Map.ofEntries(
                    Map.entry("SPC", ' '),
                    Map.entry("SPACE", ' '),
                    Map.entry("TAB", '\t'),
                    Map.entry("COMMA", ','),
                    Map.entry("PERIOD", '.'),
                    Map.entry("SLASH", '/'),
                    Map.entry("SEMICOLON", ';'),
                    Map.entry("QUOTE", '\''),
                    Map.entry("OPEN_BRACKET", '['),
                    Map.entry("CLOSE_BRACKET", ']'),
                    Map.entry("BACK_SLASH", '\\'),
                    Map.entry("MINUS", '-'),
                    Map.entry("EQUALS", '='),
                    Map.entry("BACK_QUOTE", '`'));

    private static final Map<String, Character> SHIFTED_KEYS =
            Map.ofEntries(
                    Map.entry("COMMA", '<'),
                    Map.entry("PERIOD", '>'),
                    Map.entry("SLASH", '?'),
                    Map.entry("SEMICOLON", ':'),
                    Map.entry("QUOTE", '"'),
                    Map.entry("OPEN_BRACKET", '{'),
                    Map.entry("CLOSE_BRACKET", '}'),
                    Map.entry("BACK_SLASH", '|'),
                    Map.entry("MINUS", '_'),
                    Map.entry("EQUALS", '+'),
                    Map.entry("BACK_QUOTE", '~'),
                    Map.entry("1", '!'),
                    Map.entry("2", '@'),
                    Map.entry("3", '#'),
                    Map.entry("4", '$'),
                    Map.entry("5", '%'),
                    Map.entry("6", '^'),
                    Map.entry("7", '&'),
                    Map.entry("8", '*'),
                    Map.entry("9", '('),
                    Map.entry("0", ')'));

    public static Chord parse(String text) {
        if (text == null) return null;
        String rest = text.trim();
        if (rest.isEmpty()) return null;
        return rest.indexOf(' ') >= 0 || rest.indexOf('\t') >= 0
                ? parseHostSpelling(rest)
                : parsePrefixSpelling(rest);
    }

    private static Chord parseHostSpelling(String text) {
        String[] tokens = text.split("\\s+");
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        for (int i = 0; i < tokens.length - 1; i++) {
            switch (tokens[i].toLowerCase(Locale.ROOT)) {
                case "control", "ctrl" -> ctrl = true;
                case "alt", "meta" -> alt = true;
                case "shift" -> shift = true;
                default -> {
                    return null;
                }
            }
        }
        Character key = keyNamed(tokens[tokens.length - 1], shift);
        if (key == null || (!ctrl && !alt)) return null;
        boolean shiftedLetter = Character.isLetter(key) && shift;
        return new Chord(ctrl, alt, shiftedLetter, Character.toLowerCase(key));
    }

    private static Chord parsePrefixSpelling(String text) {
        String rest = text;
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        while (rest.length() > 2 && rest.charAt(1) == '-') {
            switch (Character.toUpperCase(rest.charAt(0))) {
                case 'C' -> ctrl = true;
                case 'M', 'A' -> alt = true;
                case 'S' -> shift = true;
                default -> {
                    return null;
                }
            }
            rest = rest.substring(2);
        }
        Character named = keyNamed(rest, shift);
        if (named == null || (!ctrl && !alt)) return null;
        char key = named;
        if (Character.isUpperCase(key)) {
            shift = true;
            key = Character.toLowerCase(key);
        }
        return new Chord(ctrl, alt, shift, key);
    }

    private static Character keyNamed(String token, boolean shift) {
        String name = token.toUpperCase(Locale.ROOT);
        if (shift) {
            Character shifted = SHIFTED_KEYS.get(name);
            if (shifted != null) return shifted;
        }
        Character plain = PLAIN_KEYS.get(name);
        if (plain != null) return plain;
        return token.length() == 1 ? token.charAt(0) : null;
    }

    public String spelling() {
        StringBuilder out = new StringBuilder();
        if (ctrl) out.append("C-");
        if (alt) out.append("M-");
        if (shift) out.append("S-");
        return out.append(key).toString();
    }
}
