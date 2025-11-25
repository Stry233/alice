package com.selkacraft.alice.ui.screens.welcome.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.selkacraft.alice.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

// The full phrase with key letters marked
private const val FULL_PHRASE = "Autofocus Lens Interface & Camera Extension"
private val KEY_LETTER_INDICES = listOf(0, 10, 15, 27, 34) // A, L, I, C, E positions
private const val AMPERSAND_INDEX = 25 // Position of & in the phrase

// Easing curves for animation
private val CinematicEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
private val DramaticRevealEasing = CubicBezierEasing(0.33f, 0.0f, 0.1f, 1.0f)
private val GentleEasing = CubicBezierEasing(0.4f, 0.0f, 0.1f, 1.0f)
private val WeightMorphEasing = CubicBezierEasing(0.1f, 0.4f, 0.2f, 1.0f)

// Data class to store original letter positions
private data class LetterPosition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Composable
fun AliceAcronymAnimation(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Single unified animation progress (0 = expanded, 1 = collapsed to ALICE)
    val animationProgress = remember { Animatable(0f) }

    // Animation state
    var isCollapsed by remember { mutableStateOf(false) }

    // Alpha and scale for subtitle with separate controls
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleScale = remember { Animatable(0.9f) }

    // Individual letter scales for micro-animations
    val letterScales = remember {
        KEY_LETTER_INDICES.map { Animatable(1f) }
    }

    // Store original positions of ALL letters - calculated ONCE
    var originalPositions by remember { mutableStateOf<List<LetterPosition>?>(null) }
    var originalTotalHeight by remember { mutableIntStateOf(0) }

    // Flag to track if initial calculation is done
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-collapse after initial display
    LaunchedEffect(Unit) {
        delay(1500)
        isCollapsed = true
    }

    // Handle collapse/expand animation state transitions
    LaunchedEffect(isCollapsed) {
        if (isCollapsed) {
            // Collapse animation with staggered key letter scaling
            KEY_LETTER_INDICES.forEachIndexed { index, _ ->
                launch {
                    delay(index * 50L)
                    letterScales[index].animateTo(
                        1.05f,
                        animationSpec = tween(200, easing = GentleEasing)
                    )
                    letterScales[index].animateTo(
                        1f,
                        animationSpec = tween(600, easing = CinematicEasing)
                    )
                }
            }

            // Main collapse animation
            launch {
                delay(150)
                animationProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 1200, easing = CinematicEasing)
                )
            }

            // Subtitle fade-in
            launch {
                delay(900)
                launch {
                    subtitleScale.animateTo(
                        1f,
                        animationSpec = tween(800, easing = DramaticRevealEasing)
                    )
                }
                subtitleAlpha.animateTo(
                    1f,
                    animationSpec = tween(1000, easing = GentleEasing)
                )
            }
        } else {
            // Expand animation
            launch {
                launch {
                    subtitleAlpha.animateTo(
                        0f,
                        animationSpec = tween(300, easing = GentleEasing)
                    )
                }
                launch {
                    subtitleScale.animateTo(
                        0.9f,
                        animationSpec = tween(400, easing = CinematicEasing)
                    )
                }
            }

            launch {
                delay(200)
                animationProgress.animateTo(
                    0f,
                    animationSpec = tween(durationMillis = 1000, easing = DramaticRevealEasing)
                )
            }

            // Reset key letter scales
            letterScales.forEach { scale ->
                launch {
                    scale.animateTo(
                        1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                    )
                }
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val progress = animationProgress.value

    val expandedFontSize = 20.sp
    val collapsedFontSize = 42.sp

    // Measure "ALICE" as a single string to get natural letter positions
    val aliceTextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = collapsedFontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    val aliceMeasureResult = remember(collapsedFontSize) {
        textMeasurer.measure("ALICE", aliceTextStyle)
    }

    // Dynamic container height with smoother interpolation
    val expandedHeight = 110.dp
    val collapsedHeight = 64.dp
    val containerHeight = lerp(expandedHeight, collapsedHeight, progress)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    isCollapsed = !isCollapsed
                }
            }
    ) {
        // The animated text container with dynamic height
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
        ) {
            Layout(
                content = {
                    FULL_PHRASE.forEachIndexed { index, char ->
                        val keyIndex = KEY_LETTER_INDICES.indexOf(index)
                        val isKey = keyIndex != -1

                        // Apply cinematic easing to size transitions
                        val sizeProgress = if (isKey) {
                            CinematicEasing.transform(progress)
                        } else {
                            progress
                        }

                        // Font size with smoother interpolation
                        val fontSize = if (isKey) {
                            lerp(expandedFontSize, collapsedFontSize, sizeProgress)
                        } else {
                            expandedFontSize
                        }

                        // More dramatic weight morphing with custom curve
                        val fontWeight = if (isKey) {
                            val weightProgress = WeightMorphEasing.transform(progress)
                            val weightValue = lerp(300f, 800f, weightProgress).toInt()
                            FontWeight(weightValue)
                        } else {
                            val fadeWeight = lerp(400f, 200f, progress).toInt()
                            FontWeight(fadeWeight)
                        }

                        // CINEMATIC ALPHA with multiple stages
                        val alpha = if (isKey) {
                            // Key letters stay visible but can have subtle variation
                            val minAlpha = 0.95f
                            minAlpha + (1f - minAlpha) * (1f - progress * 0.1f)
                        } else {
                            // Non-key letters fade with eased curve
                            val fadeProgress = DramaticRevealEasing.transform(
                                (progress * 1.3f).coerceIn(0f, 1f)
                            )
                            (1f - fadeProgress).pow(1.5f) // Power curve for smoother fade
                        }

                        // Apply scale to key letters
                        val scale = if (isKey) {
                            letterScales[keyIndex].value
                        } else {
                            1f
                        }

                        Text(
                            text = char.toString(),
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                letterSpacing = if (isKey) {
                                    lerp(0.sp, 1.sp, progress)
                                } else 0.sp
                            ),
                            color = textColor.copy(alpha = alpha),
                            modifier = Modifier.scale(scale)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(Constraints()) }

                val lineHeight = with(density) { 32.dp.roundToPx() }
                val maxWidth = constraints.maxWidth
                val availableWidth = maxWidth - with(density) { 32.dp.roundToPx() }

                // CRITICAL FIX: Only calculate positions ONCE at initialization
                if (!isInitialized && originalPositions == null) {
                    // Measure text to determine if line break is needed
                    val measurePlaceables = FULL_PHRASE.mapIndexed { index, char ->
                        val keyIndex = KEY_LETTER_INDICES.indexOf(index)
                        val isKey = keyIndex != -1

                        val style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = expandedFontSize,
                            fontWeight = if (isKey) FontWeight.Normal else FontWeight.Normal,
                            letterSpacing = 0.sp
                        )

                        val measureResult = textMeasurer.measure(char.toString(), style)
                        measureResult.size
                    }

                    // Calculate total width of the full phrase
                    val totalPhraseWidth = measurePlaceables.sumOf { it.width }

                    // SMART LINE BREAKING LOGIC
                    val lines = mutableListOf<MutableList<Pair<Int, androidx.compose.ui.layout.Placeable>>>()

                    if (totalPhraseWidth <= availableWidth) {
                        // Everything fits on one line
                        val singleLine = mutableListOf<Pair<Int, androidx.compose.ui.layout.Placeable>>()
                        placeables.forEachIndexed { index, placeable ->
                            singleLine.add(index to placeable)
                        }
                        lines.add(singleLine)
                    } else {
                        // Need to break into multiple lines
                        // Force break after "&" for cleaner layout
                        val firstLine = mutableListOf<Pair<Int, androidx.compose.ui.layout.Placeable>>()
                        val secondLine = mutableListOf<Pair<Int, androidx.compose.ui.layout.Placeable>>()

                        // Check if we should break after & or need more complex breaking
                        val ampersandBreakPoint = AMPERSAND_INDEX + 1

                        // Calculate width up to and including &
                        val firstPartWidth = (0 until ampersandBreakPoint).sumOf {
                            measurePlaceables[it].width
                        }

                        if (firstPartWidth <= availableWidth) {
                            // Break after & works well
                            var skipNextSpace = false
                            placeables.forEachIndexed { index, placeable ->
                                when {
                                    index < ampersandBreakPoint -> {
                                        firstLine.add(index to placeable)
                                        if (index == AMPERSAND_INDEX) {
                                            // After ampersand, flag to skip the next space if it exists
                                            skipNextSpace = true
                                        }
                                    }
                                    skipNextSpace && FULL_PHRASE[index] == ' ' -> {
                                        // Skip only the first space after ampersand
                                        skipNextSpace = false
                                    }
                                    else -> {
                                        // Add all other characters including spaces within "Camera Extension"
                                        secondLine.add(index to placeable)
                                    }
                                }
                            }
                            lines.add(firstLine)
                            if (secondLine.isNotEmpty()) {
                                lines.add(secondLine)
                            }
                        } else {
                            // Need to break before & - find natural break points
                            // Try to break at word boundaries
                            val words = mutableListOf<MutableList<Pair<Int, androidx.compose.ui.layout.Placeable>>>()
                            var currentWord = mutableListOf<Pair<Int, androidx.compose.ui.layout.Placeable>>()

                            placeables.forEachIndexed { index, placeable ->
                                if (FULL_PHRASE[index] == ' ') {
                                    if (currentWord.isNotEmpty()) {
                                        words.add(currentWord)
                                        currentWord = mutableListOf()
                                    }
                                    words.add(mutableListOf(index to placeable)) // Add space as separate "word"
                                } else {
                                    currentWord.add(index to placeable)
                                }
                            }
                            if (currentWord.isNotEmpty()) {
                                words.add(currentWord)
                            }

                            // Now arrange words into lines, preferring to break after &
                            var currentLine = mutableListOf<Pair<Int, androidx.compose.ui.layout.Placeable>>()
                            var currentLineWidth = 0

                            words.forEach { word ->
                                val wordWidth = word.sumOf { it.second.width }
                                val firstCharIndex = word.firstOrNull()?.first ?: 0

                                // Check if this word contains or is after the &
                                val isAfterAmpersand = firstCharIndex > AMPERSAND_INDEX
                                val containsAmpersand = word.any { it.first == AMPERSAND_INDEX }

                                if (currentLineWidth + wordWidth > availableWidth && currentLine.isNotEmpty()) {
                                    lines.add(currentLine)
                                    currentLine = mutableListOf()
                                    currentLineWidth = 0
                                } else if (containsAmpersand && currentLine.isNotEmpty()) {
                                    // Add & to current line and start new line after
                                    currentLine.addAll(word)
                                    lines.add(currentLine)
                                    currentLine = mutableListOf()
                                    currentLineWidth = 0
                                    return@forEach
                                }

                                if (isAfterAmpersand && currentLine.isEmpty() && lines.isNotEmpty()) {
                                    // Start new line after &
                                    currentLine.addAll(word)
                                    currentLineWidth = wordWidth
                                } else {
                                    currentLine.addAll(word)
                                    currentLineWidth += wordWidth
                                }
                            }

                            if (currentLine.isNotEmpty()) {
                                lines.add(currentLine)
                            }
                        }
                    }

                    // Calculate positions based on the line arrangement
                    val calculatedPositions = mutableListOf<LetterPosition>()
                    var currentY = with(density) { 15.dp.roundToPx() }

                    lines.forEach { line ->
                        val totalLineWidth = line.sumOf { it.second.width }
                        var currentX = (maxWidth - totalLineWidth) / 2 // Center each line

                        line.forEach { (index, placeable) ->
                            // Store position for this index (some indices might be skipped for spaces)
                            while (calculatedPositions.size < index) {
                                calculatedPositions.add(LetterPosition(0, 0, 0, 0))
                            }
                            calculatedPositions.add(
                                LetterPosition(currentX, currentY, placeable.width, placeable.height)
                            )
                            currentX += placeable.width
                        }
                        currentY += lineHeight
                    }

                    // Fill remaining positions
                    while (calculatedPositions.size < placeables.size) {
                        calculatedPositions.add(LetterPosition(0, 0, 0, 0))
                    }

                    originalTotalHeight = currentY
                    originalPositions = calculatedPositions
                    isInitialized = true
                }

                // Use the stored positions - never recalculate
                val positions = originalPositions ?: List(placeables.size) {
                    LetterPosition(0, 0, 0, 0)
                }
                val calculatedTotalHeight = originalTotalHeight

                val keyPlaceables = KEY_LETTER_INDICES.map { placeables[it] }
                val aliceTotalWidth = aliceMeasureResult.size.width
                val aliceStartX = (maxWidth - aliceTotalWidth) / 2
                val aliceY = with(density) { 16.dp.roundToPx() }

                val aliceXPositions = (0 until 5).map { charIndex ->
                    val boundingBox = aliceMeasureResult.getBoundingBox(charIndex)
                    aliceStartX + boundingBox.left.roundToInt()
                }

                val finalPlacements = mutableListOf<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()

                placeables.forEachIndexed { index, placeable ->
                    val keyIndex = KEY_LETTER_INDICES.indexOf(index)
                    val isKey = keyIndex != -1

                    val origPos = positions.getOrNull(index)
                    val origX = origPos?.x ?: 0
                    val origY = origPos?.y ?: 0

                    val finalX: Int
                    val finalY: Int

                    if (isKey) {
                        // Use cinematic easing for position transitions
                        val positionProgress = CinematicEasing.transform(progress)
                        val targetX = aliceXPositions.getOrElse(keyIndex) { origX }

                        // Add subtle arc motion for Y (creates a gentle curve)
                        val arcOffset = (1f - (2f * progress - 1f).pow(2)) * 10

                        finalX = lerp(origX.toFloat(), targetX.toFloat(), positionProgress).roundToInt()
                        finalY = (lerp(origY.toFloat(), aliceY.toFloat(), positionProgress) - arcOffset).roundToInt()
                    } else {
                        // Non-key letters stay at their original positions
                        finalX = origX
                        finalY = origY
                    }

                    finalPlacements.add(Triple(placeable, finalX, finalY))
                }

                val expandedHeightPx = calculatedTotalHeight + with(density) { 20.dp.roundToPx() }
                val collapsedHeightPx = aliceY + keyPlaceables.maxOf { it.height } + with(density) { 16.dp.roundToPx() }
                val totalHeight = lerp(expandedHeightPx.toFloat(), collapsedHeightPx.toFloat(), progress).roundToInt()

                layout(maxWidth, totalHeight) {
                    finalPlacements.forEach { (placeable, x, y) ->
                        placeable.place(x, y)
                    }
                }
            }
        }

        // Cinematic subtitle with scale and fade
        Text(
            text = "Your intelligent camera companion",
            style = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 0.5.sp // Subtle letter spacing
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .alpha(subtitleAlpha.value)
                .scale(subtitleScale.value)
                .padding(top = 8.dp)
        )
    }
}