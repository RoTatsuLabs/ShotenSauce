package org.koitharu.kotatsu.scrobbling.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.COMPLETED
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.DROPPED
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.ON_HOLD
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.PLANNED
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.READING
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus.RE_READING

class SmartStatusTest {

	private fun next(
		current: org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus?,
		chapter: Int,
		previous: Int = 0,
		total: Int = 0,
		lastLocal: Int = 100,
		localFinished: Boolean = false,
		localOngoing: Boolean = false,
		rules: SmartRules = SmartRules(),
	) = SmartStatus.next(current, chapter, previous, total, lastLocal, localFinished, localOngoing, rules)

	private val off = SmartRules(startReading = false, resumePaused = false, resumeDropped = false, reRead = false, complete = SmartCompletion.OFF)

	@Test
	fun plannedBecomesReading() {
		assertEquals(READING, next(PLANNED, chapter = 1))
		assertNull(next(PLANNED, chapter = 1, rules = off))
	}

	@Test
	fun pausedResumesAndDroppedStaysByDefault() {
		assertEquals(READING, next(ON_HOLD, chapter = 6, previous = 5))
		assertNull(next(DROPPED, chapter = 6, previous = 5))
		assertEquals(READING, next(DROPPED, chapter = 6, previous = 5, rules = SmartRules(resumeDropped = true)))
		assertNull(next(ON_HOLD, chapter = 6, previous = 5, rules = off))
		assertNull(next(ON_HOLD, chapter = 5, previous = 5))
	}

	@Test
	fun lastChapterCompletes() {
		assertEquals(COMPLETED, next(READING, chapter = 50, previous = 49, total = 50))
		assertEquals(COMPLETED, next(RE_READING, chapter = 50, previous = 49, total = 50))
		assertEquals(COMPLETED, next(PLANNED, chapter = 1, total = 1))
		assertNull(next(READING, chapter = 49, previous = 48, total = 50))
		assertNull(next(READING, chapter = 50, previous = 49, total = 50, rules = off))
	}

	@Test
	fun sourceOnlyIgnoresTrackerTotal() {
		val source = SmartRules(complete = SmartCompletion.SOURCE_ONLY)
		assertNull(next(READING, chapter = 50, previous = 49, total = 50, rules = source))
		assertEquals(COMPLETED, next(READING, chapter = 80, lastLocal = 80, localFinished = true, rules = source))
	}

	@Test
	fun unknownTotalNeedsFinishedSource() {
		assertEquals(COMPLETED, next(READING, chapter = 80, total = 0, lastLocal = 80, localFinished = true))
		assertNull(next(READING, chapter = 80, total = 0, lastLocal = 80, localFinished = false))
		assertNull(next(READING, chapter = 79, total = 0, lastLocal = 80, localFinished = true))
	}

	@Test
	fun startingAFinishedTitleOverIsReReading() {
		assertEquals(RE_READING, next(COMPLETED, chapter = 1, previous = 50, total = 50))
		assertNull(next(COMPLETED, chapter = 1, previous = 50, total = 50, rules = off))
		assertNull(next(COMPLETED, chapter = 20, previous = 50, total = 50))
		assertNull(next(COMPLETED, chapter = 1, previous = 1, total = 1))
	}

	@Test
	fun finishedTitlesAreLeftAlone() {
		assertNull(next(COMPLETED, chapter = 50, previous = 50, total = 50))
		assertNull(next(null, chapter = 3))
	}

	@Test
	fun ongoingTitleIsNeverCompleted() {
		// The tracker lists only the chapters released so far, and all of them have been read.
		assertNull(next(READING, chapter = 28, previous = 27, total = 28, localOngoing = true))
		assertNull(next(RE_READING, chapter = 28, previous = 27, lastLocal = 28, localOngoing = true))
		assertEquals(READING, next(PLANNED, chapter = 28, total = 28, localOngoing = true))
	}
}
