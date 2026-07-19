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

import io.github.chubbyhippo.dbmeow.core.Ctx;
import io.github.chubbyhippo.dbmeow.core.MeowMode;
import io.github.chubbyhippo.dbmeow.core.MeowState;

import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class InterceptorManager implements IPartListener2 {

    public static final InterceptorManager INSTANCE = new InterceptorManager();

    private InterceptorManager() {}

    private record Hook(
            ISourceViewer viewer,
            DbmeowInterceptor interceptor,
            OverlayPainter painter,
            EclipseUi ui,
            MeowState st,
            Ctx ctx) {}

    private final Map<AbstractTextEditor, Hook> hooks = new WeakHashMap<>();

    @Override
    public void partOpened(IWorkbenchPartReference ref) {
        attach(ref);
    }

    @Override
    public void partActivated(IWorkbenchPartReference ref) {
        attach(ref);
    }

    @Override
    public void partClosed(IWorkbenchPartReference ref) {
        IWorkbenchPart part = ref.getPart(false);
        if (!(part instanceof AbstractTextEditor editor)) return;
        Hook hook = hooks.remove(editor);
        if (hook != null) release(hook);
    }

    @Override
    public void partDeactivated(IWorkbenchPartReference ref) {}

    @Override
    public void partBroughtToTop(IWorkbenchPartReference ref) {}

    @Override
    public void partHidden(IWorkbenchPartReference ref) {}

    @Override
    public void partVisible(IWorkbenchPartReference ref) {}

    @Override
    public void partInputChanged(IWorkbenchPartReference ref) {}

    void attach(IWorkbenchPartReference ref) {
        IWorkbenchPart part = ref.getPart(false);
        if (!(part instanceof AbstractTextEditor editor)) return;
        ISourceViewer viewer = sourceViewerOf(editor);
        if (!(viewer instanceof ITextViewerExtension ext)) return;
        Hook existing = hooks.get(editor);
        if (existing != null && existing.viewer() == viewer) {
            existing.ui().refresh(existing.st());
            return;
        }
        if (existing != null) release(existing);

        MeowState st = new MeowState();
        st.mode = MeowMode.NORMAL;
        OverlayPainter painter = new OverlayPainter(viewer);
        EclipseUi ui = new EclipseUi(editor, st, viewer.getTextWidget(), painter);
        Ctx ctx = new Ctx(
                new EclipseEditorPort(viewer, editor), new EclipseClipboard(viewer), ui, st);
        DbmeowInterceptor interceptor = new DbmeowInterceptor(ctx);

        ext.prependVerifyKeyListener(interceptor);
        hooks.put(editor, new Hook(viewer, interceptor, painter, ui, st, ctx));
        ui.refresh(st);
    }

    Ctx ctxOf(AbstractTextEditor editor) {
        Hook hook = hooks.get(editor);
        return hook == null ? null : hook.ctx();
    }

    List<AceWindowSwt.Candidate> visibleCandidates() {
        List<AceWindowSwt.Candidate> out = new ArrayList<>();
        Map<AceWindowSwt.Candidate, Point> origins = new HashMap<>();
        for (Map.Entry<AbstractTextEditor, Hook> entry : hooks.entrySet()) {
            StyledText text = entry.getValue().viewer().getTextWidget();
            if (text == null || text.isDisposed() || !text.isVisible()) continue;
            AceWindowSwt.Candidate cand =
                    new AceWindowSwt.Candidate(entry.getKey(), entry.getValue().painter());
            origins.put(cand, text.toDisplay(0, 0));
            out.add(cand);
        }
        out.sort(
                Comparator.comparingInt((AceWindowSwt.Candidate c) -> origins.get(c).x)
                        .thenComparingInt(c -> origins.get(c).y));
        return out;
    }

    private static void release(Hook hook) {
        if (hook.viewer() instanceof ITextViewerExtension ext) {
            ext.removeVerifyKeyListener(hook.interceptor());
        }
        hook.painter().dispose();
        hook.ui().dispose();
    }

    private static ISourceViewer sourceViewerOf(AbstractTextEditor editor) {
        try {
            Method m = AbstractTextEditor.class.getDeclaredMethod("getSourceViewer");
            m.setAccessible(true);
            Object viewer = m.invoke(editor);
            return viewer instanceof ISourceViewer sv ? sv : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
