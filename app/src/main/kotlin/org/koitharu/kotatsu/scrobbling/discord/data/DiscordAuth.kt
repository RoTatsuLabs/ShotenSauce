package org.koitharu.kotatsu.scrobbling.discord.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

private const val EXPIRY_MARGIN_MS = 60_000L

/** Keeps the signed-in Discord account and hands out access tokens that are still valid. */
@Singleton
class DiscordAuth @Inject constructor(
	private val settings: AppSettings,
) {

	private val refreshMutex = Mutex()

	init {
		settings.clearLegacyDiscordToken()
	}

	val isSignedIn: Boolean
		get() = settings.discordAccessToken != null

	val userName: String?
		get() = settings.discordUserName

	fun signIn(tokens: DiscordTokens, user: DiscordUser) {
		storeTokens(tokens)
		settings.discordUserName = user.name
	}

	fun signOut() {
		settings.clearDiscordAccount()
		DiscordRpcManager.destroy()
	}

	/**
	 * Returns the stored access token, refreshing it first when it is about to expire. Returns null
	 * when nobody is signed in, when the refresh failed, or when Discord rejected the grant (the
	 * account is then signed out).
	 */
	suspend fun validAccessToken(): String? = refreshMutex.withLock {
		val token = settings.discordAccessToken ?: return null
		val expiresAt = settings.discordTokenExpiresAt
		if (expiresAt == 0L || System.currentTimeMillis() < expiresAt - EXPIRY_MARGIN_MS) {
			return token
		}
		val refreshToken = settings.discordRefreshToken
		if (refreshToken == null) {
			signOut()
			return null
		}
		when (val result = DiscordRpcManager.refreshTokens(refreshToken)) {
			is DiscordTokenResult.Success -> {
				storeTokens(result.tokens)
				result.tokens.accessToken
			}

			DiscordTokenResult.Rejected -> {
				signOut()
				null
			}

			DiscordTokenResult.Failed -> null
		}
	}

	private fun storeTokens(tokens: DiscordTokens) {
		settings.discordAccessToken = tokens.accessToken
		// Discord rotates refresh tokens; keep the old one when a response omits it.
		tokens.refreshToken?.let { settings.discordRefreshToken = it }
		settings.discordTokenExpiresAt = tokens.expiresAtMillis
	}
}
