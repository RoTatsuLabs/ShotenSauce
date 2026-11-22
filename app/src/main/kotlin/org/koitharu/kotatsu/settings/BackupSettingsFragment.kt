package org.koitharu.kotatsu.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.MihonBackupExporter
import org.koitharu.kotatsu.backup.MihonBackupManager
import org.koitharu.kotatsu.backup.MihonBackupManager.Options
import org.koitharu.kotatsu.backup.MihonBackupManager.RestoreReport
import org.koitharu.kotatsu.backup.local.domain.BackupJobs
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.koitharu.kotatsu.backup.local.ui.NotificationPermissionRequester
import org.koitharu.kotatsu.backup.local.ui.backup.MihonExportService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import org.koitharu.kotatsu.backup.local.domain.BackupUtils
import org.koitharu.kotatsu.backup.local.ui.backup.BackupService
import org.koitharu.kotatsu.backup.local.ui.periodical.PeriodicalBackupSettingsFragment
import org.koitharu.kotatsu.backup.local.ui.restore.MihonRestoreService
import org.koitharu.kotatsu.backup.local.ui.restore.RestoreDialogFragment
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.settings.compose.SettingsItem
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.ui.dialog.showConfirmDialog
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.kotatsumigration.domain.KotatsuMigrationManager
import org.koitharu.kotatsu.kotatsumigration.domain.MigrationState
import org.koitharu.kotatsu.kotatsumigration.ui.KotatsuMigrationService
import org.koitharu.kotatsu.kotatsumigration.ui.showExtensionInstallPromptDialog
import org.koitharu.kotatsu.kotatsumigration.ui.showKotatsuMigrationCompleteDialog
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.MultiChoiceDialog
import org.koitharu.kotatsu.settings.compose.NavigationSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import javax.inject.Inject

@AndroidEntryPoint
class BackupSettingsFragment : BaseComposeSettingsFragment(R.string.backup_restore) {

	@Inject
	lateinit var backupManager: MihonBackupManager

	@Inject
	lateinit var migrationManager: KotatsuMigrationManager

	@Inject
	lateinit var mihonExporter: MihonBackupExporter

