package org.koitharu.kotatsu.settings.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.scrobbling.common.domain.SmartRules
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.SegmentedSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberIntPref
import javax.inject.Inject

/** Chooses which status changes reading may make on a tracker; see [SmartRules]. */
@AndroidEntryPoint
class SmartStatusSettingsFragment : BaseComposeSettingsFragment(R.string.smart_reading_status) {

	@Inject
	lateinit var settings: AppSettings

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				SmartStatusScreen(settings.smartStatusRules)
			}
		}
	}
}

@Composable
private fun SmartStatusScreen(defaults: SmartRules) {
	var startReading by rememberBooleanPref(AppSettings.KEY_SMART_START_READING, defaults.startReading)
	var resumePaused by rememberBooleanPref(AppSettings.KEY_SMART_RESUME_PAUSED, defaults.resumePaused)
	var resumeDropped by rememberBooleanPref(AppSettings.KEY_SMART_RESUME_DROPPED, defaults.resumeDropped)
	var reRead by rememberBooleanPref(AppSettings.KEY_SMART_REREAD, defaults.reRead)
	var completion by rememberIntPref(AppSettings.KEY_SMART_COMPLETION, defaults.complete.ordinal)
	var askFirst by rememberBooleanPref(AppSettings.KEY_SMART_ASK, defaults.askFirst)
	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.smart_status_when_reading)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_status_start),
						subtitle = stringResource(R.string.smart_status_start_summary),
						checked = startReading,
						onCheckedChange = { startReading = it },
						icon = R.drawable.ic_play,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_status_resume_paused),
						subtitle = stringResource(R.string.smart_status_resume_paused_summary),
						checked = resumePaused,
						onCheckedChange = { resumePaused = it },
						icon = R.drawable.ic_pause,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_status_resume_dropped),
						subtitle = stringResource(R.string.smart_status_resume_dropped_summary),
						checked = resumeDropped,
						onCheckedChange = { resumeDropped = it },
						icon = R.drawable.ic_play_circle,
						shape = pos.shape,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_status_reread),
						subtitle = stringResource(R.string.smart_status_reread_summary),
						checked = reRead,
						onCheckedChange = { reRead = it },
						icon = R.drawable.ic_restart,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.smart_status_finishing)) {
				item { pos ->
					SegmentedSettingsItem(
						title = stringResource(R.string.smart_status_complete),
						subtitle = stringResource(R.string.smart_status_complete_summary),
						labels = listOf(
							stringResource(R.string.smart_status_complete_off),
							stringResource(R.string.smart_status_complete_auto),
							stringResource(R.string.smart_status_complete_source),
						),
						selectedIndex = completion.coerceIn(0, 2),
						onSelected = { completion = it },
						icon = R.drawable.ic_playlist_add_check,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.smart_status_confirmation)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.smart_status_ask),
						subtitle = stringResource(R.string.smart_status_ask_summary),
						checked = askFirst,
						onCheckedChange = { askFirst = it },
						icon = R.drawable.ic_eye,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
