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

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.PlatformUI;

/**
 * Workbench entry point (contributed at org.eclipse.ui.startup). Registers a
 * single {@link InterceptorManager} as the part listener of every workbench
 * window — the ones already open plus any opened later — so each text editor
 * gets meow's modal key interception.
 *
 * <p>Mirrors vrapper's VrapperStartup + VrapperPlugin.start (the local
 * vrapper clone: activator/VrapperStartup.java:10 defers to the display
 * thread; activator/VrapperPlugin.java:147-162 iterates existing windows,
 * adds an IWindowListener for future ones, and calls
 * {@code window.getPartService().addPartListener(...)}).
 */
public final class DbmeowStartup implements IStartup {

    @Override
    public void earlyStartup() {
        // must touch the workbench on the UI thread (VrapperStartup.java:10)
        PlatformUI.getWorkbench().getDisplay().asyncExec(DbmeowStartup::hookAllWindows);
    }

    private static void hookAllWindows() {
        InterceptorManager manager = InterceptorManager.INSTANCE;
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            window.getPartService().addPartListener(manager);
        }
        PlatformUI.getWorkbench().addWindowListener(new IWindowListener() {
            @Override
            public void windowOpened(IWorkbenchWindow window) {
                window.getPartService().addPartListener(manager);
            }

            @Override
            public void windowClosed(IWorkbenchWindow window) {
                window.getPartService().removePartListener(manager);
            }

            @Override
            public void windowActivated(IWorkbenchWindow window) {
            }

            @Override
            public void windowDeactivated(IWorkbenchWindow window) {
            }
        });
    }
}
