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

package io.github.chubbyhippo.dbmeow.eclipse;

import io.github.chubbyhippo.dbmeow.core.AceWindow;
import io.github.chubbyhippo.dbmeow.core.UiPort;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AceWindowSwt {

    private static final String KEYS = "asdfghjkl";

    record Candidate(AbstractTextEditor editor, OverlayPainter painter) {}

    private static Map<Character, Candidate> session;

    private AceWindowSwt() {}

    static void run(AbstractTextEditor from, UiPort ui) {
        cancel();
        List<Candidate> visible = InterceptorManager.INSTANCE.visibleCandidates();
        AceWindow.Plan plan = AceWindow.plan(visible.size());
        if (plan == AceWindow.Plan.NONE) return;
        if (plan == AceWindow.Plan.OTHER) {
            for (Candidate cand : visible) {
                if (cand.editor() != from) {
                    activate(cand.editor());
                    return;
                }
            }
            return;
        }
        Map<Character, Candidate> next = new LinkedHashMap<>();
        int count = Math.min(visible.size(), KEYS.length());
        for (int i = 0; i < count; i++) {
            Candidate cand = visible.get(i);
            next.put(KEYS.charAt(i), cand);
            cand.painter().showAceLabel(" " + KEYS.charAt(i) + " ");
        }
        session = next;
    }

    static boolean handleKey(VerifyEvent event, UiPort ui) {
        Map<Character, Candidate> current = session;
        if (current == null) return false;
        event.doit = false;
        if (event.keyCode == SWT.ESC) {
            cancel();
            return true;
        }
        if (event.character == 0) return true;
        Candidate cand = current.get(event.character);
        if (cand == null) {
            ui.hint("No such candidate: " + event.character);
            return true;
        }
        cancel();
        activate(cand.editor());
        return true;
    }

    static void cancel() {
        Map<Character, Candidate> current = session;
        session = null;
        if (current == null) return;
        for (Candidate cand : current.values()) {
            cand.painter().clearAceLabel();
        }
    }

    private static void activate(AbstractTextEditor editor) {
        editor.getSite().getPage().activate(editor);
    }
}
