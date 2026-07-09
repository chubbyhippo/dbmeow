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
import java.util.Deque;
import java.util.List;

/** The {@link UiPort} of the BDD suite: it records everything it is told. */
public class FakeUi implements UiPort {
    public record Info(String title, String body) {
    }

    public final List<String> hints = new ArrayList<>();
    public final List<Info> infos = new ArrayList<>();
    public final Deque<String> answers = new ArrayDeque<>();

    /** Host command ids dispatched through <action>(...) bindings. */
    public final List<String> ran = new ArrayList<>();

    /** Mode notifications, in order — the adapter turns these into the
     *  block/bar cursor switch and the status widget text. */
    public final List<MeowMode> modes = new ArrayList<>();

    /** The offsets the adapter would overlay with digit labels. */
    public List<Integer> expandHints = new ArrayList<>();

    @Override
    public void hint(String text) {
        hints.add(text);
    }

    @Override
    public void info(String title, String body) {
        infos.add(new Info(title, body));
    }

    @Override
    public String input(String prompt, String initial) {
        return answers.pollFirst();
    }

    @Override
    public void runCommand(String id) {
        ran.add(id);
    }

    @Override
    public void scheduleWhichKey(String kind, String buffer) {
    }

    @Override
    public void hideWhichKey() {
    }

    @Override
    public void showExpandHints(List<Integer> positions) {
        expandHints = positions;
    }

    @Override
    public void clearExpandHints() {
        expandHints = new ArrayList<>();
    }

    @Override
    public void showAvyMatches(List<EditorPort.OffsetRange> matches) {
    }

    @Override
    public void showAvyLabels(List<UiPort.AvyLabel> labels) {
    }

    @Override
    public void clearAvy() {
    }

    @Override
    public void modeChanged(MeowState st) {
        modes.add(st.mode);
    }

    @Override
    public void refresh(MeowState st) {
    }
}
