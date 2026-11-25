package org.koitharu.kotatsu.details.notes

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.koitharu.kotatsu.R

private const val UNDO_LIMIT = 100
private const val UNDO_MERGE_MS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaNotesScreen(
	title: String,
	note: String?,
	chapters: NoteChapters,
	onSave: (String) -> Unit,
	onBack: () -> Unit,
	onOpenChapter: (NoteChapter, Int?) -> Unit,
) {
	if (note == null) {
		Scaffold(topBar = { NotesTopBar(title, onBack) {} }) { padding ->
			Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
		}
	} else {
		NoteEditor(
			title = title,
			initial = note,
			chapters = chapters,
			onSave = onSave,
			onBack = onBack,
			onOpenChapter = onOpenChapter,
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit) {
	TopAppBar(
		title = {
			Column {
				Text(
					text = stringResource(R.string.notes),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
				)
				Text(
					text = title,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
				)
			}
		},
		navigationIcon = {
			IconButton(onClick = onBack) {
				Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
			}
		},
		actions = { actions() },
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
	title: String,
	initial: String,
	chapters: NoteChapters,
	onSave: (String) -> Unit,
	onBack: () -> Unit,
	onOpenChapter: (NoteChapter, Int?) -> Unit,
) {
	val scheme = MaterialTheme.colorScheme
	val colors = rememberNoteColors()
	var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
	var saved by rememberSaveable { mutableStateOf(initial) }
	var preview by rememberSaveable { mutableStateOf(initial.isNotBlank()) }
	var showFormats by rememberSaveable { mutableStateOf(false) }
	val undo = remember { mutableStateListOf<TextFieldValue>() }
	val redo = remember { mutableStateListOf<TextFieldValue>() }
	var lastPush by remember { mutableStateOf(0L) }
	val focus = remember { FocusRequester() }
	val dirty = value.text.trim() != saved.trim()

	fun save() {
		onSave(value.text)
		saved = value.text.trim()
	}

	fun change(new: TextFieldValue, forceCheckpoint: Boolean) {
		if (new.text != value.text) {
			val now = System.currentTimeMillis()
			// Typing is merged into one undo step; a toolbar action always gets its own
			if (forceCheckpoint || now - lastPush > UNDO_MERGE_MS || undo.isEmpty()) {
				undo.add(value)
				if (undo.size > UNDO_LIMIT) undo.removeAt(0)
				lastPush = now
			}
			if (forceCheckpoint) lastPush = 0L
			redo.clear()
		}
		value = new
	}

	// Leaving the screen keeps the note, so nothing typed is lost
	LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (dirty) save() }
	BackHandler {
		if (dirty) save()
		onBack()
	}
	LaunchedEffect(preview) {
		if (!preview) runCatching { focus.requestFocus() }
	}

	val transformation = remember(colors) {
		VisualTransformation { text -> TransformedText(styleNoteSource(text.text, colors), OffsetMapping.Identity) }
	}

	Scaffold(
		topBar = {
			NotesTopBar(title, onBack = { if (dirty) save(); onBack() }) {
				IconButton(
					onClick = {
						val prev = undo.removeAt(undo.lastIndex)
						redo.add(value)
						value = prev
						lastPush = 0L
					},
					enabled = undo.isNotEmpty() && !preview,
				) { Text("↶", style = MaterialTheme.typography.titleLarge) }
				IconButton(
					onClick = {
						val next = redo.removeAt(redo.lastIndex)
						undo.add(value)
						value = next
						lastPush = 0L
					},
					enabled = redo.isNotEmpty() && !preview,
				) { Text("↷", style = MaterialTheme.typography.titleLarge) }
				TextButton(onClick = { save() }, enabled = dirty) { Text(stringResource(R.string.save)) }
			}
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.consumeWindowInsets(padding)
				.imePadding()
				.fillMaxSize(),
		) {
			SingleChoiceSegmentedButtonRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 4.dp),
			) {
				SegmentedButton(
					selected = !preview,
					onClick = { preview = false },
					shape = SegmentedButtonDefaults.itemShape(0, 2),
					modifier = Modifier.height(36.dp),
					label = { Text(stringResource(R.string.edit), maxLines = 1) },
				)
				SegmentedButton(
					selected = preview,
					onClick = { preview = true },
					shape = SegmentedButtonDefaults.itemShape(1, 2),
					modifier = Modifier.height(36.dp),
					label = { Text(stringResource(R.string.notes_preview), maxLines = 1) },
				)
			}
			HorizontalDivider(color = scheme.outlineVariant)

			Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
				if (preview) {
					NotePreview(
						source = value.text,
						chapters = chapters,
						onToggleTask = { line ->
							change(value.copy(text = value.text.toggleTaskAt(line)), forceCheckpoint = true)
							save()
						},
						onOpenChapter = onOpenChapter,
					)
				} else {
					TextField(
						value = value,
						onValueChange = { change(it, forceCheckpoint = false) },
						placeholder = { Text(stringResource(R.string.notes_hint)) },
						visualTransformation = transformation,
						colors = TextFieldDefaults.colors(
							focusedContainerColor = Color.Transparent,
							unfocusedContainerColor = Color.Transparent,
							focusedIndicatorColor = Color.Transparent,
							unfocusedIndicatorColor = Color.Transparent,
						),
						textStyle = MaterialTheme.typography.bodyLarge,
						modifier = Modifier
							.fillMaxSize()
							.focusRequester(focus),
					)
					val mention = remember(value) { value.mentionToken() }
					val suggestions = remember(mention?.second, chapters) {
						mention?.let { chapters.suggest(it.second) }.orEmpty()
					}
					if (mention != null && suggestions.isNotEmpty()) {
						MentionSuggestions(
							items = suggestions,
							onPick = { chapter ->
								change(value.replaceMention(mention.first, chapter), forceCheckpoint = true)
							},
							modifier = Modifier
								.align(Alignment.BottomCenter)
								.padding(horizontal = 12.dp, vertical = 8.dp),
						)
					}
				}
			}

			if (!preview) {
				val words = remember(value.text) { value.text.split(Regex("\\s+")).count { it.isNotEmpty() } }
				Text(
					text = stringResource(R.string.notes_stats, words, value.text.length),
					style = MaterialTheme.typography.labelSmall,
					color = scheme.onSurfaceVariant,
					modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
				)
				QuickBar(
					value = value,
					onAction = { change(it(value), forceCheckpoint = true) },
					onMore = { showFormats = true },
				)
			}
		}
	}

	if (showFormats) {
		FormatSheet(
			onDismiss = { showFormats = false },
			onAction = {
				showFormats = false
				change(it(value), forceCheckpoint = true)
			},
		)
	}
}

