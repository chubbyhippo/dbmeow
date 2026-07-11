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
 * windmove — Emacs' windmove-left/right/up/down (windmove.el, Emacs 30.2), ported. No window
 * geometry is exposed to a plain plugin, so this is a composed step decision, not window.el's
 * caret-band pick: the two panes of a side-by-side compare editor are windows (original sits left
 * of modified) that directional focus never crosses, so left-from-modified / right-from-original
 * switch sides first, then leave toward the editor. What survives exactly: the direction model, no
 * wrap, and Emacs' user-error verbatim. SPC w h/j/k/l dispatch dbmeow.windmove* (plugin commands,
 * staged); {@link #plan} picks the underlying step (directional editor focus, or the compare-side
 * switch).
 */
public final class Windmove {
    private Windmove() {}

    public enum Dir {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    /**
     * What the adapter can see of the active compare editor: which side has the caret, and whether
     * the sides are laid out side by side at all.
     */
    public record DiffSideView(boolean onOriginal, boolean onModified, boolean sideBySide) {}

    private static String editorFocus(Dir dir) {
        return switch (dir) {
            case LEFT -> "dbmeow.focusLeftEditor";
            case RIGHT -> "dbmeow.focusRightEditor";
            case UP -> "dbmeow.focusAboveEditor";
            case DOWN -> "dbmeow.focusBelowEditor";
        };
    }

    /** windmove-do-window-select's user-error, verbatim. */
    public static String noWindowMessage(Dir dir) {
        return "No window " + dir.name().toLowerCase() + " from selected window";
    }

    /**
     * Decide one windmove step: in a side-by-side compare the panes are windows — original sits
     * left of modified, so left from modified and right from original cross between them;
     * everything else is the editor in that direction.
     */
    public static String plan(Dir dir, DiffSideView diff) {
        if (diff != null && diff.sideBySide()) {
            if (dir == Dir.LEFT && diff.onModified()) return "dbmeow.compareSwitch";
            if (dir == Dir.RIGHT && diff.onOriginal()) return "dbmeow.compareSwitch";
        }
        return editorFocus(dir);
    }
}
