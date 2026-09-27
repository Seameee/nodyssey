package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Null — the row is hidden, because the gesture this setting names is not this app's to configure.
 *
 * Android draws its back gesture from the launcher's edge, hands it to the app as an
 * `OnBackInvokedCallback`, and gives the app no say in which edges produce one. Adding a second
 * Compose-level edge gesture beside it would be a second back affordance fighting the system's own,
 * and the platform's own already works from both edges.
 */
@Composable
actual fun rememberBackSwipeGestureAvailable(): Boolean? = null

/** Unread where there is no row; false is the answer that keeps a caller from drawing the hint. */
actual val backSwipeGestureAppliesOnRestart: Boolean = false
