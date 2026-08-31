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
import java.util.ArrayList;
import java.util.List;

public final class Things {
    private Things() {}

    public static OffsetRange inner(Ctx ctx, char ch, int offset) {
        return compute(ctx, ch, offset, true);
    }

    public static OffsetRange bounds(Ctx ctx, char ch, int offset) {
        return compute(ctx, ch, offset, false);
    }

    private static OffsetRange compute(Ctx ctx, char ch, int offset, boolean inner) {
        String text = ctx.port().getText();
        return switch (ch) {
            case 'r', '(', ')' -> pair(text, offset, '(', ')', inner);
            case 's', '[', ']' -> pair(text, offset, '[', ']', inner);
            case 'c', '{', '}' -> pair(text, offset, '{', '}', inner);
            case 'a', '<', '>' -> pair(text, offset, '<', '>', inner);
            case 'g', '\'', '"' -> stringThing(text, offset, inner);
            case 't' -> tag(text, offset, inner);
            case '/' -> delimited(text, offset, '/', inner);
            case '?' -> delimited(text, offset, '?', inner);
            case 'e' -> symbol(text, offset);
            case 'w' -> window(ctx, text);
            case 'b' -> new OffsetRange(0, text.length());
            case 'p' -> paragraph(text, offset, inner);
            case 'l' -> line(text, offset, inner);
            case 'v' -> line(text, offset, true);
            case 'd' -> defun(ctx, text, offset);
            case '.' -> sentence(text, offset, inner);
            default -> null;
        };
    }

