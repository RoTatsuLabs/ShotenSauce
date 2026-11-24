package org.koitharu.kotatsu.core.db

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.local.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Keeps a damaged database from taking the library down with it.
 *
 * At startup, before Room opens anything, the file is checked. A corrupt one is moved to a
 * quarantine folder (never deleted, so it can still be inspected) and the newest healthy snapshot
 * takes its place. Snapshots are plain copies of the database, taken at most once a day while the
 * app is healthy; the last [MAX_SNAPSHOTS] are kept.
 */
object DatabaseGuard {

	const val DB_NAME = "kotatsu-db"

	private const val SNAPSHOT_DIR = "snapshots"
	private const val QUARANTINE_DIR = "quarantine"
	private const val SNAPSHOT_SUFFIX = ".snap"
	private const val CORRUPT_SUFFIX = ".corrupt"
	private const val MAX_SNAPSHOTS = 3
	private const val MAX_QUARANTINED = 3
	private const val SNAPSHOT_INTERVAL_MS = 24L * 60 * 60 * 1000
	private const val PREFS = "db_guard"
	private const val KEY_NOTICE = "notice"
	private const val NOTICE_FRESH = "fresh"
	private const val NOTICE_NOTIFICATION_ID = 0x0DB0

	/** Call before the database is opened. Only a confirmed corruption triggers any change. */
	fun verifyAndRecover(context: Context, name: String = DB_NAME) {
		runCatching {
			val db = context.getDatabasePath(name)
			if (!db.exists() || !isCorrupt(db)) {
				return
			}
			val root = db.parentFile ?: return
			val quarantine = File(root, QUARANTINE_DIR).apply { mkdirs() }
			val kept = File(quarantine, "$name.${System.currentTimeMillis()}$CORRUPT_SUFFIX")
			if (!db.renameTo(kept)) {
				return
			}
			File(db.path + "-wal").renameTo(File(kept.path + "-wal"))
			File(db.path + "-shm").delete()
			File(db.path + "-journal").delete()
			prune(quarantine, CORRUPT_SUFFIX, MAX_QUARANTINED)
			val snapshot = snapshots(root, name).firstOrNull { !isCorrupt(it) }
			val notice = if (snapshot != null) {
				snapshot.copyTo(db, overwrite = true)
				snapshot.lastModified().toString()
			} else {
				NOTICE_FRESH
			}
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_NOTICE, notice) }
		}.onFailure { it.printStackTraceDebug() }
	}

	/** Takes a snapshot when the newest one is older than a day. Safe to call on every start. */
	fun snapshotIfDue(context: Context, database: MangaDatabase, name: String = DB_NAME) {
		runCatching {
			val dbFile = context.getDatabasePath(name)
			val root = dbFile.parentFile ?: return
			if (!dbFile.exists()) {
				return
			}
			val dir = File(root, SNAPSHOT_DIR).apply { mkdirs() }
			val now = System.currentTimeMillis()
			val newest = snapshots(root, name).firstOrNull()
			if (newest != null && now - newest.lastModified() < SNAPSHOT_INTERVAL_MS) {
				return
			}
			// Fold the write-ahead log into the main file so a plain copy is complete
			database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
			val tmp = File(dir, "$name.tmp")
			dbFile.copyTo(tmp, overwrite = true)
			if (isCorrupt(tmp)) {
				tmp.delete()
				return
			}
			val target = File(dir, "$name.$now$SNAPSHOT_SUFFIX")
			if (tmp.renameTo(target)) {
				target.setLastModified(now)
				prune(dir, SNAPSHOT_SUFFIX, MAX_SNAPSHOTS)
			} else {
				tmp.delete()
			}
		}.onFailure { it.printStackTraceDebug() }
	}

	/** Tells the user once if the database had to be restored or started over. */
	fun notifyIfRecovered(context: Context) {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val notice = prefs.getString(KEY_NOTICE, null) ?: return
		prefs.edit { remove(KEY_NOTICE) }
		val channel = BaseBackupRestoreService.CHANNEL_ID
		if (!context.checkNotificationPermission(channel)) {
			return
		}
		BaseBackupRestoreService.createNotificationChannel(context)
		val text = if (notice == NOTICE_FRESH) {
			context.getString(R.string.db_recovered_fresh)
		} else {
			val date = notice.toLongOrNull()?.let { DateFormat.getDateTimeInstance().format(Date(it)) }.orEmpty()
			context.getString(R.string.db_recovered_restored, date)
		}
		val notification = NotificationCompat.Builder(context, channel)
			.setSmallIcon(R.drawable.general_notification)
			.setContentTitle(context.getString(R.string.db_recovered_title))
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setAutoCancel(true)
			.setSilent(true)
			.build()
		runCatching { NotificationManagerCompat.from(context).notify(NOTICE_NOTIFICATION_ID, notification) }
	}

	/**
	 * True only when SQLite says the file is damaged. A locked file, a full disk or any other failure
	 * says nothing about the data, so those never count.
	 */
	private fun isCorrupt(file: File): Boolean = try {
		SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
			DatabaseUtils.stringForQuery(it, "PRAGMA quick_check(1)", null) != "ok"
		}
	} catch (_: SQLiteDatabaseCorruptException) {
		true
	} catch (e: Exception) {
		e.printStackTraceDebug()
		false
	}

	private fun snapshots(root: File, name: String): List<File> = File(root, SNAPSHOT_DIR)
		.listFiles { f -> f.isFile && f.name.startsWith("$name.") && f.name.endsWith(SNAPSHOT_SUFFIX) }
		.orEmpty()
		.sortedByDescending { it.lastModified() }

	private fun prune(dir: File, suffix: String, keep: Int) {
		dir.listFiles { f -> f.isFile && f.name.endsWith(suffix) }
			.orEmpty()
			.sortedByDescending { it.lastModified() }
			.drop(keep)
			.forEach { old ->
				old.delete()
				File(old.path + "-wal").delete()
				File(old.path + "-shm").delete()
			}
	}
}
