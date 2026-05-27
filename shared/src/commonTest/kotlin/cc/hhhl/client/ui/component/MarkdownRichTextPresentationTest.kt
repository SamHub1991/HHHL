package cc.hhhl.client.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
                InlineMarkdownSpan.Text("code()", InlineMarkdownStyle.Code),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Link("docs", "https://dc.hhhl.cc/docs"),
                InlineMarkdownSpan.Text(" :blobcat:"),
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
                InlineMarkdownSpan.Text("large", InlineMarkdownStyle.X3),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("**not bold** #tag", richTextEnabled = false),
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
                InlineMarkdownSpan.Text("说谎的这辈子硬不起来", InlineMarkdownStyle.X4),
            ),
            spans,
        )
    }

    @Test
    fun stripsUnsupportedVisualMfmDecorators() {
        val spans = parseInlineMarkdown("""$[sparkle aaa] $[spin bbb] ${'$'}{spin 斑} $[unknown ccc]""")

        assertEquals(
            listOf(
                InlineMarkdownSpan.Text("aaa"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("bbb"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("斑"),
                InlineMarkdownSpan.Text(" "),
                InlineMarkdownSpan.Text("ccc"),
            ),
            spans,
        )
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
}
