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

/** A chapter a note can point at with `@ch12` or `@ch12:5` (chapter, page). */
data class NoteChapter(val id: Long, val number: Float, val title: String?)

/** Chapters with a positive number, keyed by number. Duplicates keep the first. */
class NoteChapters(chapters: List<NoteChapter>) {

	private val byNumber: Map<Float, NoteChapter> = chapters
		.filter { it.number > 0f }
		.groupBy { it.number }
		.mapValues { it.value.first() }

	val all: List<NoteChapter> = byNumber.values.sortedBy { it.number }

	fun find(number: Float): NoteChapter? = byNumber[number]

	companion object {
		val EMPTY = NoteChapters(emptyList())
	}
}

fun formatChapterNumber(number: Float): String =
	if (number % 1f == 0f) number.toInt().toString() else number.toString()

class NoteColors(
	val link: Color,
	val codeBackground: Color,
	val quote: Color,
	val dim: Color,
	val chipBackground: Color,
	val chipText: Color,
	val spoilerHidden: Color,
	val spoilerShown: Color,
	val muted: Color,
)

/** The pieces of a note, one per line or fenced group of lines. [line] is the first source line. */
sealed interface NoteBlock {
	val line: Int

	data class Blank(override val line: Int) : NoteBlock
	data class Paragraph(override val line: Int, val text: String) : NoteBlock
	data class Heading(override val line: Int, val level: Int, val text: String) : NoteBlock
	data class Small(override val line: Int, val text: String) : NoteBlock
	data class Bullet(override val line: Int, val indent: Int, val text: String) : NoteBlock
	data class Numbered(override val line: Int, val indent: Int, val number: String, val text: String) : NoteBlock
	data class Task(override val line: Int, val indent: Int, val checked: Boolean, val text: String) : NoteBlock
	data class Quote(override val line: Int, val text: String) : NoteBlock
	data class Code(override val line: Int, val text: String) : NoteBlock
	data class Rule(override val line: Int) : NoteBlock
}

private val INLINE = Regex(
	"""`(?<code>[^`\n]+)`""" +
		"""|\[(?<label>[^\]]+)]\(\s*<?(?<linkurl>[^)\s>]+)>?[^)]*\)""" +
		"""|\|\|(?<spoiler>.+?)\|\|""" +
		"""|\*\*(?<bold>.+?)\*\*""" +
		"""|__(?<under>.+?)__""" +
		"""|~~(?<strike>.+?)~~""" +
		"""|\*(?<ital>[^*\n]+?)\*""" +
		"""|(?<![\w])_(?<ital2>[^_\n]+?)_(?![\w])""" +
		"""|@ch(?<chnum>\d+(?:\.\d+)?)(?::(?<chpage>\d+))?""" +
		"""|(?<bare>https?://[^\s<>)\]]+)""",
	RegexOption.IGNORE_CASE,
)
private val HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*)$""")
private val SMALL = Regex("""^\s*-#\s+(.*)$""")
private val TASK = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")

fun parseNoteBlocks(source: String): List<NoteBlock> {
	val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
	val out = ArrayList<NoteBlock>()
	var i = 0
	while (i < lines.size) {
		val raw = lines[i]
		val trimmed = raw.trim()
		when {
			trimmed.startsWith("```") -> {
				val body = ArrayList<String>()
				var j = i + 1
				while (j < lines.size && !lines[j].trim().startsWith("```")) {
					body.add(lines[j])
					j++
				}
				out.add(NoteBlock.Code(i, body.joinToString("\n")))
				i = j + 1
			}
			trimmed.startsWith(">>>") -> {
				// `>>>` quotes everything after it.
				val rest = listOf(trimmed.removePrefix(">>>").trimStart()) + lines.drop(i + 1)
				out.add(NoteBlock.Quote(i, rest.joinToString("\n").trim('\n')))
				i = lines.size
			}
			QUOTE.matches(raw) -> {
				val body = ArrayList<String>()
				var j = i
				while (j < lines.size && QUOTE.matches(lines[j]) && !lines[j].trim().startsWith(">>>")) {
					body.add(QUOTE.matchEntire(lines[j])!!.groupValues[1])
					j++
				}
				out.add(NoteBlock.Quote(i, body.joinToString("\n")))
				i = j
			}
			else -> {
				out.add(parseLine(i, raw))
				i++
			}
		}
	}
	return out
}

