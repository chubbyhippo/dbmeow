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

public final class Chords {

    private Chords() {}

    public static Rc.Binding bindingFor(Chord chord) {
        if (chord == null) return null;
        return Rc.chords().get(chord);
    }

    public static boolean claims(MeowMode mode, Chord chord) {
        if (!mode.takesChords()) return false;
        return bindingFor(chord) != null;
    }

    public static boolean dispatch(Ctx ctx, Chord chord) {
        if (!claims(ctx.state().mode, chord)) return false;
        Engine.runBinding(ctx, bindingFor(chord));
        return true;
    }
}
