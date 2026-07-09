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

import io.github.chubbyhippo.dbmeow.core.EditorPort.OffsetRange;

/**
 * meow-char-thing-table:
 * <pre>
 *   r round  s square  c curly  g string  e symbol  w window  b buffer
 *   p paragraph  l line  v visual-line  d defun  . sentence
 * </pre>
 * {@link #inner} excludes delimiters, {@link #bounds} includes them; both
 * return a half-open {@link OffsetRange} or null when the thing does not exist
 * at point. Ported from codemeow's things.ts (itself the ideameow Things.kt),
 * against meow-command.el's char-thing table.
 */
public final class Things {
    private Things() {
    }

    public static OffsetRange inner(Ctx ctx, char ch, int offset) {
        return compute(ctx, ch, offset, true);
    }

    public static OffsetRange bounds(Ctx ctx, char ch, int offset) {
        return compute(ctx, ch, offset, false);
    }

    private static OffsetRange compute(Ctx ctx, char ch, int offset, boolean inner) {
        String text = ctx.port().getText();
        return switch (ch) {
            case 'r' -> pair(text, offset, '(', ')', inner);
            case 's' -> pair(text, offset, '[', ']', inner);
            case 'c' -> pair(text, offset, '{', '}', inner);
            case 'g' -> stringThing(text, offset, inner);
            case 'e' -> symbol(text, offset);
            case 'w' -> window(ctx, text);
            case 'b' -> new OffsetRange(0, text.length());
            case 'p' -> paragraph(text, offset, inner);
            case 'l' -> line(text, offset, inner);
            case 'v' -> line(text, offset, true); // no soft-wrap info: visual == logical
            case 'd' -> defun(ctx, text, offset);
            case '.' -> sentence(text, offset, inner);
            default -> null;
        };
    }

