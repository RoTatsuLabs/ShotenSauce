package org.koitharu.kotatsu.bookmarks.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.setFragmentResult
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.bookmarks.domain.BookmarkColors
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.databinding.SheetSortOrderBinding
import org.koitharu.kotatsu.settings.compose.DropSauceTheme

/** Picks the colour label for the selected bookmarks. Reports the choice through a fragment result. */
@AndroidEntryPoint
class BookmarkColorSheet : BaseAdaptiveSheet<SheetSortOrderBinding>() {

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = SheetSortOrderBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: SheetSortOrderBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.headerBar.setTitle(R.string.bookmark_color)
		binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		binding.composeView.setContent {
			DropSauceTheme {
				Content()
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	private fun choose(color: Int?) {
		setFragmentResult(REQUEST_KEY, bundleOf(KEY_COLOR to (color ?: NO_COLOR)))
		dismiss()
	}

	@Composable
	private fun Content() {
		Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Box(
					modifier = Modifier
						.size(44.dp)
						.clip(CircleShape)
						.border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape)
						.clickable(onClickLabel = stringResource(R.string.bookmark_color_none)) { choose(null) },
					contentAlignment = Alignment.Center,
				) {
					Text(text = "\u2715", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				BookmarkColors.ALL.forEach { color ->
					Box(
						modifier = Modifier
							.size(44.dp)
							.clip(CircleShape)
							.background(Color(color))
							.clickable { choose(color) },
					)
				}
			}
			Text(
				text = stringResource(R.string.bookmark_color_summary),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
			)
		}
	}

	companion object {

		const val REQUEST_KEY = "bookmark_color"
		const val KEY_COLOR = "color"

		/** Result value meaning "remove the colour". */
		const val NO_COLOR = 0
	}
}
