package org.koitharu.kotatsu.details.notes

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.databinding.ActivityMangaNotesBinding
import org.koitharu.kotatsu.settings.compose.DropSauceTheme

/** The personal note of a title, full screen: a Markdown editor with a formatting toolbar and a preview. */
@AndroidEntryPoint
class MangaNotesActivity : BaseActivity<ActivityMangaNotesBinding>() {

	private val viewModel by viewModels<MangaNotesViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMangaNotesBinding.inflate(layoutInflater))
		WindowCompat.setDecorFitsSystemWindows(window, false)
		viewBinding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		viewBinding.composeView.setContent {
			DropSauceTheme {
				val note by viewModel.note.collectAsState()
				MangaNotesScreen(
					title = viewModel.manga.title,
					note = note,
					onSave = viewModel::save,
					onBack = ::finish,
				)
			}
		}
	}

	// Compose lays itself out around the system bars and the keyboard
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets
}
