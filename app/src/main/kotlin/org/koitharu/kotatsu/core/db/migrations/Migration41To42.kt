package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the table for personal notes on titles. */
class Migration41To42 : Migration(41, 42) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS `manga_notes` (`manga_id` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
				"`updated_at` INTEGER NOT NULL, PRIMARY KEY(`manga_id`))",
		)
	}
}
