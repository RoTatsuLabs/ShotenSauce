package org.koitharu.kotatsu.backup.local.ui.backup

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
import org.koitharu.kotatsu.backup.MihonBackupExporter
import org.koitharu.kotatsu.backup.local.domain.BackupJobs
import org.koitharu.kotatsu.backup.local.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import org.koitharu.kotatsu.core.util.progress.Progress
import java.io.FileNotFoundException
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

/** Writes a Mihon backup in a foreground service, with the same progress and result notifications as the other backups. */
@AndroidEntryPoint
@SuppressLint("InlinedApi")
class MihonExportService : BaseBackupRestoreService() {

	override val notificationTag = TAG
	override val isRestoreService = false

	@Inject
	lateinit var exporter: MihonBackupExporter

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		setForeground(
			FOREGROUND_NOTIFICATION_ID,
			buildNotification(Progress.INDETERMINATE),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
		)
		val destination = intent.getStringExtra(AppRouter.KEY_DATA)?.toUriOrNull()
			?: throw FileNotFoundException()
		runExclusive(BackupJobs.Kind.MIHON_EXPORT) {
			val progress = MutableStateFlow(Progress.INDETERMINATE)
			val updateJob = launch {
				progress.collect {
					BackupJobs.update(it)
					if (applicationContext.checkNotificationPermission(CHANNEL_ID)) {
						notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildNotification(it))
					}
				}
			}
			try {
				val report = exporter.export(destination, onProgress = { progress.value = it })
				updateJob.cancelAndJoin()
				contentResolver.notifyChange(destination, null)
				showReportNotification(report)
			} catch (e: Throwable) {
				updateJob.cancel()
				runCatching { androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, destination)?.delete() }
				throw e
			}
		}
	}

	private fun IntentJobContext.showReportNotification(report: MihonBackupExporter.Report) {
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) return
		val text = if (report.skippedCount > 0) {
			getString(R.string.export_to_mihon_done_partial, report.exportedCount, report.skippedCount)
		} else {
			getString(R.string.export_to_mihon_done, report.exportedCount)
		}
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.general_notification)
			.setContentTitle(getString(R.string.export_to_mihon))
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setSilent(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.homeIntent(this@MihonExportService),
					0,
					false,
				),
			)
			.build()
		notificationManager.notify(notificationTag, startId, notification)
	}

	private fun IntentJobContext.buildNotification(progress: Progress): Notification {
		return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setContentTitle(getString(R.string.export_to_mihon))
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
			.setSmallIcon(android.R.drawable.stat_sys_upload)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(
				appcompatR.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				getCancelIntent(),
			).build()
	}

	companion object {

		private const val TAG = "MIHON_EXPORT"
		private const val FOREGROUND_NOTIFICATION_ID = 43

		@CheckResult
		fun start(context: Context, uri: Uri): Boolean = try {
			val intent = Intent(context, MihonExportService::class.java)
			intent.putExtra(AppRouter.KEY_DATA, uri.toString())
			ContextCompat.startForegroundService(context, intent)
			true
		} catch (e: Exception) {
			e.printStackTraceDebug()
			false
		}
	}
}
