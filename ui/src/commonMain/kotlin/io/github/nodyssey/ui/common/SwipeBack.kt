package io.github.nodyssey.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalPointerSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * 返回手势 — a horizontal swipe in the lower half of the screen that navigates back.
 *
 * **Why the platform's own edge gesture is not the one used.** Compose Multiplatform already installs
 * a pair of `UIScreenEdgePanGestureRecognizer`s, and the obvious way to offer both directions is to
 * enable the far one through `ComposeUIViewControllerConfiguration.endEdgePanGestureBehavior`. That
 * was the first attempt here and it is the wrong tool for this requirement: a
 * `UIScreenEdgePanGestureRecognizer` can only begin inside the system's edge zone — roughly the outer
 * 20pt, and not configurable — so a thumb resting in the middle of the screen can never reach it.
 * What was wanted is a gesture the thumb can make *where the thumb already is*, which no edge
 * recognizer can express at any setting. The platform's own edge gesture is left alone and keeps
 * working alongside this one.
 *
 * **Why this does not reimplement the animation.** It does not animate anything. It feeds
 * [DirectNavigationEventInput] — the public extension point `androidx.navigationevent` provides for
 * exactly this — and Nav3's `NavDisplay` already turns back progress into a seekable transition: it
 * reads `progress` off the latest event and calls `SeekableTransitionState.seekTo` on every update,
 * which is what makes the pages track the finger. The transition spec stays Nav3's own
 * `defaultPredictivePopTransitionSpec`, so on iOS this is the native push-and-pull bezier rather than
 * a hand-written slide. Hand-rolling the animation would be a second, worse copy of it.
 *
 * **Why a modifier and not an overlay.** A sibling strip drawn over the lower half would win the hit
 * test and swallow every vertical drag inside it, so the feed could not be scrolled from the bottom
 * half of the screen at all. Attached to the navigation container, this sits *below* the screens in
 * hit-test order instead: [horizontalDrag] reports cancellation the moment a child consumes the
 * change, and the slop measurement counts only horizontal travel, so scrollers, pagers and anything
 * else that claims the gesture keep it, and only an unclaimed sideways move in the lower half
 * becomes a back swipe.
 *
 * @param direction which swipes navigate back. [SwipeBackDirection.NONE] attaches nothing.
 */
@Composable
fun Modifier.swipeBackToNavigate(direction: SwipeBackDirection): Modifier {
    if (direction == SwipeBackDirection.NONE) return this
    // No dispatcher means no Navigable host above us — a desktop preview or a bare screen under
    // test. Drawing no gesture is right; dispatching would throw.
    val owner = LocalNavigationEventDispatcherOwner.current ?: return this

    // Read inside the gesture rather than captured, so a change to the setting is honoured by the
    // next swipe rather than by the next launch.
    val current by rememberUpdatedState(direction)

    // One input for the lifetime of this container. `addInput` is what makes the dispatcher route
    // what this input sends; dispatching from an unregistered input throws.
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(owner, input) {
        val dispatcher = owner.navigationEventDispatcher
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }

    return this.pointerInput(input) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val swipe = awaitSwipeBack(down) { current } ?: return@awaitEachGesture
            driveSwipeBack(input, swipe)
        }
    }
}

/** A gesture that has crossed touch slop sideways and been claimed as a back swipe. */
private class SwipeBack(val id: PointerId, val from: Offset, val toTheLeft: Boolean)

/**
 * Waits for a finger to commit to a sideways drag, or gives up.
 *
 * Null means the gesture was never ours: it began in the upper half, the setting does not allow that
 * direction, or a child took it first — in which case nothing was consumed here and whoever claimed
 * it is unaffected.
 */
private suspend fun AwaitPointerEventScope.awaitSwipeBack(
    down: PointerInputChange,
    direction: () -> SwipeBackDirection,
): SwipeBack? {
    // The lower half only — the part a thumb reaches without moving the hand, and the reason this
    // exists instead of the platform's edge recognizer.
    if (down.position.y < size.height * (1f - SWIPE_BACK_ZONE_FRACTION)) return null

    var claimed: SwipeBack? = null
    awaitHorizontalPointerSlopOrCancellation(down.id, down.type) { change, overSlop ->
        val toTheLeft = overSlop < 0f
        val allowed =
            if (toTheLeft) direction().allowsLeft else direction().allowsRight
        // Consuming is what ends slop detection for this pointer. Not consuming leaves the gesture
        // to anyone else who wants it, which is exactly what a disabled direction should do.
        if (allowed) {
            change.consume()
            claimed = SwipeBack(change.id, down.position, toTheLeft)
        }
    }
    return claimed
}

/** Runs one claimed swipe to a completed or a cancelled navigation. */
private suspend fun AwaitPointerEventScope.driveSwipeBack(
    input: DirectNavigationEventInput,
    swipe: SwipeBack,
) {
    // Which way the incoming page travels, in Nav3's vocabulary: a rightward swipe uncovers the page
    // from the left edge.
    val edge = if (swipe.toTheLeft) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
    val width = size.width.toFloat()

    input.backStarted(NavigationEvent(swipeEdge = edge, touchX = swipe.from.x, touchY = swipe.from.y))

    val tracker = VelocityTracker()
    var furthest = 0f
    val completed =
        horizontalDrag(swipe.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            furthest = distanceTravelled(swipe, change.position.x)
            input.backProgressed(
                NavigationEvent(
                    swipeEdge = edge,
                    progress = (furthest / width).coerceIn(0f, 1f),
                    touchX = change.position.x,
                    touchY = change.position.y,
                    frameTimeMillis = change.uptimeMillis,
                ),
            )
            change.consume()
        }

    // Commit or retreat. Distance leads because it is what the user can see — the page is already
    // part way across — and velocity only settles the fast flick that stops short, the way the
    // platform's own gesture decides it.
    val speed = tracker.calculateVelocity().x.let { if (swipe.toTheLeft) -it else it }
    val pastCommitPoint = furthest > width * SWIPE_BACK_COMMIT_FRACTION
    if (completed && (pastCommitPoint || speed > SWIPE_BACK_COMMIT_VELOCITY)) {
        input.backCompleted()
    } else {
        input.backCancelled()
    }
}

/** How far the finger has come in the direction the swipe is going, from where it went down. */
private fun distanceTravelled(swipe: SwipeBack, x: Float): Float =
    if (swipe.toTheLeft) swipe.from.x - x else x - swipe.from.x

/** The share of the screen's height, measured from the bottom, in which the swipe works. */
private const val SWIPE_BACK_ZONE_FRACTION = 0.5f

/** Fraction of the width a drag must reach to navigate back on release. */
private const val SWIPE_BACK_COMMIT_FRACTION = 0.3f

/** px/s the drag must reach to commit early, matching the platform's own flick threshold. */
private const val SWIPE_BACK_COMMIT_VELOCITY = 100f

/** Which horizontal swipes navigate back. */
enum class SwipeBackDirection(
    /** Whether a swipe to the right returns — the platform's own direction, and the narrower one. */
    val allowsRight: Boolean,
    /** Whether a swipe to the left also returns. */
    val allowsLeft: Boolean,
) {
    /** Right only. */
    RIGHT(allowsRight = true, allowsLeft = false),

    /** Either direction. The default. */
    BOTH(allowsRight = true, allowsLeft = true),

    /** No gesture; the caller attaches nothing. */
    NONE(allowsRight = false, allowsLeft = false),
}
