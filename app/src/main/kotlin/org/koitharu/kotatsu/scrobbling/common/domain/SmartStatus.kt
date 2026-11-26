package org.koitharu.kotatsu.scrobbling.common.domain

import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus

/** How "last chapter reached" decides that a title is finished. */
enum class SmartCompletion {
	/** Never marks a title Completed. */
	OFF,

	/** Uses the tracker's chapter total, or the source's own end when the tracker has no total. */
	TRACKER_OR_SOURCE,

	/** Only when the source says the title is finished and its last chapter is read. */
	SOURCE_ONLY,
}

/** Which status changes reading may make on its own. */
data class SmartRules(
	/** Planned becomes Reading. */
	val startReading: Boolean = true,
	/** On hold becomes Reading. */
	val resumePaused: Boolean = true,
	/** Dropped becomes Reading. */
	val resumeDropped: Boolean = false,
	/** Completed becomes Re-reading when the title is started again from chapter 1. */
	val reRead: Boolean = true,
	val complete: SmartCompletion = SmartCompletion.TRACKER_OR_SOURCE,
	/** Ask the user in the reader before a status is changed. */
	val askFirst: Boolean = false,
)

/**
 * Decides how a tracker's reading status follows the reading, according to [SmartRules]. Only moves
 * the status forward from what reading implies and never touches a title the user has finished or is
 * not reading, except to start a finished title over from its first chapter.
 */
internal object SmartStatus {

	/**
	 * The status to switch to after reading up to [chapter], or null to leave it as it is.
	 *
	 * @param current the status on the tracker now
	 * @param previous the chapter the tracker had before this one
	 * @param total chapters the tracker knows the title has, or 0 when it does not say
	 * @param lastLocal the last chapter number the source offers
	 * @param localFinished whether the source says the title is finished
	 * @param localOngoing whether the source says the title is still running (ongoing, paused or upcoming);
	 * such a title is never marked Completed, however many chapters the tracker lists
	 */
	fun next(
		current: ScrobblingStatus?,
		chapter: Int,
		previous: Int,
		total: Int,
		lastLocal: Int,
		localFinished: Boolean,
		localOngoing: Boolean,
		rules: SmartRules,
	): ScrobblingStatus? {
		if (chapter <= 0) return null
		var status = current
		if (status == ScrobblingStatus.PLANNED && rules.startReading) status = ScrobblingStatus.READING
		if (status == ScrobblingStatus.ON_HOLD && rules.resumePaused && chapter > previous) {
			status = ScrobblingStatus.READING
		}
		if (status == ScrobblingStatus.DROPPED && rules.resumeDropped && chapter > previous) {
			status = ScrobblingStatus.READING
		}
		// Only chapter 1 counts, so opening a middle chapter of a completed title to look something up
		// never changes its status.
		if (status == ScrobblingStatus.COMPLETED && rules.reRead && chapter == 1 && previous > 1) {
			status = ScrobblingStatus.RE_READING
		}
		if (status == ScrobblingStatus.READING || status == ScrobblingStatus.RE_READING) {
			val sourceEnded = localFinished && lastLocal > 0 && chapter >= lastLocal
			val reachedEnd = when {
				rules.complete == SmartCompletion.OFF -> false
				// Trackers often list only the chapters released so far, so a title the source still
				// calls ongoing is never finished just because everything out has been read.
				localOngoing -> false
				rules.complete == SmartCompletion.SOURCE_ONLY -> sourceEnded
				total > 0 -> chapter >= total
				else -> sourceEnded
			}
			if (reachedEnd) status = ScrobblingStatus.COMPLETED
		}
		return status.takeIf { it != current }
	}
}
