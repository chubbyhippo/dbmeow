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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AceClickSpec {

    private static List<String> labelStrings(AceClick.Session session) {
        return AceClick.labels(session).stream().map(UiPort.AvyLabel::label).toList();
    }

    @Test
    @DisplayName("given twelve targets then ace-click labels follow the avy subdivision")
    void twelveTargetsFollowAvySubdivision() {
        AceClick.Session session = AceClick.begin(12);
        assertEquals(
                List.of("a", "s", "d", "f", "g", "h", "j", "k", "la", "ls", "ld", "lf"),
                labelStrings(session));
    }

    @Test
    @DisplayName("given a unique hint key then the target is picked and the session ends")
    void uniqueHintKeyPicks() {
        AceClick.Session session = AceClick.begin(3);
        assertEquals(new AceClick.Pick(1), AceClick.press(session, 's'));
    }

    @Test
    @DisplayName("given a subtree key then the remaining suffixes stay pending")
    void subtreeKeyKeepsSuffixesPending() {
        AceClick.Session session = AceClick.begin(12);
        assertInstanceOf(AceClick.Descend.class, AceClick.press(session, 'l'));
        assertEquals(List.of("a", "s", "d", "f"), labelStrings(session));
        assertEquals(new AceClick.Pick(10), AceClick.press(session, 'd'));
    }

    @Test
    @DisplayName("given a bad hint key then ace-click reports no match and stays")
    void badHintKeyReportsNoMatch() {
        AceClick.Session session = AceClick.begin(3);
        assertEquals(new AceClick.NoMatch('x'), AceClick.press(session, 'x'));
        assertEquals(List.of("a", "s", "d"), labelStrings(session));
    }

    @Test
    @DisplayName("given a single target then ace-click labels instead of auto-picking")
    void singleTargetLabelsInsteadOfAutoPicking() {
        AceClick.Session session = AceClick.begin(1);
        assertEquals(List.of("a"), labelStrings(session));
        assertEquals(new AceClick.Pick(0), AceClick.press(session, 'a'));
    }

    @Test
    @DisplayName("given no targets then ace-click arms no session")
    void noTargetsArmsNoSession() {
        assertNull(AceClick.begin(0));
    }
}