    static OffsetRange pair(String text, int offset, char open, char close, boolean inner) {
        int depth = 0;
        int start = -1;
        for (int i = offset - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (ch == close) {
                depth++;
            } else if (ch == open) {
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

    private static OffsetRange delimited(String text, int offset, char delim, boolean inner) {
        int length = text.length();
        int i = 0;
        while (i < length) {
            if (text.charAt(i) != delim) {
                i++;
                continue;
            }
            int open = i;
            int closeEnd = delimitedEnd(text, i + 1, delim);
            if (closeEnd >= 0 && offset >= open && offset < closeEnd) {
                return inner
                        ? new OffsetRange(open + 1, closeEnd - 1)
                        : new OffsetRange(open, closeEnd);
            }
            i = closeEnd < 0 ? open + 1 : closeEnd;
        }
        return null;
    }

    private static int delimitedEnd(String text, int contentStart, char delim) {
        int length = text.length();
        int j = contentStart;
        while (j < length && text.charAt(j) != '\n') {
            char ch = text.charAt(j);
            if (ch == '\\') {
                j += 2;
                continue;
            }
            if (ch == delim) return j + 1;
            j++;
        }
        return -1;
    }

    private record OpenTag(String name, int openStart, int contentStart) {}

    private record TagPair(int openStart, int contentStart, int contentEnd, int closeEnd) {}

    private record TagEndResult(int end, boolean selfClosing) {}

    private record MarkupSpec(String prefix, String terminator) {}

    private static final List<MarkupSpec> MARKUP_SPECS =
            List.of(
                    new MarkupSpec("<!--", "-->"),
                    new MarkupSpec("<![CDATA[", "]]>"),
                    new MarkupSpec("<!", ">"),
                    new MarkupSpec("<?", "?>"));

    private static OffsetRange tag(String text, int offset, boolean inner) {
        List<TagPair> pairs = new ArrayList<>();
        List<OpenTag> stack = new ArrayList<>();
        int length = text.length();
        int i = 0;
        while (i < length) {
            if (text.charAt(i) != '<') {
                i++;
                continue;
            }
            int special = skipSpecialMarkup(text, i);
            if (special >= 0) {
                i = special;
            } else if (i + 1 < length && text.charAt(i + 1) == '/') {
                i = handleCloseTag(text, i, stack, pairs);
            } else {
                i = handleOpenTag(text, i, stack);
            }
        }
        TagPair matched = findBestPair(pairs, offset);
        if (matched == null) return null;
        return inner
                ? new OffsetRange(matched.contentStart(), matched.contentEnd())
                : new OffsetRange(matched.openStart(), matched.closeEnd());
    }

    private static int skipSpecialMarkup(String text, int i) {
        for (MarkupSpec spec : MARKUP_SPECS) {
            if (startsWith(text, i, spec.prefix())) {
                int end = indexOf(text, spec.terminator(), i + spec.prefix().length());
                return end >= 0 ? end + spec.terminator().length() : text.length();
            }
        }
        return -1;
    }

    private static int handleCloseTag(
            String text, int i, List<OpenTag> stack, List<TagPair> pairs) {
        int nameStart = i + 2;
        int nameEnd = scanTagName(text, nameStart);
        int closeTagEnd = nameEnd > nameStart ? skipTagAttributes(text, nameEnd) : -1;
        if (closeTagEnd < 0) return i + 1;
        String tagName = text.substring(nameStart, nameEnd);
        int fullCloseEnd = closeTagEnd + 1;
        int openIndex = findMatchingOpen(stack, tagName);
        if (openIndex >= 0) {
            OpenTag openTag = stack.get(openIndex);
            pairs.add(new TagPair(openTag.openStart(), openTag.contentStart(), i, fullCloseEnd));
            while (stack.size() > openIndex) {
                stack.remove(stack.size() - 1);
            }
        }
        return fullCloseEnd;
    }

    private static int handleOpenTag(String text, int i, List<OpenTag> stack) {
        int nameStart = i + 1;
        int nameEnd = scanTagName(text, nameStart);
        TagEndResult tagEndResult =
                nameEnd > nameStart
                        ? scanOpeningTagEnd(text, nameEnd)
                        : new TagEndResult(-1, false);
        if (tagEndResult.end() < 0) return i + 1;
        String tagName = text.substring(nameStart, nameEnd);
        int fullOpenEnd = tagEndResult.end() + 1;
        if (!tagEndResult.selfClosing()) {
            stack.add(new OpenTag(tagName, i, fullOpenEnd));
        }
        return fullOpenEnd;
    }

    private static TagPair findBestPair(List<TagPair> pairs, int offset) {
        TagPair bestPair = null;
        int bestSpan = Integer.MAX_VALUE;
        for (TagPair pair : pairs) {
            if (offset >= pair.openStart() && offset <= pair.closeEnd()) {
                int span = pair.closeEnd() - pair.openStart();
                if (span < bestSpan) {
                    bestSpan = span;
                    bestPair = pair;
                }
            }
        }
        return bestPair;
    }

    private static int scanTagName(String text, int start) {
        if (start >= text.length() || !isTagNameStart(text.charAt(start))) return start;
        int i = start + 1;
        while (i < text.length() && isTagNamePart(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static TagEndResult scanOpeningTagEnd(String text, int start) {
        int length = text.length();
        int j = start;
        while (j < length) {
            char c = text.charAt(j);
            if (c == '"' || c == '\'') {
                j = skipQuote(text, j + 1, c);
            } else if (c == '>') {
                int k = j - 1;
                while (k >= start && Character.isWhitespace(text.charAt(k))) k--;
                boolean isSelfClosing = k >= start && text.charAt(k) == '/';
                return new TagEndResult(j, isSelfClosing);
            } else if (c == '<') {
                j = length;
            } else {
                j++;
            }
        }
        return new TagEndResult(-1, false);
    }

    private static int skipTagAttributes(String text, int start) {
        int length = text.length();
        int j = start;
        while (j < length) {
            char c = text.charAt(j);
            if (c == '"' || c == '\'') {
                j = skipQuote(text, j + 1, c);
            } else if (c == '>') {
                return j;
            } else if (c == '<') {
                j = length;
            } else {
                j++;
            }
        }
        return -1;
    }

    private static int findMatchingOpen(List<OpenTag> stack, String name) {
        for (int idx = stack.size() - 1; idx >= 0; idx--) {
            if (stack.get(idx).name().equalsIgnoreCase(name)) {
                return idx;
            }
        }
        return -1;
    }

    private static int skipQuote(String text, int start, char quote) {
        int length = text.length();
        int j = start;
        while (j < length) {
            if (text.charAt(j) == '\\') {
                j += 2;
                continue;
            }
            if (text.charAt(j) == quote) return j + 1;
            j++;
        }
        return j;
    }

    private static boolean isTagNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    private static boolean isTagNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == ':';
    }

    private static boolean startsWith(String text, int offset, String prefix) {
        if (offset + prefix.length() > text.length()) return false;
        for (int k = 0; k < prefix.length(); k++) {
            if (text.charAt(offset + k) != prefix.charAt(k)) return false;
        }
        return true;
    }

    private static int indexOf(String text, String target, int fromIndex) {
        int max = text.length() - target.length();
        for (int i = fromIndex; i <= max; i++) {
            if (startsWith(text, i, target)) return i;
        }
        return -1;
    }

    private static OffsetRange stringThing(String text, int offset, boolean inner) {
        int length = text.length();
        int i = 0;
        while (i < length) {
            char quote = text.charAt(i);
            if (quote == '"' || quote == '\'' || quote == '`') {
                boolean triple =
                        i + 2 < length
                                && text.charAt(i + 1) == quote
                                && text.charAt(i + 2) == quote;
                int quoteLen = triple ? 3 : 1;
                int open = i;
                int j = i + quoteLen;
                int closeEnd = -1;
                while (j < length) {
                    char ch = text.charAt(j);
                    if (!triple && ch == '\n') break;
                    if (ch == '\\') {
                        j += 2;
                        continue;
                    }
                    boolean closes =
                            !triple
                                    || (j + 2 < length
                                            && text.charAt(j + 1) == quote
                                            && text.charAt(j + 2) == quote);
                    if (ch == quote && closes) {
                        closeEnd = j + quoteLen;
                        break;
                    }
                    j++;
                }
                if (closeEnd < 0) {
                    i = open + quoteLen;
                    continue;
                }
                if (offset >= open && offset < closeEnd) {
                    return inner
                            ? new OffsetRange(open + quoteLen, closeEnd - quoteLen)
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
        int inSymbol = offset;
        if (inSymbol >= text.length() || !Text.isSymbolChar(text.charAt(inSymbol))) {
            if (inSymbol > 0 && Text.isSymbolChar(text.charAt(inSymbol - 1))) inSymbol--;
            else return null;
        }
        int start = inSymbol;
        int end = inSymbol;
        while (start > 0 && Text.isSymbolChar(text.charAt(start - 1))) start--;
        while (end < text.length() && Text.isSymbolChar(text.charAt(end))) end++;
        return new OffsetRange(start, end);
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
        int caretLine = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        if (blank(text, caretLine)) return null;
        int first = caretLine;
        int last = caretLine;
        while (first > 0 && !blank(text, first - 1)) first--;
        while (last < count - 1 && !blank(text, last + 1)) last++;
        int start = Text.lineStart(text, first);
        if (inner) return new OffsetRange(start, Text.lineEnd(text, last));
        int stop = last;
        while (stop < count - 1 && blank(text, stop + 1)) stop++;
        int end = stop < count - 1 ? Text.lineStart(text, stop + 1) : Text.lineEnd(text, stop);
        return new OffsetRange(start, end);
    }

    private static OffsetRange line(String text, int offset, boolean inner) {
        int caretLine = Text.lineOfOffset(text, Text.clamp(offset, 0, text.length()));
        int end = Text.lineEnd(text, caretLine);
        return inner
                ? new OffsetRange(Text.lineStart(text, caretLine), end)
                : new OffsetRange(
                        Text.lineStart(text, caretLine), Text.lineStart(text, caretLine + 1));
    }

    private static OffsetRange defun(Ctx ctx, String text, int offset) {
        OffsetRange fromHost = ctx.port().symbolRangeAt(offset);
        if (fromHost != null) return fromHost;
        OffsetRange braces = pair(text, offset, '{', '}', false);
        if (braces == null) return null;
        while (true) {
            OffsetRange outer = pair(text, braces.start(), '{', '}', false);
            if (outer == null) break;
            braces = outer;
        }
        return braces;
    }

    private static OffsetRange sentence(String text, int offset, boolean inner) {
        if (text.isEmpty()) return null;
        String enders = Text.SENTENCE_ENDERS;
        int start = Text.clamp(offset, 0, text.length() - 1);
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (enders.indexOf(ch) >= 0
                    || (ch == '\n' && start > 1 && text.charAt(start - 2) == '\n')) break;
            start--;
        }
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        int end = Text.clamp(offset, 0, text.length());
        while (end < text.length()
                && enders.indexOf(text.charAt(end)) < 0
                && !(text.charAt(end) == '\n'
                        && end + 1 < text.length()
                        && text.charAt(end + 1) == '\n')) {
            end++;
        }
        if (end < text.length() && enders.indexOf(text.charAt(end)) >= 0) end++;
        if (end <= start) return null;
        if (inner) return new OffsetRange(start, end);
        int withTrailingSpace = end;
        while (withTrailingSpace < text.length() && text.charAt(withTrailingSpace) == ' ')
            withTrailingSpace++;
        return new OffsetRange(start, withTrailingSpace);
    }

    static boolean blank(String text, int line) {
        return text.substring(Text.lineStart(text, line), Text.lineEnd(text, line))
                .trim()
                .isEmpty();
    }
}
