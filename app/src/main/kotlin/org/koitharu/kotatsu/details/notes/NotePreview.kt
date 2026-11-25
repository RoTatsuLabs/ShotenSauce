package org.koitharu.kotatsu.details.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.R

/** The theme colours as the note renderer and the editor styling use them. */
@Composable
fun rememberNoteColors(): NoteColors {
	val scheme = MaterialTheme.colorScheme
	return remember(scheme) {
		NoteColors(
			link = scheme.primary,
			codeBackground = scheme.surfaceContainerHighest,
			quote = scheme.onSurfaceVariant,
			dim = scheme.onSurfaceVariant.copy(alpha = 0.55f),
			chipBackground = scheme.primaryContainer,
			chipText = scheme.onPrimaryContainer,
			spoilerHidden = scheme.surfaceContainerHighest,
			spoilerShown = scheme.surfaceContainerHigh,
			muted = scheme.onSurfaceVariant,
		)
	}
}

/** The rendered note: headings, lists, checklists, quotes, code blocks, spoilers and chapter chips. */
@Composable
fun NotePreview(
	source: String,
	chapters: NoteChapters,
	onToggleTask: (line: Int) -> Unit,
	onOpenChapter: (NoteChapter, Int?) -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	val colors = rememberNoteColors()
	val blocks = remember(source) { parseNoteBlocks(source) }
	var revealed by remember { mutableStateOf(emptySet<Int>()) }

	@Composable
	fun rich(text: String, line: Int): AnnotatedString = remember(text, line, revealed, chapters, colors) {
		renderNoteInline(
			text,
			InlineContext(
				colors = colors,
				chapters = chapters,
				revealed = revealed,
				scope = line,
				onReveal = { id -> revealed = if (id in revealed) revealed - id else revealed + id },
				onChapter = onOpenChapter,
			),
		)
	}

	if (source.isBlank()) {
		Text(
			text = stringResource(R.string.notes_hint),
			style = MaterialTheme.typography.bodyLarge,
			color = scheme.onSurfaceVariant,
			modifier = modifier.padding(20.dp),
		)
		return
	}

	SelectionContainer {
		Column(
			modifier = modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp, vertical = 16.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			val body = MaterialTheme.typography.bodyLarge
			for (block in blocks) {
				when (block) {
					is NoteBlock.Blank -> Spacer(Modifier.height(8.dp))
					is NoteBlock.Paragraph -> Text(rich(block.text, block.line), style = body, color = scheme.onSurface)
					is NoteBlock.Heading -> Text(
						text = rich(block.text, block.line),
						style = when (block.level) {
							1 -> MaterialTheme.typography.headlineSmall
							2 -> MaterialTheme.typography.titleLarge
							3 -> MaterialTheme.typography.titleMedium
							else -> MaterialTheme.typography.titleSmall
						},
						fontWeight = FontWeight.Bold,
						color = scheme.onSurface,
						modifier = Modifier.padding(top = 6.dp),
					)
					is NoteBlock.Small -> Text(
						text = rich(block.text, block.line),
						style = MaterialTheme.typography.bodySmall,
						color = scheme.onSurfaceVariant,
					)
					is NoteBlock.Bullet -> ListRow(block.indent, "•") {
						Text(rich(block.text, block.line), style = body, color = scheme.onSurface)
					}
					is NoteBlock.Numbered -> ListRow(block.indent, block.number + ".") {
						Text(rich(block.text, block.line), style = body, color = scheme.onSurface)
					}
					is NoteBlock.Task -> Row(
						modifier = Modifier.padding(start = (block.indent / 2 * 12).dp),
						verticalAlignment = Alignment.Top,
					) {
						Checkbox(
							checked = block.checked,
							onCheckedChange = { onToggleTask(block.line) },
							modifier = Modifier.padding(end = 4.dp),
						)
						Text(
							text = rich(block.text, block.line),
							style = body.copy(
								textDecoration = if (block.checked) TextDecoration.LineThrough else null,
							),
							color = if (block.checked) scheme.onSurfaceVariant else scheme.onSurface,
							modifier = Modifier.padding(top = 12.dp),
						)
					}
					is NoteBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
						Box(
							Modifier
								.width(3.dp)
								.fillMaxHeight()
								.background(scheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
						)
						Spacer(Modifier.width(12.dp))
						Text(
							text = rich(block.text, block.line),
							style = body,
							color = scheme.onSurfaceVariant,
						)
					}
					is NoteBlock.Code -> Surface(
						shape = RoundedCornerShape(12.dp),
						color = colors.codeBackground,
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
					) {
						Text(
							text = block.text,
							style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = body.fontSize * 0.9f),
							color = scheme.onSurface,
							modifier = Modifier
								.horizontalScroll(rememberScrollState())
								.padding(12.dp),
						)
					}
					is NoteBlock.Rule -> HorizontalDivider(
						modifier = Modifier.padding(vertical = 8.dp),
						color = scheme.outlineVariant,
					)
				}
			}
			Spacer(Modifier.height(24.dp))
		}
	}
}

@Composable
private fun ListRow(indent: Int, marker: String, content: @Composable () -> Unit) {
	Row(Modifier.padding(start = (indent / 2 * 12).dp)) {
		Text(
			text = marker,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(24.dp),
		)
		content()
	}
}
