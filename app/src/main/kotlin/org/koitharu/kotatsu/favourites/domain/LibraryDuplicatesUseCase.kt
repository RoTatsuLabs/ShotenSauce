package org.koitharu.kotatsu.favourites.domain

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.favourites.data.FavouriteManga
import org.koitharu.kotatsu.favourites.data.toManga
import org.koitharu.kotatsu.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.koitharu.kotatsu.local.data.isEpub
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

/** One copy of a title that exists more than once among the favourites. */
class DuplicateCopy(
	val manga: Manga,
	val chaptersCount: Int,
	val categories: List<String>,
	val progress: Float,
)

/** Favourites that are the same series, [copies] ordered by how many chapters each has. */
class DuplicateGroup(
	val title: String,
	val copies: List<DuplicateCopy>,
)

/**
 * Scans the whole favourites list for titles that appear more than once. Unlike [DuplicatesUseCase],
 * which compares one incoming manga loosely, this is a batch pass, so it only joins two favourites
 * when a title (or alternative title) is identical after stripping everything but letters and
 * digits, or when both are linked to the same remote entry on the same tracker. Novels and comics
 * are never merged into one group.
 */
@Reusable
class LibraryDuplicatesUseCase @Inject constructor(
	private val db: MangaDatabase,
) {

	suspend operator fun invoke(): List<DuplicateGroup> {
		val rows = db.getFavouritesDao().findAllRaw(0, Int.MAX_VALUE).groupBy { it.manga.id }
		if (rows.size < 2) {
			return emptyList()
		}
		val mangaById = HashMap<Long, Manga>(rows.size)
		for ((id, list) in rows) {
			mangaById[id] = list.first().toManga()
		}
		val sets = UnionFind(rows.keys)
		val byTitle = HashMap<String, Long>()
		for ((id, manga) in mangaById) {
			for (title in manga.allTitles()) {
				val normalized = title.normalizeTitle()
				if (normalized.length < MIN_TITLE_LENGTH) continue
				val key = "${manga.isEpub}:$normalized"
				val other = byTitle.putIfAbsent(key, id)
				if (other != null) sets.union(id, other)
			}
		}
		val byLink = HashMap<Pair<Int, Long>, Long>()
		for (link in db.getScrobblingDao().findAllLinks()) {
			val manga = mangaById[link.mangaId] ?: continue
			val other = byLink.putIfAbsent(link.scrobbler to link.targetId, link.mangaId) ?: continue
			if (mangaById.getValue(other).isEpub == manga.isEpub) {
				sets.union(link.mangaId, other)
			}
		}
		val chaptersDao = db.getChaptersDao()
		val historyDao = db.getHistoryDao()
		return rows.keys.groupBy { sets.find(it) }.values
			.filter { it.size > 1 }
			.map { ids ->
				val copies = ids.map { id ->
					DuplicateCopy(
						manga = mangaById.getValue(id),
						chaptersCount = chaptersDao.count(id),
						categories = rows.getValue(id).mapNotNull { row ->
							row.categories.singleOrNull()?.takeIf { it.deletedAt == 0L }?.title
						}.distinct(),
						progress = historyDao.find(id)?.percent ?: PROGRESS_NONE,
					)
				}.sortedByDescending { it.chaptersCount }
				DuplicateGroup(title = copies.first().manga.title, copies = copies)
			}
			.sortedBy { it.title.lowercase() }
	}

	private fun Manga.allTitles(): List<String> = buildList(altTitles.size + 1) {
		add(title)
		addAll(altTitles)
	}.filter { it.isNotBlank() }

	private fun String.normalizeTitle() = buildString(length) {
		for (c in this@normalizeTitle) {
			if (c.isLetterOrDigit()) append(c.lowercaseChar())
		}
	}

	private class UnionFind(ids: Collection<Long>) {

		private val parent = HashMap<Long, Long>().also { map -> ids.forEach { map[it] = it } }

		fun find(id: Long): Long {
			var root = id
			while (parent.getValue(root) != root) root = parent.getValue(root)
			var node = id
			while (parent.getValue(node) != root) {
				val next = parent.getValue(node)
				parent[node] = root
				node = next
			}
			return root
		}

		fun union(a: Long, b: Long) {
			val ra = find(a)
			val rb = find(b)
			if (ra != rb) parent[ra] = rb
		}
	}

	private companion object {

		/** Shorter titles are too generic to call two entries the same series. */
		const val MIN_TITLE_LENGTH = 4
	}
}
