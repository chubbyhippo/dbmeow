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
import java.util.Map;

public final class View {
    private View() {}

    public static final String RECENTER_COMMAND = "recenter-top-bottom";

    public static final List<RevealAt> RECENTER_POSITIONS =
            List.of(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM);

    public static RevealAt recenterPosition(int phase) {
        return RECENTER_POSITIONS.get(Math.floorMod(phase, RECENTER_POSITIONS.size()));
    }

    public static int nextRecenterPhase(String previousCommand, int phase) {
        return RECENTER_COMMAND.equals(previousCommand) ? phase + 1 : 0;
    }

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put(
                RECENTER_COMMAND,
                ctx -> {
                    MeowState state = ctx.state();
                    state.recenterPhase = nextRecenterPhase(state.lastCommand, state.recenterPhase);
                    state.lastCommand = RECENTER_COMMAND;
                    ctx.ui().revealCaret(recenterPosition(state.recenterPhase));
                });
    }
}
