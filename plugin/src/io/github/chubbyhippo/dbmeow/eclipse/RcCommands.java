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

import io.github.chubbyhippo.dbmeow.core.Rc;
import io.github.chubbyhippo.dbmeow.core.UiPort;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

final class RcCommands {
    private RcCommands() {}

    static File rcFile() {
        return new File(System.getProperty("user.home"), Rc.FILE_NAME);
    }

    static Rc.Config load() {
        List<String> lines = List.of();
        File f = rcFile();
        if (f.isFile()) {
            try {
                lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        return Rc.setUserLines(lines);
    }

    static void reload(UiPort ui) {
        saveDirtyRcEditors();
        Rc.Config c = load();
        ui.hint("reloaded ~/" + Rc.FILE_NAME + ": " + c.normal.size() + " normal, "
                + c.motion.size() + " motion, " + c.keypad.size() + " keypad map(s)"
                + (c.errors.isEmpty() ? "" : ", " + c.errors.size() + " problem(s)"));
    }

    static void edit() {
        File f = rcFile();
        seedIfMissing(f);
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null || window.getActivePage() == null) return;
        try {
            IDE.openEditorOnFileStore(
                    window.getActivePage(),
                    EFS.getLocalFileSystem().getStore(new Path(f.getAbsolutePath())));
        } catch (PartInitException ignored) {
        }
    }

    static void seedIfMissing(File f) {
        if (f.exists()) return;
        try {
            Files.write(f.toPath(), Rc.bundledLines(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void saveDirtyRcEditors() {
        URI rc = rcFile().toURI();
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IEditorReference ref : page.getEditorReferences()) {
                    IEditorPart part = ref.getEditor(false);
                    if (part == null || !part.isDirty()) continue;
                    IEditorInput input = part.getEditorInput();
                    if (input instanceof IURIEditorInput uriInput && sameFile(uriInput.getURI(), rc)) {
                        part.doSave(new NullProgressMonitor());
                    }
                }
            }
        }
    }

    private static boolean sameFile(URI a, URI b) {
        try {
            return Paths.get(a).toRealPath().equals(Paths.get(b).toRealPath());
        } catch (Exception e) {
            return a.equals(b);
        }
    }
}
