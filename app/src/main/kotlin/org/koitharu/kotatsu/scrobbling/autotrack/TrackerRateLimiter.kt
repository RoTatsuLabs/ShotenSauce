package org.koitharu.kotatsu.scrobbling.autotrack

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import java.io.IOException
import java.io.InterruptedIOException
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Thrown when the tracker asked the app to slow down, or when the app is holding back to keep budget for the user. */
class TrackerRateLimitedException(val retryAfterSeconds: Long) :
	IOException("Tracker rate limit reached, retry in ${retryAfterSeconds}s")

/**
 * Throttles requests to tracker APIs. Every request passes through [beforeRequest] and
 * [onResponse] (see [TrackerRateLimitInterceptor]), so the limiter knows what has been spent.
 *
 * - Requests are spaced out, since trackers run burst limiters on top of the per-minute budget.
 * - The server's `X-RateLimit-*` headers are used where they exist (AniList). Otherwise requests
 *   sent in the last minute are counted against a conservative limit.
 * - A 429 blocks the tracker for the requested time (60 seconds if none is given). The block
 *   survives an app restart.
 *
 * Bulk work calls [awaitBackground] first. It stops short of the limit, so budget remains for
 * user-initiated requests, which are never queued behind a bulk job.
 */
@Singleton
class TrackerRateLimiter @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val states = EnumMap<ScrobblerService, State>(ScrobblerService::class.java)

	private class State(val defaultLimit: Int) {
		val sent = ArrayDeque<Long>()
		var serverLimit: Int? = null
		var serverRemaining: Int? = null
		var serverObservedAt = 0L
		var sentSinceObserved = 0
		var blockedUntilWall = 0L
		var lastSentAt = 0L

		val limit: Int
			get() = serverLimit ?: defaultLimit
	}

	/** Blocking; runs on an OkHttp thread. Throws when the tracker is blocked. */
	fun beforeRequest(service: ScrobblerService) {
		val gap = synchronized(this) {
			val state = stateOf(service)
			val wall = System.currentTimeMillis()
			if (state.blockedUntilWall > wall) {
				throw TrackerRateLimitedException(ceilSeconds(state.blockedUntilWall - wall))
			}
			val now = SystemClock.elapsedRealtime()
			val wait = (state.lastSentAt + MIN_GAP_MS - now).coerceAtLeast(0L)
			// Reserve the slot now, so parallel callers queue one after another instead of waking together.
			state.lastSentAt = now + wait
			state.sent.addLast(now + wait)
			state.sentSinceObserved++
			wait
		}
		if (gap > 0) {
			try {
				Thread.sleep(gap)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				throw InterruptedIOException().apply { initCause(e) }
			}
		}
	}

	fun isBlocked(service: ScrobblerService): Boolean = synchronized(this) {
		stateOf(service).blockedUntilWall > System.currentTimeMillis()
	}

	/** Records a response. Called for every response, successful or not. */
	fun onResponse(service: ScrobblerService, code: Int, limit: Int?, remaining: Int?, retryAfterSeconds: Long?) {
		synchronized(this) {
			val state = stateOf(service)
			if (limit != null && limit > 0) {
				state.serverLimit = limit
			}
			if (remaining != null) {
				state.serverRemaining = remaining
				state.serverObservedAt = SystemClock.elapsedRealtime()
				state.sentSinceObserved = 0
			}
			if (code == HTTP_TOO_MANY_REQUESTS) {
				val seconds = (retryAfterSeconds ?: DEFAULT_BLOCK_SECONDS).coerceIn(1L, MAX_BLOCK_SECONDS)
				state.blockedUntilWall = System.currentTimeMillis() + seconds * 1000L
				state.serverRemaining = 0
				state.serverObservedAt = SystemClock.elapsedRealtime()
				prefs.edit { putLong(blockKey(service), state.blockedUntilWall) }
			}
		}
	}

	/**
	 * Suspends until a bulk operation costing about [cost] requests fits into the share of the
	 * budget that background work may use.
	 *
	 * @throws TrackerRateLimitedException when waiting would take longer than [MAX_BACKGROUND_WAIT_MS]
	 * (for example after a long 429 timeout). The caller should hand the job back to WorkManager.
	 */
	suspend fun awaitBackground(service: ScrobblerService, cost: Int = DEFAULT_COST) {
		while (true) {
			val waitMs = synchronized(this) { backgroundWaitMs(stateOf(service), cost) }
			if (waitMs <= 0L) return
			if (waitMs > MAX_BACKGROUND_WAIT_MS) {
				throw TrackerRateLimitedException(ceilSeconds(waitMs))
			}
			delay(waitMs + JITTER_MS)
		}
	}

	private fun backgroundWaitMs(state: State, cost: Int): Long {
		val wall = System.currentTimeMillis()
		if (state.blockedUntilWall > wall) {
			return state.blockedUntilWall - wall
		}
		val now = SystemClock.elapsedRealtime()
		prune(state, now)
		val reserve = (state.limit * BACKGROUND_RESERVE).roundToInt()
		if (headroom(state, now) - cost >= reserve) {
			return 0L
		}
		// Wait for the oldest request of the window to age out, which frees one slot at a time.
		val oldest = state.sent.firstOrNull()
		return if (oldest != null) {
			(oldest + WINDOW_MS - now).coerceIn(MIN_WAIT_MS, WINDOW_MS)
		} else {
			MIN_WAIT_MS
		}
	}

	private fun headroom(state: State, now: Long): Int {
		val own = state.limit - state.sent.size
		val server = state.serverRemaining
			?.takeIf { now - state.serverObservedAt < WINDOW_MS }
			?.let { it - state.sentSinceObserved }
		return (if (server != null) minOf(own, server) else own).coerceAtLeast(0)
	}

	private fun prune(state: State, now: Long) {
		while (state.sent.isNotEmpty() && now - state.sent.first() >= WINDOW_MS) {
			state.sent.removeFirst()
		}
	}

	private fun stateOf(service: ScrobblerService): State = states.getOrPut(service) {
		State(defaultLimitOf(service)).apply {
			// Restore a block that outlived the process, so reopening the app does not hit the limit again.
			blockedUntilWall = prefs.getLong(blockKey(service), 0L)
		}
	}

	private fun defaultLimitOf(service: ScrobblerService): Int = when (service) {
		ScrobblerService.SHIKIMORI -> 60
		else -> 30
	}

	private fun blockKey(service: ScrobblerService) = "blocked_${service.id}"

	private fun ceilSeconds(ms: Long): Long = (ms + 999L) / 1000L

	private companion object {

		const val PREFS_NAME = "tracker_rate_limit"
		const val HTTP_TOO_MANY_REQUESTS = 429
		const val WINDOW_MS = 60_000L
		const val MIN_GAP_MS = 120L
		const val MIN_WAIT_MS = 1_000L
		const val JITTER_MS = 250L
		const val DEFAULT_BLOCK_SECONDS = 60L
		const val MAX_BLOCK_SECONDS = 3_600L
		const val MAX_BACKGROUND_WAIT_MS = 10 * 60_000L
		const val DEFAULT_COST = 3

		/** Share of the window background work must leave for the user. */
		const val BACKGROUND_RESERVE = 0.4
	}
}