private typealias Edit = (TextFieldValue) -> TextFieldValue

@Composable
private fun QuickBar(
	value: TextFieldValue,
	onAction: (Edit) -> Unit,
	onMore: () -> Unit,
) {
	val line = value.currentLine()
	Surface(
		shape = RoundedCornerShape(28.dp),
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = Modifier
			.fillMaxWidth()
			.navigationBarsPadding()
			.padding(horizontal = 12.dp, vertical = 8.dp),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			val bold = value.isWrapped("**")
			BarButton(R.drawable.ic_format_bold, active = bold) { onAction { it.wrap("**") } }
			BarButton(R.drawable.ic_format_italic, active = value.isWrapped("*") && !bold) {
				onAction { it.wrap("*") }
			}
			BarButton(R.drawable.ic_list, active = NOTE_BULLET.containsMatchIn(line)) {
				onAction { it.toggleList(NOTE_BULLET) { _ -> "- " } }
			}
			BarButton(R.drawable.ic_format_checklist, active = NOTE_TASK.containsMatchIn(line)) {
				onAction { it.toggleList(NOTE_TASK) { _ -> "- [ ] " } }
			}
			BarButton(R.drawable.ic_alternate_email) { onAction { it.insertMention() } }
			TextButton(
				onClick = onMore,
				shape = RoundedCornerShape(20.dp),
				colors = ButtonDefaults.textButtonColors(
					containerColor = MaterialTheme.colorScheme.primary,
					contentColor = MaterialTheme.colorScheme.onPrimary,
				),
			) {
				Text(stringResource(R.string.notes_format), fontWeight = FontWeight.Medium)
			}
		}
	}
}

