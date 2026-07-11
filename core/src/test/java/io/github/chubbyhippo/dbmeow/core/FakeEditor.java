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
import java.util.Comparator;
import java.util.List;

public class FakeEditor implements EditorPort {
    public final StringBuilder text = new StringBuilder();
    public List<SelRange> sels = new ArrayList<>(List.of(new SelRange(0, 0)));
    public boolean writable = true;
    public LineRange visible = null;
    public int undoCount = 0;

    @Override
    public String getText() {
        return text.toString();
    }

    @Override
    public List<SelRange> getSelections() {
        return new ArrayList<>(sels);
    }

    @Override
    public void setSelections(List<SelRange> sels) {
        this.sels = new ArrayList<>(sels);
    }

    @Override
    public void edit(List<TextEdit> edits) {
        List<TextEdit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(TextEdit::start).reversed());
        for (TextEdit e : ordered) {
            text.replace(e.start(), e.end(), e.text());
        }
    }

    @Override
    public boolean isWritable() {
        return writable;
    }

    @Override
    public LineRange visibleLineRange() {
        return visible;
    }

    @Override
    public void undo() {
        undoCount++;
    }

    @Override
    public void closeEditor() {}

    @Override
    public OffsetRange symbolRangeAt(int offset) {
        return null;
    }
}
