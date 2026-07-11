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

import io.github.chubbyhippo.dbmeow.core.ClipboardPort;

import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;

/**
 * The kill-ring is the system clipboard (meow-use-clipboard behavior).
 * Uses the SWT {@link Clipboard} on the viewer's display.
 */
final class EclipseClipboard implements ClipboardPort {

    private final Display display;

    EclipseClipboard(ISourceViewer viewer) {
        this.display = viewer.getTextWidget().getDisplay();
    }

    @Override
    public String read() {
        Clipboard clipboard = new Clipboard(display);
        try {
            Object contents = clipboard.getContents(TextTransfer.getInstance());
            return contents instanceof String s ? s : null;
        } finally {
            clipboard.dispose();
        }
    }

    @Override
    public void write(String text) {
        Clipboard clipboard = new Clipboard(display);
        try {
            clipboard.setContents(
                    new Object[] {text},
                    new Transfer[] {TextTransfer.getInstance()});
        } finally {
            clipboard.dispose();
        }
    }
}
