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

import io.github.chubbyhippo.dbmeow.core.AceClick;
import io.github.chubbyhippo.dbmeow.core.UiPort;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AceClickSwt {

    record Target(Rectangle bounds, Runnable click, Runnable rightClick) {}

    private static AceClick.Session session;
    private static List<Target> targets;
    private static AceClickOverlay overlay;

    private AceClickSwt() {}

    static void run(AbstractTextEditor from, UiPort ui) {
        cancel();
        Shell shell = from.getSite().getShell();
        if (shell == null || shell.isDisposed()) return;
        List<Target> found = collect(shell);
        found.sort(
                Comparator.comparingInt((Target t) -> t.bounds().y)
                        .thenComparingInt(t -> t.bounds().x));
        if (found.isEmpty()) {
            ui.hint("No clickable components");
            return;
        }
        session = AceClick.begin(found.size());
        targets = found;
        overlay = new AceClickOverlay(shell);
        paint();
    }

    static boolean handleKey(VerifyEvent event, UiPort ui) {
        if (session == null) return false;
        event.doit = false;
        if (event.keyCode == SWT.ESC) {
            cancel();
            return true;
        }
        char c = event.character;
        if (c == 0) return true;
        char lower = Character.toLowerCase(c);
        boolean secondary = c != lower;
        AceClick.Result result = AceClick.press(session, lower);
        if (result instanceof AceClick.Pick pick) {
            Target target = targets.get(pick.index());
            Display display = event.display;
            cancel();
            display.asyncExec(() -> click(target, secondary, ui));
        } else if (result instanceof AceClick.Descend) {
            paint();
        } else {
            ui.hint("No such candidate: " + c);
        }
        return true;
    }

    static void cancel() {
        session = null;
        targets = null;
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
        }
    }

    private static void paint() {
        List<AceClickOverlay.Badge> badges = new ArrayList<>();
        for (UiPort.AvyLabel label : AceClick.labels(session)) {
            Target target = targets.get(label.offset());
            badges.add(new AceClickOverlay.Badge(target.bounds(), label.label()));
        }
        overlay.show(badges);
    }

    private static void click(Target target, boolean secondary, UiPort ui) {
        try {
            (secondary ? target.rightClick() : target.click()).run();
        } catch (RuntimeException e) {
            ui.hint("Click failed");
        }
    }

    private static List<Target> collect(Shell shell) {
        List<Target> out = new ArrayList<>();
        walk(shell, out);
        return out;
    }

    private static void walk(Control control, List<Target> out) {
        if (control == null || control.isDisposed() || !control.isVisible()) return;
        addControl(control, out);
        if (control instanceof ToolBar toolBar) {
            for (ToolItem item : toolBar.getItems()) addToolItem(toolBar, item, out);
        }
        if (control instanceof CTabFolder folder) {
            for (CTabItem item : folder.getItems()) addTabItem(folder, item, out);
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) walk(child, out);
        }
    }

    private static void addControl(Control control, List<Target> out) {
        if (!control.isEnabled()) return;
        Runnable click = controlClick(control);
        if (click == null) return;
        Rectangle bounds = control.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        Point origin = control.toDisplay(0, 0);
        Rectangle display = new Rectangle(origin.x, origin.y, bounds.width, bounds.height);
        out.add(new Target(display, click, () -> showMenu(control, display)));
    }

    private static Runnable controlClick(Control control) {
        if (control instanceof Button button) {
            if ((button.getStyle() & SWT.ARROW) != 0) return null;
            return () -> selection(button);
        }
        if (control instanceof Link link) {
            return () -> selection(link);
        }
        if (control instanceof Text text) {
            if ((text.getStyle() & SWT.READ_ONLY) != 0) return null;
            return () -> focus(text);
        }
        return null;
    }

    private static void addToolItem(ToolBar toolBar, ToolItem item, List<Target> out) {
        if (item.isDisposed() || !item.isEnabled()) return;
        if ((item.getStyle() & SWT.SEPARATOR) != 0) return;
        Rectangle bounds = item.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        Point origin = toolBar.toDisplay(bounds.x, bounds.y);
        Rectangle display = new Rectangle(origin.x, origin.y, bounds.width, bounds.height);
        out.add(new Target(display, () -> selection(toolBar, item), () -> showMenu(toolBar, display)));
    }

    private static void addTabItem(CTabFolder folder, CTabItem item, List<Target> out) {
        if (item.isDisposed()) return;
        Rectangle bounds = item.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) return;
        Point origin = folder.toDisplay(bounds.x, bounds.y);
        Rectangle display = new Rectangle(origin.x, origin.y, bounds.width, bounds.height);
        out.add(
                new Target(
                        display,
                        () -> select(folder, item),
                        () -> {
                            select(folder, item);
                            showMenu(folder, display);
                        }));
    }

    private static void selection(Control control) {
        if (!control.isDisposed()) control.notifyListeners(SWT.Selection, new Event());
    }

    private static void selection(ToolBar toolBar, ToolItem item) {
        if (item.isDisposed()) return;
        Event event = new Event();
        event.widget = item;
        item.notifyListeners(SWT.Selection, event);
    }

    private static void select(CTabFolder folder, CTabItem item) {
        if (folder.isDisposed() || item.isDisposed()) return;
        folder.setSelection(item);
        Event event = new Event();
        event.item = item;
        folder.notifyListeners(SWT.Selection, event);
    }

    private static void focus(Control control) {
        if (!control.isDisposed()) control.setFocus();
    }

    private static void showMenu(Control source, Rectangle display) {
        if (source.isDisposed()) return;
        Menu menu = source.getMenu();
        if (menu == null || menu.isDisposed()) return;
        menu.setLocation(display.x + display.width / 2, display.y + display.height / 2);
        menu.setVisible(true);
    }
}
