package org.koitharu.kotatsu.main.ui

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Turns an ordinary sideways swipe into a step to the neighbouring main tab, but only where the
 * swipe has nowhere else to go: a pager, carousel or chip row under the finger that can still
 * scroll that way keeps the gesture. Watches the touches without consuming them.
 *
 * While the finger is down [onDrag] follows it; when it lifts, [onFinish] says whether the swipe
 * was enough to change tab (a quick flick, or a drag across a good part of the screen).
 */
class TabEdgeSwipe(
	context: Context,
	private val onDrag: (dx: Float, step: Int) -> Unit,
	private val onFinish: (step: Int, commit: Boolean) -> Unit,
) {

	private val config = ViewConfiguration.get(context)
	private val longDistance = context.resources.displayMetrics.widthPixels * 0.3f
	private val minVelocity = config.scaledMinimumFlingVelocity * 4f
	private val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
	private var startX = 0f
	private var startY = 0f
	private var blockNext = false
	private var blockPrevious = false
	private var active = false
	private var step = 0
	private var velocity: VelocityTracker? = null

	fun onTouch(root: View, e: MotionEvent, isEnabled: Boolean) {
		when (e.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				release(commit = false)
				startX = e.rawX
				startY = e.rawY
				active = isEnabled
				if (active) {
					velocity = VelocityTracker.obtain().also { it.addMovement(e) }
					// Content that can still move towards the end blocks a swipe towards the next tab
					val chain = viewsAt(root, e.rawX.toInt(), e.rawY.toInt())
					blockNext = chain.any { it.canScrollHorizontally(1) }
					blockPrevious = chain.any { it.canScrollHorizontally(-1) }
					// A pager keeps the gesture until it sits on its first or last page, as in Telegram
					chain.filterIsInstance<ViewPager2>().forEach { pager ->
						val last = (pager.adapter?.itemCount ?: 0) - 1
						val hasNext = if (rtl) pager.currentItem > 0 else pager.currentItem < last
						val hasPrevious = if (rtl) pager.currentItem < last else pager.currentItem > 0
						blockNext = blockNext || hasNext
						blockPrevious = blockPrevious || hasPrevious
					}
				}
			}

			MotionEvent.ACTION_MOVE -> if (active) {
				velocity?.addMovement(e)
				val dx = e.rawX - startX
				val dy = e.rawY - startY
				if (step == 0) {
					if (abs(dx) < config.scaledTouchSlop || abs(dx) < abs(dy) * 1.5f) return
					// A finger moving towards the start of the line asks for what comes after
					val forward = (dx < 0) != rtl
					if (if (forward) blockNext else blockPrevious) {
						active = false
						return
					}
					step = if (forward) 1 else -1
				}
				// Only the way of the swipe follows the finger; going back past the start does nothing
				val towards = if ((dx < 0) == (step > 0) != rtl) abs(dx) else 0f
				onDrag(if (dx < 0) -towards else towards, step)
			}

			MotionEvent.ACTION_UP -> release(commit = true, e = e)
			MotionEvent.ACTION_CANCEL -> release(commit = false)
		}
	}

	private fun release(commit: Boolean, e: MotionEvent? = null) {
		val finishedStep = step
		var enough = false
		if (commit && finishedStep != 0 && e != null) {
			velocity?.addMovement(e)
			velocity?.computeCurrentVelocity(1000)
			val dx = e.rawX - startX
			val vx = velocity?.xVelocity ?: 0f
			val sameWay = (dx < 0) == (vx < 0)
			enough = (abs(vx) > minVelocity && sameWay) || abs(dx) > longDistance
		}
		velocity?.recycle()
		velocity = null
		active = false
		step = 0
		if (finishedStep != 0) onFinish(finishedStep, enough)
	}

	/**
	 * Every visible view under the point, not just the topmost branch: a transparent overlay drawn
	 * over the list must not hide the pager below it.
	 */
	private fun viewsAt(root: View, x: Int, y: Int): List<View> {
		val result = ArrayList<View>()
		val rect = Rect()
		fun visit(view: View) {
			result += view
			val group = view as? ViewGroup ?: return
			for (i in 0 until group.childCount) {
				val child = group.getChildAt(i)
				if (child.visibility == View.VISIBLE && child.getGlobalVisibleRect(rect) && rect.contains(x, y)) {
					visit(child)
				}
			}
		}
		visit(root)
		return result
	}
}
