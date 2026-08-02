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

import java.util.List;
import java.util.Map;

public final class Engine {
    private Engine() {}

    private static final Rc.Binding KEYPAD_BINDING =
            new Rc.Binding(null, null, "meow-keypad", true);

    private static final int MAX_REPLAY_DEPTH = 8;

    static Map<Character, Rc.Binding> repeatMap = null;

    public static void enterKeypad(Ctx ctx) {
        MeowState state = ctx.state();
        if (state.mode == MeowMode.KEYPAD) return;
        state.keypadPreviousState = state.mode;
        ctx.setMode(MeowMode.KEYPAD);
        ctx.ui().scheduleWhichKey("keypad", "");
    }

    public static void runEmacsMotion(Ctx ctx, String command) {
        MeowCommand cmd = Registry.COMMANDS.get(command);
        if (cmd != null) cmd.run(ctx);
        ctx.ui().refresh(ctx.state());
    }

    public static boolean handleChar(Ctx ctx, char c) {
        MeowState state = ctx.state();
        if (state.mode == MeowMode.INSERT) return false;
        if (state.mode == MeowMode.KEYPAD) {
            Keypad.key(ctx, c);
            state.lastCommand = "keypad";
            ctx.ui().refresh(state);
            return true;
        }
        if (state.avy != null) {
            Avy.key(ctx, c);
            state.lastCommand = "avy";
            ctx.ui().refresh(state);
            return true;
        }

        ctx.ui().hideWhichKey();
        ctx.ui().clearExpandHints();

        Pending pend = state.pending;
        Rc.Binding repeatBinding = pend == null && repeatMap != null ? repeatMap.get(c) : null;
        if (pend == null && repeatBinding == null) repeatMap = null;
        boolean motionish = state.mode == MeowMode.MOTION;
        Rc.Binding binding =
                pend == null
                        ? repeatBinding != null ? repeatBinding : resolve(ctx, c, motionish)
                        : null;
        String cmd = binding != null ? binding.command() : null;

        if (!state.replaying && !"repeat".equals(cmd)) {
            if (pend == null && !state.counted()) state.unit.clear();
            state.unit.add(c);
        }

        if (pend != null) {
            state.pending = null;
            resolvePending(ctx, pend, c);
            state.lastCommand = "pending";
        } else if (binding != null) {
            runBinding(ctx, binding);
            state.lastCommand =
                    cmd != null
                            ? cmd
                            : binding.action() != null ? binding.action() : state.lastCommand;
        } else {
            state.lastCommand = null;
        }

        boolean awaitingMoreKeys =
                state.pending != null
                        || (state.pendingCount() != 0
                                && cmd != null
                                && cmd.startsWith("meow-expand-"))
                        || (state.negative() && "meow-negative-argument".equals(cmd))
                        || "meow-keypad".equals(cmd);
        if (!state.replaying && !"repeat".equals(cmd) && !awaitingMoreKeys) {
            state.lastKeys = List.copyOf(state.unit);
        }

        ctx.ui().refresh(state);
        return true;
    }

    private static Rc.Binding resolve(Ctx ctx, char c, boolean motion) {
        if (c == ' ') return KEYPAD_BINDING;
        if (ctx.state().noremapDepth == 0) {
            Rc.Config cfg = Rc.cfg();
            Rc.Binding user = motion ? cfg.motion.get(c) : cfg.normal.get(c);
            if (user != null) return user;
        }
        Rc.Config d = Rc.defaults();
        return motion ? d.motion.get(c) : d.normal.get(c);
    }

    private static void resolvePending(Ctx ctx, Pending p, char c) {
        switch (p) {
            case FIND -> Motions.findTill(ctx, c, false);
            case TILL -> Motions.findTill(ctx, c, true);
            case INNER, BOUNDS, BEGIN, END -> Structures.thingSelect(ctx, p, c);
        }
    }

    public static void repeatLast(Ctx ctx) {
        MeowState state = ctx.state();
        List<Character> keys = state.lastKeys;
        if (keys.isEmpty()) return;
        state.replaying = true;
        try {
            for (char k : keys) handleChar(ctx, k);
        } finally {
            state.replaying = false;
        }
    }

    public static void runBinding(Ctx ctx, Rc.Binding b) {
        dispatch(ctx, b);
        Map<Character, Rc.Binding> map = Rc.repeatMapFor(b);
        if (map == null) return;
        if (repeatMap == null) {
            StringBuilder keys = new StringBuilder();
            for (char k : map.keySet()) {
                if (!keys.isEmpty()) keys.append(", ");
                keys.append(k);
            }
            ctx.ui().hint("Repeat with " + keys);
        }
        repeatMap = map;
    }

    private static void dispatch(Ctx ctx, Rc.Binding b) {
        MeowState state = ctx.state();
        if (b.command() != null) {
            MeowCommand cmd = Registry.COMMANDS.get(b.command());
            if (cmd != null) cmd.run(ctx);
            else ctx.ui().hint("Unknown meow command: " + b.command());
            return;
        }
        if (b.action() != null) {
            try {
                ctx.ui().runCommand(b.action());
            } catch (RuntimeException e) {
                ctx.ui().hint("Unknown command: " + b.action());
            }
            return;
        }
        if (b.keys() == null) return;
        if (state.replayDepth >= MAX_REPLAY_DEPTH) {
            ctx.ui().hint("dbmeow: mapping recursion is too deep");
            return;
        }
        boolean savedReplaying = state.replaying;
        state.replaying = true;
        state.replayDepth++;
        if (!b.recursive()) state.noremapDepth++;
        try {
            for (int i = 0; i < b.keys().length(); i++) handleChar(ctx, b.keys().charAt(i));
        } finally {
            if (!b.recursive()) state.noremapDepth--;
            state.replayDepth--;
            state.replaying = savedReplaying;
        }
    }

    public static boolean escapeKey(Ctx ctx) {
        MeowState state = ctx.state();
        if (state.avy != null) {
            Avy.cancel(ctx);
            ctx.ui().refresh(state);
            return true;
        }
        boolean hadTransient = state.pending != null || repeatMap != null;
        state.pending = null;
        repeatMap = null;
        ctx.ui().hideWhichKey();
        ctx.ui().clearExpandHints();
        if (state.mode == MeowMode.INSERT) {
            ctx.setMode(MeowMode.NORMAL);
            ctx.ui().refresh(state);
            return true;
        }
        if (state.mode == MeowMode.KEYPAD) {
            Keypad.exit(ctx);
            ctx.ui().refresh(state);
            return true;
        }
        List<SelRange> sels = ctx.port().getSelections();
        boolean anySelected = sels.stream().anyMatch(Selections::hasSelection);
        if (sels.size() > 1 || anySelected) {
            Selections.cancelAll(ctx);
            ctx.ui().refresh(state);
            return true;
        }
        return hadTransient;
    }
}
