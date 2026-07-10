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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text-mutating commands: entering INSERT (insert/append/open above/below), change, delete, kill
 * with meow's kill-line and join fallbacks, save / yank / replace against the clipboard kill-ring,
 * and undo. Multi-cursor edits are computed against the cursors in descending offset order so
 * beacon editing never invalidates the offsets still to come.
 */
public final class Edits {
    private Edits() {}

    /**
     * meow--allow-modify-p (meow-util.el): read-only buffers keep the full NORMAL layout, but the
     * text-changing commands are inert. meow gates kill/change/backspace/replace into SILENT
     * no-ops; delete/yank/open (and swap-grab) instead fail with Emacs' "Buffer is read-only" error
     * — surfaced here as a hint.
     */
    public static boolean allowModify(Ctx ctx) {
        return ctx.port().isWritable();
    }

    /**
     * @return true when the edit must be blocked — telling the user why.
     */
    public static boolean blockedReadOnly(Ctx ctx) {
        if (allowModify(ctx)) return false;
        ctx.ui().hint("Buffer is read-only");
        return true;
    }

    public static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

    static {
        commands.put("meow-insert", Edits::insert);
        commands.put("meow-append", Edits::append);
        commands.put("meow-open-above", Edits::openAbove);
        commands.put("meow-open-below", Edits::openBelow);
        commands.put("meow-change", Edits::change);
        commands.put("meow-delete", Edits::del);
        commands.put("meow-backward-delete", Edits::backwardDelete);
        commands.put("meow-kill", Edits::kill);
        commands.put("meow-save", Edits::save);
        commands.put("meow-yank", Edits::yank);
        commands.put("meow-replace", Edits::replace);
        commands.put("meow-undo", Edits::undo);
        commands.put("meow-undo-in-selection", Edits::undoInSelection);
    }

    /** One selection's contribution to a multi-cursor edit. */
    private record Computed(TextEdit edit, SelRange sel) {}

    @FunctionalInterface
    private interface Compute {
        Computed apply(SelRange sel, int lo, int hi);
    }

    /**
     * One undo step over every cursor, highest offset first: {@code compute} receives a selection
     * and returns its edit (or null) plus the cursor's new range. Descending order keeps every
     * not-yet-processed offset valid. compute answers in PRE-edit coordinates; applying the batch
     * shifts everything above an edit, so each cursor is re-based by the length delta of the edits
     * below it (its own edit excluded — its sel already sits where compute put it, e.g. after a
     * yank's insertion).
     */
    private static void editCarets(Ctx ctx, Compute compute) {
        List<SelRange> sels = ctx.port().getSelections();
        record Item(SelRange sel, int index, int lo) {}
        List<Item> order = new ArrayList<>();
        for (int i = 0; i < sels.size(); i++) {
            SelRange sel = sels.get(i);
            order.add(new Item(sel, i, Math.min(sel.anchor(), sel.active())));
        }
        order.sort(Comparator.comparingInt(Item::lo).reversed());
        List<TextEdit> edits = new ArrayList<>();
        Computed[] results = new Computed[sels.size()];
        for (Item item : order) {
            int hi = Math.max(item.sel().anchor(), item.sel().active());
            Computed r = compute.apply(item.sel(), item.lo(), hi);
            if (r.edit() != null) edits.add(r.edit());
            results[item.index()] = r;
        }
        SelRange[] newSels = new SelRange[sels.size()];
        int delta = 0;
        for (int i = order.size() - 1; i >= 0; i--) { // ascending offsets
            Item item = order.get(i);
            Computed r = results[item.index()];
            newSels[item.index()] =
                    new SelRange(r.sel().anchor() + delta, r.sel().active() + delta);
            if (r.edit() != null) {
                delta += r.edit().text().length() - (r.edit().end() - r.edit().start());
            }
        }
        // the grab region's offsets track core-applied edits, like a marker
        if (!edits.isEmpty()) {
            Grab.adjustForEdits(ctx.st(), edits);
            ctx.port().edit(edits);
        }
        ctx.port().setSelections(List.of(newSels));
    }

