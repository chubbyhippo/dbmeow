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
import java.util.Objects;

/**
 * Snapshot of the last-LOADED ~/.dbmeowrc, as a hash of the PARSED config —
 * so comment and formatting edits never demand a reload. The adapter's
 * reload surface gates on this (ideameow's RcFileState; IdeaVim's
 * VimRcFileState hashes the parsed Script for the same reason).
 */
public final class RcFileState {
    private RcFileState() {
    }

    private static volatile Integer state = null;

    private static int hash(Rc.Config c) {
        return Objects.hash(c.normal, c.motion, c.keypad, c.keypadDesc, c.whichKey, c.whichKeyDelayMs);
    }

    /** Called by {@link Rc#setUserLines} with whatever it just parsed. */
    static void saveParsed(Rc.Config c) {
        state = hash(c);
    }

    public static boolean loaded() {
        return state != null;
    }

    /** Do these rc LINES parse to the same user config the engine runs? */
    public static boolean equalTo(List<String> lines) {
        Integer s = state;
        return s != null && hash(Rc.parse(lines)) == s;
    }

    static void resetForTest() {
        state = null;
    }
}
