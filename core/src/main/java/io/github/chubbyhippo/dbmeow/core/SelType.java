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
 * Selection types mirror meow's (expand/select . type) pairs:
 * {@link MeowState#selExpand} is the cdr flag that makes follow-up commands of
 * the same family extend the selection instead of re-creating it
 * (meow-mark-word -> meow-next-word).
 */
public enum SelType {
    NONE,
    CHAR,
    WORD,
    SYMBOL,
    LINE,
    BLOCK,
    FIND,
    TILL,
    VISIT,
    JOIN,
    TRANSIENT,
}
