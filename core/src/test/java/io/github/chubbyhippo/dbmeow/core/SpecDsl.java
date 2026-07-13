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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public abstract class SpecDsl {
    protected FakeEditor editor;
    protected FakeClipboard clip;
    protected FakeUi ui;
    protected MeowState st;

    @BeforeEach
    void freshSpec() {
        editor = new FakeEditor();
        clip = new FakeClipboard();
        ui = new FakeUi();
        st = new MeowState();
        Rc.setForTest(new Rc.Config());
        Engine.repeatMap = null;
    }

    protected Ctx ctx() {
        return new Ctx(editor, clip, ui, st);
    }

    protected void given(String description, String textWithCaret) {
        int at = textWithCaret.indexOf("<caret>");
        editor.text.setLength(0);
        editor.text.append(textWithCaret.replace("<caret>", ""));
        int off = Math.max(at, 0);
        editor.sels = new java.util.ArrayList<>(List.of(new SelRange(off, off)));
        st = new MeowState();
    }

    protected void givenRc(String text) {
        Rc.setForTest(Rc.parse(List.of(text.split("\n", -1))));
    }

    protected void givenClipboard(String text) {
        clip.content = text;
    }

    protected void givenMinibufferAnswers(String... answers) {
        for (String a : answers) ui.answers.addLast(a);
    }

    protected void givenCaretAt(int offset) {
        editor.sels = new java.util.ArrayList<>(List.of(new SelRange(offset, offset)));
    }

    protected void givenReadOnly() {
        editor.writable = false;
    }

    protected void whenKeys(String keys) {
        for (int i = 0; i < keys.length(); i++) Engine.handleChar(ctx(), keys.charAt(i));
    }

    protected void whenCommand(String command) {
        Engine.runEmacsMotion(ctx(), command);
    }

    protected boolean pressEsc() {
        return Engine.escapeKey(ctx());
    }

    protected String selectedText() {
        SelRange s = editor.sels.get(0);
        if (s.anchor() == s.active()) return null;
        return editor.getText()
                .substring(Math.min(s.anchor(), s.active()), Math.max(s.anchor(), s.active()));
    }

    protected int caretLine() {
        return Text.lineOfOffset(editor.getText(), editor.sels.get(0).active());
    }

    protected void thenSelection(String expected) {
        assertEquals(expected, selectedText(), "selected text");
    }

    protected void thenNoSelection() {
        SelRange s = editor.sels.get(0);
        assertEquals(s.anchor(), s.active(), "expected no selection");
    }

    protected void thenCaretAt(int offset) {
        assertEquals(offset, editor.sels.get(0).active(), "caret offset");
    }

    protected void thenCaretLine(int line) {
        assertEquals(line, caretLine(), "caret line");
    }

    protected void thenCaretAtSelectionStart() {
        SelRange s = editor.sels.get(0);
        assertNotEquals(s.anchor(), s.active(), "expected a selection");
        assertEquals(
                Math.min(s.anchor(), s.active()),
                s.active(),
                "caret at selection start (reversed)");
    }

    protected void thenCaretAtSelectionEnd() {
        SelRange s = editor.sels.get(0);
        assertNotEquals(s.anchor(), s.active(), "expected a selection");
        assertEquals(
                Math.max(s.anchor(), s.active()), s.active(), "caret at selection end (forward)");
    }

    protected void thenText(String expected) {
        assertEquals(expected, editor.getText(), "buffer text");
    }

    protected void thenMode(MeowMode expected) {
        assertEquals(expected, st.mode, "meow mode");
    }

    protected void thenSelType(SelType expected) {
        assertEquals(expected, st.selType, "selection type");
    }

    protected void thenClipboard(String expected) {
        assertEquals(expected, clip.content, "clipboard");
    }

    protected void thenCaretCount(int expected) {
        assertEquals(expected, editor.sels.size(), "caret count");
    }
}
