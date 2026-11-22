package org.koitharu.kotatsu.backup.local.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission

/**
 * Asks for the notification permission once per app launch, so progress and result notifications
 * of backups and restores can actually be shown. Create it as a fragment property.
 */
class NotificationPermissionRequester(fragment: Fragment) {

	private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	fun requestOnce(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || asked) return
		if (context.checkNotificationPermission(null)) return
		asked = true
		launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
	}

	private companion object {

		@Volatile
		var asked = false
	}
}
