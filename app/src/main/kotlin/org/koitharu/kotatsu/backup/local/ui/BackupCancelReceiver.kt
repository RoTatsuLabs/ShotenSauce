package org.koitharu.kotatsu.backup.local.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.PendingIntentCompat
import org.koitharu.kotatsu.backup.local.domain.BackupJobs

/** Cancels whatever backup or restore currently holds [BackupJobs]; used by notification buttons. */
class BackupCancelReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context?, intent: Intent?) {
		BackupJobs.cancel()
	}

	companion object {

		fun createIntent(context: Context): PendingIntent? = PendingIntentCompat.getBroadcast(
			context,
			0,
			Intent(context, BackupCancelReceiver::class.java),
			PendingIntent.FLAG_UPDATE_CURRENT,
			false,
		)
	}
}
