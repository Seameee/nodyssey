package io.github.nodyssey

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.ui.composer.PostComposerRoute
import io.github.nodyssey.ui.composer.PostComposerViewModel
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.login.WebViewGoal
import io.github.nodyssey.ui.postdetail.PostDetailRoute
import io.github.nodyssey.ui.postdetail.PostDetailViewModel
import io.github.nodyssey.ui.stardust.StardustReceiveCard
import io.github.nodyssey.ui.stardust.StardustReceiveViewModel
import io.github.nodyssey.ui.viewer.ImageViewerScreen
import io.github.nodyssey.ui.viewer.ImageViewerViewModel
import io.github.nodyssey.ui.viewer.rememberImageGallerySaver
import io.github.nodyssey.ui.vote.VoteCard
import io.github.nodyssey.ui.vote.VoteViewModel

/**
 * The reading-and-writing core: a thread, the composer, and the image viewer.
 *
 * One of the region files `Navigation.kt`'s `destinationProvider` assembles; see [StackEntryScope]
 * for the capture rules they all share.
 */
internal fun EntryProviderScope<NavKey>.threadEntries(nav: StackEntryScope) = with(nav) {
    entry<ImageViewerKey>(
        /*
         * Fades rather than the default slide.
         *
         * Every other destination is a page that follows the one before it, and sliding says
         * so. This one is the picture already on screen, filled out — it is the same object
         * seen closer, not a place the user travelled to, and sliding it in from the edge
         * reads as having left the thread rather than having zoomed into it.
         */
        metadata =
        NavDisplay.transitionSpec { nav.fadeOrCut() } +
            NavDisplay.popTransitionSpec { nav.fadeOrCut() } +
            NavDisplay.predictivePopTransitionSpec { _ -> nav.fadeOrCut() },
    ) { key ->
        val saver = rememberImageGallerySaver(container.dispatchers)
        val viewModel: ImageViewerViewModel =
            viewModel(factory = ImageViewerViewModel.factory(saver))
        val saveOutcome by viewModel.saveOutcome.collectAsStateWithLifecycle()
        ImageViewerScreen(
            urls = key.urls,
            initialIndex = key.index,
            onClose = { backStack.removeLastOrNull() },
            // The browser, even for an image hosted on nodeseek.com: this one is the user
            // reaching for downloading and sharing, which is the browser's job and not
            // something the session's web view does better.
            onOpenBrowser = openExternalUrl,
            saveOutcome = saveOutcome,
            onSave = viewModel::save,
        )
    }

    entry<PostDetailKey>(
        /*
         * No metadata, so `NavDisplay`'s own transition applies: on iOS that is the platform's native
         * push and pull, both opening a thread and leaving it.
         *
         * This entry used to name a fade, because the thread's title flew from the feed row into the
         * header and a slide under a shared element is two motions describing one event. The flight is
         * gone — it could not coexist with the back gesture, which is described where it used to be
         * provided, in `Navigation.kt` — and with it the reason for the fade. A thread is a page that
         * follows the one before it, which is exactly what a slide says, so it now says it.
         *
         * The one thing that stays overridden is the image viewer below, and for the reason recorded
         * there: it is not a page the reader travelled to, it is the picture already on screen.
         */
    ) { key ->
        // Keyed so navigating to a different post builds a fresh ViewModel.
        val viewModel: PostDetailViewModel =
            viewModel(
                key = "post-${key.postId}",
                factory =
                PostDetailViewModel.factory(
                    container,
                    key.postId,
                    initialFloor = key.floor,
                    initialPage = key.page,
                    preview = key.preview,
                ),
            )
        // Its own ViewModel, keyed the same way: an unsent reply belongs to one thread
        // and has to outlive the sheet that shows it.
        val replyViewModel: ReplyComposerViewModel =
            viewModel(
                key = "reply-${key.postId}",
                factory = ReplyComposerViewModel.factory(container, key.postId),
            )
        PostDetailRoute(
            viewModel = viewModel,
            replyViewModel = replyViewModel,
            showBackButton = !(isListDetailExpanded() && backStack.showsListPane()),
            onBack = { backStack.removeLastOrNull() },
            onOpenBrowser = openWebUrl,
            // A link to this same thread stays on this screen; see [PostDetailViewModel.openLinkInPlace].
            onLinkClick = { url -> if (!viewModel.openLinkInPlace(url)) openContentUrl(url) },
            onAuthorClick = openSpace,
            onSignIn = { backStack.add(SignInKey) },
            onVerify = { backStack.add(WebKey(it, siteTitle, WebViewGoal.CHALLENGE)) },
            onImageClick = { urls, url -> backStack.add(imageViewerKeyFor(urls, url)) },
            onEdit = { target -> backStack.add(PostComposerKey(target)) },
            // Supplied here because this is the only layer that can reach the container.
            // Keyed by vote id and not merely by post: a thread may embed more than one, and
            // without the key they would share a single ViewModel and each other's state.
            voteContent = { voteId ->
                VoteCard(
                    viewModel =
                    viewModel(
                        key = "vote-$voteId",
                        factory = VoteViewModel.factory(container, voteId),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                    onUserClick = openSpace,
                )
            },
            // Keyed by payee *and* Ref ID, for the same reason a vote is keyed by its id: one
            // post may carry several codes, and a shared ViewModel would show one code's
            // tally under another's amount.
            stardustContent = { node ->
                StardustReceiveCard(
                    node = node,
                    viewModel =
                    viewModel(
                        key = "stardust-${node.memberId}-${node.refId}",
                        factory = StardustReceiveViewModel.factory(container, node),
                    ),
                    onSignIn = { backStack.add(SignInKey) },
                )
            },
        )
    }

    entry<PostComposerKey> { key ->
        val viewModel: PostComposerViewModel =
            viewModel(
                // Keyed by what is being written, or two edits opened in one session would
                // share the first one's ViewModel — and therefore the first one's text.
                key = "composer-${key.edit?.commentId ?: "new"}",
                factory = PostComposerViewModel.factory(container, key.edit),
            )
        // Whatever this editor is about: the thread when editing, the new-post page otherwise.
        val webUrl =
            key.edit
                ?.let { NodeSeekSite.BASE_URL + NodeSeekSite.postPath(it.postId, it.page) }
                ?: (NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH)
        PostComposerRoute(
            viewModel = viewModel,
            onClose = { backStack.removeLastOrNull() },
            onSignIn = {
                backStack.add(SignInKey)
            },
            onVerify = { url ->
                backStack.add(WebKey(url, siteTitle, WebViewGoal.CHALLENGE))
            },
            onOpenBrowser = {
                backStack.add(WebKey(webUrl, siteTitle, WebViewGoal.MANAGE))
            },
            onPublished = { postId ->
                backStack.removeLastOrNull()
                // An edit returns to the thread it came from, which is already underneath —
                // pushing it again would stack a second copy of the screen being updated.
                if (key.edit == null) postId?.let { backStack.add(PostDetailKey(it)) }
            },
        )
    }
}

/**
 * The fade, or nothing at all on electronic paper.
 *
 * One destination carries its own transition — the image viewer, which is the picture already on
 * screen rather than a page the reader travelled to. Naming one means it overrides the cut
 * `NavDisplay` is given for 墨水屏模式, because an entry's metadata wins over the display's default;
 * hence the cut is repeated here rather than left to the display. Read inside the spec lambda rather
 * than when the entry is built, because the provider around it is built once and outlives any number
 * of visits to the setting.
 */
private fun StackEntryScope.fadeOrCut(): ContentTransform =
    if (isEinkMode()) SNAP_CONTENT_TRANSFORM else fadeIn() togetherWith fadeOut()

private fun imageViewerKeyFor(urls: List<String>, url: String): ImageViewerKey =
    ImageViewerKey(urls = urls, index = urls.indexOf(url).coerceAtLeast(0))
