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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything meow remembers about one editor. */
public class MeowState {
    public MeowMode mode = MeowMode.NORMAL;
    public SelType selType = SelType.NONE;
    public boolean selExpand = false;
    public Pending pending = null;

    // digit-argument (keypad SPC 1-9, or plain digits with no selection) and
    // negative-argument, consumed by the next command
    public int pendingCount = 0;
    public boolean negative = false;

    public Character lastFind = null;

    /** Last entry is the active pattern (regexp source), meow's search ring. */
    public List<String> searchHistory = new ArrayList<>();

    /** meow--selection-history; cleared by meow--cancel-selection. */
    public ArrayDeque<SavedSelection> selectionHistory = new ArrayDeque<>();

    /** meow--selection: survives region-killing edits (stale on purpose). */
    public SavedSelection lastSelection = null;

    /** temporary-goal-column for consecutive vertical moves (j/k chains). */
    public Integer goalColumn = null;

    /** Last dispatched command name — the this-command/last-command handoff. */
    public String lastCommand = null;

    /**
     * The grab region (secondary selection), or null; tracks core-applied edits via {@link
     * Grab#adjustForEdits}. A selection made inside it spawns beacon carets ({@link Grab#beacon});
     * the SWT adapter renders only the primary caret but still applies the multi-range edit.
     */
    public EditorPort.OffsetRange grab = null;

    /**
     * In-flight avy jump (S / Q) session, or null — consumes keys until it lands or cancels (see
     * {@link Avy}).
     */
    public Avy.AvySession avy = null;

    /**
     * The armed repeat transient (Emacs repeat-mode, see Rc repeat groups): member keys re-dispatch
     * their binding, any other key or ESC ends the run and falls through to the normal map.
     */
    public Map<Character, Rc.Binding> repeatMap = null;

    public final StringBuilder keypad = new StringBuilder();

    /** meow--keypad-previous-state: the state KEYPAD returns to on exit. */
    public MeowMode keypadPreviousState = MeowMode.NORMAL;

    public final List<Character> unit = new ArrayList<>();
    public List<Character> lastKeys = List.of();
    public boolean replaying = false;

    // ~/.dbmeowrc binding replay: recursion guard, and noremap bypass depth
    public int replayDepth = 0;
    public int noremapDepth = 0;

    public int takeCount() {
        return takeCount(1);
    }

    public int takeCount(int def) {
        int n = pendingCount == 0 ? def : pendingCount;
        int r = negative ? -n : n;
        pendingCount = 0;
        negative = false;
        return r;
    }
}
