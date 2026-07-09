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
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Attaches (and detaches) a {@link DbmeowInterceptor} to every text editor as
 * its part opens, activates, and closes — one meow {@link MeowState} per
 * editor. This is the DBeaver/Eclipse analog of ideameow's editor-factory
 * listener and codemeow's {@code type} override.
 *
 * <p>Mechanism, all from the local vrapper clone
 * (net.sourceforge.vrapper.eclipse):
 * <ul>
 *   <li>an {@link IPartListener2}, as in
 *       interceptor/InputInterceptorManager.java:60;</li>
 *   <li>the source viewer is reachable only through the protected
 *       {@code AbstractTextEditor.getSourceViewer()}, obtained by reflection
 *       (InputInterceptorManager.java:206);</li>
 *   <li>the interceptor is added with
 *       {@code ITextViewerExtension.prependVerifyKeyListener(...)} and removed
 *       with {@code removeVerifyKeyListener(...)}
 *       (InputInterceptorManager.java:221,282).</li>
 * </ul>
 * DBeaver's SQL editor qualifies: it is an AbstractTextEditor subclass
 * (SQLEditor -> SQLEditorBase -> BaseTextEditor -> AbstractDecoratedTextEditor
 * -> AbstractTextEditor; verified in the dbeaver repo, 2026-07-08).
 */
public final class InterceptorManager implements IPartListener2 {

    public static final InterceptorManager INSTANCE = new InterceptorManager();

    private InterceptorManager() {
    }

    /** One interceptor per editor, so we can prepend on open and remove on
     *  close. Weak keys: a closed editor part must not be pinned by us. */
    private final Map<AbstractTextEditor, DbmeowInterceptor> attached = new WeakHashMap<>();

    @Override
    public void partOpened(IWorkbenchPartReference ref) {
        attach(ref);
    }

    @Override
    public void partActivated(IWorkbenchPartReference ref) {
        // some editors reach activation without a partOpened we saw
        // (InputInterceptorManager.java:455) — attach idempotently
        attach(ref);
    }

    @Override
    public void partClosed(IWorkbenchPartReference ref) {
        IWorkbenchPart part = ref.getPart(false);
        if (!(part instanceof AbstractTextEditor editor)) return;
        DbmeowInterceptor interceptor = attached.remove(editor);
        if (interceptor == null) return;
        ISourceViewer viewer = sourceViewerOf(editor);
        if (viewer instanceof ITextViewerExtension ext) {
            ext.removeVerifyKeyListener(interceptor);
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPartReference ref) {
    }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference ref) {
    }

    @Override
    public void partHidden(IWorkbenchPartReference ref) {
    }

    @Override
    public void partVisible(IWorkbenchPartReference ref) {
    }

    @Override
    public void partInputChanged(IWorkbenchPartReference ref) {
    }

    private void attach(IWorkbenchPartReference ref) {
        IWorkbenchPart part = ref.getPart(false);
        if (!(part instanceof AbstractTextEditor editor)) return;
        if (attached.containsKey(editor)) return;
        ISourceViewer viewer = sourceViewerOf(editor);
        if (!(viewer instanceof ITextViewerExtension ext)) return;

        MeowState st = new MeowState();
        st.mode = MeowMode.NORMAL; // like Emacs, editors start in NORMAL
        EclipseUi ui = new EclipseUi(editor, st);
        Ctx ctx = new Ctx(
                new EclipseEditorPort(viewer, editor), new EclipseClipboard(viewer), ui, st);
        DbmeowInterceptor interceptor = new DbmeowInterceptor(ctx);

        // prepend: run BEFORE the editor's own VerifyKeyListeners, so a NORMAL
        // key never reaches the widget as text (InputInterceptorManager.java:221)
        ext.prependVerifyKeyListener(interceptor);
        attached.put(editor, interceptor);
        ui.refresh(st);
    }

    /** The protected AbstractTextEditor.getSourceViewer(), by reflection —
     *  the only handle Eclipse exposes (InputInterceptorManager.java:206). */
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
