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
import java.util.Locale;
import java.util.Map;

public final class Edits {
    private Edits() {}

    public static boolean allowModify(Ctx ctx) {
        return ctx.port().isWritable();
    }

    public static boolean blockedReadOnly(Ctx ctx) {
        if (allowModify(ctx)) return false;
        ctx.ui().hint("Buffer is read-only");
        return true;
    }

    static final Map<String, MeowCommand> commands = new LinkedHashMap<>();

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
        commands.put("upcase-word", ctx -> caseWord(ctx, CaseOp.UPCASE));
        commands.put("downcase-word", ctx -> caseWord(ctx, CaseOp.DOWNCASE));
        commands.put("capitalize-word", ctx -> caseWord(ctx, CaseOp.CAPITALIZE));
        commands.put("kill-word", Edits::killWord);
        commands.put("open-line", Edits::openLine);
        commands.put("delete-horizontal-space", ctx -> horizontalSpace(ctx, ""));
        commands.put("just-one-space", ctx -> horizontalSpace(ctx, " "));
    }

    private enum CaseOp {
        UPCASE,
        DOWNCASE,
        CAPITALIZE
    }

    private record Computed(TextEdit edit, SelRange sel) {}

    @FunctionalInterface
    private interface Compute {
        Computed apply(SelRange sel, int selStart, int selEnd);
    }

    private static void editCarets(Ctx ctx, Compute compute) {
        List<SelRange> sels = ctx.port().getSelections();
        record Item(SelRange sel, int index, int selStart) {}
        List<Item> order = new ArrayList<>();
        for (int i = 0; i < sels.size(); i++) {
            SelRange sel = sels.get(i);
            order.add(new Item(sel, i, sel.selStart()));
        }
        order.sort(Comparator.comparingInt(Item::selStart).reversed());
        List<TextEdit> edits = new ArrayList<>();
        Computed[] results = new Computed[sels.size()];
        for (Item item : order) {
            int selEnd = Math.max(item.sel().anchor(), item.sel().active());
            Computed computed = compute.apply(item.sel(), item.selStart(), selEnd);
            if (computed.edit() != null) edits.add(computed.edit());
            results[item.index()] = computed;
        }
        SelRange[] newSels = new SelRange[sels.size()];
        int delta = 0;
        for (int i = order.size() - 1; i >= 0; i--) {
            Item item = order.get(i);
            Computed computed = results[item.index()];
            newSels[item.index()] =
                    new SelRange(computed.sel().anchor() + delta, computed.sel().active() + delta);
            TextEdit edit = computed.edit();
            if (edit != null) {
                delta += edit.text().length() - (edit.end() - edit.start());
            }
        }
        if (!edits.isEmpty()) {
            Grab.adjustForEdits(ctx.state(), edits);
            ctx.port().edit(edits);
        }
        ctx.port().setSelections(List.of(newSels));
    }

    private static void insert(Ctx ctx) {
        collapseCaretsAndEnterInsert(ctx, false);
    }

    private static void append(Ctx ctx) {
        collapseCaretsAndEnterInsert(ctx, true);
    }

    private static void collapseCaretsAndEnterInsert(Ctx ctx, boolean toSelectionEnd) {
        List<SelRange> collapsed = new ArrayList<>();
        for (SelRange s : ctx.port().getSelections()) {
            int caret = toSelectionEnd ? s.selEnd() : s.selStart();
            collapsed.add(new SelRange(caret, caret));
        }
        ctx.port().setSelections(collapsed);
        ctx.state().selType = SelType.NONE;
        Selections.resetSelectionMemory(ctx.state());
        ctx.setMode(MeowMode.INSERT);
    }

    private static void openBelow(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx);
        String text = ctx.port().getText();
        int eol = Text.lineEnd(text, Text.lineOfOffset(text, Selections.primary(ctx).active()));
        List<TextEdit> nl = List.of(new TextEdit(eol, eol, "\n"));
        Grab.adjustForEdits(ctx.state(), nl);
        ctx.port().edit(nl);
        ctx.port().setSelections(List.of(new SelRange(eol + 1, eol + 1)));
        ctx.setMode(MeowMode.INSERT);
    }

    private static void openLine(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx);
        int at = Selections.primary(ctx).active();
        List<TextEdit> nl = List.of(new TextEdit(at, at, "\n"));
        Grab.adjustForEdits(ctx.state(), nl);
        ctx.port().edit(nl);
        ctx.port().setSelections(List.of(new SelRange(at, at)));
    }

    private static void horizontalSpace(Ctx ctx, String replacement) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx);
        String text = ctx.port().getText();
        int at = Selections.primary(ctx).active();
        int from = at;
        while (from > 0 && Motions.isBlank(text.charAt(from - 1))) from--;
        int to = at;
        while (to < text.length() && Motions.isBlank(text.charAt(to))) to++;
        if (from == to && replacement.isEmpty()) return;
        List<TextEdit> edits = List.of(new TextEdit(from, to, replacement));
        Grab.adjustForEdits(ctx.state(), edits);
        ctx.port().edit(edits);
        int caret = from + replacement.length();
        ctx.port().setSelections(List.of(new SelRange(caret, caret)));
    }

    private static void openAbove(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        Selections.collapse(ctx);
        String text = ctx.port().getText();
        int bol = Text.lineStart(text, Text.lineOfOffset(text, Selections.primary(ctx).active()));
        List<TextEdit> nl = List.of(new TextEdit(bol, bol, "\n"));
        Grab.adjustForEdits(ctx.state(), nl);
        ctx.port().edit(nl);
        ctx.port().setSelections(List.of(new SelRange(bol, bol)));
        ctx.setMode(MeowMode.INSERT);
    }

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
        if (!allowModify(ctx)) return;
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        if (!Selections.hasSelection(prim) && prim.active() >= text.length()) return;
        editCarets(ctx, deleteForward(text));
        ctx.state().selType = SelType.NONE;
        ctx.setMode(MeowMode.INSERT);
    }

    private static void del(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        editCarets(ctx, deleteForward(ctx.port().getText()));
        ctx.state().selType = SelType.NONE;
    }

    private static void backwardDelete(Ctx ctx) {
        if (!allowModify(ctx)) return;
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
        ctx.state().selType = SelType.NONE;
    }

    private static int[] killRange(Ctx ctx, SelRange sel, String text) {
        int start = sel.selStart();
        int end = sel.selEnd();
        if (ctx.state().selType == SelType.LINE
                && sel.active() >= sel.anchor()
                && end < text.length()) {
            if (text.charAt(end) == '\r') end++;
            if (end < text.length() && text.charAt(end) == '\n') end++;
        }
        return new int[] {start, end};
    }

    private static List<SelRange> regionsInOrder(List<SelRange> sels) {
        List<SelRange> regions = new ArrayList<>();
        for (SelRange sel : sels) {
            if (sel.anchor() != sel.active()) regions.add(sel);
        }
        regions.sort(Comparator.comparingInt(s -> s.selStart()));
        return regions;
    }

    private static String joinedKillText(Ctx ctx, String text, List<SelRange> regions) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < regions.size(); i++) {
            int[] r = killRange(ctx, regions.get(i), text);
            if (i > 0) joined.append('\n');
            joined.append(text, r[0], r[1]);
        }
        return joined.toString();
    }

    private static void kill(Ctx ctx) {
        if (!allowModify(ctx)) return;
        MeowState state = ctx.state();
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        if (state.selType == SelType.JOIN && Selections.hasSelection(prim)) {
            joinKill(ctx);
            return;
        }
        if (Selections.hasSelection(prim)) {
            ctx.clipboard()
                    .write(joinedKillText(ctx, text, regionsInOrder(ctx.port().getSelections())));
            editCarets(
                    ctx,
                    (sel, selStart, selEnd) -> {
                        if (selStart == selEnd) return new Computed(null, sel);
                        int[] killed = killRange(ctx, sel, text);
                        return new Computed(
                                new TextEdit(killed[0], killed[1], ""),
                                new SelRange(killed[0], killed[0]));
                    });
            state.selType = SelType.NONE;
            return;
        }
        if (text.isEmpty()) return;
        int caret = prim.active();
        int line = Text.lineOfOffset(text, caret);
        int eol = Text.lineEnd(text, line);
        int end = caret == eol ? Text.lineStart(text, line + 1) : eol;
        if (end > caret) {
            ctx.clipboard().write(text.substring(caret, end));
            ctx.port().edit(List.of(new TextEdit(caret, end, "")));
            ctx.port().setSelections(List.of(new SelRange(caret, caret)));
        }
    }

    private static void joinKill(Ctx ctx) {
        String text = ctx.port().getText();
        SelRange prim = Selections.primary(ctx);
        int start = prim.selStart();
        int end = prim.selEnd();
        char before = start > 0 ? text.charAt(start - 1) : '\n';
        char after = end < text.length() ? text.charAt(end) : '\n';
        boolean space =
                before != '\n'
                        && after != '\n'
                        && !Character.isWhitespace(before)
                        && !Character.isWhitespace(after)
                        && ")]}.,;:".indexOf(after) < 0
                        && "([{".indexOf(before) < 0;
        ctx.port().edit(List.of(new TextEdit(start, end, space ? " " : "")));
        ctx.port().setSelections(List.of(new SelRange(start, start)));
        ctx.state().selType = SelType.NONE;
        ctx.state().selExpand = false;
    }

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
            int[] r = killRange(ctx, s, text);
            int caret = s.active() >= s.anchor() ? r[1] : r[0];
            collapsed.add(new SelRange(caret, caret));
        }
        ctx.port().setSelections(collapsed);
        ctx.state().selType = SelType.NONE;
        ctx.state().selExpand = false;
    }

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

    private static void replace(Ctx ctx) {
        if (!allowModify(ctx)) return;
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
        ctx.state().selType = SelType.NONE;
    }

    private static String casified(String slice, CaseOp op) {
        return switch (op) {
            case UPCASE -> slice.toUpperCase(Locale.ROOT);
            case DOWNCASE -> slice.toLowerCase(Locale.ROOT);
            case CAPITALIZE -> capitalizedWords(slice);
        };
    }

    private static String capitalizedWords(String slice) {
        Text.CharPredicate isWord = Text.charPred(false);
        StringBuilder out = new StringBuilder(slice.length());
        boolean inWord = false;
        for (int i = 0; i < slice.length(); i++) {
            char ch = slice.charAt(i);
            if (isWord.test(ch)) {
                out.append(inWord ? Character.toLowerCase(ch) : Character.toUpperCase(ch));
                inWord = true;
            } else {
                out.append(ch);
                inWord = false;
            }
        }
        return out.toString();
    }

    private static void caseWord(Ctx ctx, CaseOp op) {
        if (blockedReadOnly(ctx)) return;
        int count = ctx.state().takeCount(1);
        if (count == 0) return;
        boolean hadSelection = Selections.hasSelection(Selections.primary(ctx));
        String text = ctx.port().getText();
        Text.CharPredicate isWord = Text.charPred(false);
        editCarets(
                ctx,
                (sel, selStart, selEnd) -> {
                    int from = sel.active();
                    int[] range = wordKillRange(text, from, count, isWord);
                    if (range[0] == range[1]) return new Computed(null, sel);
                    int caret = count > 0 ? range[1] : from;
                    return new Computed(
                            new TextEdit(
                                    range[0],
                                    range[1],
                                    casified(text.substring(range[0], range[1]), op)),
                            new SelRange(caret, caret));
                });
        if (hadSelection) Selections.collapse(ctx);
    }

    private static int[] wordKillRange(
            String text, int from, int count, Text.CharPredicate isWord) {
        int target =
                count > 0
                        ? Text.Words.nextEnd(text, from, count, isWord)
                        : Text.Words.prevStart(text, from, -count, isWord);
        return new int[] {Math.min(from, target), Math.max(from, target)};
    }

    private static void killWord(Ctx ctx) {
        if (blockedReadOnly(ctx)) return;
        int count = ctx.state().takeCount(1);
        if (count == 0) return;
        String text = ctx.port().getText();
        Text.CharPredicate isWord = Text.charPred(false);
        List<int[]> killed = new ArrayList<>();
        for (SelRange sel : ctx.port().getSelections()) {
            int[] range = wordKillRange(text, sel.active(), count, isWord);
            if (range[0] != range[1]) killed.add(range);
        }
        if (killed.isEmpty()) return;
        killed.sort(Comparator.comparingInt(range -> range[0]));
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < killed.size(); i++) {
            if (i > 0) joined.append('\n');
            int[] range = killed.get(i);
            joined.append(text, range[0], range[1]);
        }
        ctx.clipboard().write(joined.toString());
        editCarets(
                ctx,
                (sel, lo, hi) -> {
                    int[] range = wordKillRange(text, sel.active(), count, isWord);
                    if (range[0] == range[1]) {
                        return new Computed(null, new SelRange(sel.active(), sel.active()));
                    }
                    return new Computed(
                            new TextEdit(range[0], range[1], ""), new SelRange(range[0], range[0]));
                });
        ctx.state().selType = SelType.NONE;
        ctx.state().selExpand = false;
    }

    private static void undo(Ctx ctx) {
        if (Selections.hasSelection(Selections.primary(ctx))) Selections.cancel(ctx);
        ctx.port().undo();
    }

    private static void undoInSelection(Ctx ctx) {
        if (Selections.hasSelection(Selections.primary(ctx))) ctx.port().undo();
    }
}
