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

public final class AttachPolicy {
    private AttachPolicy() {}

    private static final Set<String> READONLY = Set.of("diff", "output");

    private static final Set<String> SKIP = Set.of("oneline", "search", "comment");

    public static MeowMode attachMode(String kind) {
        return SKIP.contains(kind) ? null : MeowMode.NORMAL;
    }

    public static boolean isWritable(String kind) {
        return !READONLY.contains(kind);
    }
}