@Composable
private fun BarButton(
	@DrawableRes icon: Int,
	active: Boolean = false,
	onClick: () -> Unit,
) {
	IconButton(
		onClick = onClick,
		modifier = Modifier.size(44.dp),
		colors = IconButtonDefaults.iconButtonColors(
			containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
			contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
		),
	) {
		Icon(painterResource(icon), contentDescription = null)
	}
}

private class FormatItem(
	@DrawableRes val icon: Int,
	@StringRes val label: Int,
	val edit: Edit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSheet(onDismiss: () -> Unit, onAction: (Edit) -> Unit) {
	val text = listOf(
		FormatItem(R.drawable.ic_format_bold, R.string.notes_tool_bold) { it.wrap("**") },
		FormatItem(R.drawable.ic_format_italic, R.string.notes_tool_italic) { it.wrap("*") },
		FormatItem(R.drawable.ic_format_underlined, R.string.notes_tool_underline) { it.wrap("__") },
		FormatItem(R.drawable.ic_format_strikethrough, R.string.notes_tool_strike) { it.wrap("~~") },
		FormatItem(R.drawable.ic_visibility_off, R.string.notes_tool_spoiler) { it.wrap("||") },
		FormatItem(R.drawable.ic_code, R.string.notes_tool_code) { it.wrap("`") },
		FormatItem(R.drawable.ic_link, R.string.notes_tool_link) { it.insertLink() },
		FormatItem(R.drawable.ic_title, R.string.notes_tool_heading) { it.cycleHeading() },
	)
	val blocks = listOf(
		FormatItem(R.drawable.ic_list, R.string.notes_tool_list) { it.toggleList(NOTE_BULLET) { _ -> "- " } },
		FormatItem(R.drawable.ic_format_numbered, R.string.notes_tool_numbered) { it.toggleList(NOTE_NUMBERED) { i -> "${i + 1}. " } },
		FormatItem(R.drawable.ic_format_checklist, R.string.notes_tool_checklist) { it.toggleList(NOTE_TASK) { _ -> "- [ ] " } },
		FormatItem(R.drawable.ic_format_quote, R.string.notes_tool_quote) { it.toggleList(NOTE_QUOTE) { _ -> "> " } },
		FormatItem(R.drawable.ic_code_block, R.string.notes_tool_codeblock) { it.insertCodeBlock() },
		FormatItem(R.drawable.ic_horizontal_rule, R.string.notes_tool_rule) { it.insertRule() },
		FormatItem(R.drawable.ic_text_decrease, R.string.notes_tool_small) { it.toggleList(NOTE_SMALL) { _ -> "-# " } },
	)
	val insert = listOf(
		FormatItem(R.drawable.ic_alternate_email, R.string.notes_tool_chapter) { it.insertMention() },
	)
	ModalBottomSheet(onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier
				.padding(horizontal = 16.dp)
				.padding(bottom = 24.dp),
		) {
			FormatGroup(R.string.notes_group_text, text, onAction)
			FormatGroup(R.string.notes_group_blocks, blocks, onAction)
			FormatGroup(R.string.notes_group_insert, insert, onAction)
		}
	}
}

@Composable
private fun FormatGroup(@StringRes title: Int, items: List<FormatItem>, onAction: (Edit) -> Unit) {
	Text(
		text = stringResource(title),
		style = MaterialTheme.typography.labelMedium,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
	)
	items.chunked(4).forEach { row ->
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			row.forEach { item ->
				Surface(
					onClick = { onAction(item.edit) },
					shape = RoundedCornerShape(16.dp),
					color = MaterialTheme.colorScheme.surfaceContainerHigh,
					modifier = Modifier
						.weight(1f)
						.height(64.dp),
				) {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center,
					) {
						Icon(
							painter = painterResource(item.icon),
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurface,
						)
						Text(
							text = stringResource(item.label),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
							maxLines = 1,
						)
					}
				}
			}
			// Keeps the last row's cells the same width as the others
			repeat(4 - row.size) { Box(Modifier.weight(1f)) }
		}
	}
}

