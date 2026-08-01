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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecenterSpec extends SpecDsl {

    private static final String BUFFER = "one\ntwo\nthree<caret>\nfour\nfive\n";

    @Test
    @DisplayName("given the recenter cycle then the positions follow Emacs recenter-positions")
    void recenterCycleFollowsEmacs() {
        assertEquals(
                List.of(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM, RevealAt.CENTER),
                List.of(
                        View.recenterPosition(0),
                        View.recenterPosition(1),
                        View.recenterPosition(2),
                        View.recenterPosition(3)));
    }

    @Test
    @DisplayName("given a different previous command then the recenter cycle starts over")
    void differentPreviousCommandRestarts() {
        assertEquals(1, View.nextRecenterPhase(View.RECENTER_COMMAND, 0));
        assertEquals(3, View.nextRecenterPhase(View.RECENTER_COMMAND, 2));
        assertEquals(0, View.nextRecenterPhase("meow-left", 2));
        assertEquals(0, View.nextRecenterPhase(null, 2));
    }

    @Test
    @DisplayName("given repeated C-l then the view cycles center top bottom like Emacs")
    void repeatedRecenterCycles() {
        given("a caret mid-buffer", BUFFER);
        for (int i = 0; i < 4; i++) whenCommand(View.RECENTER_COMMAND);
        assertEquals(
                List.of(RevealAt.CENTER, RevealAt.TOP, RevealAt.BOTTOM, RevealAt.CENTER),
                ui.revealed);
    }

    @Test
    @DisplayName("given a motion between two C-l then the second one centers again")
    void motionRestartsTheCycle() {
        given("a caret mid-buffer", BUFFER);
        whenCommand(View.RECENTER_COMMAND);
        whenKeys("h");
        whenCommand(View.RECENTER_COMMAND);
        assertEquals(List.of(RevealAt.CENTER, RevealAt.CENTER), ui.revealed);
    }

    @Test
    @DisplayName("given the bundled rc then C-l runs recenter-top-bottom")
    void bundledRcBindsRecenter() {
        givenRc("");
        Rc.Binding binding = Chords.bindingFor(Chord.parse("C-l"));
        assertNotNull(binding);
        assertEquals(View.RECENTER_COMMAND, binding.command());
    }
}
