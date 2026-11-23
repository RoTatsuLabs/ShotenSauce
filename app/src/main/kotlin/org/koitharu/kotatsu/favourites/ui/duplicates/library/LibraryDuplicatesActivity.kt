package org.koitharu.kotatsu.favourites.ui.duplicates.library

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.databinding.ActivityLibraryDuplicatesBinding
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import javax.inject.Inject

/** Every title that sits in the favourites more than once, with a one-tap way to keep a single copy. */
@AndroidEntryPoint
class LibraryDuplicatesActivity : BaseActivity<ActivityLibraryDuplicatesBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel: LibraryDuplicatesViewModel by viewModels()

	private val bottomInset = mutableIntStateOf(0)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityLibraryDuplicatesBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		setTitle(R.string.library_duplicates)
		viewBinding.composeView.setViewCompositionStrategy(
			ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
		)
		viewBinding.composeView.setContent {
			DropSauceTheme {
				val density = LocalDensity.current
				val groups by viewModel.groups.collectAsState()
				LibraryDuplicatesScreen(
					groups = groups,
					imageLoader = coil,
					bottomInset = with(density) { bottomInset.intValue.toDp() },
					onOpen = { router.openDetails(it) },
					onKeep = viewModel::keep,
				)
			}
		}
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.composeView))
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.appbar.updatePaddingRelative(
			start = bars.start(v),
			top = bars.top,
			end = bars.end(v),
		)
		bottomInset.intValue = bars.bottom
		return insets
	}
}
