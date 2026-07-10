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
import java.util.Map;

/**
 * Every command under its meow name (plus Emacs' `repeat` and `ignore`, exactly as meow's suggested
 * layout spells them) — the targets a ~/.dbmeowrc line can bind a key to. Each command family
 * contributes its own map; the dispatcher-level entries (counts, keypad, repeat, quit, no-op) live
 * here.
 */
public final class Registry {
    private Registry() {}

    public static final Map<String, MeowCommand> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.putAll(Motions.commands);
        COMMANDS.putAll(Selections.commands);
        COMMANDS.putAll(Search.commands);
        COMMANDS.putAll(Structures.commands);
        COMMANDS.putAll(Grab.commands);
        COMMANDS.putAll(Avy.commands);
        COMMANDS.putAll(Edits.commands);
        COMMANDS.put("meow-negative-argument", ctx -> ctx.st().negative = true);
        // meow's QWERTY table binds Emacs' own `negative-argument`; accept
        // that exact spelling too so the canonical table pastes verbatim
        COMMANDS.put("negative-argument", ctx -> ctx.st().negative = true);
        COMMANDS.put("meow-quit", ctx -> ctx.port().closeEditor());
        COMMANDS.put(
                "meow-keypad",
                ctx -> {
                    ctx.setMode(MeowMode.KEYPAD);
                    ctx.ui().scheduleWhichKey("keypad", "");
                });
        COMMANDS.put("repeat", Engine::repeatLast);
        COMMANDS.put("ignore", ctx -> {});
    }
}
