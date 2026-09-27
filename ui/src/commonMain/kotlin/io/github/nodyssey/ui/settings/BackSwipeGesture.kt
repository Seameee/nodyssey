package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * Whether 返回手势 is a question this platform can answer, or null where it is not.
 *
 * Null hides the row, which is the honest answer rather than an explanation shown to someone who can
 * do nothing with it. Android's back gesture belongs to the system — it is an `OnBackInvokedCallback`
 * the launcher draws — and no app can add an edge to it or take one away; on Android 10 the platform
 * already offers both edges, so nothing is missing there either. iOS is the one platform where the
 * app owns the second edge, and there the answer is true.
 *
 * The same shape as [rememberAppLinkHandlingEnabled] beside it: a settings row whose existence is a
 * platform question is asked of the platform, not branched on in `commonMain`.
 */
@Composable
expect fun rememberBackSwipeGestureAvailable(): Boolean?

/**
 * Whether a change to 返回手势 needs the app restarted before it does anything.
 *
 * True on iOS. The two edge recognizers are installed by Compose Multiplatform's own
 * `ComposeUIViewController` while it is constructed — it reads `endEdgePanGestureBehavior` once, in
 * the scene layer's initialiser, and offers no way to hand it a second answer afterwards. So the row
 * says the change lands at the next launch, the way 语言 already does on this platform, instead of
 * appearing to do nothing.
 *
 * False where there is no row at all; a caller only reads it once [rememberBackSwipeGestureAvailable]
 * has answered non-null.
 */
expect val backSwipeGestureAppliesOnRestart: Boolean
