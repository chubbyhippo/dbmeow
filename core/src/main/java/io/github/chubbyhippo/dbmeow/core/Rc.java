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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Rc {
    private Rc() {}

    public static final String FILE_NAME = ".dbmeowrc";

    public record Binding(String action, String keys, String command, boolean recursive) {}

    public static final class Config {
        public final Map<Character, Binding> normal = new HashMap<>();
        public final Map<Character, Binding> motion = new HashMap<>();
        public final Map<String, Binding> keypad = new LinkedHashMap<>();
        public final Map<String, String> keypadDesc = new HashMap<>();

        public final Map<String, Map<Character, Binding>> repeat = new LinkedHashMap<>();

        public Boolean whichKey = null;
        public Integer whichKeyDelayMs = null;
        public final List<String> errors = new ArrayList<>();
    }

    private static Config userConfig = new Config();
    private static Config defaultConfig = null;

    public static Config parse(List<String> lines) {
        return RcParser.parse(lines);
    }

    public static Config initDefaults(List<String> lines) {
        defaultConfig = parse(lines);
        return defaultConfig;
    }

    public static Config setUserLines(List<String> lines) {
        userConfig = parse(lines);
        RcFileState.saveParsed(userConfig);
        return userConfig;
    }

    public static void setForTest(Config c) {
        userConfig = c;
        RcFileState.resetForTest();
    }

    public static Config cfg() {
        return userConfig;
    }

    public static Config defaults() {
        if (defaultConfig == null) initDefaults(readBundledLines());
        return defaultConfig;
    }

    public static List<String> bundledLines() {
        return readBundledLines();
    }

    private static List<String> readBundledLines() {
        try (InputStream in = Rc.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) return List.of();
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
                return lines;
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Map<String, Binding> keypad() {
        Map<String, Binding> merged = new LinkedHashMap<>(defaults().keypad);
        merged.putAll(cfg().keypad);
        return merged;
    }

    public static Map<String, String> keypadDescs() {
        Map<String, String> merged = new HashMap<>(defaults().keypadDesc);
        merged.putAll(cfg().keypadDesc);
        return merged;
    }

    public static Map<String, Map<Character, Binding>> repeatGroups() {
        Map<String, Map<Character, Binding>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Character, Binding>> e : defaults().repeat.entrySet()) {
            merged.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        for (Map.Entry<String, Map<Character, Binding>> e : cfg().repeat.entrySet()) {
            merged.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>()).putAll(e.getValue());
        }
        for (Map<Character, Binding> members : merged.values()) {
            members.values().removeIf(b -> "ignore".equals(b.command()));
        }
        merged.values().removeIf(Map::isEmpty);
        return merged;
    }

    public static Map<Character, Binding> repeatMapFor(Binding b) {
        for (Map<Character, Binding> members : repeatGroups().values()) {
            for (Binding m : members.values()) {
                if (Objects.equals(m.action(), b.action())
                        && Objects.equals(m.command(), b.command())
                        && Objects.equals(m.keys(), b.keys())) {
                    return members;
                }
            }
        }
        return null;
    }

    public static boolean whichKeyEnabled() {
        if (cfg().whichKey != null) return cfg().whichKey;
        if (defaults().whichKey != null) return defaults().whichKey;
        return true;
    }

    private static final int DEFAULT_WHICH_KEY_DELAY_MS = 250;

    public static int whichKeyDelayMs() {
        if (cfg().whichKeyDelayMs != null) return cfg().whichKeyDelayMs;
        if (defaults().whichKeyDelayMs != null) return defaults().whichKeyDelayMs;
        return DEFAULT_WHICH_KEY_DELAY_MS;
    }
}
