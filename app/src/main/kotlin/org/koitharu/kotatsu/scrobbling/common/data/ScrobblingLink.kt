package org.koitharu.kotatsu.scrobbling.common.data

/** A bare tracker link: which remote entry a manga is attached to. */
class ScrobblingLink(
	val mangaId: Long,
	val scrobbler: Int,
	val targetId: Long,
)
