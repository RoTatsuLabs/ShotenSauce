package org.koitharu.kotatsu.backup.local.ui.periodical

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import org.koitharu.kotatsu.backup.local.domain.BackupJobs
import org.koitharu.kotatsu.backup.local.ui.BackupCancelReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.local.data.LocalBackupRepository
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.koitharu.kotatsu.backup.local.domain.BackupUtils
import org.koitharu.kotatsu.backup.local.domain.ExternalBackupStorage
import org.koitharu.kotatsu.backup.local.domain.TelegramBackupUploader
import org.koitharu.kotatsu.backup.local.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.trySetForeground
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltWorker
class PeriodicalBackupWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val repository: LocalBackupRepository,
	private val externalBackupStorage: ExternalBackupStorage,
	private val telegramUploader: TelegramBackupUploader,
	private val settings: AppSettings,
) : CoroutineWorker(context, workerParams) {

	private val notificationManager by lazy { NotificationManagerCompat.from(applicationContext) }

	@Volatile
	private var currentProgress = Progress.INDETERMINATE

	@Volatile
	private var isTelegramOnly = false

	override suspend fun doWork(): Result {
		// The device backup and the Telegram one run on their own schedules, each as its own work
		val target = inputData.getString(KEY_TARGET)
		val saveLocally = target != TARGET_TELEGRAM && settings.periodicalBackupDirectory != null
		val sendToTelegram = target != TARGET_LOCAL && settings.isBackupTelegramConfigured
		if (!saveLocally && !sendToTelegram) {
			return Result.success()
		}
		isTelegramOnly = !saveLocally
		val job = currentCoroutineContext()[Job]
		val kind = if (saveLocally) BackupJobs.Kind.SCHEDULED else BackupJobs.Kind.TELEGRAM
		// Another backup or restore is running: retry later so two never run at once
		if (!BackupJobs.tryAcquire(kind) { job?.cancel() }) {
			return Result.retry()
		}
		try {
			return runBackup(saveLocally, sendToTelegram)
		} finally {
			BackupJobs.release()
		}
	}

	private suspend fun updateProgress(progress: Progress) {
		currentProgress = progress
		BackupJobs.update(progress)
		if (applicationContext.checkNotificationPermission(BaseBackupRestoreService.CHANNEL_ID)) {
			trySetForeground()
		}
	}

	private suspend fun runBackup(saveLocally: Boolean, sendToTelegram: Boolean): Result {
		trySetForeground()
		val localSections = settings.backupSections
		val telegramSections = settings.backupTelegramSections
		val files = ArrayList<File>(2)
		var outputUri: Uri? = null
		var fileName: String? = null
		var telegramError: Throwable? = null
		try {
			var localFile: File? = null
			if (saveLocally) {
				localFile = createBackupFile(localSections).also(files::add)
				fileName = localFile.name
				outputUri = externalBackupStorage.put(localFile)
			}
			if (sendToTelegram) {
				try {
					val telegramFile = if (localFile != null && localSections == telegramSections) {
						localFile
					} else {
						createBackupFile(telegramSections).also(files::add)
					}
					fileName = fileName ?: telegramFile.name
					updateProgress(Progress.INDETERMINATE)
					telegramUploader.send(telegramFile)
				} catch (e: CancellationException) {
					throw e
				} catch (e: Throwable) {
					if (!saveLocally) throw e
					e.printStackTraceDebug()
					telegramError = e
				}
			}
		} catch (e: CancellationException) {
			files.forEach { it.delete() }
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			files.forEach { it.delete() }
			postErrorNotification(e)
			return Result.failure()
		}
		files.forEach { it.delete() }
		if (outputUri != null) {
			externalBackupStorage.trim(settings.periodicalBackupMaxCount)
		}
		if (telegramError != null) {
			postErrorNotification(telegramError)
		} else {
			postCompletionNotification(outputUri?.lastPathSegment ?: fileName, telegramOnly = !saveLocally)
		}
		return Result.success()
	}

	private suspend fun createBackupFile(sections: Set<BackupSection>): File {
		val file = BackupUtils.createTempFile(applicationContext)
		val progress = MutableStateFlow(Progress.INDETERMINATE)
		try {
			coroutineScope {
				val updateJob = launch { progress.collect { updateProgress(it) } }
				try {
					ZipOutputStream(file.outputStream()).use { output ->
						repository.createBackup(output, progress, sections)
					}
				} finally {
					updateJob.cancel()
				}
			}
		} catch (e: Throwable) {
			file.delete()
			throw e
		}
		return file
	}

	override suspend fun getForegroundInfo(): ForegroundInfo {
		BaseBackupRestoreService.createNotificationChannel(applicationContext)
		val notification = buildOngoingNotification(currentProgress)
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(
				FOREGROUND_NOTIFICATION_ID,
				notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
			)
		} else {
			ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
		}
	}

	private fun buildOngoingNotification(progress: Progress): Notification {
		return NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(
				applicationContext.getString(
					if (isTelegramOnly) R.string.backup_job_sending_telegram else R.string.creating_backup,
				),
			)
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
					applicationContext.getString(R.string.processing_)
				} else {
					applicationContext.getString(R.string.fraction_pattern, progress.progress, progress.total)
				},
			)
			.setSmallIcon(android.R.drawable.stat_sys_upload)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(
				androidx.appcompat.R.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				BackupCancelReceiver.createIntent(applicationContext),
			)
			.build()
	}

	private fun postCompletionNotification(name: String?, telegramOnly: Boolean) {
		if (!applicationContext.checkNotificationPermission(BaseBackupRestoreService.CHANNEL_ID)) return
		val notification = NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(applicationContext.getString(if (telegramOnly) R.string.backup_telegram_sent else R.string.backup_saved))
			.setContentText(name)
			.setSmallIcon(R.drawable.general_notification)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setSilent(true)
			.setAutoCancel(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.homeIntent(applicationContext),
					0,
					false,
				),
			)
			.build()
		notificationManager.notify(TAG_RESULT, RESULT_NOTIFICATION_ID, notification)
	}

	private fun postErrorNotification(error: Throwable) {
		if (!applicationContext.checkNotificationPermission(BaseBackupRestoreService.CHANNEL_ID)) return
		val message = error.getDisplayMessage(applicationContext.resources)
		val notification = NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.error_occurred))
			.setContentText(message)
			.setStyle(NotificationCompat.BigTextStyle().bigText(message))
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setSilent(true)
			.setAutoCancel(true)
			.build()
		notificationManager.notify(TAG_RESULT, RESULT_NOTIFICATION_ID, notification)
	}

	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		private val settings: AppSettings,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val deviceFrequency = if (settings.periodicalBackupDirectory != null) {
				settings.periodicalBackupFrequencyMillis
			} else {
				0L
			}
			scheduleTarget(TARGET_LOCAL, TAG, deviceFrequency)
			scheduleTarget(TARGET_TELEGRAM, TAG_TELEGRAM, settings.telegramBackupFrequencyMillis)
		}

		private suspend fun scheduleTarget(target: String, tag: String, frequencyMillis: Long) {
			if (frequencyMillis <= 0L) {
				workManager.cancelUniqueWork(tag).await()
				return
			}
			val constraints = Constraints.Builder()
				.setRequiresStorageNotLow(true)
				.setRequiresBatteryNotLow(true)
				.build()
			val request = PeriodicWorkRequestBuilder<PeriodicalBackupWorker>(frequencyMillis, TimeUnit.MILLISECONDS)
				.setConstraints(constraints)
				.setInputData(workDataOf(KEY_TARGET to target))
				.addTag(tag)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
				.build()
			workManager
				.enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager.cancelUniqueWork(TAG).await()
			workManager.cancelUniqueWork(TAG_TELEGRAM).await()
		}

		override suspend fun isScheduled(): Boolean {
			return listOf(TAG, TAG_TELEGRAM).any { tag ->
				workManager.awaitUniqueWorkInfoByName(tag).any { !it.state.isFinished }
			}
		}
	}

	companion object {

		/** Sends a backup to Telegram right now, as a foreground job with progress and a cancel button. */
		fun sendToTelegramNow(context: Context) {
			val request = OneTimeWorkRequestBuilder<PeriodicalBackupWorker>()
				.setInputData(workDataOf(KEY_TARGET to TARGET_TELEGRAM))
				.addTag(TAG_TELEGRAM_NOW)
				.build()
			WorkManager.getInstance(context)
				.enqueueUniqueWork(TAG_TELEGRAM_NOW, ExistingWorkPolicy.KEEP, request)
		}

		private const val TAG_TELEGRAM_NOW = "backup_telegram_now"
		private const val FOREGROUND_NOTIFICATION_ID = 41
		private const val RESULT_NOTIFICATION_ID = 42
		private const val TAG = "periodical_backup"
		private const val TAG_TELEGRAM = "periodical_backup_telegram"
		private const val KEY_TARGET = "target"
		private const val TARGET_LOCAL = "local"
		private const val TARGET_TELEGRAM = "telegram"
		private const val TAG_RESULT = "periodical_backup_result"
	}
}
