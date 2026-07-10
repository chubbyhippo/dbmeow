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

import static io.github.chubbyhippo.dbmeow.core.AttachPolicy.attachMode;
import static io.github.chubbyhippo.dbmeow.core.AttachPolicy.isWritable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which editors get meow, by kind — the Eclipse analog of the siblings' attach checks.
 * Platform-specific (no meow source of truth), so dbmeow-shaped rather than name-for-name:
 * read-only kinds attach in NORMAL like Emacs read-only buffers and report non-writable, while
 * dialog inputs keep native editing.
 */
class AttachSpec {
    @Test
    @DisplayName("given a SQL editor then meow attaches in NORMAL")
    void sqlEditorAttaches() {
        assertEquals(MeowMode.NORMAL, attachMode("sql"));
        assertTrue(isWritable("sql"));
    }

    @Test
    @DisplayName("given a plain text editor then meow attaches in NORMAL")
    void textEditorAttaches() {
        assertEquals(MeowMode.NORMAL, attachMode("text"));
    }

    @Test
    @DisplayName("given the VCS commit message box then meow attaches in NORMAL")
    void commitBoxAttaches() {
        assertEquals(MeowMode.NORMAL, attachMode("commit"));
        assertTrue(isWritable("commit"));
    }

    @Test
    @DisplayName("given a diff revision side then NORMAL, reported read-only")
    void diffRevisionReadOnly() {
        assertEquals(MeowMode.NORMAL, attachMode("diff"));
        assertFalse(isWritable("diff"));
    }

    @Test
    @DisplayName("given the output view then NORMAL, reported read-only")
    void outputViewReadOnly() {
        assertEquals(MeowMode.NORMAL, attachMode("output"));
        assertFalse(isWritable("output"));
    }

    @Test
    @DisplayName("given a one-line field then meow stays away")
    void oneLineFieldStaysAway() {
        assertNull(attachMode("oneline"));
    }

    @Test
    @DisplayName("given dialog and search inputs then meow stays away")
    void dialogInputsStayAway() {
        assertNull(attachMode("comment"));
        assertNull(attachMode("search"));
    }
}
