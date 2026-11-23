package org.koitharu.kotatsu.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.koitharu.kotatsu.core.db.entity.MangaNoteEntity

@Dao
abstract class MangaNotesDao {

	@Query("SELECT * FROM manga_notes WHERE manga_id = :mangaId")
	abstract suspend fun find(mangaId: Long): MangaNoteEntity?

	@Query("SELECT * FROM manga_notes WHERE manga_id = :mangaId")
	abstract fun observe(mangaId: Long): Flow<MangaNoteEntity?>

	@Query("DELETE FROM manga_notes WHERE manga_id = :mangaId")
	abstract suspend fun delete(mangaId: Long)

	@Upsert
	abstract suspend fun upsert(entity: MangaNoteEntity)
}