@Composable
private fun MentionSuggestions(
	items: List<NoteChapter>,
	onPick: (NoteChapter) -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		shape = RoundedCornerShape(16.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		shadowElevation = 6.dp,
		modifier = modifier.fillMaxWidth(),
	) {
		Column(
			Modifier
				.heightIn(max = 280.dp)
				.verticalScroll(rememberScrollState())
				.padding(6.dp),
		) {
			items.forEach { chapter ->
				Surface(
					onClick = { onPick(chapter) },
					shape = RoundedCornerShape(10.dp),
					color = Color.Transparent,
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 10.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text(
							text = stringResource(R.string.notes_chapter_label, formatChapterNumber(chapter.number)),
							style = MaterialTheme.typography.bodyMedium,
						)
						Text(
							text = chapter.title.orEmpty(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							modifier = Modifier.padding(start = 12.dp),
						)
					}
				}
			}
		}
	}
}

private val NOTE_BULLET = Regex("""^(\s*)[-*+]\s+(?!\[[ xX]]\s)""")
private val NOTE_TASK = Regex("""^(\s*)[-*+]\s+\[[ xX]]\s+""")
private val NOTE_NUMBERED = Regex("""^(\s*)\d+[.)]\s+""")
private val NOTE_QUOTE = Regex("""^(\s*)>\s?""")
private val NOTE_SMALL = Regex("""^(\s*)-#\s+""")
private val NOTE_HEADING = Regex("""^(#{1,6})\s+""")
private val MENTION = Regex("""(?:^|\s)@([\w.:]*)$""")
private val TASK_LINE = Regex("""^(\s*[-*+]\s+)\[([ xX])]""")

/** Flips the checkbox of the given source line, if it has one. */
private fun String.toggleTaskAt(line: Int): String {
	val lines = split('\n').toMutableList()
	if (line !in lines.indices) return this
	// The pattern is anchored to the line start, so it can only match once
	lines[line] = TASK_LINE.replace(lines[line]) { m ->
		m.groupValues[1] + if (m.groupValues[2] == " ") "[x]" else "[ ]"
	}
	return lines.joinToString("\n")
}

private fun TextFieldValue.currentLine(): String {
	val s = selection.min
	val lineStart = text.lastIndexOf('\n', s - 1) + 1
	return text.substring(lineStart).substringBefore('\n')
}

/** True when the selection (or the cursor) sits right inside [marker] on both sides. */
private fun TextFieldValue.isWrapped(marker: String): Boolean {
	val start = selection.min
	val end = selection.max
	val len = marker.length
	return start >= len && text.startsWith(marker, start - len) && text.startsWith(marker, end)
}

/** Surrounds the selection with [marker]; wraps it again with the marker taken off when it is already there. */
private fun TextFieldValue.wrap(marker: String): TextFieldValue {
	val start = selection.min
	val end = selection.max
	val len = marker.length
	return if (isWrapped(marker)) {
		val newText = text.removeRange(end, end + len).removeRange(start - len, start)
		copy(text = newText, selection = TextRange(start - len, end - len))
	} else {
		val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
		copy(text = newText, selection = TextRange(start + len, end + len))
	}
}

/** Applies [transform] to every line the selection touches. */
private fun TextFieldValue.mapLines(transform: (index: Int, line: String) -> String): TextFieldValue {
	val s = selection.min
	val e = selection.max
	val lineStart = text.lastIndexOf('\n', s - 1) + 1
	var lineEnd = text.indexOf('\n', e)
	if (lineEnd < 0) lineEnd = text.length
	val block = text.substring(lineStart, lineEnd)
	val newBlock = block.split('\n').mapIndexed(transform).joinToString("\n")
	val delta = newBlock.length - block.length
	val newSelection = if (selection.collapsed) {
		TextRange((s + delta).coerceAtLeast(lineStart))
	} else {
		TextRange(lineStart, lineEnd + delta)
	}
	return copy(text = text.substring(0, lineStart) + newBlock + text.substring(lineEnd), selection = newSelection)
}

