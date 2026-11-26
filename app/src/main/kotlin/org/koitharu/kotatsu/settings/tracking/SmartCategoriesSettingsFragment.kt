package org.koitharu.kotatsu.settings.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.details.ui.scrobbling.iconResId
import org.koitharu.kotatsu.details.ui.scrobbling.labelResId
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.ChoiceDialog
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import javax.inject.Inject

/** Binds each tracker status to a favourites category; see [org.koitharu.kotatsu.scrobbling.smartcategories.SmartCategoriesController]. */
@AndroidEntryPoint
class SmartCategoriesSettingsFragment : BaseComposeSettingsFragment(R.string.smart_categories) {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var favourites: FavouritesRepository

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				val categories by favourites.observeCategories().collectAsState(emptyList())
				SmartCategoriesScreen(
					categories = categories,
					bindings = remember {
						mutableStateMapOf<ScrobblingStatus, Long>().apply {
							ScrobblingStatus.entries.forEach { put(it, settings.getSmartCategory(it)) }
						}
					},
					onBind = settings::setSmartCategory,
				)
			}
		}
	}
}

@Composable
private fun SmartCategoriesScreen(
	categories: List<FavouriteCategory>,
	bindings: MutableMap<ScrobblingStatus, Long>,
	onBind: (ScrobblingStatus, Long) -> Unit,
) {
	var enabled by rememberBooleanPref(AppSettings.KEY_SMART_CATEGORIES, false)
	var picking by remember { mutableStateOf<ScrobblingStatus?>(null) }
	val unbound = stringResource(R.string.smart_categories_unbound)
	picking?.let { status ->
		val current = bindings[status] ?: 0L
		ChoiceDialog(
			title = stringResource(status.labelResId),
			entries = categories.map { it.title } + unbound,
			selectedIndex = categories.indexOfFirst { it.id == current }.takeIf { it >= 0 } ?: categories.size,
			onSelect = { index ->
				val id = categories.getOrNull(index)?.id ?: 0L
				bindings[status] = id
				onBind(status, id)
			},
			onDismiss = { picking = null },
			icon = R.drawable.ic_smart_category,
		)
	}
	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_categories),
						subtitle = stringResource(R.string.smart_categories_summary),
						checked = enabled,
						onCheckedChange = { enabled = it },
						icon = R.drawable.ic_smart_category,
						shape = pos.shape,
						main = true,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.smart_categories_bindings)) {
				ScrobblingStatus.entries.forEach { status ->
					item { pos ->
						val id = bindings[status] ?: 0L
						SettingsItem(
							title = stringResource(status.labelResId),
							// A deleted category shows as unbound, matching how the controller treats it.
							subtitle = categories.firstOrNull { it.id == id }?.title ?: unbound,
							icon = status.iconResId,
							shape = pos.shape,
							enabled = enabled,
							onClick = { picking = status },
						)
					}
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
