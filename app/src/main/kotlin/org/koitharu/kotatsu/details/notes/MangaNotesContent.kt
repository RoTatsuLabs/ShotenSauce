package org.koitharu.kotatsu.details.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
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
	onSave: (String) -> Unit,
	onBack: () -> Unit,
) {
	Scaffold(
		topBar = {
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
			)
		},
	) { padding ->
		if (note == null) {
			Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
				CircularProgressIndicator()
			}
		} else {
			NoteEditor(
				initial = note,
				onSave = onSave,
				onBack = onBack,
				modifier = Modifier
					.padding(padding)
					.consumeWindowInsets(padding)
					.imePadding(),
			)
		}
	}
}

@Composable
private fun NoteEditor(
	initial: String,
	onSave: (String) -> Unit,
	onBack: () -> Unit,
	modifier: Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial)) }
	var saved by rememberSaveable { mutableStateOf(initial) }
	var preview by rememberSaveable { mutableStateOf(initial.isNotBlank()) }
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

	Column(modifier = modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
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
			TextButton(onClick = { save() }, enabled = dirty) {
				Text(stringResource(R.string.save))
			}
		}
		HorizontalDivider(color = scheme.outlineVariant)

		Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
			if (preview) {
				val rendered = remember(value.text, scheme) {
					renderNoteMarkdown(
						source = value.text,
						linkColor = scheme.primary,
						codeBackground = scheme.surfaceContainerHighest,
						quoteColor = scheme.onSurfaceVariant,
					)
				}
				SelectionContainer {
					Text(
						text = if (value.text.isBlank()) androidx.compose.ui.text.AnnotatedString(stringResource(R.string.notes_hint)) else rendered,
						style = MaterialTheme.typography.bodyLarge,
						color = if (value.text.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
						modifier = Modifier
							.fillMaxSize()
							.verticalScroll(rememberScrollState())
							.padding(20.dp),
					)
				}
			} else {
				TextField(
					value = value,
					onValueChange = { change(it, forceCheckpoint = false) },
					placeholder = { Text(stringResource(R.string.notes_hint)) },
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
			}
		}

		if (!preview) {
			HorizontalDivider(color = scheme.outlineVariant)
			val words = remember(value.text) { value.text.split(Regex("\\s+")).count { it.isNotEmpty() } }
			Text(
				text = stringResource(R.string.notes_stats, words, value.text.length),
				style = MaterialTheme.typography.labelSmall,
				color = scheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
			)
			FormatToolbar(
				canUndo = undo.isNotEmpty(),
				canRedo = redo.isNotEmpty(),
				onUndo = {
					val prev = undo.removeAt(undo.lastIndex)
					redo.add(value)
					value = prev
					lastPush = 0L
				},
				onRedo = {
					val next = redo.removeAt(redo.lastIndex)
					undo.add(value)
					value = next
					lastPush = 0L
				},
				onAction = { change(it(value), forceCheckpoint = true) },
			)
		}
	}
}

@Composable
private fun FormatToolbar(
	canUndo: Boolean,
	canRedo: Boolean,
	onUndo: () -> Unit,
	onRedo: () -> Unit,
	onAction: ((TextFieldValue) -> TextFieldValue) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(0.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		ToolButton("↶", enabled = canUndo, onClick = onUndo)
		ToolButton("↷", enabled = canRedo, onClick = onRedo)
		ToolButton("B", fontWeight = FontWeight.Bold) { onAction { it.wrap("**") } }
		ToolButton("I", fontStyle = FontStyle.Italic) { onAction { it.wrap("*") } }
		ToolButton("S", decoration = TextDecoration.LineThrough) { onAction { it.wrap("~~") } }
		ToolButton("H") { onAction { it.cycleHeading() } }
		ToolButton("•") { onAction { it.toggleList(NOTE_BULLET) { _ -> "- " } } }
		ToolButton("1.") { onAction { it.toggleList(NOTE_NUMBERED) { i -> "${i + 1}. " } } }
		ToolButton("❝") { onAction { it.toggleList(NOTE_QUOTE) { _ -> "> " } } }
		ToolButton("</>") { onAction { it.wrap("`") } }
		ToolButton(stringResource(R.string.notes_tool_link)) { onAction { it.insertLink() } }
		ToolButton("—") { onAction { it.insertRule() } }
	}
}

@Composable
private fun ToolButton(
	label: String,
	enabled: Boolean = true,
	fontWeight: FontWeight? = null,
	fontStyle: FontStyle? = null,
	decoration: TextDecoration? = null,
	onClick: () -> Unit,
) {
	TextButton(onClick = onClick, enabled = enabled) {
		Text(
			text = label,
			fontWeight = fontWeight,
			fontStyle = fontStyle,
			textDecoration = decoration,
			style = MaterialTheme.typography.titleMedium,
		)
	}
}

private val NOTE_BULLET = Regex("""^(\s*)[-*+]\s+""")
private val NOTE_NUMBERED = Regex("""^(\s*)\d+[.)]\s+""")
private val NOTE_QUOTE = Regex("""^(\s*)>\s?""")
private val NOTE_HEADING = Regex("""^(#{1,6})\s+""")

/** Surrounds the selection with [marker]; wraps it again with the marker taken off when it is already there. */
private fun TextFieldValue.wrap(marker: String): TextFieldValue {
	val start = selection.min
	val end = selection.max
	val len = marker.length
	val wrapped = start >= len && text.startsWith(marker, start - len) && text.startsWith(marker, end)
	return if (wrapped) {
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

/** Adds the list or quote marker to the selected lines, or takes it off when they all have it. */
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
				val body = line.trimStart().replaceFirst(NOTE_BULLET, "").replaceFirst(NOTE_NUMBERED, "").replaceFirst(NOTE_QUOTE, "")
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