	private val restoreMihonBackupLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			startMihonRestore(uri)
		}
	}

	private val notificationPermission = NotificationPermissionRequester(this)

	private var pendingSections: Set<BackupSection>? = null

	private val createLocalBackupLauncher = registerForActivityResult(
		ActivityResultContracts.CreateDocument(BackupUtils.MIME_TYPE),
	) { uri ->
		if (uri != null && !isBusy() && BackupService.start(requireContext(), uri, pendingSections)) {
			Toast.makeText(requireContext(), R.string.creating_backup, Toast.LENGTH_SHORT).show()
		}
	}

	private val exportMihonBackupLauncher = registerForActivityResult(
		ActivityResultContracts.CreateDocument(MihonBackupExporter.MIME_TYPE),
	) { uri ->
		if (uri != null) {
			startMihonExport(uri)
		}
	}

	private val restoreLocalBackupLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null && !isBusy()) {
			RestoreDialogFragment.show(parentFragmentManager, uri)
		}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				val migrationState by migrationManager.state.collectAsState()
				val migrationSubtitle = when (val s = migrationState) {
					is MigrationState.Running -> stringResource(
						R.string.kotatsu_migration_progress,
						s.done,
						s.total,
					)

					is MigrationState.Finished -> stringResource(
						R.string.kotatsu_migration_result,
						s.summary.converted,
						s.summary.total,
					)

					MigrationState.Idle -> stringResource(R.string.migrate_from_kotatsu_summary)
				}
				val activeJob by BackupJobs.state.collectAsState()
				BackupScreen(
					activeJob = activeJob,
					onCancelJob = BackupJobs::cancel,
					onCreateBackup = { sections ->
						pendingSections = sections
						createLocalBackupLauncher.launch(
							BackupUtils.generateFileName(requireContext()),
						)
					},
					onRestoreLocal = {
						restoreLocalBackupLauncher.launch(
							arrayOf(BackupUtils.MIME_TYPE, "application/*", "*/*"),
						)
					},
					onOpenPeriodic = {
						(activity as? SettingsActivity)?.openFragment(
							PeriodicalBackupSettingsFragment::class.java,
							null,
							isFromRoot = false,
						)
					},
					onRestoreFromTachiyomi = {
						restoreMihonBackupLauncher.launch(arrayOf("application/*", "*/*"))
					},
					migrationSubtitle = migrationSubtitle,
					onMigrateFromKotatsu = ::confirmAndStartKotatsuMigration,
					onExportToMihon = {
						exportMihonBackupLauncher.launch(MihonBackupExporter.generateFileName())
					},
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		notificationPermission.requestOnce(requireContext())
		migrationManager.onStarted.observeEvent(viewLifecycleOwner) {
			Toast.makeText(requireContext(), R.string.kotatsu_migration_started, Toast.LENGTH_SHORT).show()
		}
		// The restore itself runs in a service; this only offers the missing extensions if the screen is still open
		backupManager.onRestoreCompleted.observeEvent(viewLifecycleOwner) { report ->
			requireContext().showExtensionInstallPromptDialog(report.missingSources)
		}
		migrationManager.onCompleted.observeEvent(viewLifecycleOwner) { summary ->
			requireContext().showKotatsuMigrationCompleteDialog(summary)
		}
	}

	private fun confirmAndStartKotatsuMigration() {
		if (migrationManager.isRunning) {
			Toast.makeText(requireContext(), R.string.kotatsu_migration_running, Toast.LENGTH_SHORT).show()
			return
		}
		showConfirmDialog(
			context = requireContext(),
			icon = R.drawable.ic_migrate,
			title = getString(R.string.migrate_from_kotatsu),
			message = getString(R.string.migrate_from_kotatsu_confirm),
			confirmLabel = getString(R.string.migrate_from_kotatsu),
		) {
			if (KotatsuMigrationService.start(requireContext())) {
				Toast.makeText(requireContext(), R.string.kotatsu_migration_running, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun isBusy(): Boolean {
		if (!BackupJobs.isBusy) return false
		Toast.makeText(requireContext(), R.string.backup_job_busy, Toast.LENGTH_SHORT).show()
		return true
	}

	private fun startMihonExport(uri: Uri) {
		if (isBusy()) return
		if (MihonExportService.start(requireContext(), uri)) {
			Toast.makeText(requireContext(), R.string.export_to_mihon, Toast.LENGTH_SHORT).show()
		}
	}

	private fun startMihonRestore(uri: Uri) {
		val context = requireContext()
		if (isBusy()) return
		if (MihonRestoreService.start(context, uri)) {
			Toast.makeText(context, R.string.restoring_backup, Toast.LENGTH_SHORT).show()
		}
	}

}

@Composable
private fun BackupScreen(
	activeJob: BackupJobs.State?,
	onCancelJob: () -> Unit,
	onCreateBackup: (Set<BackupSection>) -> Unit,
	onRestoreLocal: () -> Unit,
	onOpenPeriodic: () -> Unit,
	onRestoreFromTachiyomi: () -> Unit,
	migrationSubtitle: String,
	onMigrateFromKotatsu: () -> Unit,
	onExportToMihon: () -> Unit,
) {
	var showCreateDialog by remember { mutableStateOf(false) }

	if (showCreateDialog) {
		val sections = BackupSection.selectable
		MultiChoiceDialog(
			title = stringResource(R.string.backup_contents),
			entries = sections.map { stringResource(it.titleResId) },
			selectedIndices = sections.indices.toSet(),
			onConfirm = { indices ->
				if (indices.isNotEmpty()) {
					onCreateBackup(indices.mapTo(HashSet()) { sections[it] })
				}
			},
			onDismiss = { showCreateDialog = false },
		)
	}
	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.backup_restore)) {
				item { pos ->
					SettingsItem(
						title = stringResource(R.string.create_backup),
						subtitle = stringResource(R.string.create_backup_summary),
						icon = R.drawable.ic_save,
						
						shape = pos.shape,
						onClick = { showCreateDialog = true },
						below = jobBar(activeJob, onCancelJob, BackupJobs.Kind.BACKUP),
					)
				}
				item { pos ->
					SettingsItem(
						title = stringResource(R.string.restore_backup),
						subtitle = stringResource(R.string.restore_summary),
						icon = R.drawable.ic_revert,
						
						shape = pos.shape,
						onClick = onRestoreLocal,
						below = jobBar(activeJob, onCancelJob, BackupJobs.Kind.RESTORE),
					)
				}
				item { pos ->
					SettingsItem(
						title = stringResource(R.string.periodic_backups),
						subtitle = stringResource(R.string.periodic_backups_summary),
						icon = R.drawable.ic_backup_restore,
						
						shape = pos.shape,
						onClick = onOpenPeriodic,
						below = jobBar(activeJob, onCancelJob, BackupJobs.Kind.SCHEDULED, BackupJobs.Kind.TELEGRAM),
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.other_apps)) {
				item { pos ->
					SettingsItem(
						title = stringResource(R.string.restore_from_tachiyomi),
						subtitle = stringResource(R.string.restore_tachiyomi_summary),
						icon = R.drawable.ic_revert,
						shape = pos.shape,
						onClick = onRestoreFromTachiyomi,
						below = jobBar(activeJob, onCancelJob, BackupJobs.Kind.MIHON_RESTORE),
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.migrate_from_kotatsu),
						subtitle = migrationSubtitle,
						icon = R.drawable.ic_backup_restore,

						shape = pos.shape,
						onClick = onMigrateFromKotatsu,
					)
				}
				item { pos ->
					SettingsItem(
						title = stringResource(R.string.export_to_mihon),
						subtitle = stringResource(R.string.export_to_mihon_summary),
						icon = R.drawable.ic_upload_file,

						shape = pos.shape,
						onClick = onExportToMihon,
						below = jobBar(activeJob, onCancelJob, BackupJobs.Kind.MIHON_EXPORT),
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}

@Composable
private fun JobBar(progress: Progress, onCancel: () -> Unit) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (progress.isIndeterminate || progress.total <= 0) {
			LinearProgressIndicator(modifier = Modifier.weight(1f))
		} else {
			LinearProgressIndicator(
				progress = { progress.progress.toFloat() / progress.total },
				modifier = Modifier.weight(1f),
			)
		}
		TextButton(onClick = onCancel) {
			Text(stringResource(android.R.string.cancel))
		}
	}
}

private fun jobBar(
	job: BackupJobs.State?,
	onCancel: () -> Unit,
	vararg kinds: BackupJobs.Kind,
): (@Composable () -> Unit)? = if (job != null && job.kind in kinds) {
	{ JobBar(job.progress, onCancel) }
} else {
	null
}
