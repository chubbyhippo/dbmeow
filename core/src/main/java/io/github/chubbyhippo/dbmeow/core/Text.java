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

public final class Text {
    private Text() {}

    @FunctionalInterface
    public interface CharPredicate {
        boolean test(char ch);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static String escapeRegExp(String pattern) {
        return pattern.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    public static int lineOfOffset(String text, int offset) {
        int line = 0;
        int end = clamp(offset, 0, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    public static int lineCount(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    public static int lineStart(String text, int line) {
        if (line <= 0) return 0;
        int newlinesSeen = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && ++newlinesSeen == line) return i + 1;
        }
        return text.length();
    }

    public static int lineEnd(String text, int line) {
        int start = lineStart(text, line);
        int newline = text.indexOf('\n', start);
        if (newline < 0) return text.length();
        return newline > start && text.charAt(newline - 1) == '\r' ? newline - 1 : newline;
    }

    public static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch);
    }

    public static boolean isSymbolChar(char ch) {
        return isWordChar(ch) || ch == '_' || ch == '$';
    }

    public static CharPredicate charPred(boolean symbol) {
        return symbol ? Text::isSymbolChar : Text::isWordChar;
    }

    public static int indexOfChar(String text, char ch, int from) {
        for (int i = Math.max(from, 0); i < text.length(); i++) {
            if (text.charAt(i) == ch) return i;
        }
        return -1;
    }

    public static int lastIndexOfChar(String text, char ch, int from) {
        for (int i = Math.min(from, text.length() - 1); i >= 0; i--) {
            if (text.charAt(i) == ch) return i;
        }
        return -1;
    }

    public static int nthCharTarget(
            String text, char ch, int caret, int count, boolean backward, boolean till) {
        int found = -1;
        int from = backward ? (till ? caret - 2 : caret - 1) : (till ? caret + 1 : caret);
        for (int step = 0; step < count; step++) {
            found = backward ? lastIndexOfChar(text, ch, from) : indexOfChar(text, ch, from);
            if (found < 0) return -1;
            from = backward ? found - 1 : found + 1;
        }
        if (found < 0) return -1;
        if (backward) return till ? found + 1 : found;
        return till ? found : found + 1;
    }

    public static final String SENTENCE_ENDERS = ".!?";

    private static boolean isSentenceGap(char ch) {
        return Character.isWhitespace(ch) || SENTENCE_ENDERS.indexOf(ch) >= 0;
    }

    public static int nextSentenceEnd(String text, int from, int count) {
        int i = clamp(from, 0, text.length());
        for (int step = 0; step < count; step++) {
            while (i < text.length() && SENTENCE_ENDERS.indexOf(text.charAt(i)) < 0) i++;
            while (i < text.length() && SENTENCE_ENDERS.indexOf(text.charAt(i)) >= 0) i++;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        }
        return i;
    }

    public static int prevSentenceStart(String text, int from, int count) {
        int i = clamp(from, 0, text.length());
        for (int step = 0; step < count; step++) {
            while (i > 0 && isSentenceGap(text.charAt(i - 1))) i--;
            while (i > 0 && !isSentenceGap(text.charAt(i - 1))) i--;
        }
        return i;
    }

    private static int lineStartAt(String text, int offset) {
        int i = offset;
        while (i > 0 && text.charAt(i - 1) != '\n') i--;
        return i;
    }

    private static int followingLineStart(String text, int lineStartOffset) {
        int i = lineStartOffset;
        while (i < text.length() && text.charAt(i) != '\n') i++;
        return i < text.length() ? i + 1 : i;
    }

    private static boolean blankLineAt(String text, int lineStartOffset) {
        int i = lineStartOffset;
        while (i < text.length() && text.charAt(i) != '\n') {
            if (!Character.isWhitespace(text.charAt(i))) return false;
            i++;
        }
        return true;
    }

    public static int nextParagraphEnd(String text, int from, int count) {
        int pos = clamp(from, 0, text.length());
        for (int step = 0; step < count; step++) {
            int i = lineStartAt(text, pos);
            while (i < text.length() && blankLineAt(text, i)) i = followingLineStart(text, i);
            while (i < text.length() && !blankLineAt(text, i)) i = followingLineStart(text, i);
            pos = i;
        }
        return pos;
    }

    public static int prevParagraphStart(String text, int from, int count) {
        int pos = clamp(from, 0, text.length());
        for (int step = 0; step < count; step++) {
            if (pos > 0) {
                int start = paragraphStartBefore(text, pos);
                pos = start < pos ? start : paragraphStartBefore(text, start - 1);
            }
        }
        return pos;
    }

    private static int paragraphStartBefore(String text, int offset) {
        int i = lineStartAt(text, offset);
        while (i > 0 && blankLineAt(text, i)) i = lineStartAt(text, i - 1);
        while (i > 0 && !blankLineAt(text, lineStartAt(text, i - 1))) i = lineStartAt(text, i - 1);
        boolean prevLineEmpty =
                i > 0 && text.charAt(i - 1) == '\n' && (i == 1 || text.charAt(i - 2) == '\n');
        return prevLineEmpty ? i - 1 : i;
    }

    public static final class Words {
        private Words() {}

        public static int nextEnd(String text, int from, int count, CharPredicate isWord) {
            int i = clamp(from, 0, text.length());
            for (int step = 0; step < count; step++) {
                while (i < text.length() && !isWord.test(text.charAt(i))) i++;
                while (i < text.length() && isWord.test(text.charAt(i))) i++;
            }
            return i;
        }

        public static int prevStart(String text, int from, int count, CharPredicate isWord) {
            int i = clamp(from, 0, text.length());
            for (int step = 0; step < count; step++) {
                while (i > 0 && !isWord.test(text.charAt(i - 1))) i--;
                while (i > 0 && isWord.test(text.charAt(i - 1))) i--;
            }
            return i;
        }

        public static int fixSelectionMark(String text, int pos, int mark, CharPredicate isWord) {
            int probe = clamp(mark > pos ? pos : pos - 1, 0, Math.max(text.length() - 1, 0));
            int[] bounds = boundsAt(text, probe, isWord);
            if (bounds == null) return mark;
            return mark > pos ? Math.min(mark, bounds[1]) : Math.max(mark, bounds[0]);
        }

        public static int[] boundsAt(String text, int offset, CharPredicate isWord) {
            int inWord = offsetInWord(text, offset, isWord);
            if (inWord < 0) return null;
            int start = inWord;
            int end = inWord;
            while (start > 0 && isWord.test(text.charAt(start - 1))) start--;
            while (end < text.length() && isWord.test(text.charAt(end))) end++;
            return new int[] {start, end};
        }

        private static int offsetInWord(String text, int offset, CharPredicate isWord) {
            if (offset < text.length() && isWord.test(text.charAt(offset))) return offset;
            if (offset > 0 && isWord.test(text.charAt(offset - 1))) return offset - 1;
            int scan = offset;
            while (scan < text.length() && !isWord.test(text.charAt(scan))) scan++;
            return scan < text.length() ? scan : -1;
        }
    }
}
