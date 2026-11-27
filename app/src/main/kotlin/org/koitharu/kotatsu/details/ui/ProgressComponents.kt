package org.koitharu.kotatsu.details.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.details.ui.model.HistoryInfo
import org.koitharu.kotatsu.list.domain.ReadingProgress

@Composable
internal fun ProgressBentoCard(historyInfo: HistoryInfo, isLoading: Boolean, accent: Color, modifier: Modifier = Modifier) {
	val ctx = LocalContext.current
	val res = ctx.resources
	val chaptersText = when {
		isLoading && historyInfo.totalChapters < 0 -> stringResource(R.string.loading_)
		historyInfo.currentChapter >= 0 ->
			stringResource(R.string.chapter_d_of_d, historyInfo.currentChapter + 1, historyInfo.totalChapters)
		historyInfo.totalChapters == 0 -> stringResource(R.string.no_chapters)
		historyInfo.totalChapters == -1 -> stringResource(R.string.error_occurred)
		else -> pluralStringResource(R.plurals.chapters, historyInfo.totalChapters, historyInfo.totalChapters)
	}
	val timeText = historyInfo.estimatedTime?.let { time ->
		time.formatShort(res)?.let { stringResource(if (time.isContinue) R.string.reading_time_left else R.string.reading_time_total, it) }
	}
	val hasHistory = historyInfo.history != null
	val percent = historyInfo.percent.coerceIn(0f, 1f)
	val showProgress = hasHistory && percent > 0f
	val displayPercent = if (ReadingProgress.isCompleted(historyInfo.percent)) 100 else (percent * 100f).toInt()

	Surface(
		shape = RoundedCornerShape(20.dp),
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		modifier = modifier,
	) {
		// The bar sits at the bottom, so a taller neighbouring tile does not leave a gap under it
		Column(
			modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp),
			verticalArrangement = Arrangement.SpaceBetween,
		) {
			Column {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(
						painter = painterResource(R.drawable.ic_read),
						contentDescription = null,
						tint = accent,
						modifier = Modifier.size(18.dp),
					)
					Spacer(Modifier.width(8.dp))
					Text(
						text = chaptersText,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 3,
					)
				}
				if (showProgress) {
					Spacer(Modifier.height(6.dp))
					Text(
						text = stringResource(R.string.percent_string_pattern, displayPercent.toString()),
						style = MaterialTheme.typography.headlineLarge,
						fontWeight = FontWeight.Light,
						color = accent,
					)
				}
			}
			Column {
				if (showProgress) {
					Spacer(Modifier.height(10.dp))
					val animatedProgress by animateFloatAsState(
						targetValue = percent,
						animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
						label = "readingProgress",
					)
					LinearWavyProgressIndicator(
						progress = { animatedProgress.coerceIn(0f, 1f) },
						color = accent,
						trackColor = accent.copy(alpha = 0.22f),
						modifier = Modifier.fillMaxWidth(),
					)
				}
				if (timeText != null) {
					Spacer(Modifier.height(8.dp))
					Text(
						text = timeText,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}
