package org.koitharu.kotatsu.favourites.ui.duplicates.library

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.util.ReversibleAction
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.favourites.domain.DuplicateCopy
import org.koitharu.kotatsu.favourites.domain.DuplicateGroup
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.domain.LibraryDuplicatesUseCase
import javax.inject.Inject

@HiltViewModel
class LibraryDuplicatesViewModel @Inject constructor(
	db: MangaDatabase,
	private val findDuplicates: LibraryDuplicatesUseCase,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val onActionDone = MutableEventFlow<ReversibleAction>()

	/** Null while the first scan runs. Rescans on its own whenever the favourites change, undo included. */
	val groups: StateFlow<List<DuplicateGroup>?> = db.getFavouritesDao().observeMangaIds()
		.map { findDuplicates() }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	/** Drops every other copy of the title from favourites; history and downloads are left alone. */
	fun keep(group: DuplicateGroup, copy: DuplicateCopy) {
		launchJob(Dispatchers.Default) {
			val ids = group.copies.filter { it !== copy }.map { it.manga.id }
			val handle = favouritesRepository.removeFromFavourites(ids)
			onActionDone.call(ReversibleAction(R.string.duplicates_removed, handle))
		}
	}
}
