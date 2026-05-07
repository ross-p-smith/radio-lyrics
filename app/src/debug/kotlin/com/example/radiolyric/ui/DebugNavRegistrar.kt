package com.example.radiolyric.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.example.radiolyric.ui.devtools.DevLogRoute
import com.example.radiolyric.ui.devtools.devLogScreen

/**
 * Debug-source-set registrar. Adds the developer log viewer to the nav graph and exposes a
 * bottom-bar item for it. The release source set ships a no-op stub with the same FQNs so
 * `AppNavigation.kt` can call these unconditionally.
 */
fun NavGraphBuilder.installDebugRoutes(navController: NavController) {
    devLogScreen(navController)
}

fun debugNavItems(): List<NavItemSpec> =
        listOf(NavItemSpec(DevLogRoute.ROUTE, "Logs", Icons.Filled.BugReport))