    /** Innermost pair of open/close containing {@code offset}, nesting-aware. */
    static OffsetRange pair(String text, int offset, char open, char close, boolean inner) {
        int depth = 0;
        int start = -1;
        for (int i = offset - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == close) {
                depth++;
            } else if (c == open) {
                if (depth == 0) {
                    start = i;
                    break;
                }
                depth--;
            }
        }
        if (start < 0) return null;
        depth = 0;
        int end = -1;
        for (int j = offset; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == open && j != start) {
                depth++;
            } else if (c == close) {
                if (depth == 0) {
                    end = j;
                    break;
                }
                depth--;
            }
        }
        if (end < 0) return null;
        return inner ? new OffsetRange(start + 1, end) : new OffsetRange(start, end + 1);
    }

    /**
     * String thing (meow {@code g}): the quoted run at point. meow delegates to
     * the major-mode syntax table (bounds-of-thing-at-point 'string, then
     * skip-syntax over the string-quote/fence classes, which strips the WHOLE
     * delimiter run); this port is a text scan instead, so ,g/.g still work in
     * plain-text and language-agnostic buffers — a deliberate divergence (see
     * meow-semantics.md). It recognizes single AND triple runs of the three
     * quote chars (single-quote, double-quote, backtick), so Python/Kotlin
     * triple quotes, Markdown/JS fences and template literals all select.
     * inner() drops the full delimiter run on each side, bounds() keeps it
     * (mirroring the skip-syntax intent). A single-char run stays on one line; a
     * triple run spans lines (docstrings/fences). A backslash escapes the next
     * char. Unterminated openers are skipped so a stray apostrophe can't swallow
     * the rest of the buffer — but, like meow, a text scan can still be fooled by
     * an odd quote earlier on the same line.
     */
    private static OffsetRange stringThing(String text, int offset, boolean inner) {
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                boolean triple = i + 2 < n && text.charAt(i + 1) == c && text.charAt(i + 2) == c;
                int len = triple ? 3 : 1;
                int open = i;
                int j = i + len;
                int closeEnd = -1;
                while (j < n) {
                    char d = text.charAt(j);
                    if (!triple && d == '\n') break; // single-char runs stay on one line
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    boolean closes = !triple
                            || (j + 2 < n && text.charAt(j + 1) == c && text.charAt(j + 2) == c);
                    if (d == c && closes) {
                        closeEnd = j + len;
                        break;
                    }
                    j++;
                }
                if (closeEnd < 0) {
                    i = open + len; // unterminated opener: skip it, keep scanning
                    continue;
                }
                if (offset >= open && offset < closeEnd) {
                    return inner
                            ? new OffsetRange(open + len, closeEnd - len)
                            : new OffsetRange(open, closeEnd);
                }
                i = closeEnd;
                continue;
            }
            i++;
        }
        return null;
    }

    private static OffsetRange symbol(String text, int offset) {
        int o = offset;
        if (o >= text.length() || !Text.isSymbolChar(text.charAt(o))) {
            if (o > 0 && Text.isSymbolChar(text.charAt(o - 1))) o--;
            else return null;
        }
        int s = o;
        int e = o;
        while (s > 0 && Text.isSymbolChar(text.charAt(s - 1))) s--;
        while (e < text.length() && Text.isSymbolChar(text.charAt(e))) e++;
        return new OffsetRange(s, e);
    }

    private static OffsetRange window(Ctx ctx, String text) {
        EditorPort.LineRange vis = ctx.port().visibleLineRange();
        int last = Text.lineCount(text) - 1;
        int first = Text.clamp(vis != null ? vis.first() : 0, 0, Math.max(last, 0));
        int stop = Text.clamp(vis != null ? vis.last() : last, 0, Math.max(last, 0));
        return new OffsetRange(Text.lineStart(text, first), Text.lineEnd(text, stop));
    }

    private static OffsetRange paragraph(String text, int offset, boolean inner) {
        if (text.isEmpty()) return null;
        int count = Text.lineCount(text);
        int ln = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        if (blank(text, ln)) return null;
        int first = ln;
        int last = ln;
        while (first > 0 && !blank(text, first - 1)) first--;
        while (last < count - 1 && !blank(text, last + 1)) last++;
        int start = Text.lineStart(text, first);
        if (inner) return new OffsetRange(start, Text.lineEnd(text, last));
        // bounds include the trailing blank lines (emacs forward-paragraph)
        int stop = last;
        while (stop < count - 1 && blank(text, stop + 1)) stop++;
        int end = stop < count - 1 ? Text.lineStart(text, stop + 1) : Text.lineEnd(text, stop);
        return new OffsetRange(start, end);
    }

    private static OffsetRange line(String text, int offset, boolean inner) {
        int ln = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        int end = Text.lineEnd(text, ln);
        return inner
                ? new OffsetRange(Text.lineStart(text, ln), end)
                : new OffsetRange(Text.lineStart(text, ln), Math.min(end + 1, text.length()));
    }

    /**
     * defun: the host's symbol provider when it knows a function around point;
     * falls back to the outermost curly block for plain text.
     */
    private static OffsetRange defun(Ctx ctx, String text, int offset) {
        OffsetRange fromHost = ctx.port().symbolRangeAt(offset);
        if (fromHost != null) return fromHost;
        OffsetRange b = pair(text, offset, '{', '}', false);
        if (b == null) return null;
        while (true) {
            OffsetRange outer = pair(text, b.start(), '{', '}', false);
            if (outer == null) break;
            b = outer;
        }
        return b;
    }

    private static OffsetRange sentence(String text, int offset, boolean inner) {
        if (text.isEmpty()) return null;
        String enders = ".!?";
        int s = Text.clamp(offset, 0, text.length() - 1);
        while (s > 0) {
            char c = text.charAt(s - 1);
            if (enders.indexOf(c) >= 0 || (c == '\n' && s > 1 && text.charAt(s - 2) == '\n')) break;
            s--;
        }
        while (s < text.length() && Character.isWhitespace(text.charAt(s))) s++;
        int e = Text.clamp(offset, 0, text.length());
        while (e < text.length()
                && enders.indexOf(text.charAt(e)) < 0
                && !(text.charAt(e) == '\n' && e + 1 < text.length() && text.charAt(e + 1) == '\n')) {
            e++;
        }
        if (e < text.length() && enders.indexOf(text.charAt(e)) >= 0) e++;
        if (e <= s) return null;
        if (inner) return new OffsetRange(s, e);
        int be = e;
        while (be < text.length() && text.charAt(be) == ' ') be++;
        return new OffsetRange(s, be);
    }

    static boolean blank(String text, int line) {
        return text.substring(Text.lineStart(text, line), Text.lineEnd(text, line))
                .trim()
                .isEmpty();
    }
}
