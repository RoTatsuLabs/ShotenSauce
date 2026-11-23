package org.koitharu.kotatsu.bookmarks.domain

/** The fixed palette a bookmark can be labelled with. A bookmark stores the ARGB value itself. */
object BookmarkColors {

	val ALL: List<Int> = listOf(
		0xFFF2716B.toInt(),
		0xFFF5A25D.toInt(),
		0xFFEFD35F.toInt(),
		0xFF7CC98A.toInt(),
		0xFF6EB3F2.toInt(),
		0xFFB58CF0.toInt(),
	)
}
