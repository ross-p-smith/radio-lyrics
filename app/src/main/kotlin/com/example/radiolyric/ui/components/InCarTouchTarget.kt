package com.example.radiolyric.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wrapper enforcing the in-car minimum touch target (56 dp height + 24 dp horizontal padding).
 * Use on any tappable element in [com.example.radiolyric.ui] composables.
 */
@Composable
fun InCarTouchTarget(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
            modifier = modifier.heightIn(min = 56.dp).padding(horizontal = 24.dp),
            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) { content() }
}
