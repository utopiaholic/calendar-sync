package dev.albert.calmerge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val clusters by viewModel.conflictClusters.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Agenda") })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    if (clusters.isEmpty()) {
                        Text("Conflicts")
                    } else {
                        BadgedBox(badge = { Badge { Text("${clusters.size}") } }) { Text("Conflicts") }
                    }
                },
            )
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Feeds") })
        }
        when (selectedTab) {
            0 -> AgendaScreen(viewModel)
            1 -> ConflictsScreen(viewModel)
            else -> FeedsScreen(viewModel)
        }
    }
}
