package com.example.radiolyric.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private object Routes {
    const val NOW_PLAYING = "now_playing"
    const val LYRICS = "lyrics"
    const val STATIONS = "stations"
}

/** Bottom-bar item descriptor. Public so the debug source set can construct one. */
data class NavItemSpec(val route: String, val label: String, val icon: ImageVector)

private val NavItems =
        listOf(
                NavItemSpec(Routes.NOW_PLAYING, "Now Playing", Icons.Filled.MusicNote),
                NavItemSpec(Routes.LYRICS, "Lyrics", Icons.Filled.Subtitles),
                NavItemSpec(Routes.STATIONS, "Stations", Icons.Filled.LibraryMusic),
        )

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val backStack by navController.currentBackStackEntryAsState()
                    val current = backStack?.destination
                    (NavItems + debugNavItems()).forEach { item ->
                        NavigationBarItem(
                                selected =
                                        current?.hierarchy?.any { it.route == item.route } == true,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                        )
                    }
                }
            },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                    navController = navController,
                    startDestination = Routes.NOW_PLAYING,
                    modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.NOW_PLAYING) {
                    NowPlayingScreen(
                            modifier = Modifier.fillMaxSize(),
                            onOpenLyrics = { navController.navigate(Routes.LYRICS) },
                            onOpenStations = { navController.navigate(Routes.STATIONS) },
                    )
                }
                composable(Routes.LYRICS) { LyricsScreen(modifier = Modifier.fillMaxSize()) }
                composable(Routes.STATIONS) {
                    StationPickerScreen(modifier = Modifier.fillMaxSize())
                }
                installDebugRoutes(navController)
            }
            // innerPadding is consumed by the Scaffold; no extra inset needed in MVP since each
            // screen handles its own padding.
            innerPadding.toString()
        }
    }
}
