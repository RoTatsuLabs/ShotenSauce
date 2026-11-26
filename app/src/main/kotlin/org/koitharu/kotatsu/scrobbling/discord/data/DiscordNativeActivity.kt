package org.koitharu.kotatsu.scrobbling.discord.data

/**
 * Rich Presence payload for [DiscordRpcManager.setActivity]. The fields mirror what
 * discord_bridge.cpp forwards to discordpp::Activity. Timestamps are epoch seconds, 0 means unset.
 */
data class DiscordNativeActivity(
	val name: String,
	val details: String? = null,
	val state: String? = null,
	val startTimestamp: Long = 0L,
	val endTimestamp: Long = 0L,
	val largeImage: String? = null,
	val largeText: String? = null,
	val smallImage: String? = null,
	val smallText: String? = null,
	val button1Label: String? = null,
	val button1Url: String? = null,
	val button2Label: String? = null,
	val button2Url: String? = null,
	val activityType: Int = TYPE_WATCHING,
) {

	companion object {

		/** Value of discordpp::ActivityTypes::Watching. */
		const val TYPE_WATCHING = 3
	}
}
