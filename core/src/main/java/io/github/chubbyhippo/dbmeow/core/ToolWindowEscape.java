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

/**
 * Double-ESC in a tool window returns focus to the editor. The pairing state machine is pure: a
 * plain ESC reports which surface owns focus (null = not a tool-window surface) and its time; the
 * second press on the SAME surface within {@link #TIMEOUT_MS} is the jump; a miss (different
 * surface, too slow, null) re-arms with the current press. Observing the key and re-emitting a lone
 * first press is SWT adapter wiring, staged for later.
 */
public final class ToolWindowEscape {
    private ToolWindowEscape() {}

    /** Two presses at most this many ms apart count as a double-press. */
    public static final long TIMEOUT_MS = 500;

    private static String lastSurface = null;
    private static long lastAt = 0;

    /** True = second press of a pair on the same surface: the caller jumps. */
    public static boolean onEscape(String surface, long at) {
        boolean doubled =
                surface != null && surface.equals(lastSurface) && at - lastAt <= TIMEOUT_MS;
        if (doubled) {
            reset();
            return true;
        }
        lastSurface = surface;
        lastAt = at;
        return false;
    }

    public static void reset() {
        lastSurface = null;
        lastAt = 0;
    }
}
