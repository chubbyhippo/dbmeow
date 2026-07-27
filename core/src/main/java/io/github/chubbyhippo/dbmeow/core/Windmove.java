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
import java.util.List;

public final class Windmove {
    private Windmove() {}

    public enum Dir {
        LEFT(true),
        RIGHT(true),
        UP(false),
        DOWN(false);

        private final boolean horizontal;

        Dir(boolean horizontal) {
            this.horizontal = horizontal;
        }

        public boolean horizontal() {
            return horizontal;
        }
    }

    public record DiffSideView(boolean onOriginal, boolean onModified, boolean sideBySide) {}

    public record Rect(int x, int y, int width, int height) {}

    public record Caret(int x, int y) {}

    public record Candidate<T>(T window, Rect rect) {}

    private static String editorFocus(Dir dir) {
        return switch (dir) {
            case LEFT -> "dbmeow.focusLeftEditor";
            case RIGHT -> "dbmeow.focusRightEditor";
            case UP -> "dbmeow.focusAboveEditor";
            case DOWN -> "dbmeow.focusBelowEditor";
        };
    }

    public static String noWindowMessage(Dir dir) {
        return "No window " + dir.name().toLowerCase() + " from selected window";
    }

    public static String plan(Dir dir, DiffSideView diff) {
        if (diff != null && diff.sideBySide()) {
            if (dir == Dir.LEFT && diff.onModified()) return "dbmeow.compareSwitch";
            if (dir == Dir.RIGHT && diff.onOriginal()) return "dbmeow.compareSwitch";
        }
        return editorFocus(dir);
    }

    public static int reference(Dir dir, Rect current, Caret caret) {
        boolean onCaret = caret != null && contains(current, caret);
        if (dir.horizontal()) return onCaret ? caret.y() : current.y() + 1;
        return onCaret ? caret.x() : current.x() + 1;
    }

    public static <T> T pick(
            Dir dir, Rect current, int position, Rect frame, List<Candidate<T>> candidates) {
        List<Candidate<T>> inBand = new ArrayList<>();
        List<Candidate<T>> outOfBand = new ArrayList<>();
        for (Candidate<T> candidate : candidates) {
            if (inBand(dir, candidate.rect(), position)) inBand.add(candidate);
            else outOfBand.add(candidate);
        }
        T aligned = pickAligned(dir, current, frame, inBand);
        return aligned != null ? aligned : pickByBand(dir, current, position, frame, outOfBand);
    }

    private static boolean contains(Rect rect, Caret caret) {
        return caret.x() >= rect.x()
                && caret.x() < rect.x() + rect.width()
                && caret.y() >= rect.y()
                && caret.y() < rect.y() + rect.height();
    }

    private static boolean inBand(Dir dir, Rect rect, int position) {
        int bandLead = dir.horizontal() ? rect.y() : rect.x();
        int bandSize = dir.horizontal() ? rect.height() : rect.width();
        return bandLead <= position && position < bandLead + bandSize;
    }

    private static int initialEdge(Dir dir, Rect frame) {
        return switch (dir) {
            case DOWN -> frame.height();
            case RIGHT -> frame.width();
            case LEFT, UP -> -1;
        };
    }

    private static <T> T pickAligned(
            Dir dir, Rect current, Rect frame, List<Candidate<T>> candidates) {
        boolean horizontal = dir.horizontal();
        int first = horizontal ? current.x() : current.y();
        int last = first + (horizontal ? current.width() : current.height());
        int bestEdge = initialEdge(dir, frame);
        T best = null;
        for (Candidate<T> candidate : candidates) {
            int lead = horizontal ? candidate.rect().x() : candidate.rect().y();
            if (alignedInDir(dir, lead, first, last, bestEdge)) {
                bestEdge = lead;
                best = candidate.window();
            }
        }
        return best;
    }

    private static <T> T pickByBand(
            Dir dir, Rect current, int position, Rect frame, List<Candidate<T>> candidates) {
        boolean horizontal = dir.horizontal();
        int first = horizontal ? current.x() : current.y();
        int last = first + (horizontal ? current.width() : current.height());
        int bestEdge = initialEdge(dir, frame);
        int bestBandDiff = horizontal ? frame.height() : frame.width();
        T best = null;
        for (Candidate<T> candidate : candidates) {
            Rect rect = candidate.rect();
            int lead = horizontal ? rect.x() : rect.y();
            int size = horizontal ? rect.width() : rect.height();
            int bandLead = horizontal ? rect.y() : rect.x();
            int bandSize = horizontal ? rect.height() : rect.width();
            if (!strictlyBeyond(dir, lead, size, first, last)) continue;
            int bandDiff =
                    bandLead > position ? bandLead - position : position - bandLead - bandSize;
            if (bandCloser(dir, lead, bandDiff, bestBandDiff, bestEdge)) {
                bestEdge = lead;
                bestBandDiff = bandDiff;
                best = candidate.window();
            }
        }
        return best;
    }

    private static boolean alignedInDir(Dir dir, int lead, int first, int last, int edge) {
        return switch (dir) {
            case LEFT, UP -> lead > edge && lead <= first;
            case RIGHT -> lead >= last && lead < edge;
            case DOWN -> lead >= first && lead < edge;
        };
    }

    private static boolean strictlyBeyond(Dir dir, int lead, int size, int first, int last) {
        return switch (dir) {
            case LEFT, UP -> lead + size <= first;
            case RIGHT, DOWN -> last <= lead;
        };
    }

    private static boolean bandCloser(
            Dir dir, int lead, int bandDiff, int bestBandDiff, int bestEdge) {
        if (bandDiff < bestBandDiff) return true;
        if (bandDiff != bestBandDiff) return false;
        return switch (dir) {
            case LEFT, UP -> lead > bestEdge;
            case RIGHT, DOWN -> lead < bestEdge;
        };
    }
}
