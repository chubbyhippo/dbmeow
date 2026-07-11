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

public final class RcFileState {
    private RcFileState() {}

    private static volatile Integer state = null;

    private static int hash(Rc.Config c) {
        return Objects.hash(
                c.normal,
                c.motion,
                c.keypad,
                c.keypadDesc,
                c.repeat,
                c.whichKey,
                c.whichKeyDelayMs);
    }

    static void saveParsed(Rc.Config c) {
        state = hash(c);
    }

    public static boolean equalTo(List<String> lines) {
        Integer s = state;
        return s != null && hash(Rc.parse(lines)) == s;
    }

    static void resetForTest() {
        state = null;
    }
}
