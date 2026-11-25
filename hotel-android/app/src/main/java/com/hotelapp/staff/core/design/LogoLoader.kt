package com.hotelapp.staff.core.design

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember(context) { isAnimatorScaleZero(context) }
}

private fun isAnimatorScaleZero(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

@Composable
fun AnimatedHotelLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    skipEntrance: Boolean = false,
    animate: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val shouldAnimate = animate && !reducedMotion

    val roofProgress = remember(skipEntrance, shouldAnimate) {
        Animatable(if (skipEntrance || !shouldAnimate) 1f else 0f)
    }
    val monogramProgress = remember(skipEntrance, shouldAnimate) {
        Animatable(if (skipEntrance || !shouldAnimate) 1f else 0f)
    }
    val baselineProgress = remember(skipEntrance, shouldAnimate) {
        Animatable(if (skipEntrance || !shouldAnimate) 1f else 0f)
    }
    val settleScale = remember(skipEntrance, shouldAnimate) {
        Animatable(if (skipEntrance || !shouldAnimate) 1f else 0.94f)
    }
    val settleAlpha = remember(skipEntrance, shouldAnimate) {
        Animatable(if (skipEntrance || !shouldAnimate) 1f else 0f)
    }

    LaunchedEffect(skipEntrance, shouldAnimate) {
        if (!skipEntrance && shouldAnimate) {
            launch {
                settleAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
            }
            launch {
                settleScale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            }
            launch {
                roofProgress.animateTo(1f, tween(380, delayMillis = 60, easing = FastOutSlowInEasing))
            }
            launch {
                monogramProgress.animateTo(1f, tween(550, delayMillis = 200, easing = FastOutSlowInEasing))
            }
            launch {
                baselineProgress.animateTo(1f, tween(320, delayMillis = 560, easing = FastOutSlowInEasing))
            }
        }
    }

    val breatheAlpha: Float
    val sheenFraction: Float
    if (shouldAnimate) {
        val infinite = rememberInfiniteTransition(label = "hotel_logo_motion")
        val breath by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.87f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "logo_breath",
        )
        val sheen by infinite.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4200
                    -0.5f at 0 using LinearEasing
                    -0.5f at 900 using LinearEasing
                    1.6f at 2400 using FastOutSlowInEasing
                    1.6f at 4200 using LinearEasing
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "logo_sheen",
        )
        breatheAlpha = breath
        sheenFraction = sheen
    } else {
        breatheAlpha = 1f
        sheenFraction = -1f
    }

    val pathMeasure = remember { PathMeasure() }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = settleScale.value
                scaleY = settleScale.value
                alpha = settleAlpha.value * breatheAlpha
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        val s = minOf(w, h) / 64f
        val corner = CornerRadius(15f * s, 15f * s)

        // Tile background & border
        drawRoundRect(
            color = Color(0xFF0B211A),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0xFFD9B572),
            topLeft = Offset(1.5f * s, 1.5f * s),
            size = Size(61f * s, 61f * s),
            cornerRadius = CornerRadius(13.5f * s, 13.5f * s),
            style = Stroke(width = 3f * s),
        )

        // Roofline
        val fullRoof = Path().apply {
            moveTo(13f * s, 27f * s)
            lineTo(32f * s, 13f * s)
            lineTo(51f * s, 27f * s)
        }
        if (roofProgress.value > 0f) {
            val seg = Path()
            pathMeasure.setPath(fullRoof, false)
            pathMeasure.getSegment(0f, pathMeasure.length * roofProgress.value, seg, true)
            drawPath(
                path = seg,
                color = Color(0xFFD9B572),
                style = Stroke(width = 3.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // S-Monogram
        val fullMonogram = Path().apply {
            moveTo(42f * s, 28f * s)
            cubicTo(42f * s, 22f * s, 22f * s, 22f * s, 22f * s, 29f * s)
            cubicTo(22f * s, 37f * s, 42f * s, 33f * s, 42f * s, 42f * s)
            cubicTo(42f * s, 50f * s, 22f * s, 50f * s, 22f * s, 43f * s)
        }
        if (monogramProgress.value > 0f) {
            val seg = Path()
            pathMeasure.setPath(fullMonogram, false)
            pathMeasure.getSegment(0f, pathMeasure.length * monogramProgress.value, seg, true)
            drawPath(
                path = seg,
                color = Color(0xFFFFFDF7),
                style = Stroke(width = 4f * s, cap = StrokeCap.Round),
            )
        }

        // Baseline
        val baselineP = baselineProgress.value
        if (baselineP > 0f) {
            val startX = 16f * s
            val endX = startX + (32f * s * baselineP)
            drawLine(
                color = Color(0xFFD9B572),
                start = Offset(startX, 51f * s),
                end = Offset(endX, 51f * s),
                strokeWidth = 3f * s,
                cap = StrokeCap.Round,
            )
        }

        // Periodic champagne sheen clipped inside tile
        if (sheenFraction in -0.45f..1.55f) {
            val clip = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        cornerRadius = corner,
                    ),
                )
            }
            clipPath(clip) {
                val bandWidth = w * 0.45f
                val centerX = w * sheenFraction
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x18FFFDF7),
                            Color(0x47D9B572),
                            Color(0x18FFFDF7),
                            Color.Transparent,
                        ),
                        start = Offset(centerX - bandWidth, 0f),
                        end = Offset(centerX + bandWidth, h),
                    ),
                )
            }
        }
    }
}

@Composable
fun HotelLogoLoader(
    modifier: Modifier = Modifier,
    message: String? = null,
    hotelName: String? = null,
    fullScreen: Boolean = false,
    delayMs: Long = if (fullScreen) 0L else 180L,
) {
    val colors = HotelTheme.colors
    var visible by remember(delayMs) { mutableStateOf(delayMs <= 0L) }

    LaunchedEffect(delayMs) {
        if (delayMs > 0L) {
            delay(delayMs)
            visible = true
        }
    }

    val statusText = message ?: "Loading"
    val containerModifier = if (fullScreen) {
        modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(24.dp)
    }

    Box(
        modifier = containerModifier.semantics {
            contentDescription = statusText
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AnimatedHotelLogoMark(
                    size = if (fullScreen) 84.dp else 52.dp,
                )
                if (fullScreen && !hotelName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = hotelName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(if (fullScreen) 8.dp else 12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
