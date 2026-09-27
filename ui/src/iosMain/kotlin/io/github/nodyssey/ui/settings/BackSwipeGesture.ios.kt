package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable

/**
 * True — iOS is the one platform where this app owns the far edge, so the row is drawn here.
 *
 * The gesture itself is not ours to write: Compose Multiplatform's iOS host installs a
 * `UIScreenEdgePanGestureRecognizer` on each edge and turns them into the same `NavigationEvent`s
 * that drive predictive back. What it does not decide is whether the far edge *returns* or goes
 * forward — that is `endEdgePanGestureBehavior` on the configuration `ComposeUIViewController` is
 * built from, and `NodysseyApp.buildController` in `:iosapp` is the one place that reads 返回手势
 * and sets it.
 */
@Composable
actual fun rememberBackSwipeGestureAvailable(): Boolean? = true

/**
 * True, and the reason the row carries a 重新打开 App 后生效 hint.
 *
 * `IosComposeSceneLayer` builds its `IosBackNavigationEventInput` — and therefore both recognizers —
 * with `endEdgePanGestureBehavior = configuration.endEdgePanGestureBehavior` read once, in its
 * initialiser. The controller is built once per process (see `NodysseyApp` in `:iosapp`, which is
 * deliberate: rebuilding it would put the reader back on the feed), so a change here is a change to
 * the *next* process rather than to this one.
 */
actual val backSwipeGestureAppliesOnRestart: Boolean = true
