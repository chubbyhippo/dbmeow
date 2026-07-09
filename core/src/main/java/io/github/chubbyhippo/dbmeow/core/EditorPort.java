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

/**
 * The engine's entire view of the host editor. The core never imports a UI
 * toolkit: the host adapter implements these ports, and the test suite
 * implements them over a plain string buffer — which is what makes every
 * meow behavior testable without an editor process.
 */
public interface EditorPort {
    /** An inclusive span of line numbers. */
    record LineRange(int first, int last) {
    }

    /** A half-open offset range, e.g. a language-aware defun. */
    record OffsetRange(int start, int end) {
    }

    String getText();

    /** All selections, primary first. An empty range is a bare caret. */
    List<SelRange> getSelections();

    /** Replace all selections (primary first) and reveal the primary caret. */
    void setSelections(List<SelRange> sels);

    /** Apply non-overlapping edits as ONE undo step. */
    void edit(List<TextEdit> edits);

    boolean isWritable();

    /** Line span currently on screen (the `w` window thing); null = unknown. */
    LineRange visibleLineRange();

    void undo();

    void closeEditor();

    /** Language-aware defun range at offset when the host can provide one. */
    OffsetRange symbolRangeAt(int offset);
}
