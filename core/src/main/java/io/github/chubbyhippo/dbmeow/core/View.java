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
    public static final String SCROLL_UP_COMMAND = "scroll-up-command";
    public static final String SCROLL_DOWN_COMMAND = "scroll-down-command";

    private static final int SCREEN_CONTEXT_LINES = 2;

    public static final List<RevealAt> RECENTER_POSITIONS =
            List.of(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM);

    public static RevealAt recenterPosition(int phase) {
        return RECENTER_POSITIONS.get(Math.floorMod(phase, RECENTER_POSITIONS.size()));
    }

    public static int nextRecenterPhase(String previousCommand, int phase) {
        return RECENTER_COMMAND.equals(previousCommand) ? phase + 1 : 0;
    }

    public static int pageLineCount(Ctx ctx) {
        EditorPort.LineRange visible = ctx.port().visibleLineRange();
        int count =
                visible != null
                        ? visible.last() - visible.first() + 1
                        : Text.lineCount(ctx.port().getText());
        return Math.max(1, count - SCREEN_CONTEXT_LINES);
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
        commands.put(
                SCROLL_UP_COMMAND,
                ctx -> {
                    Motions.lineOrExpand(ctx, pageLineCount(ctx) * ctx.state().takeCount(1));
                    ctx.state().lastCommand = SCROLL_UP_COMMAND;
                });
        commands.put(
                SCROLL_DOWN_COMMAND,
                ctx -> {
                    Motions.lineOrExpand(ctx, -pageLineCount(ctx) * ctx.state().takeCount(1));
                    ctx.state().lastCommand = SCROLL_DOWN_COMMAND;
                });
    }
}
