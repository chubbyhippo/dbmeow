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
    @DisplayName(
            "given caret inside parens when dot r then bounds include the parens and select backward")
    void boundsRoundBackward() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(".r");
        thenSelection("(bar baz)");
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
    @DisplayName("given caret inside parens when comma or dot with parens then performs same as r")
    void parenAliasKeys() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(",(");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(",)");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(".(");
        thenSelection("(bar baz)");
        thenCaretAtSelectionStart();

        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys(".)");
        thenSelection("(bar baz)");
        thenCaretAtSelectionStart();

        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("[(");
        thenSelection("b");
        thenCaretAtSelectionStart();

        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("])");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();
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
    @DisplayName(
            "given caret inside square brackets when comma or dot with square brackets then"
                    + " performs same as s")
    void squareAliasKeys() {
        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys(",[");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys(",]");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys(".[");
        thenSelection("[bar baz]");
        thenCaretAtSelectionStart();

        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys(".]");
        thenSelection("[bar baz]");
        thenCaretAtSelectionStart();

        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys("[[");
        thenSelection("b");
        thenCaretAtSelectionStart();

        given("square pair", "foo [b<caret>ar baz] qux");
        whenKeys("]]");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given caret inside curly brackets when comma or dot with curly brackets then performs"
                    + " same as c")
    void curlyAliasKeys() {
        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys(",{");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys(",}");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys(".{");
        thenSelection("{bar baz}");
        thenCaretAtSelectionStart();

        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys(".}");
        thenSelection("{bar baz}");
        thenCaretAtSelectionStart();

        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys("[{");
        thenSelection("b");
        thenCaretAtSelectionStart();

        given("curly pair", "foo {b<caret>ar baz} qux");
        whenKeys("]}");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given angle pair when comma dot or a brackets then selects inner bounds start end")
    void anglePair() {
        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(",a");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(".a");
        thenSelection("<bar baz>");
        thenCaretAtSelectionStart();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(",<");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(",>");
        thenSelection("bar baz");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(".<");
        thenSelection("<bar baz>");
        thenCaretAtSelectionStart();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys(".>");
        thenSelection("<bar baz>");
        thenCaretAtSelectionStart();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys("[a");
        thenSelection("b");
        thenCaretAtSelectionStart();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys("]a");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys("[<");
        thenSelection("b");
        thenCaretAtSelectionStart();

        given("angle pair", "foo <b<caret>ar baz> qux");
        whenKeys("]>");
        thenSelection("ar baz");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given tag thing when comma t then selects between angle brackets and dot t selects the"
                    + " whole tag")
    void tagInnerAndBounds() {
        given("tag", "foo <tag>con<caret>tent</tag> bar");
        whenKeys(",t");
        thenSelection("content");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        whenKeys(".t");
        thenSelection("<tag>content</tag>");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName("given nested tags when comma t then innermost tag is selected")
    void nestedTagsInnermost() {
        given("nested tags", "<div><span>he<caret>llo</span></div>");
        whenKeys(",t");
        thenSelection("hello");
        whenKeys(".t");
        thenSelection("<span>hello</span>");
    }

    @Test
    @DisplayName("given tag with attributes when comma t and dot t then tag is properly selected")
    void tagWithAttributes() {
        given("tag with attributes", "<tag class=\"foo\" attr='bar'>val<caret>ue</tag>");
        whenKeys(",t");
        thenSelection("value");
        whenKeys(".t");
        thenSelection("<tag class=\"foo\" attr='bar'>value</tag>");
    }

    @Test
    @DisplayName(
            "given tag with attribute containing angle bracket when comma t then inner is selected")
    void tagWithAttributeContainingAngleBracket() {
        given("tag with angle in attribute", "<tag attr=\"a > b\">inn<caret>er</tag>");
        whenKeys(",t");
        thenSelection("inner");
        whenKeys(".t");
        thenSelection("<tag attr=\"a > b\">inner</tag>");
    }

    @Test
    @DisplayName("given caret on opening or closing tag when comma t then inner is selected")
    void caretOnOpeningOrClosingTag() {
        given("caret on open tag", "<<caret>div>hello</div>");
        whenKeys(",t");
        thenSelection("hello");

        given("caret on close tag", "<div>hello</di<caret>v>");
        whenKeys(",t");
        thenSelection("hello");
    }

    @Test
    @DisplayName("given open and close bracket t then selects to start and end of tag")
    void tagBeginAndEnd() {
        given("tag", "foo <tag>con<caret>tent</tag> bar");
        whenKeys("[t");
        thenSelection("con");
        thenCaretAtSelectionStart();

        given("tag", "foo <tag>con<caret>tent</tag> bar");
        whenKeys("]t");
        thenSelection("tent");
        thenCaretAtSelectionEnd();
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
    @DisplayName(
            "given a single quoted string when comma g then inner selects the run and dot g keeps the quotes")
    void singleQuotedString() {
        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("'hi there'");
    }

    @Test
    @DisplayName(
            "given single or double quotes when comma or dot with quote chars then performs same as"
                    + " g")
    void quoteAliasKeys() {
        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys(",'");
        thenSelection("hi there");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys(".'");
        thenSelection("'hi there'");
        thenCaretAtSelectionStart();

        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys("['");
        thenSelection("hi th");
        thenCaretAtSelectionStart();

        given("single quotes", "say 'hi th<caret>ere' now");
        whenKeys("]'");
        thenSelection("ere");
        thenCaretAtSelectionEnd();

        given("double quotes", "say \"hi th<caret>ere\" now");
        whenKeys(",\"");
        thenSelection("hi there");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("double quotes", "say \"hi th<caret>ere\" now");
        whenKeys(".\"");
        thenSelection("\"hi there\"");
        thenCaretAtSelectionStart();

        given("double quotes", "say \"hi th<caret>ere\" now");
        whenKeys("[\"");
        thenSelection("hi th");
        thenCaretAtSelectionStart();

        given("double quotes", "say \"hi th<caret>ere\" now");
        whenKeys("]\"");
        thenSelection("ere");
        thenCaretAtSelectionEnd();
    }

    @Test
    @DisplayName(
            "given a backtick string when comma g then inner selects the run and dot g keeps the backticks")
    void backtickString() {
        given("backticks", "say `hi th<caret>ere` now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("`hi there`");
    }

    @Test
    @DisplayName(
            "given a triple double quoted string when comma g then inner drops the quotes and dot "
                    + "g keeps them")
    void tripleDoubleQuotedString() {
        given("triple double", "say \"\"\"hi th<caret>ere\"\"\" now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("\"\"\"hi there\"\"\"");
    }

    @Test
    @DisplayName(
            "given a triple single quoted string when comma g then inner drops the quotes and dot "
                    + "g keeps them")
    void tripleSingleQuotedString() {
        given("triple single", "say '''hi th<caret>ere''' now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("'''hi there'''");
    }

    @Test
    @DisplayName(
            "given a triple backtick fence when comma g then inner drops all three backticks and dot g keeps them")
    void tripleBacktickFence() {
        given("triple backtick", "say ```hi th<caret>ere``` now");
        whenKeys(",g");
        thenSelection("hi there");
        whenKeys(".g");
        thenSelection("```hi there```");
    }

    @Test
    @DisplayName(
            "given a triple quoted docstring spanning lines when comma g then the whole multiline run is selected")
    void tripleQuotedDocstringSpanningLines() {
        given("multiline docstring", "x = \"\"\"\nhe<caret>llo\nworld\n\"\"\"");
        whenKeys(",g");
        thenSelection("\nhello\nworld\n");
        whenKeys(".g");
        thenSelection("\"\"\"\nhello\nworld\n\"\"\"");
    }

    @Test
    @DisplayName(
            "given an apostrophe earlier on another line when comma g then the real string below still selects")
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
    @DisplayName(
            "given slash or question delimiters when comma or dot then selects inner and bounds")
    void slashAndQuestionDelimiters() {
        given("slash pair", "val regex = /foo\\/b<caret>ar/g");
        whenKeys(",/");
        thenSelection("foo\\/bar");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("slash pair", "val regex = /foo\\/b<caret>ar/g");
        whenKeys("./");
        thenSelection("/foo\\/bar/");
        thenCaretAtSelectionStart();

        given("slash pair", "val regex = /foo\\/b<caret>ar/g");
        whenKeys("[/");
        thenSelection("foo\\/b");
        thenCaretAtSelectionStart();

        given("slash pair", "val regex = /foo\\/b<caret>ar/g");
        whenKeys("]/");
        thenSelection("ar");
        thenCaretAtSelectionEnd();

        given("question pair", "pattern ?foo\\?b<caret>ar? flag");
        whenKeys(",?");
        thenSelection("foo\\?bar");
        thenSelType(SelType.TRANSIENT);
        thenCaretAtSelectionEnd();

        given("question pair", "pattern ?foo\\?b<caret>ar? flag");
        whenKeys(".?");
        thenSelection("?foo\\?bar?");
        thenCaretAtSelectionStart();

        given("question pair", "pattern ?foo\\?b<caret>ar? flag");
        whenKeys("[?");
        thenSelection("foo\\?b");
        thenCaretAtSelectionStart();

        given("question pair", "pattern ?foo\\?b<caret>ar? flag");
        whenKeys("]?");
        thenSelection("ar");
        thenCaretAtSelectionEnd();
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
    @DisplayName(
            "given a curly block in plain text when comma d then the defun fallback selects the braces")
    void defunFallback() {
        given("pseudo function", "fun x() {\n  bo<caret>dy\n}");
        whenKeys(",d");
        thenSelection("{\n  body\n}");
    }

    @Test
    @DisplayName(
            "given open bracket r then selects from point back to the thing beginning with cursor at the beginning")
    void beginningOfThing() {
        given("round pair", "foo (b<caret>ar baz) qux");
        whenKeys("[r");
        thenSelection("b");
        thenCaretAtSelectionStart();
    }

    @Test
    @DisplayName(
            "given close bracket r then selects from point to the thing end with cursor at the end")
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
    @DisplayName(
            "given m then the join region between this line and the previous non-empty one is selected")
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

    @Test
    @DisplayName("given a CRLF document then the line thing bounds include the whole delimiter")
    void crlfLineThingBoundsIncludeWholeDelimiter() {
        given("two crlf lines", "a<caret>b\r\ncd");
        whenKeys(".l");
        thenSelection("ab\r\n");
    }
}
