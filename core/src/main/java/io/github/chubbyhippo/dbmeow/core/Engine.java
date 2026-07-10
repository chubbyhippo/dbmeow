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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The key dispatcher. Like meow in Emacs, the engine binds no keys of its
 * own: every command is registered by its meow name in the registry, and
 * keys resolve through rc bindings only — ~/.dbmeowrc over the bundled
 * default .dbmeowrc (see {@link Rc}). Besides dispatch, this class owns the
 * pieces of behavior that need the whole-keystroke view: the repeat unit
 * (`'`), rc-binding replay with its noremap/recursion bookkeeping, and the
 * repeat transient (Emacs repeat-mode: rc `repeat` groups arm a one-shot map
 * whose member keys re-dispatch — tap `.`/`,` to keep walking errors after
 * SPC . e).
 */
public final class Engine {
    private Engine() {
    }

    private static final Rc.Binding KEYPAD_BINDING =
            new Rc.Binding(null, null, "meow-keypad", true);

    /** @return true when the key was consumed (the type handler skips insertion). */
    public static boolean handleChar(Ctx ctx, char c) {
        MeowState st = ctx.st();
        if (st.mode == MeowMode.INSERT) return false;
        if (st.mode == MeowMode.KEYPAD) {
            Keypad.key(ctx, c);
            st.lastCommand = "keypad";
            ctx.ui().refresh(st);
            return true;
        }
        if (st.avy != null) { // an in-flight avy session consumes keys
            Avy.key(ctx, c);
            st.lastCommand = "avy";
            ctx.ui().refresh(st);
            return true;
        }

        ctx.ui().hideWhichKey();
        ctx.ui().clearExpandHints();

        Pending pend = st.pending;
        // the repeat transient: a member key of the armed group re-dispatches
        // its binding, shadowing the normal map for exactly that keypress
        // (runBinding re-arms it); any other key ends the run and falls
        // through to the resolve below — Emacs set-transient-map semantics,
        // never swallowed. ESC ends the run too (escapeKey).
        Rc.Binding repeatBinding =
                pend == null && st.repeatMap != null ? st.repeatMap.get(c) : null;
        if (pend == null && repeatBinding == null) st.repeatMap = null;
        // like Emacs: read-only buffers stay in NORMAL with every motion working
        // (the modify commands gate themselves via allow-modify in edits); the
        // motion map applies only to the MOTION state proper
        boolean motionish = st.mode == MeowMode.MOTION;
        Rc.Binding binding = pend == null
                ? repeatBinding != null ? repeatBinding : resolve(ctx, c, motionish)
                : null;
        String cmd = binding != null ? binding.command() : null;

        // the repeat unit: everything since the last complete command, so `'`
        // can replay counts and pending args (2fa) as one stroke
        if (!st.replaying && !"repeat".equals(cmd)) {
            if (pend == null && st.pendingCount == 0 && !st.negative) st.unit.clear();
            st.unit.add(c);
        }

        if (pend != null) {
            st.pending = null;
            resolvePending(ctx, pend, c);
            st.lastCommand = "pending";
        } else if (binding != null) {
            runBinding(ctx, binding);
            // the this-command/last-command handoff: vertical-motion chains keep
            // their goal column only while uninterrupted (see Motions.goalColumn);
            // a keys-replay binding keeps the innermost replayed command's name
            st.lastCommand = cmd != null
                    ? cmd
                    : binding.action() != null ? binding.action() : st.lastCommand;
        } else {
            st.lastCommand = null;
        } // undefined key: swallow, never self-insert

        boolean prefixy = st.pending != null
                || (st.pendingCount != 0 && cmd != null && cmd.startsWith("meow-expand-"))
                || (st.negative && "meow-negative-argument".equals(cmd))
                || "meow-keypad".equals(cmd);
        if (!st.replaying && !"repeat".equals(cmd) && !prefixy) {
            st.lastKeys = List.copyOf(st.unit);
        }

        ctx.ui().refresh(st);
        return true;
    }

