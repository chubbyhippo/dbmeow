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
 * KEYPAD state. In Emacs, SPC x/c/m reach the C-x / C-c / M- keymaps; here
 * the same key sequences dispatch host commands. Like the NORMAL/MOTION
 * layout, the whole table lives in rc lines: the bundled default .dbmeowrc
 * defines it and ~/.dbmeowrc `map <leader>...` entries layer on top (see
 * {@link Rc#keypad()}). SPC 1-9 = digit argument, SPC ? = cheatsheet,
 * SPC / = describe key. A which-key hint lists continuations of a prefix.
 */
public final class Keypad {
    private Keypad() {
    }

    public static void key(Ctx ctx, char c) {
        MeowState st = ctx.st();
        ctx.ui().hideWhichKey();
        Map<String, Rc.Binding> keypad = Rc.keypad();
        String buf = st.keypad.toString();

        if (buf.equals("/")) {
            describe(ctx, c);
            exit(ctx);
            return;
        }
        if (buf.isEmpty()) {
            if (c >= '0' && c <= '9') {
                st.pendingCount = st.pendingCount * 10 + (c - '0');
                exit(ctx);
                return;
            }
            if (c == '?') {
                exit(ctx);
                ctx.ui().info("Meow Cheatsheet", CHEATSHEET);
                return;
            }
            if (c == '/') {
                st.keypad.append('/');
                return;
            }
        }

        st.keypad.append(c);
        String cur = st.keypad.toString();
        Rc.Binding binding = keypad.get(cur);
        if (binding != null) {
            exit(ctx);
            Engine.runBinding(ctx, binding);
            return;
        }
        boolean hasPrefix = false;
        for (String seq : keypad.keySet()) {
            if (seq.startsWith(cur)) {
                hasPrefix = true;
                break;
            }
        }
        if (!hasPrefix) {
            exit(ctx);
            ctx.ui().hint("SPC " + spaced(cur) + " is undefined");
        } else {
            ctx.ui().scheduleWhichKey("keypad", cur);
        }
    }

    public static void exit(Ctx ctx) {
        ctx.ui().hideWhichKey();
        ctx.setMode(MeowMode.NORMAL);
    }

    private static String spaced(String seq) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < seq.length(); i++) {
            if (i > 0) out.append(' ');
            out.append(seq.charAt(i));
        }
        return out.toString();
    }

    private static void describe(Ctx ctx, char c) {
        Map<String, String> descs = Rc.keypadDescs();
        List<String> rows = new ArrayList<>();
        Rc.keypad().entrySet().stream()
                .filter(e -> e.getKey().startsWith(String.valueOf(c)))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    Rc.Binding b = e.getValue();
                    String target = b.action() != null
                            ? b.action()
                            : b.command() != null ? b.command() : b.keys() != null ? b.keys() : "";
                    String desc = descs.containsKey(e.getKey())
                            ? "  (" + descs.get(e.getKey()) + ")"
                            : "";
                    rows.add("SPC " + spaced(e.getKey()) + "  ->  " + target + desc);
                });
        String entries = String.join("\n", rows);
        ctx.ui().info(
                "Meow Describe: SPC " + c,
                entries.isEmpty() ? "SPC " + c + " is undefined" : entries);
    }

    public static final String CHEATSHEET = """
            The bundled default layout (meow's suggested QWERTY) — every key below can
            be rebound from ~/.dbmeowrc.

            NORMAL — selection first, then act
              h j k l  move (cancel selection)       H J K L  extend char selection
              w / W    mark word / symbol            e / E    next word / symbol end
              b / B    back word / symbol            x        line (repeat: extend)
              f / t    find / till char (inclusive / exclusive)
              1-9, 0   expand selection by N units (0 = 10); without selection: count
              -        negative argument              ;        reverse selection
              i / a    insert at start / end          I / A    open line above / below
              c        change                         s        kill (cut)
              d / D    delete char/sel fwd / back     y        save (copy)
              p        yank (paste at point)          r        replace selection with clipboard
              u        undo                           '        repeat last command
              z        pop selection                  g        cancel selection / cursors
              Q / X    goto line                      q        close editor tab
              ESC      insert -> normal; drops extra cursors

            KEYPAD (SPC)
              SPC 1-9 count   SPC ? this sheet   SPC / describe key
              the SPC command table itself is rc lines: map <leader><seq> <target>

            ~/.dbmeowrc: nmap <key> <action>(command.id) | nmap <key> meow-command | nmap <key> <meow keys>
              mmap ... (MOTION mode) | map <leader><seq> ... | desc <leader><seq> text | set nowhich-key
              every binding above is an rc line — the defaults ship as a bundled
              .dbmeowrc on the classpath; ~/.dbmeowrc overrides them key by key
            """.strip();
}