private fun parseLine(index: Int, raw: String): NoteBlock {
	if (RULE.matches(raw)) return NoteBlock.Rule(index)
	HEADING.matchEntire(raw)?.let { return NoteBlock.Heading(index, it.groupValues[1].length, it.groupValues[2]) }
	SMALL.matchEntire(raw)?.let { return NoteBlock.Small(index, it.groupValues[1]) }
	TASK.matchEntire(raw)?.let {
		return NoteBlock.Task(index, it.groupValues[1].length, it.groupValues[2] != " ", it.groupValues[3])
	}
	BULLET.matchEntire(raw)?.let { return NoteBlock.Bullet(index, it.groupValues[1].length, it.groupValues[2]) }
	NUMBERED.matchEntire(raw)?.let {
		return NoteBlock.Numbered(index, it.groupValues[1].length, it.groupValues[2], it.groupValues[3])
	}
	return if (raw.isBlank()) NoteBlock.Blank(index) else NoteBlock.Paragraph(index, raw)
}

class InlineContext(
	val colors: NoteColors,
	val chapters: NoteChapters,
	val revealed: Set<Int>,
	/** Keeps spoiler ids unique across lines. */
	val scope: Int,
	val onReveal: (Int) -> Unit,
	val onChapter: (NoteChapter, Int?) -> Unit,
)

fun renderNoteInline(text: String, ctx: InlineContext): AnnotatedString = buildAnnotatedString {
	appendInline(text, ctx, 0)
}

private fun AnnotatedString.Builder.appendInline(text: String, ctx: InlineContext, offset: Int) {
	val c = ctx.colors
	var last = 0
	for (m in INLINE.findAll(text)) {
		append(text.substring(last, m.range.first))
		last = m.range.last + 1
		val start = length
		val g = m.groups
		when {
			g["code"] != null -> {
				append(g["code"]!!.value)
				addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBackground), start, length)
			}
			g["label"] != null -> {
				append(g["label"]!!.value)
				val style = SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)
				addLink(LinkAnnotation.Url(g["linkurl"]!!.value, TextLinkStyles(style)), start, length)
			}
			g["spoiler"] != null -> {
				val inner = g["spoiler"]!!.value
				val id = ctx.scope * 10_000 + offset + m.range.first
				if (id in ctx.revealed) {
					appendInline(inner, ctx, offset + m.range.first)
					addStyle(SpanStyle(background = c.spoilerShown), start, length)
				} else {
					append(inner)
					addStyle(SpanStyle(color = c.spoilerHidden, background = c.spoilerHidden), start, length)
					addLink(LinkAnnotation.Clickable("spoiler$id", null) { ctx.onReveal(id) }, start, length)
				}
			}
			g["bold"] != null -> {
				appendInline(g["bold"]!!.value, ctx, offset + m.range.first)
				addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
			}
			g["under"] != null -> {
				appendInline(g["under"]!!.value, ctx, offset + m.range.first)
				addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, length)
			}
			g["strike"] != null -> {
				appendInline(g["strike"]!!.value, ctx, offset + m.range.first)
				addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
			}
			g["ital"] != null || g["ital2"] != null -> {
				appendInline((g["ital"] ?: g["ital2"])!!.value, ctx, offset + m.range.first)
				addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
			}
			g["chnum"] != null -> appendChapter(g["chnum"]!!.value, g["chpage"]?.value, ctx)
			g["bare"] != null -> {
				val url = g["bare"]!!.value
				append(url)
				val style = SpanStyle(color = c.link, textDecoration = TextDecoration.Underline)
				addLink(LinkAnnotation.Url(url, TextLinkStyles(style)), start, length)
			}
		}
	}
	append(text.substring(last))
}

private fun AnnotatedString.Builder.appendChapter(number: String, page: String?, ctx: InlineContext) {
	val c = ctx.colors
	val chapter = number.toFloatOrNull()?.let(ctx.chapters::find)
	val pageNumber = page?.toIntOrNull()
	val label = buildString {
		append("Ch. ").append(number)
		if (pageNumber != null) append(" · p.").append(pageNumber)
	}
	val start = length
	append(" $label ")
	if (chapter != null) {
		addStyle(SpanStyle(color = c.chipText, background = c.chipBackground, fontWeight = FontWeight.Medium), start, length)
		addLink(LinkAnnotation.Clickable("chapter${chapter.id}", null) { ctx.onChapter(chapter, pageNumber) }, start, length)
	} else {
		addStyle(SpanStyle(color = c.muted, background = c.codeBackground), start, length)
	}
}

