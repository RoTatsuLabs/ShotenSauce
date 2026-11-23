package org.koitharu.kotatsu.details.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

private val INLINE = Regex(
	"""\[([^\]]+)]\(\s*<?([^)\s>]+)>?[^)]*\)|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|`([^`\n]+)`|\*([^*\n]+?)\*|(?<![\w])_([^_\n]+?)_(?![\w])""",
)
private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

/**
 * A small Markdown renderer for notes: headings, bold, italic, strikethrough, inline code, links,
 * bullet and numbered lists, quotes and rules. Anything else is shown as typed.
 */
fun renderNoteMarkdown(source: String, linkColor: Color, codeBackground: Color, quoteColor: Color): AnnotatedString =
	buildAnnotatedString {
		val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
		lines.forEachIndexed { index, raw ->
			if (index > 0) append('\n')
			val heading = HEADING.matchEntire(raw)
			val bullet = BULLET.matchEntire(raw)
			val numbered = NUMBERED.matchEntire(raw)
			val quote = QUOTE.matchEntire(raw)
			when {
				RULE.matches(raw) -> append("\u2014\u2014\u2014\u2014\u2014\u2014")
				heading != null -> {
					val size = when (heading.groupValues[1].length) {
						1 -> 22.sp
						2 -> 19.sp
						else -> 16.sp
					}
					val start = length
					appendInline(heading.groupValues[2], linkColor, codeBackground)
					addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size), start, length)
				}
				bullet != null -> {
					append(" ".repeat(bullet.groupValues[1].length / 2 * 2))
					append("\u2022 ")
					appendInline(bullet.groupValues[2], linkColor, codeBackground)
				}
				numbered != null -> {
					append(" ".repeat(numbered.groupValues[1].length / 2 * 2))
					append(numbered.groupValues[2])
					append(". ")
					appendInline(numbered.groupValues[3], linkColor, codeBackground)
				}
				quote != null -> {
					val start = length
					append("\u258E ")
					appendInline(quote.groupValues[1], linkColor, codeBackground)
					addStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic), start, length)
				}
				else -> appendInline(raw, linkColor, codeBackground)
			}
		}
	}

private fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color, codeBackground: Color) {
	var last = 0
	for (m in INLINE.findAll(text)) {
		append(text.substring(last, m.range.first))
		val g = m.groupValues
		val start = length
		when {
			g[1].isNotEmpty() -> {
				append(g[1])
				val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
				addLink(LinkAnnotation.Url(g[2], TextLinkStyles(style)), start, length)
			}
			g[3].isNotEmpty() -> { append(g[3]); addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length) }
			g[4].isNotEmpty() -> { append(g[4]); addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length) }
			g[5].isNotEmpty() -> { append(g[5]); addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length) }
			g[6].isNotEmpty() -> {
				append(g[6])
				addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground), start, length)
			}
			g[7].isNotEmpty() -> { append(g[7]); addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length) }
			g[8].isNotEmpty() -> { append(g[8]); addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length) }
		}
		last = m.range.last + 1
	}
	append(text.substring(last))
}
