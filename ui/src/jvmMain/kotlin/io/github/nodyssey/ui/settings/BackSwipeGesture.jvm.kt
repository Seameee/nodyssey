package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Null — the desktop window is a design-system gallery rather than a shipped shell, and it has no
 * edge-swipe navigation of its own for this to configure. See `:gallery`.
 */
@Composable
actual fun rememberBackSwipeGestureAvailable(): Boolean? = null

/** Unread where there is no row; false is the answer that keeps a caller from drawing the hint. */
actual val backSwipeGestureAppliesOnRestart: Boolean = false
