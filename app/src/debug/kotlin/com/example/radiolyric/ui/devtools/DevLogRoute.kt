package com.example.radiolyric.ui.devtools

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

object DevLogRoute {
    const val ROUTE: String = "dev/logs"
}

fun NavGraphBuilder.devLogScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    composable(DevLogRoute.ROUTE) { DevLogScreen(modifier = Modifier.fillMaxSize()) }
}
