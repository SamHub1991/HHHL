package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownRichTextPresentationTest {
    @Test
    fun parsesMarkdownBlocksForParagraphQuoteAndCodeBlock() {
        val blocks = parseMarkdownBlocks(
            """
            Hello **Sharkey**
            > quoted [site](https://dc.hhhl.cc) #tag
            ```
            val x = 1
            ```
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        val paragraph = assertIs<MarkdownBlock.Paragraph>(blocks[0])
        val quote = assertIs<MarkdownBlock.Quote>(blocks[1])
        val code = assertIs<MarkdownBlock.Code>(blocks[2])

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("Hello "),
                InlineMarkdownSpan.Text("Sharkey", InlineMarkdownStyle.Bold),
            ),
            paragraph.spans,
        )
        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("quoted "),
                InlineMarkdownSpan.Link("site", "https://dc.hhhl.cc"),
                InlineMarkdownSpan.Text(" #tag"),
            ),
            quote.spans,
        )
        assertEquals("val x = 1", code.value)
    }

    @Test
    fun parsesInlineMarkdownWithoutBreakingRichTextTokens() {
        val spans = parseInlineMarkdown(
            "Hi @alice_name **bold #topic** *italic* `code()` [docs](https://dc.hhhl.cc/docs) :blobcat:",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("Hi @alice_name "),
                InlineMarkdownSpan.Text("bold #topic", InlineMarkdownStyle.Bold),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("italic", InlineMarkdownStyle.Italic),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("code()", InlineMarkdownStyle.Code, richTextEnabled = false),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs", "https://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" :blobcat:"),
            ),
            spans,
        )
    }

    @Test
    fun parsesMarkdownLinksWithNestedLabelAndBalancedUrlParentheses() {
        val spans = parseInlineMarkdown(
            "See [docs [v2]](https://dc.hhhl.cc/wiki/a_(b)) and [plain](https://dc.hhhl.cc/path)",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("See "),
                InlineMarkdownSpan.Link("docs [v2]", "https://dc.hhhl.cc/wiki/a_(b)"),
                InlineMarkdownSpan.Text(" and "),
                InlineMarkdownSpan.Link("plain", "https://dc.hhhl.cc/path"),
            ),
            spans,
        )
    }

    @Test
    fun keepsMalformedMarkdownLinksAsPlainTextWithoutSwallowingFollowingMfm() {
        val spans = parseInlineMarkdown(
            "bad [docs](https://dc.hhhl.cc/path $[x2 ok]",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("bad [docs](https://dc.hhhl.cc/path "),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
    }

    @Test
    fun parsesCommonHtmlAndMfmTextSpans() {
        val spans = parseInlineMarkdown(
            "A <small>small</small> <strong>bold</strong> <sub>2</sub><sup>+</sup> $[x2 big] $[blur quiet]",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("A "),
                InlineMarkdownSpan.Text("small", InlineMarkdownStyle.Small),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bold", InlineMarkdownStyle.Bold),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("2", InlineMarkdownStyle.Sub),
                InlineMarkdownSpan.Text("+", InlineMarkdownStyle.Sup),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("big", InlineMarkdownStyle.X2),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("quiet", InlineMarkdownStyle.Blurry),
            ),
            spans,
        )
    }

    @Test
    fun parsesHtmlLinksRubyAndEmojiAltText() {
        val spans = parseInlineMarkdown(
            """See <a href="https://dc.hhhl.cc/notes/abc"><span>note</span></a> <ruby>漢<rp>(</rp><rt>kan</rt><rp>)</rp></ruby> <img alt=":blobcat:" />""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("See "),
                InlineMarkdownSpan.Link("note", "https://dc.hhhl.cc/notes/abc"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Ruby("漢", "kan"),
                InlineMarkdownSpan.Text(" :blobcat:"),
            ),
            spans,
        )
    }

    @Test
    fun parsesRichMarkdownLinkLabelsAsClickablePlainLabels() {
        val spans = parseInlineMarkdown("""[$[fg.color=ff0000 **docs** :blobcat:] \[v2\]](https://dc.hhhl.cc/docs)""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs :blobcat: [v2]",
                    url = "https://dc.hhhl.cc/docs",
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesParagraphSpanAndLineBreakHtmlAsPlainInlineText() {
        val spans = parseInlineMarkdown(
            """<p>Hello<br><span data-x="1">world &amp; friends</span></p>""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("Hello\nworld & friends"),
            ),
            spans,
        )
    }

    @Test
    fun keepsOverlongInlineHtmlAsTextWithoutExpensiveParsing() {
        val longAttribute = "x".repeat(600)
        val longBody = "x".repeat(4_200)

        assertEquals(
            listOf(InlineMarkdownSpan.Text("<span data-x=\"$longAttribute\">body</span>")),
            parseInlineMarkdown("<span data-x=\"$longAttribute\">body</span>"),
        )
        assertEquals(
            listOf(InlineMarkdownSpan.Text("<small>$longBody</small>")),
            parseInlineMarkdown("<small>$longBody</small>"),
        )
    }

    @Test
    fun parsesMfmRubyLinkScaleAndPlainSpans() {
        val spans = parseInlineMarkdown(
            """$[ruby 漢 kan] $[link https://dc.hhhl.cc docs] $[link docs https://dc.hhhl.cc/docs] $[scale.x=3 large] $[plain **not bold** #tag]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Ruby("漢", "kan"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs", "https://dc.hhhl.cc"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs", "https://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "large",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 3.0f, scaleY = 1.0f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("**not bold** #tag", richTextEnabled = false),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmUnicodeCodepoints() {
        val spans = parseInlineMarkdown("""$[unicode 1f600 2764,fe0f] ${'$'}{unicode U+1F44D} $[unicode d800 bad]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("😀❤️ 👍 d800 bad"),
            ),
            spans,
        )
        assertEquals(true, "$[unicode 1f600]".containsValidMfmSyntax())
        assertEquals(true, "${'$'}{unicode 1f44d}".containsValidMfmSyntax())
    }

    @Test
    fun parsesMfmUnixtimeAsCompactDateLabel() {
        val seconds = assertIs<InlineMarkdownSpan.Text>(parseInlineMarkdown("$[unixtime 946728000]").single())
        val millis = assertIs<InlineMarkdownSpan.Text>(parseInlineMarkdown("$[unixtime 946728000000]").single())
        val invalid = assertIs<InlineMarkdownSpan.Text>(parseInlineMarkdown("$[unixtime bad]").single())

        assertEquals(true, seconds.value.startsWith("2000-01-"))
        assertEquals(true, millis.value.startsWith("2000-01-"))
        assertEquals("bad", invalid.value)
    }

    @Test
    fun parsesQuotedMfmRubyAndLinkValues() {
        val spans = parseInlineMarkdown(
            """$[ruby "超 高校級" "Ultimate Lucky Student"] $[link https://dc.hhhl.cc "docs page"] $[link "docs page" https://dc.hhhl.cc/docs]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Ruby("超 高校級", "Ultimate Lucky Student"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs page", "https://dc.hhhl.cc"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs page", "https://dc.hhhl.cc/docs"),
            ),
            spans,
        )
    }

    @Test
    fun keepsMfmClosingMarkersInsideQuotedValues() {
        val spans = parseInlineMarkdown(
            """$[ruby "A ] B" "note ] text"] $[link "docs ] page" https://dc.hhhl.cc/docs] $[fg.color="#ff0000" "red ] text"]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Ruby("A ] B", "note ] text"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs ] page", "https://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "red ] text",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
            ),
            spans,
        )
    }

    @Test
    fun carriesOuterMfmStyleIntoLinksAndRuby() {
        val spans = parseInlineMarkdown(
            """$[fg.color=ff0000 $[link docs https://dc.hhhl.cc/docs] $[ruby 漢 kan]]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc/docs",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
                InlineMarkdownSpan.Text(" ", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
                InlineMarkdownSpan.Ruby(
                    base = "漢",
                    annotation = "kan",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
            ),
            spans,
        )
    }

    @Test
    fun directMfmLinkAndRubyStylesApplySemanticOptions() {
        val spans = parseInlineMarkdown(
            """$[link.color=ff0000 docs https://dc.hhhl.cc] $[ruby.bg=001122 漢 kan] $[link.border.color=abcdef,w=2 docs https://dc.hhhl.cc/docs] $[ruby.scale=1.5 漢 kan]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Ruby(
                    base = "漢",
                    annotation = "kan",
                    mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "001122"),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc/docs",
                    mfmStyle = MfmInlineStyle.Default.copy(borderColor = "abcdef", borderWidthDp = 2f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Ruby(
                    base = "漢",
                    annotation = "kan",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 1.5f, scaleY = 1.5f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun mfmLinkLabelsRenderNestedRichTextAsPlainClickableLabels() {
        val spans = parseInlineMarkdown(
            """$[link "$[fg.color=ff0000 **docs** :blobcat:]" https://dc.hhhl.cc/docs] $[link $[x2 wiki] https://dc.hhhl.cc/wiki]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link("docs :blobcat:", "https://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("wiki", "https://dc.hhhl.cc/wiki"),
            ),
            spans,
        )
    }

    @Test
    fun linksAcceptHttpSchemesCaseInsensitivelyButStillRejectUnsafeSchemes() {
        assertEquals(
            listOf(
                InlineMarkdownSpan.Link("docs", "HTTPS://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("site", "HTTP://dc.hhhl.cc"),
                InlineMarkdownSpan.Text(" $[link bad javascript:alert(1)]"),
            ),
            parseInlineMarkdown(
                """[docs](HTTPS://dc.hhhl.cc/docs) $[link site HTTP://dc.hhhl.cc] $[link bad javascript:alert(1)]""",
            ),
        )
    }

    @Test
    fun braceMfmLinkAndRubyStylesApplySemanticOptions() {
        val spans = parseInlineMarkdown(
            """${'$'}{link.color=ff0000 docs https://dc.hhhl.cc} ${'$'}{ruby.border.color=abcdef,w=2 漢 kan}""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Ruby(
                    base = "漢",
                    annotation = "kan",
                    mfmStyle = MfmInlineStyle.Default.copy(borderColor = "abcdef", borderWidthDp = 2f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMultilineMfmRainbowSpans() {
        val spans = parseInlineMarkdown(
            """
            $[rainbow
            说谎的这
            辈子硬不起来
            @sunxiaochuan
            ]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "说谎的这\n辈子硬不起来\n@sunxiaochuan",
                    InlineMarkdownStyle.Rainbow,
                ),
            ),
            spans,
        )
    }

    @Test
    fun unwrapsNestedMfmFunctionText() {
        val spans = parseInlineMarkdown("""$[x4 $[rainbow 说谎的这辈子硬不起来]]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "说谎的这辈子硬不起来",
                    InlineMarkdownStyle.Rainbow,
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 2.0f, scaleY = 2.0f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmColorFontTransformAndNestedSpans() {
        val spans = parseInlineMarkdown(
            """$[fg.color=ff0000 red $[font.monospace code]] $[bg.color=00ff00 bg] $[rotate.deg=15 tilt] $[position.x=1,y=-1 pos] $[flip.h flip]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("red ", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
                InlineMarkdownSpan.Text(
                    "code",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000", font = MfmFont.Monospace),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bg", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "00ff00")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("tilt", mfmStyle = MfmInlineStyle.Default.copy(rotateDeg = 15f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("pos", mfmStyle = MfmInlineStyle.Default.copy(positionX = 1f, positionY = -1f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("flip", mfmStyle = MfmInlineStyle.Default.copy(flipX = true, flipY = false)),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmBorderOptionsAndCarriesThemIntoNestedSpans() {
        val spans = parseInlineMarkdown(
            """$[border.color=00aaff,width=3,radius=12 framed $[link docs https://dc.hhhl.cc]] $[border.thick,nocorner hard] $[border.width=99,radius=-1 capped]""",
        )

        val framed = MfmInlineStyle.Default.copy(
            borderColor = "00aaff",
            borderWidthDp = 3f,
            borderRadiusDp = 12f,
        )
        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("framed ", mfmStyle = framed),
                InlineMarkdownSpan.Link("docs", "https://dc.hhhl.cc", mfmStyle = framed),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "hard",
                    mfmStyle = MfmInlineStyle.Default.copy(borderWidthDp = 2f, borderRadiusDp = 0f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "capped",
                    mfmStyle = MfmInlineStyle.Default.copy(borderWidthDp = 4f, borderRadiusDp = 0f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmCaseInsensitiveNamesHashColorsAndClampedTransforms() {
        val spans = parseInlineMarkdown(
            """$[FG.color=#ABCDEF red] $[Font.Monospace code] $[scale.x=99,y=-2 scaled] $[rotate.deg=999 tilt] $[position.x=99,y=-99 move]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("red", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "abcdef")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("code", mfmStyle = MfmInlineStyle.Default.copy(font = MfmFont.Monospace)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "scaled",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 4f, scaleY = 0.25f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("tilt", mfmStyle = MfmInlineStyle.Default.copy(rotateDeg = 360f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "move",
                    mfmStyle = MfmInlineStyle.Default.copy(positionX = 4f, positionY = -4f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesShortMfmHexColors() {
        val spans = parseInlineMarkdown("""$[fg.color=#f00 red] $[bg.color=0f08 green] $[border.color=abc box]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("red", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("green", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "00ff0088")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "box",
                    mfmStyle = MfmInlineStyle.Default.copy(borderColor = "aabbcc", borderWidthDp = 1f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun keepsMfmOptionValuesCaseSensitiveWhileMatchingKeysCaseInsensitive() {
        val spans = parseInlineMarkdown(
            """$[FG.COLOR=#ABCDEF "Keep CASE Text"] $[SCALE.X="2",Y="1.5" BigCase] $[LINK "Docs Page" https://dc.hhhl.cc/CasePath]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "Keep CASE Text",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "abcdef"),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "BigCase",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 2f, scaleY = 1.5f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("Docs Page", "https://dc.hhhl.cc/CasePath"),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmDecimalAndSignedOptionsWithoutSplittingDots() {
        val spans = parseInlineMarkdown(
            """$[scale.x=1.5,y=0.75 scaled] $[rotate.deg=-12.5 tilt] $[position.x=-0.5,y=1.25 move]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "scaled",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 1.5f, scaleY = 0.75f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("tilt", mfmStyle = MfmInlineStyle.Default.copy(rotateDeg = -12.5f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "move",
                    mfmStyle = MfmInlineStyle.Default.copy(positionX = -0.5f, positionY = 1.25f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesBraceMfmParamsAndMixedNestedFunctions() {
        val spans = parseInlineMarkdown(
            """${'$'}{fg.color=ff0000 red $[scale.x=2,y=1 big]} $[x2 ${'$'}{bg.color=00ff00 glow}]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("red ", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
                InlineMarkdownSpan.Text(
                    "big",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000", scaleX = 2f, scaleY = 1f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "glow",
                    InlineMarkdownStyle.X2,
                    mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "00ff00"),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMixedMfmNestingWithoutPrematureCloseCharacters() {
        val spans = parseInlineMarkdown(
            """$[x2 ${'$'}{fg.color=ff0000 a]b}] ${'$'}{bg.color=00ff00 $[x2 c}d]}""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "a]b",
                    InlineMarkdownStyle.X2,
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "c}d",
                    InlineMarkdownStyle.X2,
                    mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "00ff00"),
                ),
            ),
            spans,
        )
    }

    @Test
    fun capsMfmNestingDepthAsPlainText() {
        val text = buildString {
            repeat(20) { append("$[x2 ") }
            append("deep")
            repeat(20) { append(']') }
        }
        val spans = parseInlineMarkdown(text)

        assertEquals(1, spans.size)
        val span = assertIs<InlineMarkdownSpan.Text>(spans.single())
        assertEquals(true, span.value.contains("deep"))
    }

    @Test
    fun capsMixedMfmCloseScannerDepthAsPlainText() {
        val text = buildString {
            repeat(10) { append("$[x2 ") }
            repeat(10) { append("${'$'}{fg.color=ff0000 ") }
            append("deep")
            repeat(10) { append('}') }
            repeat(10) { append(']') }
        }
        val spans = parseInlineMarkdown(text)

        assertEquals(1, spans.size)
        val span = assertIs<InlineMarkdownSpan.Text>(spans.single())
        assertEquals(true, span.value.contains("deep"))
    }

    @Test
    fun keepsBrokenMfmSyntaxAsPlainText() {
        val spans = parseInlineMarkdown("""before $[x2 broken $[rainbow text] after ${'$'}{fg.color=ff0000 red""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("""before $[x2 broken $[rainbow text] after ${'$'}{fg.color=ff0000 red"""),
            ),
            spans,
        )
    }

    @Test
    fun keepsMalformedClosedMfmAsTextWithoutSwallowingFollowingRichText() {
        val spans = parseInlineMarkdown("""before ${'$'}{name} middle $[fg.color=zzzzzz bad] after $[x2 ok]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("""before name middle bad after """),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
    }

    @Test
    fun rendersPlainMfmInterpolationAsText() {
        val spans = parseInlineMarkdown("""hello ${'$'}{name} and ${'$'}{user_id}""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("hello name and user_id"),
            ),
            spans,
        )
    }

    @Test
    fun keepsInvalidOrEmptyMfmInterpolationTextWithoutCrashing() {
        val spans = parseInlineMarkdown("""a ${'$'}{} b ${'$'}{two words} c $[x2 ok]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("a ${'$'}{} b ${'$'}{two words} c "),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
    }

    @Test
    fun keepsUnknownBraceMfmTextWithoutTreatingItAsSyntax() {
        val spans = parseInlineMarkdown("""a ${'$'}{two words} b ${'$'}{unknown text} c ${'$'}{fg.color=ff0000 red}""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("a ${'$'}{two words} b ${'$'}{unknown text} c "),
                InlineMarkdownSpan.Text("red", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
            ),
            spans,
        )
        assertEquals(false, "${'$'}{two words}".containsValidMfmSyntax())
        assertEquals(false, "${'$'}{unknown text}".containsValidMfmSyntax())
        assertEquals(true, "${'$'}{fg.color=ff0000 red}".containsValidMfmSyntax())
    }

    @Test
    fun truncatesRichTextPreviewBeforeBrokenMfmOrEmojiToken() {
        assertEquals("hello...", "hello $[fg.color=ff0000 red text] tail".truncateRichTextPreview(18))
        assertEquals("hello...", "hello ${'$'}{fg.color=ff0000 red text} tail".truncateRichTextPreview(18))
        assertEquals("hello...", "hello :blobcat: tail".truncateRichTextPreview(11))
        assertEquals("...", "$[x2 hello]".truncateRichTextPreview(4))
        assertEquals("...", ":blobcat:".truncateRichTextPreview(4))
    }

    @Test
    fun truncatesRichTextPreviewBeforePartialMarkdownLinks() {
        assertEquals(
            "before...",
            "before [docs](https://dc.hhhl.cc/wiki/a_(b)) after".truncateRichTextPreview(18),
        )
        assertEquals(
            "before...",
            "before [docs](https://dc.hhhl.cc/wiki/a_(b)) after".truncateRichTextPreview(34),
        )
        assertEquals(
            "before [docs](https://dc.hhhl.cc/wiki/a_(b))...",
            "before [docs](https://dc.hhhl.cc/wiki/a_(b)) after".truncateRichTextPreview(45),
        )
    }

    @Test
    fun richTextPreviewDoesNotTreatUrlOrPunctuationColonsAsEmojiStarts() {
        assertEquals(
            "before...",
            "before [docs](https://dc.hhhl.cc/wiki/a_(b)) after".truncateRichTextPreview(25),
        )
        assertEquals(
            "time: 12...",
            "time: 1234567890 tail".truncateRichTextPreview(8),
        )
    }

    @Test
    fun boundsOverlongMfmOptionsWithoutDroppingContent() {
        val noisyOptions = buildString {
            append("color=")
            repeat(2_000) { append('f') }
            repeat(40) { index -> append(",x$index=$index") }
        }
        val spans = parseInlineMarkdown("$[fg.$noisyOptions visible] $[x2 ok]")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("visible "),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
    }

    @Test
    fun boundsSingleOverlongMfmOptionAndValueTokens() {
        val longToken = "a".repeat(5_000)
        val spans = parseInlineMarkdown("$[fg.$longToken visible] $[ruby $longToken note] tail")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("visible "),
                InlineMarkdownSpan.Ruby(longToken.take(512), "note"),
                InlineMarkdownSpan.Text(" tail"),
            ),
            spans,
        )
    }

    @Test
    fun boundsOverlongMfmFunctionBodiesAndContinuesParsingLaterSyntax() {
        val longBody = "a".repeat(5_000)
        val spans = parseInlineMarkdown("before $[x2 $longBody] middle $[x2 ok]")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("before $[x2 $longBody] middle "),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
        assertEquals(false, "$[x2 $longBody]".containsValidMfmSyntax())
        assertEquals(true, "$[x2 ok]".containsValidMfmSyntax())
    }

    @Test
    fun boundsOverlongPlainMfmInterpolationsAndKeepsLaterSyntax() {
        val longName = "a".repeat(5_000)
        val spans = parseInlineMarkdown("before ${'$'}{$longName} middle ${'$'}{name}")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("before $longName middle name"),
            ),
            spans,
        )
        assertEquals(false, "${'$'}{$longName}".containsValidMfmSyntax())
        assertEquals(true, "${'$'}{name}".containsValidMfmSyntax())
    }

    @Test
    fun preservesNestedClickableTokensAndEmojiTextInsideMfm() {
        val spans = parseInlineMarkdown("""$[fg.color=ff0000 Hi @alice #topic :blobcat: https://dc.hhhl.cc]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "Hi @alice #topic :blobcat: https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
            ),
            spans,
        )
    }

    @Test
    fun richTextSegmentsClickableTokensInsideMfmTextSpans() {
        val span = assertIs<InlineMarkdownSpan.Text>(
            parseInlineMarkdown("""$[fg.color=ff0000 Hi @alice #topic :blobcat: https://dc.hhhl.cc]""").single(),
        )
        val segments = cachedRichTextSegments(
            span.value,
            mapOf(":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp"),
        )

        assertEquals(
            listOf(
                RichTextSegment.Text("Hi "),
                RichTextSegment.Mention("@alice", "alice"),
                RichTextSegment.Text(" "),
                RichTextSegment.Hashtag("#topic", "topic"),
                RichTextSegment.Text(" "),
                RichTextSegment.Emoji(":blobcat:", "https://dc.hhhl.cc/emoji/blobcat.webp"),
                RichTextSegment.Text(" "),
                RichTextSegment.Url("https://dc.hhhl.cc"),
            ),
            segments,
        )
        assertEquals(MfmInlineStyle.Default.copy(foregroundColor = "ff0000"), span.mfmStyle)
    }

    @Test
    fun mfmStyleCarriesOntoMarkdownLinksInsideMfm() {
        val spans = parseInlineMarkdown(
            """$[fg.color=ff0000 $[bg.color=001122 $[border.color=abcdef,w=2 [docs](https://dc.hhhl.cc)]]]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        foregroundColor = "ff0000",
                        backgroundColor = "001122",
                        borderColor = "abcdef",
                        borderWidthDp = 2f,
                    ),
                ),
            ),
            spans,
        )
    }

    @Test
    fun truncatesRichTextPreviewOnlyWhenLimitIsSet() {
        assertEquals("abcdef", "abcdef".truncateRichTextPreview(null))
        assertEquals("abcdef", "abcdef".truncateRichTextPreview(12))
        assertEquals("abc...", "abcdef".truncateRichTextPreview(3))
        assertEquals("abc...", "abc   def".truncateRichTextPreview(5))
    }

    @Test
    fun richTextCacheEntrySnapshotsTrimOldestEntries() {
        val cache = (0 until 256).associate { index -> "k$index" to index }
            .withRichTextCacheEntry("k256", 256)

        assertEquals(256, cache.size)
        assertEquals(false, cache.containsKey("k0"))
        assertEquals(256, cache["k256"])
    }

    @Test
    fun richTextEmojiCacheSignatureDependsOnlyOnMatchedEmojiCodesAndUrls() {
        val text = "hi :blobcat: :missing:"
        val base = mapOf(
            ":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp",
            ":unused:" to "https://dc.hhhl.cc/emoji/unused.webp",
        )
        val withUnrelatedEmojiChanged = mapOf(
            ":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp",
            ":unused:" to "https://dc.hhhl.cc/emoji/changed.webp",
        )
        val withMatchedEmojiChanged = mapOf(
            ":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat-v2.webp",
            ":unused:" to "https://dc.hhhl.cc/emoji/unused.webp",
        )

        assertEquals(
            text.richTextEmojiDependencySignature(base),
            text.richTextEmojiDependencySignature(withUnrelatedEmojiChanged),
        )
        assertEquals(
            false,
            text.richTextEmojiDependencySignature(base) == text.richTextEmojiDependencySignature(withMatchedEmojiChanged),
        )
        assertEquals(null, "hi :missing:".richTextEmojiDependencySignature(base))
    }

    @Test
    fun richTextEmojiCacheSignatureDisablesCacheWhenTooManyEmojiDependencies() {
        val text = (0 until 70).joinToString(separator = " ") { index -> ":e$index:" }
        val emojiUrls = (0 until 70).associate { index -> ":e$index:" to "https://dc.hhhl.cc/emoji/e$index.webp" }

        assertEquals(null, text.richTextEmojiDependencySignature(emojiUrls))
    }

    @Test
    fun colonTextWithoutCurrentEmojiMatchDoesNotPoisonLaterEmojiParsing() {
        val text = "hi :latecat:"

        assertEquals(listOf(RichTextSegment.Text(text)), cachedRichTextSegments(text, emptyMap()))
        assertEquals(
            listOf(
                RichTextSegment.Text("hi "),
                RichTextSegment.Emoji(":latecat:", "https://dc.hhhl.cc/emoji/latecat.webp"),
            ),
            cachedRichTextSegments(text, mapOf(":latecat:" to "https://dc.hhhl.cc/emoji/latecat.webp")),
        )
    }

    @Test
    fun unmatchedColonTextDoesNotForceRichTextSegmentationWhenEmojiMapExists() {
        val text = "time: 12:30 and :missing:"

        assertEquals(
            listOf(InlineMarkdownSpan.Text(text)),
            parseInlineMarkdown(text),
        )
        assertEquals(
            listOf(RichTextSegment.Text(text)),
            cachedRichTextSegments(text, mapOf(":blobcat:" to "https://dc.hhhl.cc/emoji/blobcat.webp")),
        )
    }

    @Test
    fun keepsInlineCodeAndCodeBlocksFromParsingMfm() {
        val inlineSpans = parseInlineMarkdown("""`$[x2 code]` <code>@bob #html</code> $[code @alice #tag https://dc.hhhl.cc :blobcat:] $[x2 text]""")
        val blocks = parseMarkdownBlocks(
            """
            ```
            $[x2 code]
            ```
            $[x2 text]
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("$[x2 code]", InlineMarkdownStyle.Code, richTextEnabled = false),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("@bob #html", InlineMarkdownStyle.Code, richTextEnabled = false),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "@alice #tag https://dc.hhhl.cc :blobcat:",
                    InlineMarkdownStyle.Code,
                    richTextEnabled = false,
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("text", InlineMarkdownStyle.X2),
            ),
            inlineSpans,
        )
        assertEquals(MarkdownBlock.Code("$[x2 code]"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph(listOf(InlineMarkdownSpan.Text("text", InlineMarkdownStyle.X2))), blocks[1])
    }

    @Test
    fun inlineCodeHandlesEscapedBackticksAndBrokenCodeWithoutSwallowingMfm() {
        val spans = parseInlineMarkdown("""`a\`b $[x2 raw]` bad ` $[x2 ok]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("a`b $[x2 raw]", InlineMarkdownStyle.Code, richTextEnabled = false),
                InlineMarkdownSpan.Text(" bad ` "),
                InlineMarkdownSpan.Text("ok", InlineMarkdownStyle.X2),
            ),
            spans,
        )
    }

    @Test
    fun preservesVisualMfmEffectsAsMetadata() {
        val spans = parseInlineMarkdown("""$[sparkle aaa] $[spin bbb] ${'$'}{spin 斑} $[unknown ccc]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("aaa", mfmStyle = MfmInlineStyle.Default.copy(effect = MfmEffect.Sparkle)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bbb", mfmStyle = MfmInlineStyle.Default.copy(effect = MfmEffect.Spin)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("斑", mfmStyle = MfmInlineStyle.Default.copy(effect = MfmEffect.Spin)),
                InlineMarkdownSpan.Text(" ccc"),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmAnimationOptionsAsBoundedMetadata() {
        val spans = parseInlineMarkdown("""$[spin.speed=2,left,alternate fast] $[jelly.speed=0.01 slow] $[tada.speed=999 tiny] $[shake.speed=2s two-sec]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "fast",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Spin,
                        effectDurationMillis = 700,
                        effectReverse = true,
                        effectAlternate = true,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "slow",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Jelly,
                        effectDurationMillis = 5_000,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "tiny",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Tada,
                        effectDurationMillis = 120,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "two-sec",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Shake,
                        effectDurationMillis = 2_000,
                    ),
                ),
            ),
            spans,
        )
    }

    @Test
    fun boundsRenderedMfmAnimationsByTextLengthAndSpanBudget() {
        val animated = MfmInlineStyle.Default.copy(
            effect = MfmEffect.Spin,
            effectDurationMillis = 800,
            effectReverse = true,
            effectAlternate = true,
        )
        val budget = RichTextAnimationBudget(maxAnimatedSpans = 1)

        assertEquals(MfmEffect.Spin, animated.withBoundedAnimation(textLength = 8, animationBudget = budget).effect)
        val overBudget = animated.withBoundedAnimation(textLength = 8, animationBudget = budget)
        assertEquals(MfmEffect.None, overBudget.effect)
        assertEquals(null, overBudget.effectDurationMillis)
        assertEquals(false, overBudget.effectReverse)
        assertEquals(false, overBudget.effectAlternate)

        assertEquals(
            MfmEffect.None,
            animated.withBoundedAnimation(textLength = 260, animationBudget = RichTextAnimationBudget(4)).effect,
        )
    }

    @Test
    fun carriesOuterMfmAnimationOptionsIntoNestedSpans() {
        val spans = parseInlineMarkdown("""$[spin.speed=2,left $[fg.color=ff0000 nested]]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "nested",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        foregroundColor = "ff0000",
                        effect = MfmEffect.Spin,
                        effectDurationMillis = 700,
                        effectReverse = true,
                    ),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmStyleAliasesAndUniformScale() {
        val spans = parseInlineMarkdown(
            """$[fg=00ff00 green] $[bg.c=112233 bg] $[border=445566,w=2,r=8 box] $[rotate=45 tilted] $[opacity.a=0.5 faint] $[font.family=monospace code] $[scale=2 big]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("green", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "00ff00")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bg", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "112233")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "box",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        borderColor = "445566",
                        borderWidthDp = 2f,
                        borderRadiusDp = 8f,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("tilted", mfmStyle = MfmInlineStyle.Default.copy(rotateDeg = 45f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("faint", mfmStyle = MfmInlineStyle.Default.copy(opacity = 0.5f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("code", mfmStyle = MfmInlineStyle.Default.copy(font = MfmFont.Monospace)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "big",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 2f, scaleY = 2f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesAdditionalMfmOptionAliases() {
        val spans = parseInlineMarkdown(
            """$[fg.colour=00ff00 fg] $[bg.col=112233 bg] $[border.colour=445566,w=2,r=8 box] $[position.left=1,top=-2 pos] $[shake.duration=2s shake] $[spin.d=2 spin]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("fg", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "00ff00")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bg", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "112233")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "box",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        borderColor = "445566",
                        borderWidthDp = 2f,
                        borderRadiusDp = 8f,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("pos", mfmStyle = MfmInlineStyle.Default.copy(positionX = 1f, positionY = -2f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "shake",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Shake,
                        effectDurationMillis = 2000,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "spin",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Spin,
                        effectDurationMillis = 700,
                    ),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesCommonMfmFunctionNameAliases() {
        val spans = parseInlineMarkdown(
            """$[color=ff0000 red] $[background.color=001122 back] $[pos.l=1,t=-1 moved] $[rotation.deg=30 rot] $[alpha=0.4 fade] $[bordered.color=abcdef box] $[x2.x=1.5,y=2 zoom]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("red", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("back", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "001122")),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("moved", mfmStyle = MfmInlineStyle.Default.copy(positionX = 1f, positionY = -1f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("rot", mfmStyle = MfmInlineStyle.Default.copy(rotateDeg = 30f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("fade", mfmStyle = MfmInlineStyle.Default.copy(opacity = 0.4f)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "box",
                    mfmStyle = MfmInlineStyle.Default.copy(borderColor = "abcdef", borderWidthDp = 1f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "zoom",
                    InlineMarkdownStyle.X2,
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 1.5f, scaleY = 2f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmBooleanOptionsCaseInsensitively() {
        val spans = parseInlineMarkdown(
            """$[border.thick=YES,circle=on thick] $[border.nocorner=1 flat] $[spin.LEFT=True,ALT=y spin] $[flip.vertical=ON flip]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "thick",
                    mfmStyle = MfmInlineStyle.Default.copy(borderWidthDp = 2f, borderRadiusDp = 16f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "flat",
                    mfmStyle = MfmInlineStyle.Default.copy(borderWidthDp = 1f, borderRadiusDp = 0f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "spin",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Spin,
                        effectReverse = true,
                        effectAlternate = true,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "flip",
                    mfmStyle = MfmInlineStyle.Default.copy(flipX = false, flipY = true),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesQuotedMfmOptionsWithoutSplittingCommas() {
        val spans = parseInlineMarkdown(
            """$[border.color="#00aaff",width="2",radius='10',label="a,b" framed] $[font.family="monospace" code] $[spin.speed="2s",alternate fast] $[scale.x="2",y='1.5' scaled]""",
        )

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "framed",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        borderColor = "00aaff",
                        borderWidthDp = 2f,
                        borderRadiusDp = 10f,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("code", mfmStyle = MfmInlineStyle.Default.copy(font = MfmFont.Monospace)),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "fast",
                    mfmStyle = MfmInlineStyle.Default.copy(
                        effect = MfmEffect.Spin,
                        effectDurationMillis = 2000,
                        effectAlternate = true,
                    ),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text(
                    "scaled",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 2f, scaleY = 1.5f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun parsesMfmInsideMarkdownMarkersAndKeepsOuterStyle() {
        val spans = parseInlineMarkdown("""**$[fg=ff0000 bold red]** and *$[link docs https://dc.hhhl.cc]*""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text(
                    "bold red",
                    style = InlineMarkdownStyle.Bold,
                    mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000"),
                ),
                InlineMarkdownSpan.Text(" and "),
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default,
                ),
            ),
            spans,
        )
    }

    @Test
    fun outerMfmScaleAppliesToClickableAndRubySpans() {
        val spans = parseInlineMarkdown("""$[x2 $[link docs https://dc.hhhl.cc]] $[x3 $[ruby 漢 kan]]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Link(
                    label = "docs",
                    url = "https://dc.hhhl.cc",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 1.35f, scaleY = 1.35f),
                ),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Ruby(
                    base = "漢",
                    annotation = "kan",
                    mfmStyle = MfmInlineStyle.Default.copy(scaleX = 1.7f, scaleY = 1.7f),
                ),
            ),
            spans,
        )
    }

    @Test
    fun stripsOverlongMfmEffectsBeforeRichTextSegmentation() {
        val longAnimatedText = "@alice ".repeat(50)
        val spans = parseInlineMarkdown("$[spin.speed=2,left,alternate $longAnimatedText]")

        assertEquals(1, spans.size)
        val span = assertIs<InlineMarkdownSpan.Text>(spans.single())
        assertEquals(MfmEffect.None, span.mfmStyle.effect)
        assertEquals(null, span.mfmStyle.effectDurationMillis)
        assertEquals(false, span.mfmStyle.effectReverse)
        assertEquals(false, span.mfmStyle.effectAlternate)
        assertEquals(longAnimatedText.trim(), span.value)
    }

    @Test
    fun capsInlineMarkdownSpanCountAndKeepsRemainingText() {
        val text = buildString {
            repeat(240) { index ->
                append("$[x2 m")
                append(index)
                append("] ")
            }
            append("tail")
        }

        val spans = parseInlineMarkdown(text)

        assertTrue(spans.size <= 181)
        assertTrue(spans.joinToString(separator = "") { it.plainValue() }.startsWith("m0 m1 m2"))
        val tail = assertIs<InlineMarkdownSpan.Text>(spans.last())
        assertTrue(tail.value.contains("$[x2"))
        assertTrue(tail.value.endsWith("tail"))
    }

    @Test
    fun parsesCenteredHtmlBlock() {
        val blocks = parseMarkdownBlocks("<center>hello</center>")

        val center = assertIs<MarkdownBlock.Center>(blocks.single())
        assertEquals(listOf(InlineMarkdownSpan.Text("hello")), center.spans)
    }

    @Test
    fun parsesStaticHtmlAndMfmBlocks() {
        val blocks = parseMarkdownBlocks(
            """
            <blockquote>quoted <small>small</small></blockquote>
            <pre><code>val x = 1</code></pre>
            $[quote mfm quote]
            $[code val y = 2]
            """.trimIndent(),
        )

        val htmlQuote = assertIs<MarkdownBlock.Quote>(blocks[0])
        val htmlCode = assertIs<MarkdownBlock.Code>(blocks[1])
        val mfmQuote = assertIs<MarkdownBlock.Quote>(blocks[2])
        val mfmCode = assertIs<MarkdownBlock.Code>(blocks[3])

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("quoted "),
                InlineMarkdownSpan.Text("small", InlineMarkdownStyle.Small),
            ),
            htmlQuote.spans,
        )
        assertEquals("val x = 1", htmlCode.value)
        assertEquals(listOf(InlineMarkdownSpan.Text("mfm quote")), mfmQuote.spans)
        assertEquals("val y = 2", mfmCode.value)
    }

    @Test
    fun parsesMfmBlocksWithSharedNestedParser() {
        val blocks = parseMarkdownBlocks(
            """
            $[center $[x2 title]]
            $[quote nested $[fg.color=ff0000 quote]]
            $[code val x = "$[not parsed]"]
            """.trimIndent(),
        )

        val center = assertIs<MarkdownBlock.Center>(blocks[0])
        val quote = assertIs<MarkdownBlock.Quote>(blocks[1])
        val code = assertIs<MarkdownBlock.Code>(blocks[2])

        assertEquals(listOf(InlineMarkdownSpan.Text("title", InlineMarkdownStyle.X2)), center.spans)
        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("nested "),
                InlineMarkdownSpan.Text("quote", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
            ),
            quote.spans,
        )
        assertEquals("val x = \"$[not parsed]\"", code.value)
    }

    @Test
    fun parsesMultilineMfmBlocksWithSharedNestedParser() {
        val blocks = parseMarkdownBlocks(
            """
            $[center
            $[x2 title]
            ]
            $[quote
            nested $[fg.color=ff0000 quote]
            ]
            $[code
            val x = "$[not parsed]"
            ]
            """.trimIndent(),
        )

        val center = assertIs<MarkdownBlock.Center>(blocks[0])
        val quote = assertIs<MarkdownBlock.Quote>(blocks[1])
        val code = assertIs<MarkdownBlock.Code>(blocks[2])

        assertEquals(listOf(InlineMarkdownSpan.Text("title", InlineMarkdownStyle.X2)), center.spans)
        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("nested "),
                InlineMarkdownSpan.Text("quote", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
            ),
            quote.spans,
        )
        assertEquals("val x = \"$[not parsed]\"", code.value)
    }

    @Test
    fun multilineMfmBlockParserIgnoresSimilarFunctionPrefixes() {
        val blocks = parseMarkdownBlocks(
            """
            $[codec
            plain text
            ]
            $[center ok]
            """.trimIndent(),
        )

        val paragraph = assertIs<MarkdownBlock.Paragraph>(blocks[0])
        val center = assertIs<MarkdownBlock.Center>(blocks[1])

        assertEquals(listOf(InlineMarkdownSpan.Text("$[codec\nplain text\n]")), paragraph.spans)
        assertEquals(listOf(InlineMarkdownSpan.Text("ok")), center.spans)
    }

    @Test
    fun parsesBraceMfmInsideMfmQuoteAndCenterBlocks() {
        val blocks = parseMarkdownBlocks(
            """
            $[center ${'$'}{fg.color=ff0000 title}]
            $[quote hi ${'$'}{username} ${'$'}{bg.color=001122 there}]
            """.trimIndent(),
        )

        val center = assertIs<MarkdownBlock.Center>(blocks[0])
        val quote = assertIs<MarkdownBlock.Quote>(blocks[1])

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("title", mfmStyle = MfmInlineStyle.Default.copy(foregroundColor = "ff0000")),
            ),
            center.spans,
        )
        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("hi username "),
                InlineMarkdownSpan.Text("there", mfmStyle = MfmInlineStyle.Default.copy(backgroundColor = "001122")),
            ),
            quote.spans,
        )
    }
}

private fun InlineMarkdownSpan.plainValue(): String {
    return when (this) {
        is InlineMarkdownSpan.Text -> value
        is InlineMarkdownSpan.Link -> label
        is InlineMarkdownSpan.Ruby -> base + annotation
    }
}
