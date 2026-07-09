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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * meow-inner/bounds/beginning/end-of-thing with the char table, meow-block,
 * meow-to-block, meow-join (the selection half; the kill half is in editing).
 * Name-for-name port of codemeow's things.test.ts.
 */
class ThingsSpec extends SpecDsl {
    @Test
    @DisplayName("given caret inside parens when comma r then inner round is selected forward")
    void innerRoundForward() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(",r");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given caret inside parens when dot r then bounds include the parens and select backward")
    void boundsRoundBackward() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(".r");
        thenSelection("(bar baz)");
        // meow-thing-selection-directions: bounds -> backward (probed on 1.5.0)
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given nested pairs when comma r then the innermost pair wins")
    void innermostPairWins() {
        given("nested", "(a (b<caret>) c)");
        whenKeys(",r");
        thenSelection("b");
    }

    @Test
    @DisplayName("given square and curly things then s and c select them")
    void squareAndCurly() {
        given("square", "a [b<caret> c] d");
        whenKeys(",s");
        thenSelection("b c");

        given("curly", "a {b<caret> c} d");
        whenKeys(".c");
        thenSelection("{b c}");
    }

    @Test
    @DisplayName("given a double quoted string when comma g then the quoted run is selected")
    void doubleQuotedString() {
        given("string", "say \"hi th<caret>ere\" now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("\"hi there\"");
    }

    @Test
    @DisplayName("given a single quoted string when comma g then inner selects the run and dot g keeps the quotes")
    void singleQuotedString() {
        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("'hi there'");
    }

    @Test
    @DisplayName("given a backtick string when comma g then inner selects the run and dot g keeps the backticks")
    void backtickString() {
        given("backticks", "say `hi th<caret>ere` now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("`hi there`");
    }

    @Test
    @DisplayName("given a triple double quoted string when comma g then inner drops all three quotes and dot g keeps them")
    void tripleDoubleQuotedString() {
        given("triple double", "say \"\"\"hi th<caret>ere\"\"\" now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("\"\"\"hi there\"\"\"");
    }

    @Test
    @DisplayName("given a triple single quoted string when comma g then inner drops all three quotes and dot g keeps them")
    void tripleSingleQuotedString() {
        given("triple single", "say '''hi th<caret>ere''' now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("'''hi there'''");
    }

    @Test
    @DisplayName("given a triple backtick fence when comma g then inner drops all three backticks and dot g keeps them")
    void tripleBacktickFence() {
        given("triple backtick", "say ```hi th<caret>ere``` now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("```hi there```");
    }

    @Test
    @DisplayName("given a triple quoted docstring spanning lines when comma g then the whole multiline run is selected")
    void tripleQuotedDocstringSpanningLines() {
        given("multiline docstring", "x = \"\"\"\nhe<caret>llo\nworld\n\"\"\"");
        whenKeys(",g");
        thenSelection("\nhello\nworld\n");
        whenKeys(".g");
        thenSelection("\"\"\"\nhello\nworld\n\"\"\"");
    }

    @Test
    @DisplayName("given an apostrophe earlier on another line when comma g then the real string below still selects")
    void apostropheEarlierAnotherLine() {
        given("stray apostrophe", "don't\nx = 'h<caret>i'");
        whenKeys(",g");
        thenSelection("hi");
    }

    @Test
    @DisplayName("given an unterminated quote when comma g then nothing is selected")
    void unterminatedQuote() {
        given("unterminated", "it'<caret>s fine");
        whenKeys(",g");
        thenNoSelection();
    }

    @Test
    @DisplayName("given a symbol thing when comma e then the symbol is selected")
    void symbolThing() {
        given("symbol", "f<caret>oo_bar baz");
        whenKeys(",e");
        thenSelection("foo_bar");
    }

    @Test
    @DisplayName("given a paragraph when comma p then the block of lines is selected")
    void paragraphInner() {
        given("paragraphs", "aaa\nb<caret>bb\n\nccc");
        whenKeys(",p");
        thenSelection("aaa\nbbb");
    }

    @Test
    @DisplayName("given a paragraph when dot p then trailing blank lines are included")
    void paragraphBounds() {
        given("paragraphs", "aaa\nb<caret>bb\n\nccc");
        whenKeys(".p");
        thenSelection("aaa\nbbb\n\n");
    }

    @Test
    @DisplayName("given a line thing then comma l excludes and dot l includes the newline")
    void lineThing() {
        given("lines", "a<caret>b\ncd");
        whenKeys(",l");
        thenSelection("ab");
        whenKeys(".l");
        thenSelection("ab\n");
    }

    @Test
    @DisplayName("given the buffer thing when comma b then everything is selected")
    void bufferThing() {
        given("buffer", "on<caret>e\ntwo");
        whenKeys(",b");
        thenSelection("one\ntwo");
    }

    @Test
    @DisplayName("given sentences when comma dot then the sentence around point is selected")
    void sentenceThing() {
        given("sentences", "One. Tw<caret>o. Three.");
        whenKeys(",.");
        thenSelection("Two.");
    }

    @Test
    @DisplayName("given a curly block in plain text when comma d then the defun fallback selects the braces")
    void defunFallback() {
        given("pseudo function", "fun x() {\n  bo<caret>dy\n}");
        whenKeys(",d");
        thenSelection("{\n  body\n}");
    }

    @Test
    @DisplayName("given open bracket r then selects from point back to the thing beginning with cursor at the beginning")
    void beginningOfThing() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("[r");
        thenSelection("b");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given close bracket r then selects from point to the thing end with cursor at the end")
    void endOfThing() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("]r");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given angle bracket aliases then they behave like square brackets")
    void angleBracketAlias() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("<r");
        thenCaretAtSelectionStart();
        thenSelection("b");
    }

    @Test
    @DisplayName("given no thing at point when comma r then the selection is unchanged")
    void noThingAtPoint() {
        given("no parens", "he<caret>llo");
        whenKeys(",r");
        thenNoSelection();
    }

    @Test
    @DisplayName("given o then the enclosing block including delimiters is selected")
    void blockIncludesDelimiters() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("o");
        thenSelection("(bar baz)");
        thenSelType(SelType.BLOCK);
    }

    @Test
    @DisplayName("given a block selection when o again then it expands to the parent block")
    void blockExpandsToParent() {
        given("nested", "((x<caret>))");
        whenKeys("o");
        thenSelection("(x)");
        whenKeys("o");
        thenSelection("((x))");
    }

    @Test
    @DisplayName("given a negative argument when o then the block selection is backward")
    void blockNegativeBackward() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("-o");
        thenSelection("(bar baz)");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given O then selects from point to the end of the current block")
    void toBlockEnd() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("O");
        thenSelection("ar baz)");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName("given m then the join region between this line and the previous non-empty one is selected")
    void joinRegionBackward() {
        given("indented continuation", "one\n  t<caret>wo");
        whenKeys("m");
        thenSelType(SelType.JOIN);
        thenSelection("\n  ");
    }

    @Test
    @DisplayName("given the first line when m then nothing is selected")
    void joinFirstLineNothing() {
        given("first line", "o<caret>ne\ntwo");
        whenKeys("m");
        thenNoSelection();
    }

    @Test
    @DisplayName("given negative argument when - m then the join region reaches forward instead")
    void joinForwardNegative() {
        given("forward join", "o<caret>ne\n  two");
        whenKeys("-m");
        thenSelType(SelType.JOIN);
        thenSelection("\n  ");
    }
}
