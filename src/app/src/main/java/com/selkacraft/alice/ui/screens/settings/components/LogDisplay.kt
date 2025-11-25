package com.selkacraft.alice.ui.screens.settings.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selkacraft.alice.ui.theme.SourceCodePro
import com.selkacraft.alice.util.CameraViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG_LDC = "LogDisplay"

private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

private data class LogLevelStyle(
    val symbol: String,
    val color: Color,
    val bgColor: Color
)

private data class CategoryStyle(
    val shortName: String,
    val color: Color
)

private fun getLogLevelStyle(level: CameraViewModel.LogLevel): LogLevelStyle = when (level) {
    CameraViewModel.LogLevel.DEBUG -> LogLevelStyle(
        symbol = "▪",
        color = Color(0xFF4DD0E1),
        bgColor = Color(0xFF263238)
    )
    CameraViewModel.LogLevel.INFO -> LogLevelStyle(
        symbol = "●",
        color = Color(0xFF66BB6A),
        bgColor = Color(0xFF1B5E20)
    )
    CameraViewModel.LogLevel.WARNING -> LogLevelStyle(
        symbol = "▲",
        color = Color(0xFFFFB74D),
        bgColor = Color(0xFF4E342E)
    )
    CameraViewModel.LogLevel.ERROR -> LogLevelStyle(
        symbol = "✖",
        color = Color(0xFFE57373),
        bgColor = Color(0xFF4E342E)
    )
}

private fun getCategoryStyle(category: CameraViewModel.LogCategory): CategoryStyle = when (category) {
    CameraViewModel.LogCategory.SYSTEM -> CategoryStyle("SYS", Color(0xFF9FA8DA))
    CameraViewModel.LogCategory.USB -> CategoryStyle("USB", Color(0xFF81D4FA))
    CameraViewModel.LogCategory.CAMERA -> CategoryStyle("CAM", Color(0xFF80CBC4))
    CameraViewModel.LogCategory.MOTOR -> CategoryStyle("MTR", Color(0xFFFFCC80))
    CameraViewModel.LogCategory.REALSENSE -> CategoryStyle("RS ", Color(0xFFCE93D8))
    CameraViewModel.LogCategory.AUTOFOCUS -> CategoryStyle("AF ", Color(0xFFA5D6A7))
    CameraViewModel.LogCategory.ERROR -> CategoryStyle("ERR", Color(0xFFEF5350))
}

private fun formatLogEntry(entry: CameraViewModel.LogEntry): AnnotatedString {
    val timestamp = dateFormat.format(Date(entry.timestamp))
    val levelStyle = getLogLevelStyle(entry.level)
    val categoryStyle = getCategoryStyle(entry.category)

    return buildAnnotatedString {
        // Timestamp
        withStyle(SpanStyle(
            color = Color(0xFF78909C),
            fontFamily = SourceCodePro,
            fontSize = 11.sp
        )) {
            append(timestamp)
        }

        append("  ")

        // Level indicator
        withStyle(SpanStyle(
            color = levelStyle.color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )) {
            append(levelStyle.symbol)
        }

        append(" ")

        // Category badge
        withStyle(SpanStyle(
            color = categoryStyle.color,
            fontFamily = SourceCodePro,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )) {
            append("[${categoryStyle.shortName}]")
        }

        append(" ")

        // Message with keyword highlighting
        appendHighlightedMessage(entry.message, entry.level)
    }
}

private fun AnnotatedString.Builder.appendHighlightedMessage(message: String, level: CameraViewModel.LogLevel) {
    val baseColor = when (level) {
        CameraViewModel.LogLevel.ERROR -> Color(0xFFFFCDD2)
        CameraViewModel.LogLevel.WARNING -> Color(0xFFFFE0B2)
        CameraViewModel.LogLevel.INFO -> Color(0xFFE0E0E0)
        CameraViewModel.LogLevel.DEBUG -> Color(0xFFB0BEC5)
    }

    val start = length
    withStyle(SpanStyle(
        color = baseColor,
        fontFamily = SourceCodePro,
        fontSize = 12.sp
    )) {
        append(message)
    }

    // Keyword highlighting
    val keywords = mapOf(
        "CONNECTED" to SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold),
        "DISCONNECTED" to SpanStyle(color = Color(0xFFEF5350), fontWeight = FontWeight.Bold),
        "ATTACHED" to SpanStyle(color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold),
        "DETACHED" to SpanStyle(color = Color(0xFFFFA726), fontWeight = FontWeight.Bold),
        "SUCCESS" to SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold),
        "SUCCESSFUL" to SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold),
        "FAILED" to SpanStyle(color = Color(0xFFE57373), fontWeight = FontWeight.Bold),
        "ERROR" to SpanStyle(color = Color(0xFFE57373), fontWeight = FontWeight.Bold),
        "WARNING" to SpanStyle(color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold),
        "DENIED" to SpanStyle(color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
    )

    keywords.forEach { (keyword, style) ->
        var searchStart = 0
        while (searchStart < message.length) {
            val index = message.indexOf(keyword, searchStart, ignoreCase = true)
            if (index == -1) break
            addStyle(style, start + index, start + index + keyword.length)
            searchStart = index + keyword.length
        }
    }
}
@Composable
fun LogDisplay(
    logMessages: List<CameraViewModel.LogEntry>,
    modifier: Modifier = Modifier
) {
    Log.d(TAG_LDC, "Composing with ${logMessages.size} messages.")
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            Log.d(TAG_LDC, "New log message added (total ${logMessages.size}), scrolling to bottom.")
            coroutineScope.launch {
                listState.animateScrollToItem(logMessages.size - 1)
            }
        } else {
            Log.d(TAG_LDC, "Log messages list is empty or size unchanged, no scroll action.")
        }
    }

    val terminalBackgroundColor = Color(0xFF0D1117)
    val headerBackgroundColor = Color(0xFF161B22)
    val borderColor = Color(0xFF30363D)

    Column(
        modifier = modifier
            .background(terminalBackgroundColor)
    ) {
        // Console Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBackgroundColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = SourceCodePro,
                            color = Color(0xFF58A6FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSOLE",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = SourceCodePro,
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Event count
                Text(
                    text = "${logMessages.size} events",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = SourceCodePro,
                        color = Color(0xFF58606A),
                        fontSize = 10.sp
                    )
                )
            }
        }

        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(borderColor)
        )

        // Log content
        Box(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = logMessages.takeLast(100),
                        key = { index, entry -> "${entry.timestamp}-${entry.category.name}-$index" }
                    ) { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (entry.isRaw) 0.dp else 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = if (entry.isRaw) {
                                    // Display raw text without formatting (for logo, ASCII art, etc.)
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(
                                            color = Color(0xFF8B949E),
                                            fontFamily = SourceCodePro,
                                            fontSize = 11.sp
                                        )) {
                                            append(entry.message)
                                        }
                                    }
                                } else {
                                    formatLogEntry(entry)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = SourceCodePro,
                                    lineHeight = if (entry.isRaw) 14.sp else 18.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (logMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "┌─────────────────────────────┐\n" +
                                           "│   Console ready...          │\n" +
                                           "│   Waiting for events...     │\n" +
                                           "└─────────────────────────────┘",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = SourceCodePro,
                                        color = Color(0xFF58606A),
                                        fontSize = 11.sp
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}