/** Styles the note source for the editor without changing the text, so the cursor and selection stay put. */
fun styleNoteSource(source: String, c: NoteColors): AnnotatedString = buildAnnotatedString {
	append(source)
	var offset = 0
	var inFence = false
	for (line in source.split('\n')) {
		val end = offset + line.length
		when {
			line.trim().startsWith("```") -> {
				addStyle(SpanStyle(color = c.dim), offset, end)
				inFence = !inFence
			}
			inFence -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBackground), offset, end)
			else -> styleLine(line, offset, c)
		}
		offset = end + 1
	}
}

private fun AnnotatedString.Builder.styleLine(line: String, offset: Int, c: NoteColors) {
	val end = offset + line.length
	var bodyStart = 0
	val heading = HEADING.matchEntire(line)
	if (heading != null) {
		val markerEnd = heading.groups[2]?.range?.first ?: line.length
		addStyle(SpanStyle(color = c.dim), offset, offset + markerEnd)
		val size = when (heading.groupValues[1].length) {
			1 -> 22.sp
			2 -> 19.sp
			else -> 16.sp
		}
		addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size), offset + markerEnd, end)
		bodyStart = markerEnd
	} else {
		val marker = TASK.matchEntire(line)?.let { line.length - it.groupValues[3].length }
			?: SMALL.matchEntire(line)?.let { line.length - it.groupValues[1].length }
			?: BULLET.matchEntire(line)?.let { line.length - it.groupValues[2].length }
			?: NUMBERED.matchEntire(line)?.let { line.length - it.groupValues[3].length }
			?: QUOTE.matchEntire(line)?.let { line.length - it.groupValues[1].length }
		if (marker != null) {
			addStyle(SpanStyle(color = c.dim), offset, offset + marker)
			bodyStart = marker
			if (QUOTE.matches(line)) {
				addStyle(SpanStyle(color = c.quote, fontStyle = FontStyle.Italic), offset + marker, end)
			}
		}
	}
	for (m in INLINE.findAll(line)) {
		if (m.range.first < bodyStart) continue
		val a = offset + m.range.first
		val b = offset + m.range.last + 1
		val g = m.groups
		when {
			g["code"] != null -> {
				addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBackground), a, b)
				addStyle(SpanStyle(color = c.dim), a, a + 1)
				addStyle(SpanStyle(color = c.dim), b - 1, b)
			}
			g["label"] != null -> {
				val label = g["label"]!!.range
				addStyle(SpanStyle(color = c.dim), a, offset + label.first)
				addStyle(SpanStyle(color = c.link), offset + label.first, offset + label.last + 1)
				addStyle(SpanStyle(color = c.dim), offset + label.last + 1, b)
			}
			g["spoiler"] != null -> {
				addStyle(SpanStyle(background = c.spoilerShown), a + 2, b - 2)
				addStyle(SpanStyle(color = c.dim), a, a + 2)
				addStyle(SpanStyle(color = c.dim), b - 2, b)
			}
			g["bold"] != null -> {
				addStyle(SpanStyle(fontWeight = FontWeight.Bold), a + 2, b - 2)
				addStyle(SpanStyle(color = c.dim), a, a + 2)
				addStyle(SpanStyle(color = c.dim), b - 2, b)
			}
			g["under"] != null -> {
				addStyle(SpanStyle(textDecoration = TextDecoration.Underline), a + 2, b - 2)
				addStyle(SpanStyle(color = c.dim), a, a + 2)
				addStyle(SpanStyle(color = c.dim), b - 2, b)
			}
			g["strike"] != null -> {
				addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), a + 2, b - 2)
				addStyle(SpanStyle(color = c.dim), a, a + 2)
				addStyle(SpanStyle(color = c.dim), b - 2, b)
			}
			g["ital"] != null || g["ital2"] != null -> {
				addStyle(SpanStyle(fontStyle = FontStyle.Italic), a + 1, b - 1)
				addStyle(SpanStyle(color = c.dim), a, a + 1)
				addStyle(SpanStyle(color = c.dim), b - 1, b)
			}
			g["chnum"] != null -> addStyle(SpanStyle(color = c.chipText, background = c.chipBackground), a, b)
			g["bare"] != null -> addStyle(SpanStyle(color = c.link), a, b)
		}
	}
}
