package com.dfamaya.concentric.companion

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompanionApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
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
    val watchConnectedMsg = stringResource(R.string.watch_connected)

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
                    if (selectedTab == 1) {
                        IconButton(
                            onClick = { if (!sendFeedback(context)) snack(noEmailMsg) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_feedback),
                                contentDescription = stringResource(R.string.send_feedback),
                                modifier = Modifier.size(22.dp),
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
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rate))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                ShortNavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
                    label = { Text(stringResource(R.string.home)) },
                )
                ShortNavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                    label = { Text(stringResource(R.string.about)) },
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                // Muted palette for the non-actionable states (checking / no watch).
                val mutedContainer = MaterialTheme.colorScheme.surfaceContainerHighest
                val mutedContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                val actionable = watchState == WatchState.NOT_INSTALLED || watchState == WatchState.INSTALLED

                ExtendedFloatingActionButton(
                    onClick = {
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
                        if (watchState == WatchState.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = mutedContent,
                            )
                        } else {
                            Icon(painterResource(R.drawable.ic_watch), contentDescription = null)
                        }
                    },
                    text = {
                        Text(
                            stringResource(
                                when (watchState) {
                                    WatchState.CHECKING -> R.string.status_checking
                                    WatchState.NO_WATCH -> R.string.no_watch_connected
                                    WatchState.NOT_INSTALLED -> R.string.install_on_watch
                                    WatchState.INSTALLED -> R.string.set_on_watch
                                }
                            )
                        )
                    },
                    containerColor = if (actionable) MaterialTheme.colorScheme.primaryContainer else mutedContainer,
                    contentColor = if (actionable) MaterialTheme.colorScheme.onPrimaryContainer else mutedContent,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(Modifier.fillMaxSize())
                else -> AboutScreen(Modifier.fillMaxSize())
            }
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
