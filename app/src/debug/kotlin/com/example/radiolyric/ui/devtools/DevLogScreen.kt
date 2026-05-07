package com.example.radiolyric.ui.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.radiolyric.devtools.AppLog
import com.example.radiolyric.devtools.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app log viewer (debug builds only). Mirrors [AppLog] into a scrollable list with level
 * filters, free-text search, copy-to-clipboard, and clear actions. Auto-scrolls to the bottom only
 * when the user is already at the bottom — preserves scroll position when the user has scrolled up
 * to inspect history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLogScreen(modifier: Modifier = Modifier) {
    val entries by AppLog.recent.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val enabledLevels = remember { mutableStateOf(LogEntry.Level.values().toSet()) }

    val filtered by
            remember(entries, query, enabledLevels.value) {
                derivedStateOf {
                    entries.filter {
                        it.level in enabledLevels.value &&
                                (query.isBlank() ||
                                        it.message.contains(query, ignoreCase = true) ||
                                        it.tag.contains(query, ignoreCase = true))
                    }
                }
            }

    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    // Auto-scroll only if the user is parked at the bottom.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(filtered.size, atBottom) {
        if (atBottom && filtered.isNotEmpty()) {
            listState.scrollToItem(filtered.lastIndex)
        }
    }

    Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                        title = { Text("Dev Logs (${filtered.size}/${entries.size})") },
                        actions = {
                            IconButton(
                                    onClick = {
                                        clipboard.setText(
                                                AnnotatedString(
                                                        filtered.joinToString("\n") {
                                                            it.formatted()
                                                        },
                                                ),
                                        )
                                    },
                            ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
                            IconButton(onClick = { AppLog.clear() }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        },
                )
            },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogEntry.Level.values().forEach { level ->
                    FilterChip(
                            selected = level in enabledLevels.value,
                            onClick = {
                                enabledLevels.value =
                                        if (level in enabledLevels.value)
                                                enabledLevels.value - level
                                        else enabledLevels.value + level
                            },
                            label = { Text(level.name) },
                    )
                }
            }
            OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter (tag or message)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(filtered) { entry ->
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(entry.level.background())
                                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    ) {
                        Text(
                                text = entry.formatted(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private val TimestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private fun LogEntry.formatted(): String {
    val ts = TimestampFormat.format(Date(timestampMs))
    val base = "$ts ${level.name} $tag: $message"
    return if (throwable.isNullOrBlank()) base else "$base\n$throwable"
}

private fun LogEntry.Level.background(): Color =
        when (this) {
            LogEntry.Level.D -> Color.Transparent
            LogEntry.Level.I -> Color(0x1100AA00)
            LogEntry.Level.W -> Color(0x22FFA500)
            LogEntry.Level.E -> Color(0x33FF3030)
        }