    private static void insert(Ctx ctx) {
        List<SelRange> collapsed = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) {
            int o = Math.min(s.anchor(), s.active());
            collapsed.add(new SelRange(o, o));
        }
        ctx.port().setSelections(collapsed);
        ctx.st().selType = SelType.NONE;
        Selections.resetSelectionMemory(ctx.st()); // meow-insert runs meow--cancel-selection
        ctx.setMode(MeowMode.INSERT);
    }

    private static void append(Ctx ctx) {
        List<SelRange> collapsed = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) {
            int o = Math.max(s.anchor(), s.active());
            collapsed.add(new SelRange(o, o));
        }
        ctx.port().setSelections(collapsed);
        ctx.st().selType = SelType.NONE;
        Selections.resetSelectionMemory(ctx.st()); // meow-append runs meow--cancel-selection
        ctx.setMode(MeowMode.INSERT);
    }

    /** Open a line below the caret's line and enter INSERT there. */
    private static void openBelow(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx); // meow-open-below never cancels, the RET just deactivates
        String text = ctx.port().getText();
        int eol = Text.lineEnd(text, Text.lineOfOffset(text, Selections.primary(ctx).active()));
        List<TextEdit> nl = List.of(new TextEdit(eol, eol, "\n"));
        Grab.adjustForEdits(ctx.st(), nl);
        ctx.port().edit(nl);
        ctx.port().setSelections(List.of(new SelRange(eol + 1, eol + 1)));
        ctx.setMode(MeowMode.INSERT);
    }

    /** Open a line above the caret's line and enter INSERT there. */
    private static void openAbove(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx); // as in openBelow: no history clearing
        String text = ctx.port().getText();
        int bol = Text.lineStart(text, Text.lineOfOffset(text, Selections.primary(ctx).active()));
        List<TextEdit> nl = List.of(new TextEdit(bol, bol, "\n"));
        Grab.adjustForEdits(ctx.st(), nl);
        ctx.port().edit(nl);
        ctx.port().setSelections(List.of(new SelRange(bol, bol)));
        ctx.setMode(MeowMode.INSERT);
    }

    /**
     * The delete-the-region-else-one-char compute that change and del share: the char fallback
     * takes ANY char, newlines included (meow-change-char / meow-C-d = delete-forward-char).
     */
    private static Compute deleteForward(String text) {
        return (sel, lo, hi) -> {
            if (lo != hi) {
                return new Computed(new TextEdit(lo, hi, ""), new SelRange(lo, lo));
            }
            if (lo < text.length()) {
                return new Computed(new TextEdit(lo, lo + 1, ""), new SelRange(lo, lo));
            }
            return new Computed(null, new SelRange(lo, lo));
        };
    }

    private static void change(Ctx ctx) {
        if (!allowModify(ctx)) return; // meow gates change silently
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        // fallback meow-change-char at point-max: nothing happens, not even INSERT
        if (!Selections.hasSelection(prim) && prim.active() >= text.length()) return;
        editCarets(ctx, deleteForward(text));
        ctx.st().selType = SelType.NONE;
        ctx.setMode(MeowMode.INSERT);
    }

    private static void del(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        editCarets(ctx, deleteForward(ctx.port().getText()));
        ctx.st().selType = SelType.NONE;
    }

    private static void backwardDelete(Ctx ctx) {
        if (!allowModify(ctx)) return; // meow gates backspace silently
        editCarets(
                ctx,
                (sel, lo, hi) -> {
                    if (lo != hi) {
                        return new Computed(new TextEdit(lo, hi, ""), new SelRange(lo, lo));
                    }
                    if (lo > 0) {
                        return new Computed(
                                new TextEdit(lo - 1, lo, ""), new SelRange(lo - 1, lo - 1));
                    }
                    return new Computed(null, new SelRange(lo, lo));
                });
        ctx.st().selType = SelType.NONE;
    }

    /**
     * meow--prepare-region-for-kill (meow-util.el): the range one selection contributes to a kill
     * or save — a FORWARD line-type selection includes its trailing newline. Backward selections
     * and the last line kill as-is. Probed against meow 1.5.0 itself (batch Emacs, 2026-07-06).
     */
    private static int[] killRange(Ctx ctx, SelRange sel, int textLen) {
        int lo = Math.min(sel.anchor(), sel.active());
        int hi = Math.max(sel.anchor(), sel.active());
        if (ctx.st().selType == SelType.LINE && sel.active() >= sel.anchor() && hi < textLen) {
            hi++;
        }
        return new int[] {lo, hi};
    }

    /** The region-bearing selections in buffer order — the cursors a kill or save reads. */
    private static List<SelRange> regionsInOrder(List<SelRange> sels) {
        List<SelRange> regions = new ArrayList<>();
        for (SelRange s : sels) {
            if (s.anchor() != s.active()) regions.add(s);
        }
        regions.sort(Comparator.comparingInt(s -> Math.min(s.anchor(), s.active())));
        return regions;
    }

    /**
     * The \n-joined kill-ring text those regions contribute, each through {@link #killRange} — one
     * rule for kill AND save, so the hand-probed newline behavior cannot drift between them.
     */
    private static String joinedKillText(Ctx ctx, String text, List<SelRange> regions) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < regions.size(); i++) {
            int[] r = killRange(ctx, regions.get(i), text.length());
            if (i > 0) joined.append('\n');
            joined.append(text, r[0], r[1]);
        }
        return joined.toString();
    }

    private static void kill(Ctx ctx) {
        if (!allowModify(ctx)) return; // meow gates kill silently
        MeowState st = ctx.st();
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        if (st.selType == SelType.JOIN && Selections.hasSelection(prim)) {
            joinKill(ctx);
            return;
        }
        if (Selections.hasSelection(prim)) {
            // cut: the kill-ring is the clipboard; multi-cursor kills join with \n
            ctx.clipboard()
                    .write(joinedKillText(ctx, text, regionsInOrder(ctx.port().getSelections())));
            editCarets(
                    ctx,
                    (sel, lo, hi) -> {
                        if (lo == hi) return new Computed(null, sel);
                        int[] r = killRange(ctx, sel, text.length());
                        return new Computed(new TextEdit(r[0], r[1], ""), new SelRange(r[0], r[0]));
                    });
            st.selType = SelType.NONE;
            return;
        }
        // fallback meow-C-k: kill to end of line, or the newline when at eol
        if (text.isEmpty()) return;
        int caret = prim.active();
        int eol = Text.lineEnd(text, Text.lineOfOffset(text, caret));
        int end = caret == eol ? Math.min(eol + 1, text.length()) : eol;
        if (end > caret) {
            ctx.clipboard().write(text.substring(caret, end));
            // grab module port point: Grab.adjustForEdits before applying
            ctx.port().edit(List.of(new TextEdit(caret, end, "")));
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        }
    }

    /**
     * Killing a join selection = delete-indentation: single space, none at line edges or against
     * brackets (Emacs' fixup-whitespace).
     */
    private static void joinKill(Ctx ctx) {
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        int s = Math.min(prim.anchor(), prim.active());
        int e = Math.max(prim.anchor(), prim.active());
        char before = s > 0 ? text.charAt(s - 1) : '\n';
        char after = e < text.length() ? text.charAt(e) : '\n';
        boolean space =
                before != '\n'
                        && after != '\n'
                        && !Character.isWhitespace(before)
                        && !Character.isWhitespace(after)
                        && ")]}.,;:".indexOf(after) < 0
                        && "([{".indexOf(before) < 0;
        // grab module port point: Grab.adjustForEdits before applying
        ctx.port().edit(List.of(new TextEdit(s, e, space ? " " : "")));
        ctx.port().setSelections(List.of(new SelRange(s, s)));
        ctx.st().selType = SelType.NONE;
        ctx.st().selExpand = false;
    }

    /**
     * meow-save: copy — with kill-ring-save's mark deactivation: the selection is cancelled
     * afterwards and every cursor stays at its point (past the newline for a forward line
     * selection).
     */
    private static void save(Ctx ctx) {
        String text = ctx.port().getText();
        List<SelRange> sels = ctx.port().getSelections();
        List<SelRange> withSel = regionsInOrder(sels);
        if (withSel.isEmpty()) return;
        ctx.clipboard().write(joinedKillText(ctx, text, withSel));
        List<SelRange> collapsed = new ArrayList<>();
        for (SelRange s : sels) {
            if (s.anchor() == s.active()) {
                collapsed.add(s);
                continue;
            }
            int[] r = killRange(ctx, s, text.length());
            int caret = s.active() >= s.anchor() ? r[1] : r[0];
            collapsed.add(new SelRange(caret, caret));
        }
        ctx.port().setSelections(collapsed);
        ctx.st().selType = SelType.NONE;
        ctx.st().selExpand = false;
    }

    /** meow-yank: insert the clipboard at every cursor, cursor lands after it. */
    private static void yank(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        String clip = ctx.clipboard().read();
        if (clip == null || clip.isEmpty()) return;
        editCarets(
                ctx,
                (sel, lo, hi) ->
                        new Computed(
                                new TextEdit(sel.active(), sel.active(), clip),
                                new SelRange(
                                        sel.active() + clip.length(),
                                        sel.active() + clip.length())));
    }

    /** meow-replace: selection := clipboard; the clipboard stays intact. */
    private static void replace(Ctx ctx) {
        if (!allowModify(ctx)) return; // meow gates replace silently
        if (!Selections.hasSelection(Selections.primary(ctx))) return;
        String raw = ctx.clipboard().read();
        if (raw == null) return;
        String clip = raw.replaceAll("\\n+$", "");
        editCarets(
                ctx,
                (sel, lo, hi) ->
                        lo == hi
                                ? new Computed(null, sel)
                                : new Computed(
                                        new TextEdit(lo, hi, clip),
                                        new SelRange(lo + clip.length(), lo + clip.length())));
        ctx.st().selType = SelType.NONE;
    }

    /**
     * meow-undo cancels the selection (with its history) BEFORE undoing — but only when a region is
     * active.
     */
    private static void undo(Ctx ctx) {
        if (Selections.hasSelection(Selections.primary(ctx))) Selections.cancel(ctx);
        ctx.port().undo();
    }

    /**
     * meow-undo-in-selection only acts with an active region; the region-scoped undo itself has no
     * host analog, so it is a plain undo (see README).
     */
    private static void undoInSelection(Ctx ctx) {
        if (Selections.hasSelection(Selections.primary(ctx))) ctx.port().undo();
    }
}
