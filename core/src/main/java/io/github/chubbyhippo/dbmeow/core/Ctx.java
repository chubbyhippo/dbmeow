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

/** Everything a command needs, bundled — the one parameter they all take. */
public record Ctx(
        EditorPort port,
        ClipboardPort clipboard,
        UiPort ui,
        MeowState st) {

    public void setMode(MeowMode mode) {
        st.mode = mode;
        if (mode != MeowMode.KEYPAD) st.keypad.setLength(0);
        ui.modeChanged(st);
    }
}
