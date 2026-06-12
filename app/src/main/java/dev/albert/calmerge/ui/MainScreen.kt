package dev.albert.calmerge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.albert.calmerge.ui.theme.SlateDark2

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    AGENDA("Agenda", Icons.Rounded.List),
    CALENDAR("Calendar", Icons.Rounded.CalendarMonth),
    CONFLICTS("Conflicts", Icons.Rounded.Warning),
}

private enum class SubScreen(val title: String) { FEEDS("Feeds"), SETTINGS("Settings") }

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var prevTabOrdinal by rememberSaveable { mutableStateOf(0) }
    var subScreen by rememberSaveable { mutableStateOf<SubScreen?>(null) }
    val clusters by viewModel.conflictClusters.collectAsState()
    var navBarVisible by remember { mutableStateOf(true) }

    BackHandler(enabled = subScreen != null) { subScreen = null }
    BackHandler(enabled = subScreen == null && selectedTab != MainTab.HOME) {
        prevTabOrdinal = selectedTab.ordinal
        selectedTab = MainTab.HOME
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -12f -> navBarVisible = false
                    available.y > 12f -> navBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = navBarVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(tween(150)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeOut(tween(100)),
            ) {
                NavigationBar(containerColor = SlateDark2) {
                    MainTab.entries.forEach { tab ->
                        val selected = selectedTab == tab && subScreen == null
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                subScreen = null
                                prevTabOrdinal = selectedTab.ordinal
                                selectedTab = tab
                                navBarVisible = true
                            },
                            icon = {
                                if (tab == MainTab.CONFLICTS && clusters.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${clusters.size}") } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection),
        ) {
            // Main tab content with slide+fade transitions
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > prevTabOrdinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(
                        initialOffsetX = { (it * 0.3f * dir).toInt() },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { (-it * 0.3f * dir).toInt() },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) + fadeOut(tween(150)))
                },
                label = "tabTransition",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onOpenFeeds = { subScreen = SubScreen.FEEDS },
                        onOpenSettings = { subScreen = SubScreen.SETTINGS },
                        onOpenAgenda = {
                            prevTabOrdinal = selectedTab.ordinal
                            selectedTab = MainTab.AGENDA
                        },
                        onOpenConflicts = {
                            prevTabOrdinal = selectedTab.ordinal
                            selectedTab = MainTab.CONFLICTS
                        },
                    )
                    MainTab.AGENDA -> AgendaScreen(viewModel)
                    MainTab.CALENDAR -> CalendarScreen(
                        viewModel = viewModel,
                        onOpenConflicts = {
                            prevTabOrdinal = selectedTab.ordinal
                            selectedTab = MainTab.CONFLICTS
                        },
                    )
                    MainTab.CONFLICTS -> ConflictsScreen(viewModel)
                }
            }

            // Sub-screens slide in from the right
            AnimatedVisibility(
                visible = subScreen != null,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn(tween(200)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            ) {
                // Hold the last non-null screen so content stays visible during the exit slide.
                var lastSubScreen by remember { mutableStateOf(SubScreen.FEEDS) }
                subScreen?.let { lastSubScreen = it }
                SubScreenShell(
                    title = lastSubScreen.title,
                    onBack = { subScreen = null },
                ) {
                    when (lastSubScreen) {
                        SubScreen.FEEDS -> FeedsScreen(viewModel)
                        SubScreen.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubScreenShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}
