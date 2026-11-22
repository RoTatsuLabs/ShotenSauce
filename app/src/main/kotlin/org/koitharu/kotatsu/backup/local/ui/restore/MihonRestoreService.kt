package org.koitharu.kotatsu.backup.local.ui.restore

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.MihonBackupManager
import org.koitharu.kotatsu.backup.local.domain.BackupJobs
import org.koitharu.kotatsu.backup.local.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.powerManager
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.core.util.ext.withPartialWakeLock
import org.koitharu.kotatsu.core.util.progress.Progress
import java.io.FileNotFoundException
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

/**
 * Restores a Mihon/Tachiyomi backup in a foreground service, so it keeps going when the settings
 * screen is left, and shows its progress in a notification.
 */
@AndroidEntryPoint
@SuppressLint("InlinedApi")
class MihonRestoreService : BaseBackupRestoreService() {

	override val notificationTag = TAG
	override val isRestoreService = true

	@Inject
	lateinit var backupManager: MihonBackupManager

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		setForeground(
			FOREGROUND_NOTIFICATION_ID,
			buildNotification(Progress.INDETERMINATE),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
		val source = intent.getStringExtra(AppRouter.KEY_DATA)?.toUriOrNull()
			?: throw FileNotFoundException()
		runExclusive(BackupJobs.Kind.MIHON_RESTORE) {
			val progress = MutableStateFlow(Progress.INDETERMINATE)
			val updateJob = launch {
				progress.collect {
					backupManager.setRestoreProgress(it)
					BackupJobs.update(it)
					if (applicationContext.checkNotificationPermission(CHANNEL_ID)) {
						notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(it))
					}
				}
			}
			try {
				val report = backupManager.restoreBackup(source, onProgress = { progress.value = it })
				updateJob.cancelAndJoin()
				showReportNotification(report)
				backupManager.onRestoreCompleted.call(report)
			} finally {
				updateJob.cancel()
				backupManager.setRestoreProgress(null)
			}
		}
	}

	private fun IntentJobContext.showReportNotification(report: MihonBackupManager.RestoreReport) {
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) return
		val details = buildString {
			append(getString(R.string.restore_report_restored_simple, report.restoredMangaCount))
			if (report.missingSources.isNotEmpty()) {
				append('\n')
				append(report.missingSources.joinToString())
			}
		}
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.general_notification)
			.setContentTitle(getString(R.string.data_restored_success))
			.setContentText(details)
			.setStyle(NotificationCompat.BigTextStyle().bigText(details))
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setSilent(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.homeIntent(this@MihonRestoreService),
					0,
					false,
				),
			)
			.build()
		notificationManager.notify(notificationTag, startId, notification)
	}

	private fun IntentJobContext.buildNotification(progress: Progress): Notification {
		return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setContentTitle(getString(R.string.restoring_backup))
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setDefaults(0)
			.setSilent(true)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(
				progress.total.coerceAtLeast(0),
				progress.progress.coerceAtLeast(0),
				progress.isIndeterminate,
			)
			.setContentText(
				if (progress.isIndeterminate) {
					getString(R.string.processing_)
				} else {
					getString(R.string.fraction_pattern, progress.progress, progress.total)
				},
			)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(
				appcompatR.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				getCancelIntent(),
			).build()
	}

	companion object {

		private const val TAG = "MIHON_RESTORE"
		private const val FOREGROUND_NOTIFICATION_ID = 40

		@CheckResult
		fun start(context: Context, uri: Uri): Boolean = try {
			val intent = Intent(context, MihonRestoreService::class.java)
			intent.putExtra(AppRouter.KEY_DATA, uri.toString())
			ContextCompat.startForegroundService(context, intent)
			true
		} catch (e: Exception) {
			e.printStackTraceDebug()
			false
		}
	}
}
