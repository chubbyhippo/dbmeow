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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Registry {
    private Registry() {}

    static final Map<String, MeowCommand> COMMANDS;

    static {
        Map<String, MeowCommand> commands = new LinkedHashMap<>();
        commands.putAll(Motions.commands);
        commands.putAll(Selections.commands);
        commands.putAll(Search.commands);
        commands.putAll(Structures.commands);
        commands.putAll(Grab.commands);
        commands.putAll(Avy.commands);
        commands.putAll(Edits.commands);
        commands.put("meow-negative-argument", ctx -> ctx.st().negative = true);
        commands.put("negative-argument", ctx -> ctx.st().negative = true);
        commands.put("meow-quit", ctx -> ctx.port().closeEditor());
        commands.put("meow-keypad", Engine::enterKeypad);
        commands.put("repeat", Engine::repeatLast);
        commands.put("ignore", ctx -> {});
        COMMANDS = Collections.unmodifiableMap(commands);
    }
}
