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

import java.util.Set;

/**
 * Which editors get meow, by editor kind — the attach policy. Everything that attaches gets NORMAL:
 * like Emacs, a read-only viewer keeps the full layout and the modify commands gate themselves (see
 * {@link Edits#allowModify}); it just reports non-writable through {@link EditorPort}. Inputs that
 * need their own keys (one-line fields, dialog inputs) keep native editing. The adapter
 * (InterceptorManager) is the intended caller; MOTION exists for mmap setups but nothing attaches
 * to it by default.
 */
public final class AttachPolicy {
    private AttachPolicy() {}

    /** Read-only kinds: attach in NORMAL but report non-writable. */
    private static final Set<String> READONLY = Set.of("diff", "output");

    /** Kinds that keep native editing (meow never attaches). */
    private static final Set<String> SKIP = Set.of("oneline", "search", "comment");

    /** NORMAL for an attaching editor, or null when meow stays away. */
    public static MeowMode attachMode(String kind) {
        return SKIP.contains(kind) ? null : MeowMode.NORMAL;
    }

    public static boolean isWritable(String kind) {
        return !READONLY.contains(kind);
    }
}
