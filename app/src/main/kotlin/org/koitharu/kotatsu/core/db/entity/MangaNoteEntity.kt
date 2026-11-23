package org.koitharu.kotatsu.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.koitharu.kotatsu.core.db.TABLE_MANGA_NOTES

/** A personal note on a title. Kept apart from the preferences row, which is dropped when it has no overrides. */
@Entity(tableName = TABLE_MANGA_NOTES)
data class MangaNoteEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "note") val note: String,
	@ColumnInfo(name = "updated_at") val updatedAt: Long,
)
