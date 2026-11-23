package org.koitharu.kotatsu.favourites.ui.duplicates.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.favourites.domain.DuplicateCopy
import org.koitharu.kotatsu.favourites.domain.DuplicateGroup
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.parsers.model.Manga

private val ContentPadding = 16.dp

@Composable
fun LibraryDuplicatesScreen(
	groups: List<DuplicateGroup>?,
	imageLoader: ImageLoader,
	bottomInset: androidx.compose.ui.unit.Dp,
	onOpen: (Manga) -> Unit,
	onKeep: (DuplicateGroup, DuplicateCopy) -> Unit,
) {
	when {
		groups == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}

		groups.isEmpty() -> Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(ContentPadding),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(R.string.library_duplicates_none),
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center,
			)
			Text(
				text = stringResource(R.string.library_duplicates_none_summary),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(top = 4.dp),
			)
		}

		else -> LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = ContentPadding,
				end = ContentPadding,
				top = 4.dp,
				bottom = ContentPadding + bottomInset,
			),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			item {
				Text(
					text = stringResource(
						R.string.library_duplicates_summary,
						groups.size,
						groups.sumOf { it.copies.size },
					),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
				)
			}
			items(groups, key = { group -> group.copies.first().manga.id }) { group ->
				GroupCard(group, imageLoader, onOpen, onKeep)
			}
		}
	}
}

@Composable
private fun GroupCard(
	group: DuplicateGroup,
	imageLoader: ImageLoader,
	onOpen: (Manga) -> Unit,
	onKeep: (DuplicateGroup, DuplicateCopy) -> Unit,
) {
	Surface(
		shape = RoundedCornerShape(24.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
	) {
		Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 4.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = group.title,
					style = MaterialTheme.typography.titleMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				Text(
					text = stringResource(R.string.library_duplicates_copies, group.copies.size),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 8.dp),
				)
			}
			group.copies.forEachIndexed { index, copy ->
				CopyRow(
					copy = copy,
					isMostChapters = index == 0 && group.copies.getOrNull(1)?.chaptersCount != copy.chaptersCount,
					imageLoader = imageLoader,
					onOpen = { onOpen(copy.manga) },
					onKeep = { onKeep(group, copy) },
				)
			}
		}
	}
}

@Composable
private fun CopyRow(
	copy: DuplicateCopy,
	isMostChapters: Boolean,
	imageLoader: ImageLoader,
	onOpen: () -> Unit,
	onKeep: () -> Unit,
) {
	val context = LocalContext.current
	Surface(
		shape = RoundedCornerShape(18.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHighest,
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(18.dp))
			.clickable(onClick = onOpen),
	) {
		Row(
			modifier = Modifier.padding(8.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			AsyncImage(
				model = copy.manga.coverUrl,
				imageLoader = imageLoader,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.width(44.dp)
					.aspectRatio(13f / 18f)
					.clip(RoundedCornerShape(10.dp)),
			)
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = copy.manga.source.getTitle(context),
						style = MaterialTheme.typography.bodyMedium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (isMostChapters) {
						Text(
							text = stringResource(R.string.library_duplicates_most_chapters),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier
								.padding(start = 6.dp)
								.clip(RoundedCornerShape(6.dp))
								.background(MaterialTheme.colorScheme.primary)
								.padding(horizontal = 6.dp, vertical = 1.dp),
						)
					}
				}
				Text(
					text = stringResource(R.string.library_duplicates_chapters, copy.chaptersCount),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (ReadingProgress.isValid(copy.progress) && copy.progress > 0f) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp))
							.background(MaterialTheme.colorScheme.surfaceVariant),
					) {
						Box(
							modifier = Modifier
								.fillMaxWidth(copy.progress.coerceIn(0f, 1f))
								.fillMaxHeight()
								.background(MaterialTheme.colorScheme.primary),
						)
					}
				}
				if (copy.categories.isNotEmpty()) {
					Text(
						text = copy.categories.joinToString(" \u00B7 "),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
			FilledTonalButton(onClick = onKeep, contentPadding = PaddingValues(horizontal = 12.dp)) {
				Text(text = stringResource(R.string.library_duplicates_keep), style = MaterialTheme.typography.labelMedium)
			}
		}
	}
}
