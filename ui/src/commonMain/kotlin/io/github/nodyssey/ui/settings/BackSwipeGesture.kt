package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Whether 返回手势 is a question this platform can answer, or null where it is not.
 *
 * Null hides the row, which is the honest answer rather than an explanation shown to someone who can
 * do nothing with it. Android's back gesture belongs to the system — it is an `OnBackInvokedCallback`
 * the launcher draws — and no app can add an edge to it or take one away; on Android 10 the platform
 * already offers both edges, so nothing is missing there either. iOS is the one platform where the
 * app owns the gesture, and there the answer is true.
 *
 * The same shape as [rememberAppLinkHandlingEnabled] beside it: a settings row whose existence is a
 * platform question is asked of the platform, not branched on in `commonMain`.
 */
@Composable
expect fun rememberBackSwipeGestureAvailable(): Boolean?
