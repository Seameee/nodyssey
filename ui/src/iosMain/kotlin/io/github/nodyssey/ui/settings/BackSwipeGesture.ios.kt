package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * True — iOS is the one platform where this app draws its own back gesture, so the row belongs here.
 *
 * The gesture used to be Compose Multiplatform's, which installs a `UIScreenEdgePanGestureRecognizer`
 * per edge and is configured once from `endEdgePanGestureBehavior`. It is now this app's own drag —
 * see `swipeBackToNavigate` in `:ui`'s `common/SwipeBack.kt` — which exists because an edge
 * recognizer cannot reach the middle of the screen, and a thumb can.
 */
@Composable
actual fun rememberBackSwipeGestureAvailable(): Boolean? = true
