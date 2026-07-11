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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * which-key: after a short delay on a pending prefix (keypad SPC sequences, or the , . [ ] thing
 * table), the adapter lists the available continuations in a menu whose input dispatches typed keys
 * through the engine (they never filter — chains must type through the menu unchanged).
 * Descriptions come from `desc` / `let g:WhichKeyDesc_*` entries; delay and on/off from `set
 * timeoutlen` / `set nowhich-key`. The row computation is pure and lives here.
 */
public final class WhichKey {
    private WhichKey() {}

    /** One which-key row: the next key and its label. */
    public record Row(String key, String label) {}

    /** The `, . [ ] < >` thing-table rows. Staged for the SWT which-key overlay (a stub today). */
    public static final List<Row> THINGS =
            List.of(
                    new Row("r", "round ( )"),
                    new Row("s", "square [ ]"),
                    new Row("c", "curly { }"),
                    new Row("g", "string"),
                    new Row("e", "symbol"),
                    new Row("w", "window"),
                    new Row("b", "buffer"),
                    new Row("p", "paragraph"),
                    new Row("l", "line"),
                    new Row("v", "visual line"),
                    new Row("d", "defun"),
                    new Row(".", "sentence"));

    /** One row per next key continuing {@code buffer}: terminal label or group desc. */
    public static List<Row> keypadRows(String buffer) {
        Map<String, String> descs = Rc.keypadDescs();
        Map<String, String> rows = new LinkedHashMap<>();
        for (Map.Entry<String, Rc.Binding> e : Rc.keypad().entrySet()) {
            String seq = e.getKey();
            if (!seq.startsWith(buffer) || seq.equals(buffer)) continue;
            String child = buffer + seq.charAt(buffer.length());
            String label;
            if (seq.equals(child)) {
                Rc.Binding b = e.getValue();
                label =
                        descs.containsKey(seq)
                                ? descs.get(seq)
                                : b.action() != null
                                        ? b.action()
                                        : b.command() != null
                                                ? b.command()
                                                : b.keys() != null ? b.keys() : "";
            } else {
                label = descs.containsKey(child) ? descs.get(child) : "+more";
            }
            if (!rows.containsKey(child) || descs.containsKey(child)) rows.put(child, label);
        }
        List<Row> out = new ArrayList<>();
        rows.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            char key = e.getKey().charAt(e.getKey().length() - 1);
                            out.add(
                                    new Row(
                                            key == ' ' ? "SPC" : String.valueOf(key),
                                            e.getValue()));
                        });
        return out;
    }
}
