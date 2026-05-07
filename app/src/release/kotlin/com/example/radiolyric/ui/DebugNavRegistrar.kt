package com.example.radiolyric.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Release-source-set stub. The real implementation lives in `app/src/debug/.../ui/`. Keeping the
 * FQN identical means `AppNavigation.kt` can call these unconditionally; AGP merges only one of
 * `src/debug` and `src/release` per variant, so the names never collide.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.installDebugRoutes(navController: NavController) {
    // Intentionally empty — no developer surfaces in release builds.
}

fun debugNavItems(): List<NavItemSpec> = emptyList()
