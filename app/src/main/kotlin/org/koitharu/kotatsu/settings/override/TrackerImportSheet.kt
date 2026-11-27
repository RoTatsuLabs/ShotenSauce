package org.koitharu.kotatsu.settings.override

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.dialog.ExpressivePillButton
import org.koitharu.kotatsu.core.ui.image.coverBlur
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerMangaStatus
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingInfo
import org.koitharu.kotatsu.settings.compose.LocalSettingsItemColor
import org.koitharu.kotatsu.settings.compose.SegmentedRow
import org.koitharu.kotatsu.settings.compose.SegmentedSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsSwitch
import org.koitharu.kotatsu.settings.compose.rememberIntPref

/** What happens to the editor's current value when a field is imported. */
enum class ImportMode(val titleResId: Int) {
	REPLACE(R.string.import_mode_replace),
	APPEND(R.string.import_mode_append),
	FILL(R.string.import_mode_fill),
}

/** What the tracker can hand over, in the order the editor shows those fields. */
enum class ImportField(val titleResId: Int, val modes: List<ImportMode>) {
	COVER(R.string.cover, listOf(ImportMode.REPLACE)),
	TITLE(R.string.name, listOf(ImportMode.REPLACE)),
	DESCRIPTION(R.string.description, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	ALT_NAMES(R.string.alt_names, listOf(ImportMode.APPEND)),
	GENRES(R.string.genres, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	AUTHORS(R.string.authors, listOf(ImportMode.REPLACE, ImportMode.APPEND, ImportMode.FILL)),
	STATUS(R.string.state, listOf(ImportMode.REPLACE, ImportMode.FILL)),
	CONTENT_RATING(R.string.content_rating, listOf(ImportMode.REPLACE, ImportMode.FILL)),
	RATING(R.string.rating, listOf(ImportMode.REPLACE, ImportMode.FILL)),
}

/** What the editor holds now, so the sheet can show what an import would change. */
class ImportCurrent(val cover: String?, val values: Map<ImportField, String>)

private enum class FieldState { CHANGE, KEPT, SAME }

/**
 * Picks what to pull from a linked tracker. Every field is shown as what it is now against what
 * the tracker has, and only the ones that would change start switched on. The values only land in
 * the editor's fields, so this sheet never has a destructive outcome: Save does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerImportSheet(
	manga: Manga,
	trackers: List<ScrobblingInfo>,
	current: ImportCurrent,
	imageLoader: ImageLoader,
	onImport: (ScrobblingInfo, Map<ImportField, ImportMode>, String?) -> Unit,
	onDismiss: () -> Unit,
) {
	if (trackers.isEmpty()) {
		return
	}
	val context = LocalContext.current
	var selectedTracker by remember { mutableStateOf(trackers.first().scrobbler) }
	val tracker = trackers.firstOrNull { it.scrobbler == selectedTracker } ?: trackers.first()
	var applyMode by remember { mutableStateOf(ImportMode.FILL) }
	var quality by rememberIntPref(AppSettings.KEY_IMPORT_COVER_QUALITY, COVER_HIGH)
	val coverUrl = tracker.coverUrl?.let { if (quality == COVER_STANDARD) it.asStandardCover() else it }
	val canChooseQuality = tracker.coverUrl?.let { it.asStandardCover() != it } == true

	val states = remember(tracker, applyMode, quality, current) {
		ImportField.entries.mapNotNull { field ->
			val value = if (field == ImportField.COVER) coverUrl else tracker.valueOf(field, context)
			val now = if (field == ImportField.COVER) current.cover else current.values[field]
			stateOf(field, value, now, applyMode)?.let { field to it }
		}.toMap()
	}
	var selection by remember(states) {
		mutableStateOf(
			states.filterValues { it == FieldState.CHANGE }.keys.associateWith { applyMode.forField(it) },
		)
	}
	val changing = ImportField.entries.filter { states[it] == FieldState.CHANGE }
	val resting = ImportField.entries.filter { states[it] != null && states[it] != FieldState.CHANGE }
	val missing = ImportField.entries.filter { it !in states }

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
		shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
		containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
	) {
		CompositionLocalProvider(LocalSettingsItemColor provides MaterialTheme.colorScheme.surfaceContainerHigh) {
			Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
				Text(
					text = stringResource(R.string.import_from_tracker),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.SemiBold,
				)
				Spacer(Modifier.height(12.dp))
				// Only the list scrolls: the buttons below stay in reach.
				Column(
					modifier = Modifier
						.weight(1f, fill = false)
						.verticalScroll(rememberScrollState()),
				) {
					if (trackers.size > 1) {
						SegmentedSettingsItem(
							title = stringResource(R.string.import_tracker),
							labels = trackers.map { stringResource(it.scrobbler.titleResId) },
							selectedIndex = trackers.indexOf(tracker),
							onSelected = { selectedTracker = trackers[it].scrobbler },
							shape = RoundedCornerShape(24.dp),
						)
						Spacer(Modifier.height(8.dp))
					}
					SegmentedSettingsItem(
						title = stringResource(R.string.import_apply_as),
						subtitle = stringResource(applyMode.hintResId),
						labels = ImportMode.entries.map { stringResource(it.titleResId) },
						selectedIndex = applyMode.ordinal,
						onSelected = { applyMode = ImportMode.entries[it] },
						shape = RoundedCornerShape(24.dp),
					)
					if (changing.isNotEmpty()) {
						SettingsGroup(title = stringResource(R.string.import_changes_group, changing.size)) {
							changing.forEach { field ->
								item { pos ->
									FieldRow(
										manga = manga,
										field = field,
										now = if (field == ImportField.COVER) current.cover else current.values[field],
										value = if (field == ImportField.COVER) coverUrl else tracker.valueOf(field, context),
										checked = field in selection,
										onToggle = { on ->
											selection = if (on) selection + (field to applyMode.forField(field)) else selection - field
										},
										imageLoader = imageLoader,
										shape = pos.shape,
										below = if (field == ImportField.COVER && canChooseQuality && field in selection) {
											{
												SegmentedRow(
													labels = listOf(
														stringResource(R.string.import_cover_standard),
														stringResource(R.string.import_cover_high),
													),
													selectedIndex = quality,
													onSelected = { quality = it },
													enabled = true,
												)
											}
										} else null,
									)
								}
							}
						}
					}
					if (resting.isNotEmpty()) {
						SettingsGroup(title = stringResource(R.string.import_resting_group, resting.size)) {
							resting.forEach { field ->
								item { pos ->
									FieldRow(
										manga = manga,
										field = field,
										now = if (states[field] == FieldState.KEPT) {
											if (field == ImportField.COVER) current.cover else current.values[field]
										} else null,
										value = if (field == ImportField.COVER) coverUrl else tracker.valueOf(field, context),
										checked = field in selection,
										dimmed = field !in selection,
										onToggle = { on ->
											// A kept or identical field only changes when it is replaced outright
											selection = if (on) selection + (field to field.modes.first()) else selection - field
										},
										imageLoader = imageLoader,
										shape = pos.shape,
									)
								}
							}
						}
					}
					if (missing.isNotEmpty()) {
						Text(
							text = stringResource(
								R.string.import_unavailable,
								missing.joinToString(", ") { context.getString(it.titleResId) },
							),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
						)
					}
				}
				Spacer(Modifier.height(16.dp))
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					ExpressivePillButton(
						text = stringResource(android.R.string.cancel),
						primary = false,
						modifier = Modifier.weight(1f),
						onClick = onDismiss,
					)
					ExpressivePillButton(
						text = context.resources.getQuantityString(R.plurals.import_changes, selection.size, selection.size),
						enabled = selection.isNotEmpty(),
						modifier = Modifier.weight(2f),
						onClick = { onImport(tracker, selection, coverUrl) },
					)
				}
			}
		}
	}
}

@Composable
private fun FieldRow(
	manga: Manga,
	field: ImportField,
	now: String?,
	value: String?,
	checked: Boolean,
	onToggle: (Boolean) -> Unit,
	imageLoader: ImageLoader,
	shape: androidx.compose.ui.graphics.Shape,
	dimmed: Boolean = false,
	below: (@Composable () -> Unit)? = null,
) {
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	SettingsItem(
		title = stringResource(field.titleResId),
		shape = shape,
		onClick = { onToggle(!checked) },
		trailing = { SettingsSwitch(checked = checked, onCheckedChange = onToggle) },
		below = below,
		supporting = {
			if (field == ImportField.COVER) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					if (now != null && now != value) {
						CoverThumb(manga, now, imageLoader)
						Text("→", color = MaterialTheme.colorScheme.primary)
					}
					CoverThumb(manga, value, imageLoader)
				}
			} else {
				val shown = if (field == ImportField.ALT_NAMES) stringResource(R.string.alt_names_hint) else value
				if (!now.isNullOrBlank() && !dimmed) {
					Text(
						text = now,
						style = MaterialTheme.typography.bodySmall,
						color = muted,
						textDecoration = TextDecoration.LineThrough,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (shown != null) {
					Text(
						text = shown,
						style = MaterialTheme.typography.bodySmall,
						color = if (dimmed) muted else MaterialTheme.colorScheme.primary,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				}
				if (dimmed && !now.isNullOrBlank()) {
					Text(
						text = stringResource(R.string.import_kept, now),
						style = MaterialTheme.typography.bodySmall,
						color = muted,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
		},
	)
}

@Composable
private fun CoverThumb(manga: Manga, url: String?, imageLoader: ImageLoader) {
	val context = LocalContext.current
	AsyncImage(
		model = remember(url) {
			ImageRequest.Builder(context).data(url).crossfade(true).mangaSourceExtra(manga.source).build()
		},
		imageLoader = imageLoader,
		contentDescription = null,
		contentScale = ContentScale.Crop,
		modifier = Modifier
			.size(width = 40.dp, height = 56.dp)
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceContainerHighest)
			.coverBlur(manga),
	)
}

/**
 * What an import of [value] over [now] does under [mode]. Null when the tracker has nothing for
 * the field. A cover is a change whenever it differs: the current one is the source's, not the user's.
 */
private fun stateOf(field: ImportField, value: String?, now: String?, mode: ImportMode): FieldState? {
	if (value.isNullOrBlank()) return null
	if (now.isNullOrBlank()) return FieldState.CHANGE
	if (isSame(field, now, value)) return FieldState.SAME
	return if (mode == ImportMode.FILL && field != ImportField.COVER) FieldState.KEPT else FieldState.CHANGE
}

private fun isSame(field: ImportField, now: String, value: String): Boolean {
	fun list(text: String) = text.split(',', '\n').map { it.trim().replace('_', ' ').lowercase() }.filter { it.isNotEmpty() }.toSet()
	return when (field) {
		ImportField.GENRES, ImportField.AUTHORS -> list(now) == list(value)
		else -> now.trim().equals(value.trim(), ignoreCase = true)
	}
}

/** The mode a field is imported with when the user picked [this] for the whole import. */
private fun ImportMode.forField(field: ImportField) = if (this in field.modes) this else field.modes.first()

private val ImportMode.hintResId: Int
	get() = when (this) {
		ImportMode.REPLACE -> R.string.import_hint_replace
		ImportMode.APPEND -> R.string.import_hint_append
		ImportMode.FILL -> R.string.import_hint_fill
	}

private const val COVER_STANDARD = 0
private const val COVER_HIGH = 1

private fun ScrobblingInfo.valueOf(field: ImportField, context: Context): String? = when (field) {
	ImportField.COVER -> coverUrl
	ImportField.TITLE -> title
	ImportField.DESCRIPTION -> description?.toString()
	ImportField.ALT_NAMES -> altNames.joinToString(", ")
	ImportField.GENRES -> genres.joinToString(", ")
	ImportField.AUTHORS -> authors.joinToString(", ")
	ImportField.STATUS -> asMangaState()?.let { context.getString(it.titleResId) }
	ImportField.CONTENT_RATING -> if (isAdult) context.getString(ContentRating.ADULT.titleResId) else null
	ImportField.RATING -> if (score > 0f) "%.1f / 10".format(score) else null
}

private fun ScrobblingInfo.availableFields(context: Context): Set<ImportField> =
	ImportField.entries.filterTo(HashSet()) { !valueOf(it, context).isNullOrBlank() }

/** The tracker's publication status as the app's own status. */
fun ScrobblingInfo.asMangaState(): MangaState? = when (mangaStatus) {
	ScrobblerMangaStatus.UPCOMING -> MangaState.UPCOMING
	ScrobblerMangaStatus.ONGOING -> MangaState.ONGOING
	ScrobblerMangaStatus.FINISHED -> MangaState.FINISHED
	ScrobblerMangaStatus.PAUSED -> MangaState.PAUSED
	ScrobblerMangaStatus.CANCELLED -> MangaState.ABANDONED
	null -> null
}

private val SHEET_CORNER = 28.dp
