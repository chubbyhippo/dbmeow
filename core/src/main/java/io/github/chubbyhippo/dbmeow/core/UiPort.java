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

public interface UiPort {
    void hint(String text);

    void info(String title, String body);

    String input(String prompt, String initial);

    default String input(String prompt) {
        return input(prompt, null);
    }

    void runCommand(String id);

    void scheduleWhichKey(String kind, String buffer);

    void hideWhichKey();

    void showExpandHints(List<Integer> positions);

    void clearExpandHints();

    void showAvyMatches(List<EditorPort.OffsetRange> matches);

    void showAvyLabels(List<AvyLabel> labels);

    void clearAvy();

    record AvyLabel(int offset, String label) {}

    void modeChanged(MeowState st);

    void refresh(MeowState st);
}
