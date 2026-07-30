package com.dfamaya.concentric.companion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
// `entry` is a member of EntryProviderScope, not a top-level function — it
// resolves off the entryProvider receiver and must not be imported.
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch

/** The two top-level destinations, in bottom-bar order. */
private sealed interface Destination

private data object Home : Destination

private data object About : Destination

// Nav3's own rememberNavBackStack persists keys with kotlinx-serialization,
// which this module doesn't otherwise need (AGP's built-in Kotlin compiles
// :mobile — there's no Kotlin Gradle Plugin to hang the serialization compiler
// plugin off). Two parameterless destinations save fine as plain strings.
private val DestinationSaver = listSaver<SnapshotStateList<Destination>, String>(
    save = { stack -> stack.map { if (it == About) "about" else "home" } },
    restore = { saved -> saved.map { if (it == "about") About else Home }.toMutableStateList() },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompanionApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Home is the root; About sits on top of it, so the bar's own transitions
    // and the system back gesture agree on which way is "back".
    val backStack = rememberSaveable(saver = DestinationSaver) {
        mutableStateListOf<Destination>(Home)
    }
    val current = backStack.lastOrNull() ?: Home
    var installing by remember { mutableStateOf(false) }
    var watchState by remember { mutableStateOf(WatchState.CHECKING) }
    var connectedAnnounced by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val sendingMsg = stringResource(R.string.install_sending)
    val sentMsg = stringResource(R.string.install_sent)
    val failedMsg = stringResource(R.string.install_failed)
    val storeUnavailableMsg = stringResource(R.string.store_unavailable)
    val noEmailMsg = stringResource(R.string.no_email_client)
    val shareUnavailableMsg = stringResource(R.string.share_unavailable)
    val watchConnectedMsg = stringResource(R.string.watch_connected)

    // Material 3 motion tokens: the spatial spec moves things, the effects spec
    // handles the non-spatial fade and the FAB's colour swap. Read here because
    // the transition lambdas below aren't composable.
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    fun snack(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Re-query watch connectivity / install state on launch and every resume, so
    // returning after installing the face on the watch refreshes the FAB.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { watchState = queryWatchState(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Announce a freshly connected watch exactly once; re-arm when it drops off.
    LaunchedEffect(watchState) {
        when (watchState) {
            WatchState.NOT_INSTALLED, WatchState.INSTALLED -> {
                if (!connectedAnnounced) {
                    connectedAnnounced = true
                    snack(watchConnectedMsg)
                }
            }
            WatchState.NO_WATCH -> connectedAnnounced = false
            WatchState.CHECKING -> {}
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // A labelled button on each side leaves the centered title almost
                // no width, and it wraps mid-word, so feedback is icon-only and
                // the review label is kept short.
                navigationIcon = {
                    if (current == About) {
                        IconButton(
                            onClick = { if (!sendFeedback(context)) snack(noEmailMsg) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_feedback),
                                contentDescription = stringResource(R.string.send_feedback),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { if (!openReview(context)) snack(storeUnavailableMsg) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.rate))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                ShortNavigationBarItem(
                    selected = current == Home,
                    onClick = { if (current != Home) backStack.removeLastOrNull() },
                    icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
                    label = { Text(stringResource(R.string.home)) },
                )
                ShortNavigationBarItem(
                    selected = current == About,
                    onClick = { if (current != About) backStack.add(About) },
                    icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                    label = { Text(stringResource(R.string.about)) },
                )
            }
        },
        floatingActionButton = {
            // Material 3: the FAB belongs to no single tab. On a destination
            // change it scales away first, then the replacement scales back in
            // once the new content has slid into place — hence the delay on the
            // enter half. Keyed on the destination only, so watch-state changes
            // within Home still update the label in place.
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val enter = tween<Float>(
                        durationMillis = 200,
                        delayMillis = 180,
                        easing = LinearOutSlowInEasing,
                    )
                    val exit = tween<Float>(durationMillis = 100, easing = FastOutLinearInEasing)
                    (fadeIn(enter) + scaleIn(enter, initialScale = 0.8f))
                        .togetherWith(fadeOut(exit) + scaleOut(exit, targetScale = 0.8f))
                        // snap: the container takes the incoming FAB's size at
                        // once rather than animating between the two widths.
                        // clip = false: the FAB's elevation shadow is drawn
                        // outside its bounds and AnimatedContent would otherwise
                        // clip it flat for the length of the transition.
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                // The FAB is end-aligned in the Scaffold, so the animating
                // children must be too — with the default TopStart the narrower
                // of the two labels sits left of its final position until the
                // transition ends.
                contentAlignment = Alignment.CenterEnd,
                label = "fab",
            ) { destination ->
                val onAbout = destination == About
                // Muted palette for the non-actionable states (checking / no watch).
                val mutedContainer = MaterialTheme.colorScheme.surfaceContainerHighest
                val mutedContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                val actionable = watchState == WatchState.NOT_INSTALLED || watchState == WatchState.INSTALLED

                ExtendedFloatingActionButton(
                    onClick = {
                        if (onAbout) {
                            if (!shareApp(context)) snack(shareUnavailableMsg)
                            return@ExtendedFloatingActionButton
                        }
                        when (watchState) {
                            WatchState.NOT_INSTALLED -> {
                                if (!installing) {
                                    installing = true
                                    snack(sendingMsg)
                                    installOnWatch(context) { result ->
                                        installing = false
                                        snack(if (result == InstallResult.SENT) sentMsg else failedMsg)
                                    }
                                }
                            }
                            WatchState.INSTALLED -> showSetupDialog = true
                            else -> {} // CHECKING / NO_WATCH: non-interactive
                        }
                    },
                    expanded = true,
                    icon = {
                        when {
                            onAbout ->
                                Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                            watchState == WatchState.CHECKING ->
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = mutedContent,
                                )
                            else ->
                                Icon(painterResource(R.drawable.ic_watch), contentDescription = null)
                        }
                    },
                    text = {
                        Text(
                            stringResource(
                                if (onAbout) {
                                    R.string.share
                                } else {
                                    when (watchState) {
                                        WatchState.CHECKING -> R.string.status_checking
                                        WatchState.NO_WATCH -> R.string.no_watch_connected
                                        WatchState.NOT_INSTALLED -> R.string.install_on_watch
                                        WatchState.INSTALLED -> R.string.set_on_watch
                                    }
                                }
                            )
                        )
                    },
                    containerColor = when {
                        onAbout -> MaterialTheme.colorScheme.tertiaryContainer
                        actionable -> MaterialTheme.colorScheme.primaryContainer
                        else -> mutedContainer
                    },
                    contentColor = when {
                        onAbout -> MaterialTheme.colorScheme.onTertiaryContainer
                        actionable -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> mutedContent
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavDisplay(
                backStack = backStack,
                // Home is the root, so back only ever pops About off it.
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                // A shared-axis X pair: a short slide (a tenth of the width, not
                // a full page push) carried on the spatial spec, with the fade on
                // the effects spec. Forward moves left, back moves right.
                transitionSpec = {
                    (slideInHorizontally(slideSpec) { it / 10 } + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec) { -it / 10 } + fadeOut(fadeSpec))
                },
                popTransitionSpec = {
                    (slideInHorizontally(slideSpec) { -it / 10 } + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec) { it / 10 } + fadeOut(fadeSpec))
                },
                predictivePopTransitionSpec = {
                    (slideInHorizontally(slideSpec) { -it / 10 } + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec) { it / 10 } + fadeOut(fadeSpec))
                },
                entryProvider = entryProvider {
                    entry<Home> { HomeScreen(Modifier.fillMaxSize()) }
                    entry<About> { AboutScreen(Modifier.fillMaxSize()) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (installing) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    // Setup instructions, shown once the face is installed on the watch.
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.setup_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SetupStep(1, stringResource(R.string.setup_step_1))
                    SetupStep(2, stringResource(R.string.setup_step_2))
                    SetupStep(3, stringResource(R.string.setup_step_3))
                }
            },
            confirmButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text(stringResource(R.string.setup_got_it), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun SetupStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
