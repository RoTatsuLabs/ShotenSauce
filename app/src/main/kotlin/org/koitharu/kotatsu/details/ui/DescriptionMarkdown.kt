package org.koitharu.kotatsu.details.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

private val MD_LINK = Regex("""\[([^\]]+)]\(\s*<?([^)\s>]+)>?[^)]*\)""")
private val MD_BOLD = Regex("""\*\*(.+?)\*\*""", RegexOption.DOT_MATCHES_ALL)
private val MD_UNDERLINE = Regex("""(?<!\w)__(.+?)__(?!\w)""", RegexOption.DOT_MATCHES_ALL)
private val MD_STRIKE = Regex("""~~(.+?)~~""", RegexOption.DOT_MATCHES_ALL)
private val MD_ITALIC = Regex("""(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])""")

private val MD_ALL = Regex(
	listOf(MD_LINK, MD_BOLD, MD_UNDERLINE, MD_STRIKE, MD_ITALIC).joinToString("|") { "(?:${it.pattern})" },
	RegexOption.DOT_MATCHES_ALL,
)

/**
 * A description typed by hand, with the same styles an HTML one can show: `**bold**`, `*italic*`,
 * `__underline__`, `~~strikethrough~~` and `[text](url)`. Anything else stays as written.
 */
internal fun String.markdownToAnnotated(linkColor: Color, linksClickable: Boolean): AnnotatedString =
	buildAnnotatedString { appendMarkdown(this@markdownToAnnotated.trim(), linkColor, linksClickable) }

private fun AnnotatedString.Builder.appendMarkdown(text: String, linkColor: Color, linksClickable: Boolean) {
	var last = 0
	for (match in MD_ALL.findAll(text)) {
		append(text.substring(last, match.range.first))
		last = match.range.last + 1
		val link = MD_LINK.matchEntire(match.value)
		when {
			link != null -> {
				val style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
				if (linksClickable) {
					withLink(LinkAnnotation.Url(link.groupValues[2], TextLinkStyles(style))) { append(link.groupValues[1]) }
				} else {
					withStyle(style) { append(link.groupValues[1]) }
				}
			}
			else -> {
				val (style, inner) = when {
					match.value.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold) to match.value.drop(2).dropLast(2)
					match.value.startsWith("__") -> SpanStyle(textDecoration = TextDecoration.Underline) to match.value.drop(2).dropLast(2)
					match.value.startsWith("~~") -> SpanStyle(textDecoration = TextDecoration.LineThrough) to match.value.drop(2).dropLast(2)
					else -> SpanStyle(fontStyle = FontStyle.Italic) to match.value.drop(1).dropLast(1)
				}
				withStyle(style) { appendMarkdown(inner, linkColor, linksClickable) }
			}
		}
	}
	append(text.substring(last))
}
