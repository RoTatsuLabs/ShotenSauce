package org.koitharu.kotatsu.backup.local.domain

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.progress.Progress

/**
 * Process-wide record of the backup or restore that is running right now. Every path (local
 * backup, restore, Mihon import/export, scheduled backup, Telegram send) takes the slot before it
 * starts, so only one runs at a time and the UI can show the same progress for all of them.
 */
object BackupJobs {

	enum class Kind(@StringRes val titleResId: Int) {
		BACKUP(R.string.creating_backup),
		RESTORE(R.string.restoring_backup),
		MIHON_RESTORE(R.string.restoring_backup),
		MIHON_EXPORT(R.string.export_to_mihon),
		SCHEDULED(R.string.creating_backup),
		TELEGRAM(R.string.backup_job_sending_telegram),
	}

	data class State(
		val kind: Kind,
		val progress: Progress,
	)

	private val lock = Any()
	private val mutableState = MutableStateFlow<State?>(null)
	private var cancelAction: (() -> Unit)? = null

	val state: StateFlow<State?> = mutableState.asStateFlow()

	val isBusy: Boolean
		get() = mutableState.value != null

	/** @return false if another job already holds the slot. */
	fun tryAcquire(kind: Kind, cancel: (() -> Unit)?): Boolean = synchronized(lock) {
		if (mutableState.value != null) {
			false
		} else {
			cancelAction = cancel
			mutableState.value = State(kind, Progress.INDETERMINATE)
			true
		}
	}

	fun update(progress: Progress) {
		mutableState.value = mutableState.value?.copy(progress = progress) ?: return
	}

	fun release() = synchronized(lock) {
		cancelAction = null
		mutableState.value = null
	}

	fun cancel() {
		val action = synchronized(lock) { cancelAction }
		action?.invoke()
	}
}
