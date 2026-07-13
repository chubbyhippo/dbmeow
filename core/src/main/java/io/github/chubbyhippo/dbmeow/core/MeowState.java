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

public class MeowState {
    public MeowMode mode = MeowMode.NORMAL;
    public SelType selType = SelType.NONE;
    public boolean selExpand = false;
    public Pending pending = null;

    public int pendingCount = 0;
    public boolean negative = false;

    public Character lastFind = null;

    public List<String> searchHistory = new ArrayList<>();

    public ArrayDeque<SavedSelection> selectionHistory = new ArrayDeque<>();

    public SavedSelection lastSelection = null;

    public Integer goalColumn = null;

    public String lastCommand = null;

    public EditorPort.OffsetRange grab = null;

    public Avy.AvySession avy = null;

    public final StringBuilder keypad = new StringBuilder();

    public MeowMode keypadPreviousState = MeowMode.NORMAL;

    public final List<Character> unit = new ArrayList<>();
    public List<Character> lastKeys = List.of();
    public boolean replaying = false;

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
