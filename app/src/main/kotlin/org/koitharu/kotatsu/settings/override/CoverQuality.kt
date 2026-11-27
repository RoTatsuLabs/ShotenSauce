package org.koitharu.kotatsu.settings.override

private val SIZE_SWAPS = listOf(
	// AniList: .../manga/cover/extraLarge/bx1.jpg
	Regex("/cover/extraLarge/") to "/cover/large/",
	// Kitsu: .../poster_images/1/original.jpg
	Regex("/poster_images/(\\d+)/original\\.") to "/poster_images/$1/large.",
	// Shikimori: .../system/mangas/original/1.jpg
	Regex("/(mangas|ranobe)/original/") to "/$1/preview/",
)

/**
 * The smaller size of a tracker cover, for when the sharpest one is not worth the download. Trackers
 * that do not offer a smaller size at a predictable address keep their URL.
 */
fun String.asStandardCover(): String =
	SIZE_SWAPS.fold(this) { url, (pattern, replacement) -> pattern.replaceFirst(url, replacement) }
