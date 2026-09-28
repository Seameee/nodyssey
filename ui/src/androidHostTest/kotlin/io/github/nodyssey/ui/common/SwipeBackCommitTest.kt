package io.github.nodyssey.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What letting go of a swipe-back does.
 *
 * The two thresholds are the whole of the gesture's feel, and the first version of them was wrong in
 * a way a reader notices immediately: dragging to about the middle and letting go navigated back
 * instead of springing back, because the distance threshold was a third of the width and the
 * velocity threshold was the platform's 100 px/s — a speed a finger that has merely not stopped yet
 * clears easily. So the two `springs back` cases below are the bug, and the two that navigate back
 * are the guard that the correction did not go too far and stop the gesture working at all.
 */
class SwipeBackCommitTest {
    @Test
    fun `letting go at the middle of the screen springs back`() {
        assertFalse(swipeBackCommits(travelledFraction = 0.5f, velocity = 0f))
    }

    @Test
    fun `letting go short of the middle springs back`() {
        assertFalse(swipeBackCommits(travelledFraction = 0.42f, velocity = 0f))
    }

    @Test
    fun `a slow drag's leftover speed is not a flick, so it springs back`() {
        // 300 px/s is a hand still drifting, not a flick. It cleared the old 100 threshold, which is
        // what made a short, unhurried drag navigate back on speed alone.
        assertFalse(swipeBackCommits(travelledFraction = 0.2f, velocity = 300f))
    }

    @Test
    fun `dragging past the middle navigates back`() {
        assertTrue(swipeBackCommits(travelledFraction = 0.62f, velocity = 0f))
    }

    @Test
    fun `a flick navigates back however short the drag`() {
        assertTrue(swipeBackCommits(travelledFraction = 0.15f, velocity = 2_400f))
    }

    @Test
    fun `a flick back the other way does not navigate back`() {
        assertFalse(swipeBackCommits(travelledFraction = 0.2f, velocity = -2_400f))
    }
}
