package org.koitharu.kotatsu.details.notes

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaNoteEntity
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.require
import javax.inject.Inject

@HiltViewModel
class MangaNotesViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val db: MangaDatabase,
	private val mangaDataRepository: MangaDataRepository,
) : BaseViewModel() {

	val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	/** null until the saved note is read, so the sheet does not flash the empty state. */
	val note = MutableStateFlow<String?>(null)

	/** The numbered chapters of the title, for `@ch12` mentions and their suggestions. */
	val chapters = MutableStateFlow(NoteChapters.EMPTY)

	init {
		launchLoadingJob(Dispatchers.Default) {
			note.value = db.getMangaNotesDao().find(manga.id)?.note.orEmpty()
		}
		launchJob(Dispatchers.Default) {
			val list = mangaDataRepository.findMangaById(manga.id, withChapters = true)?.chapters.orEmpty()
			chapters.value = NoteChapters(list.map { NoteChapter(it.id, it.number, it.title) })
		}
	}

	fun save(text: String) {
		val trimmed = text.trim()
		note.value = trimmed
		launchJob(Dispatchers.Default) {
			if (trimmed.isEmpty()) {
				db.getMangaNotesDao().delete(manga.id)
			} else {
				db.getMangaNotesDao().upsert(MangaNoteEntity(manga.id, trimmed, System.currentTimeMillis()))
			}
		}
	}
}