    /** SPC = keypad (reserved), then ~/.dbmeowrc maps (skipped inside a
     *  noremap replay), then the bundled default rc; null = undefined key. */
    private static Rc.Binding resolve(Ctx ctx, char c, boolean motion) {
        if (c == ' ') return KEYPAD_BINDING;
        if (ctx.st().noremapDepth == 0) {
            Rc.Config cfg = Rc.cfg();
            Rc.Binding user = motion ? cfg.motion.get(c) : cfg.normal.get(c);
            if (user != null) return user;
        }
        Rc.Config d = Rc.defaults();
        return motion ? d.motion.get(c) : d.normal.get(c);
    }

    /** Commands that read one more key: find/till chars and the thing table. */
    private static void resolvePending(Ctx ctx, Pending p, char c) {
        switch (p) {
            case FIND -> Motions.findTill(ctx, c, false);
            case TILL -> Motions.findTill(ctx, c, true);
            case INNER, BOUNDS, BEGIN, END -> Structures.thingSelect(ctx, p, c);
        }
    }

    public static void repeatLast(Ctx ctx) {
        MeowState st = ctx.st();
        List<Character> keys = st.lastKeys;
        if (keys.isEmpty()) return;
        st.replaying = true;
        try {
            for (char k : keys) handleChar(ctx, k);
        } finally {
            st.replaying = false;
        }
    }

    /** Run a binding: a named meow command, a host command, or meow keys
     *  replayed through the engine (noremap bindings skip user maps while
     *  replaying). Afterwards, Emacs repeat-mode's post-command arming: a
     *  binding whose target sits in an rc repeat group arms that group's
     *  transient — membership by target identity (the repeat-map symbol
     *  property), no entered-with-key check (init.el sets repeat-check-key
     *  'no for every keypad-entered map, and keypad keys are never members). */
    public static void runBinding(Ctx ctx, Rc.Binding b) {
        dispatch(ctx, b);
        Map<Character, Rc.Binding> map = Rc.repeatMapFor(b);
        if (map == null) return;
        MeowState st = ctx.st();
        if (st.repeatMap == null) {
            // repeat-echo-message, once per run: "Repeat with ., ,"
            StringBuilder keys = new StringBuilder();
            for (char k : map.keySet()) {
                if (!keys.isEmpty()) keys.append(", ");
                keys.append(k);
            }
            ctx.ui().hint("Repeat with " + keys);
        }
        st.repeatMap = map;
    }

    private static void dispatch(Ctx ctx, Rc.Binding b) {
        MeowState st = ctx.st();
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
        if (st.replayDepth >= 8) {
            ctx.ui().hint("dbmeow: mapping recursion is too deep");
            return;
        }
        boolean savedReplaying = st.replaying;
        st.replaying = true; // inner keys must not clobber the ' (repeat) unit
        st.replayDepth++;
        if (!b.recursive()) st.noremapDepth++;
        try {
            for (int i = 0; i < b.keys().length(); i++) handleChar(ctx, b.keys().charAt(i));
        } finally {
            if (!b.recursive()) st.noremapDepth--;
            st.replayDepth--;
            st.replaying = savedReplaying;
        }
    }

    /**
     * The ESC key: INSERT/KEYPAD -> NORMAL, drops pending keys, collapses beacon
     * cursors. @return false when there was nothing meow-related to do (the host
     * may fall through to its own escape behavior).
     */
    public static boolean escapeKey(Ctx ctx) {
        MeowState st = ctx.st();
        if (st.avy != null) { // an in-flight avy session cancels in place
            Avy.cancel(ctx);
            ctx.ui().refresh(st);
            return true;
        }
        st.pending = null;
        st.repeatMap = null; // ESC always ends a repeat run (a non-member key)
        ctx.ui().hideWhichKey();
        ctx.ui().clearExpandHints();
        if (st.mode == MeowMode.INSERT || st.mode == MeowMode.KEYPAD) {
            ctx.setMode(MeowMode.NORMAL);
            ctx.ui().refresh(st);
            return true;
        }
        List<SelRange> sels = ctx.port().getSelections();
        if (sels.size() > 1) {
            SelRange p = sels.get(0);
            ctx.port().setSelections(new ArrayList<>(List.of(new SelRange(p.active(), p.active()))));
            ctx.ui().refresh(st);
            return true;
        }
        return false;
    }
}
