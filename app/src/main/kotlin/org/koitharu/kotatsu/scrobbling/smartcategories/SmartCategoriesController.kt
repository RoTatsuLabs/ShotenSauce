package org.koitharu.kotatsu.scrobbling.smartcategories

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.favourites.data.toMangaList
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

/**
 * Keeps favourites filed by tracker status: when the status of a favourite changes on a tracker, it
 * moves to the category the user bound to that status. Categories without a binding are never touched.
 */
@Singleton
class SmartCategoriesController @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val favourites: FavouritesRepository,
	private val scrobblers: Provider<Set<@JvmSuppressWildcards Scrobbler>>,
) {

	@OptIn(ExperimentalCoroutinesApi::class)
	fun start(scope: CoroutineScope) {
		scope.launch(Dispatchers.Default) {
			settings.observeAsFlow(AppSettings.KEY_SMART_CATEGORIES) { isSmartCategories }
				.flatMapLatest { enabled -> if (enabled) watch() else emptyFlow<Unit>() }
				.collect {}
		}
	}

	private fun watch() = scrobblers.get().map { scrobbler ->
		// The first emission is the state as it was; only what differs from it afterwards is a change
		var known: Map<Long, String?>? = null
		db.getScrobblingDao().observe(scrobbler.scrobblerService.id).map { list ->
			val previous = known
			known = list.associate { it.mangaId to it.status }
			if (previous != null) {
				for (entity in list) {
					if (previous[entity.mangaId] != entity.status) {
						onStatusChanged(scrobbler, entity)
					}
				}
			}
		}
	}.merge()

	private suspend fun onStatusChanged(scrobbler: Scrobbler, entity: ScrobblingEntity) {
		val status = scrobbler.statusOf(entity.status) ?: return
		runCatchingCancellable { file(entity.mangaId, status) }.onFailure { it.printStackTraceDebug() }
	}

	private suspend fun file(mangaId: Long, status: ScrobblingStatus) {
		val target = settings.getSmartCategory(status).takeIf { it != 0L } ?: return
		val existing = favourites.observeCategories().first().mapTo(HashSet()) { it.id }
		if (target !in existing) return
		val favourite = db.getFavouritesDao().findByIds(listOf(mangaId)).toMangaList().firstOrNull() ?: return
		val others = ScrobblingStatus.entries
			.map { settings.getSmartCategory(it) }
			.filter { it != 0L && it != target && it in existing }
			.distinct()
		db.withTransaction {
			for (category in others) {
				favourites.removeFromCategory(category, listOf(mangaId))
			}
			favourites.addToCategory(target, listOf(favourite))
		}
	}
}