/** Adds the list, checklist or quote marker to the selected lines, or takes it off when they all have it. */
private fun TextFieldValue.toggleList(existing: Regex, marker: (index: Int) -> String): TextFieldValue {
	val s = selection.min
	val e = selection.max
	val lineStart = text.lastIndexOf('\n', s - 1) + 1
	var lineEnd = text.indexOf('\n', e)
	if (lineEnd < 0) lineEnd = text.length
	val lines = text.substring(lineStart, lineEnd).split('\n')
	val allMarked = lines.filter { it.isNotBlank() }.let { it.isNotEmpty() && it.all(existing::containsMatchIn) }
	return mapLines { i, line ->
		when {
			allMarked -> line.replaceFirst(existing, "$1")
			line.isBlank() && lines.size > 1 -> line
			else -> {
				val indent = line.takeWhile { it == ' ' || it == '\t' }
				val body = line.trimStart()
					.replaceFirst(NOTE_TASK, "")
					.replaceFirst(NOTE_SMALL, "")
					.replaceFirst(NOTE_BULLET, "")
					.replaceFirst(NOTE_NUMBERED, "")
					.replaceFirst(NOTE_QUOTE, "")
				indent + marker(i) + body
			}
		}
	}
}

/** None, then # to ###, then back to none, based on the first selected line. */
private fun TextFieldValue.cycleHeading(): TextFieldValue {
	val s = selection.min
	val lineStart = text.lastIndexOf('\n', s - 1) + 1
	val first = text.substring(lineStart).substringBefore('\n')
	val level = NOTE_HEADING.find(first)?.groupValues?.get(1)?.length ?: 0
	val next = if (level >= 3) 0 else level + 1
	return mapLines { _, line ->
		val body = line.replaceFirst(NOTE_HEADING, "")
		if (next == 0) body else "#".repeat(next) + " " + body
	}
}

/** Turns the selection into a link label and selects the address to type. */
private fun TextFieldValue.insertLink(): TextFieldValue {
	val start = selection.min
	val end = selection.max
	val label = text.substring(start, end).ifEmpty { "link" }
	val newText = text.substring(0, start) + "[" + label + "](url)" + text.substring(end)
	val urlStart = start + label.length + 3
	return copy(text = newText, selection = TextRange(urlStart, urlStart + 3))
}

private fun TextFieldValue.insertRule(): TextFieldValue {
	val at = selection.max
	val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
	val insert = "\n---\n"
	val newText = text.substring(0, lineEnd) + insert + text.substring(lineEnd)
	return copy(text = newText, selection = TextRange(lineEnd + insert.length))
}

/** Fences the selection as a code block, on lines of its own. */
private fun TextFieldValue.insertCodeBlock(): TextFieldValue {
	val start = selection.min
	val end = selection.max
	val prefix = if (start == 0 || text[start - 1] == '\n') "" else "\n"
	val selected = text.substring(start, end)
	val newText = text.substring(0, start) + prefix + "```\n" + selected + "\n```" + text.substring(end)
	val bodyStart = start + prefix.length + 4
	return copy(text = newText, selection = TextRange(bodyStart, bodyStart + selected.length))
}

/** Types an @ at the cursor, which opens the chapter suggestions. */
private fun TextFieldValue.insertMention(): TextFieldValue {
	val start = selection.min
	val end = selection.max
	val needsSpace = start > 0 && !text[start - 1].isWhitespace()
	val insert = if (needsSpace) " @" else "@"
	return copy(
		text = text.substring(0, start) + insert + text.substring(end),
		selection = TextRange(start + insert.length),
	)
}

/** Where the @ being typed starts and what follows it, or null when the cursor is not in a mention. */
private fun TextFieldValue.mentionToken(): Pair<Int, String>? {
	if (!selection.collapsed) return null
	val before = text.substring(0, selection.start)
	val group = MENTION.find(before)?.groups?.get(1) ?: return null
	return (group.range.first - 1) to group.value
}

private fun TextFieldValue.replaceMention(atIndex: Int, chapter: NoteChapter): TextFieldValue {
	val cursor = selection.start
	val insert = "@ch" + formatChapterNumber(chapter.number) + " "
	return copy(
		text = text.substring(0, atIndex) + insert + text.substring(cursor),
		selection = TextRange(atIndex + insert.length),
	)
}

private fun NoteChapters.suggest(query: String): List<NoteChapter> {
	val q = query.substringBefore(':').removePrefix("ch").removePrefix("Ch").removePrefix("CH").trim()
	return all.filter {
		q.isEmpty() ||
			formatChapterNumber(it.number).startsWith(q) ||
			it.title?.contains(q, ignoreCase = true) == true
	}
}
