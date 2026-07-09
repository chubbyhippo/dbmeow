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
        return userConfig;
    }

    public static void setForTest(Config c) {
        userConfig = c;
    }

    public static Config cfg() {
        return userConfig;
    }

    /** The bundled defaults, loaded lazily from the classpath resource. */
    public static Config defaults() {
        if (defaultConfig == null) initDefaults(readBundledLines());
        return defaultConfig;
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
