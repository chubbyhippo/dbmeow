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

/** Plain-text scanning shared by the command modules and the expand hints. */
public final class Text {
    private Text() {
    }

    /** A predicate over buffer chars (avoids Character boxing in the scans). */
    @FunctionalInterface
    public interface CharPredicate {
        boolean test(char c);
    }

    public static int clamp(int n, int lo, int hi) {
        return Math.min(Math.max(n, lo), hi);
    }

    public static String escapeRegExp(String s) {
        return s.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    // ------------------------------------------------------------------ lines

    public static int lineOfOffset(String text, int offset) {
        int ln = 0;
        int end = clamp(offset, 0, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') ln++;
        }
        return ln;
    }

    public static int lineCount(String text) {
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') n++;
        }
        return n;
    }

    public static int lineStart(String text, int line) {
        if (line <= 0) return 0;
        int ln = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++ln == line) return i + 1;
        }
        return text.length();
    }

    /** Offset of the line's newline (or end of text) — eol is not included. */
    public static int lineEnd(String text, int line) {
        int s = lineStart(text, line);
        int nl = text.indexOf('\n', s);
        return nl < 0 ? text.length() : nl;
    }

    // ------------------------------------------------------------ char classes

    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    public static boolean isSymbolChar(char c) {
        return isWordChar(c) || c == '_' || c == '$';
    }

    /** The char class a word or symbol motion scans by. */
    public static CharPredicate charPred(boolean symbol) {
        return symbol ? Text::isSymbolChar : Text::isWordChar;
    }

    // ------------------------------------------------------------- char scans

    public static int indexOfChar(String text, char c, int from) {
        for (int i = Math.max(from, 0); i < text.length(); i++) {
            if (text.charAt(i) == c) return i;
        }
        return -1;
    }

    public static int lastIndexOfChar(String text, char c, int from) {
        for (int i = Math.min(from, text.length() - 1); i >= 0; i--) {
            if (text.charAt(i) == c) return i;
        }
        return -1;
    }

    /**
     * Selection target after the nth occurrence of {@code ch} from
     * {@code caret} — the scan behind meow-find (selects THROUGH the char) and
     * meow-till (stops short of it), shared by the find/till commands and
     * their digit expand. -1 when there is no nth occurrence.
     */
    public static int nthCharTarget(
            String text,
            char ch,
            int caret,
            int n,
            boolean backward,
            boolean till) {
        int found = -1;
        int from = backward
                ? (till ? caret - 2 : caret - 1)
                : (till ? caret + 1 : caret);
        for (int k = 0; k < n; k++) {
            found = backward
                    ? lastIndexOfChar(text, ch, from)
                    : indexOfChar(text, ch, from);
            if (found < 0) return -1;
            from = backward ? found - 1 : found + 1;
        }
        if (found < 0) return -1;
        if (backward) return till ? found + 1 : found;
        return till ? found : found + 1;
    }

    // --------------------------------------------------------- words / symbols

    /** Word/symbol scanning shared by commands and hints. */
    public static final class Words {
        private Words() {
        }

        public static int nextEnd(String text, int from, int n, CharPredicate pred) {
            int i = clamp(from, 0, text.length());
            for (int k = 0; k < n; k++) {
                while (i < text.length() && !pred.test(text.charAt(i))) i++;
                while (i < text.length() && pred.test(text.charAt(i))) i++;
            }
            return i;
        }

        public static int prevStart(String text, int from, int n, CharPredicate pred) {
            int i = clamp(from, 0, text.length());
            for (int k = 0; k < n; k++) {
                while (i > 0 && !pred.test(text.charAt(i - 1))) i--;
                while (i > 0 && pred.test(text.charAt(i - 1))) i--;
            }
            return i;
        }

        /** meow--fix-thing-selection-mark (meow 1.5.0): the mark of a fresh
         *  next/back-thing selection snaps to the selected thing's own bounds,
         *  so the separators between the old point and the thing stay outside —
         *  e e e steps bare word by bare word (batch-probed). Forward
         *  (mark < pos): max(mark, start of the thing ending at pos); backward:
         *  min(mark, end of the thing starting at pos). Expand chains ignore
         *  this (the anchor comes from the region ends). */
        public static int fixSelectionMark(String text, int pos, int mark, CharPredicate pred) {
            int probe = clamp(
                    mark > pos ? pos : pos - 1,
                    0,
                    Math.max(text.length() - 1, 0));
            int[] bounds = boundsAt(text, probe, pred);
            if (bounds == null) return mark;
            return mark > pos ? Math.min(mark, bounds[1]) : Math.max(mark, bounds[0]);
        }

        /** The [start, end) of the word/symbol at (or next after) offset; null
         *  when there is none. */
        public static int[] boundsAt(String text, int offset, CharPredicate pred) {
            int o = offset;
            if (o >= text.length() || !pred.test(text.charAt(o))) {
                if (o > 0 && pred.test(text.charAt(o - 1))) {
                    o--;
                } else {
                    // between words: take the next word, like forward-thing
                    int f = o;
                    while (f < text.length() && !pred.test(text.charAt(f))) f++;
                    if (f >= text.length()) return null;
                    o = f;
                }
            }
            int s = o;
            int e = o;
            while (s > 0 && pred.test(text.charAt(s - 1))) s--;
            while (e < text.length() && pred.test(text.charAt(e))) e++;
            return new int[] {s, e};
        }
    }
}
