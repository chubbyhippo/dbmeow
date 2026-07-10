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

/**
 * The two rc layers and their layering rules. Like meow in Emacs, the engine
 * binds NO keys — the whole keymap (NORMAL/MOTION layout AND the SPC keypad
 * table) is rc lines. The repo's .dbmeowrc ships on the classpath as the
 * DEFAULTS layer; an optional ~/.dbmeowrc overrides it entry by entry, and
 * `nnoremap`/`mnoremap` replays resolve through the defaults alone. Syntax
 * lives in {@link RcParser}. The core stays IO-free beyond the bundled
 * resource: the host adapter (or the test suite) reads the user file and
 * feeds the lines in.
 */
public final class Rc {
    private Rc() {
    }

    public static final String FILE_NAME = ".dbmeowrc";

    /** One key's target: a host command, replayed keys, or a named meow command. */
    public record Binding(String action, String keys, String command, boolean recursive) {
    }

    /** Everything one rc file declares. */
    public static final class Config {
        public final Map<Character, Binding> normal = new HashMap<>();
        public final Map<Character, Binding> motion = new HashMap<>();
        public final Map<String, Binding> keypad = new LinkedHashMap<>();
        public final Map<String, String> keypadDesc = new HashMap<>();

        /** Repeat groups (Emacs repeat-mode transient maps): group name ->
         *  member key -> the binding it re-dispatches while the run is live. */
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

    /** The bundled .dbmeowrc — the default layer beneath ~/.dbmeowrc. */
    public static Config initDefaults(List<String> lines) {
        defaultConfig = parse(lines);
        return defaultConfig;
    }

    /** Load (or reload) the user layer from rc lines. */
    public static Config setUserLines(List<String> lines) {
        userConfig = parse(lines);
        RcFileState.saveParsed(userConfig); // the reload surface's snapshot
        return userConfig;
    }

    public static void setForTest(Config c) {
        userConfig = c;
        RcFileState.resetForTest(); // no stale reload state across specs
    }

    public static Config cfg() {
        return userConfig;
    }

    /** The bundled defaults, loaded lazily from the classpath resource. */
    public static Config defaults() {
        if (defaultConfig == null) initDefaults(readBundledLines());
        return defaultConfig;
    }

    /** The bundled rc verbatim — what a first ~/.dbmeowrc is seeded from
     *  (the adapter's SPC c m, mirroring the siblings). */
    public static List<String> bundledLines() {
        return readBundledLines();
    }

    private static List<String> readBundledLines() {
        try (InputStream in = Rc.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) return List.of();
            List<String> lines = new ArrayList<>();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
            return lines;
        } catch (IOException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------ effective views

    /** Effective keypad table: bundled defaults with ~/.dbmeowrc on top. */
    public static Map<String, Binding> keypad() {
        Map<String, Binding> merged = new LinkedHashMap<>(defaults().keypad);
        merged.putAll(cfg().keypad);
        return merged;
    }

    /** Effective which-key labels: bundled defaults with ~/.dbmeowrc on top. */
    public static Map<String, String> keypadDescs() {
        Map<String, String> merged = new HashMap<>(defaults().keypadDesc);
        merged.putAll(cfg().keypadDesc);
        return merged;
    }

    /** Effective repeat groups: ~/.dbmeowrc lines layer per (group, key)
     *  over the bundled defaults; a member re-bound to `ignore` gives its key
     *  back (like `mmap <key> ignore` on trees) and an emptied group is gone. */
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

    /** The transient map a just-dispatched binding arms — Emacs' repeat-map
     *  symbol property, ported: membership is the TARGET (action, command or
     *  keys — not the key that ran it, repeat-check-key 'no style), and the
     *  first declared group wins. Null when the binding repeats nothing. */
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

    public static int whichKeyDelayMs() {
        if (cfg().whichKeyDelayMs != null) return cfg().whichKeyDelayMs;
        if (defaults().whichKeyDelayMs != null) return defaults().whichKeyDelayMs;
        return 250;
    }
}
