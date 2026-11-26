package org.koitharu.kotatsu.scrobbling.common.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import java.util.Collections

/** A status change that smart reading status wants to make, waiting for the user to confirm it. */
class SmartStatusPrompt(
	val mangaId: Long,
	val title: String,
	val service: ScrobblerService,
	val status: ScrobblingStatus,
	val apply: suspend () -> Unit,
)

/**
 * Carries [SmartStatusPrompt]s from the background scrobbling to the reader, which asks the user.
 * A change the user declines is not offered again until the app restarts.
 */
object SmartStatusPrompts {

	private val declined = Collections.synchronizedSet(HashSet<String>())
	private val _prompts = MutableSharedFlow<SmartStatusPrompt>(
		extraBufferCapacity = 8,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)

	val prompts: SharedFlow<SmartStatusPrompt> = _prompts.asSharedFlow()

	fun isDeclined(prompt: SmartStatusPrompt) = key(prompt) in declined

	fun decline(prompt: SmartStatusPrompt) {
		declined.add(key(prompt))
	}

	fun offer(prompt: SmartStatusPrompt) {
		if (!isDeclined(prompt)) _prompts.tryEmit(prompt)
	}

	private fun key(p: SmartStatusPrompt) = "${p.mangaId}:${p.service.id}:${p.status}"
}
