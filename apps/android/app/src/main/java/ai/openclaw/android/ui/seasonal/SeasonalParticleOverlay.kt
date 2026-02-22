package ai.openclaw.android.ui.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 25
private const val BASE_ALPHA = 0.6f

// ── Particle data ──────────────────────────────────────────────────────────

private data class Particle(
    var x: Float,
    var y: Float,
    var size: Float,
    var speed: Float,         // dp/s
    var swayAmplitude: Float, // dp
    var swayFrequency: Float, // rad/s
    var rotation: Float,      // degrees
    var rotationSpeed: Float, // deg/s
    var alpha: Float,
    var alphaSpeed: Float,    // for twinkling
    var phase: Float,         // random offset for sway
    var colorIndex: Int
)

// ── Color palettes ─────────────────────────────────────────────────────────

private val winterColors = listOf(
    Color.White,
    Color(0xFFB3E5FC), // light blue
    Color(0xFFE1F5FE),
)

private val springColors = listOf(
    Color(0xFFFFB7C5), // sakura pink
    Color(0xFFF8BBD0),
    Color(0xFFFFCDD2),
    Color(0xFFFCE4EC),
)

private val summerColors = listOf(
    Color(0xFFFFD54F), // golden
    Color(0xFFFFE082),
    Color(0xFFFFF176),
    Color(0xFFFFECB3),
)

private val autumnColors = listOf(
    Color(0xFFFF8A65), // orange
    Color(0xFFE57373), // red
    Color(0xFFA1887F), // brown
    Color(0xFFFFCC80), // light orange
)

// ── Overlay composable ─────────────────────────────────────────────────────

@Composable
fun SeasonalParticleOverlay(effect: SeasonalEffect, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val particles = remember(effect) {
        mutableStateListOf<Particle>().apply {
            repeat(PARTICLE_COUNT) {
                add(spawnParticle(effect, screenWidthPx, screenHeightPx, randomY = true))
            }
        }
    }

    // Animation loop
    LaunchedEffect(effect) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) 0f else (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                if (dt > 0f && dt < 0.5f) {
                    for (i in particles.indices) {
                        val p = particles[i]
                        updateParticle(p, dt, effect, screenWidthPx, screenHeightPx)
                        particles[i] = p.copy() // trigger recomposition
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        for (p in particles) {
            drawParticle(p, effect)
        }
    }
}

// ── Particle factory ───────────────────────────────────────────────────────

private fun spawnParticle(
    effect: SeasonalEffect,
    screenWidth: Float,
    screenHeight: Float,
    randomY: Boolean = false
): Particle {
    val rng = Random
    val x = rng.nextFloat() * screenWidth
    val y = if (randomY) rng.nextFloat() * screenHeight
            else if (effect == SeasonalEffect.SUMMER) screenHeight + rng.nextFloat() * 40f
            else -rng.nextFloat() * 40f

    return when (effect) {
        SeasonalEffect.WINTER -> Particle(
            x = x, y = y,
            size = 3f + rng.nextFloat() * 5f,
            speed = 30f + rng.nextFloat() * 25f,
            swayAmplitude = 15f + rng.nextFloat() * 20f,
            swayFrequency = 0.5f + rng.nextFloat() * 0.8f,
            rotation = 0f,
            rotationSpeed = 0f,
            alpha = 0.4f + rng.nextFloat() * 0.4f,
            alphaSpeed = 0f,
            phase = rng.nextFloat() * 6.28f,
            colorIndex = rng.nextInt(winterColors.size)
        )
        SeasonalEffect.SPRING -> Particle(
            x = x, y = y,
            size = 5f + rng.nextFloat() * 6f,
            speed = 25f + rng.nextFloat() * 20f,
            swayAmplitude = 25f + rng.nextFloat() * 25f,
            swayFrequency = 0.4f + rng.nextFloat() * 0.6f,
            rotation = rng.nextFloat() * 360f,
            rotationSpeed = 20f + rng.nextFloat() * 40f,
            alpha = 0.4f + rng.nextFloat() * 0.35f,
            alphaSpeed = 0f,
            phase = rng.nextFloat() * 6.28f,
            colorIndex = rng.nextInt(springColors.size)
        )
        SeasonalEffect.SUMMER -> Particle(
            x = x, y = y,
            size = 2f + rng.nextFloat() * 4f,
            speed = 15f + rng.nextFloat() * 15f,  // upward
            swayAmplitude = 10f + rng.nextFloat() * 10f,
            swayFrequency = 0.8f + rng.nextFloat() * 1.2f,
            rotation = 0f,
            rotationSpeed = 0f,
            alpha = 0.3f + rng.nextFloat() * 0.5f,
            alphaSpeed = 1.5f + rng.nextFloat() * 2f,  // twinkle
            phase = rng.nextFloat() * 6.28f,
            colorIndex = rng.nextInt(summerColors.size)
        )
        SeasonalEffect.AUTUMN -> Particle(
            x = x, y = y,
            size = 6f + rng.nextFloat() * 7f,
            speed = 35f + rng.nextFloat() * 25f,
            swayAmplitude = 35f + rng.nextFloat() * 30f,
            swayFrequency = 0.3f + rng.nextFloat() * 0.5f,
            rotation = rng.nextFloat() * 360f,
            rotationSpeed = 30f + rng.nextFloat() * 60f,
            alpha = 0.4f + rng.nextFloat() * 0.35f,
            alphaSpeed = 0f,
            phase = rng.nextFloat() * 6.28f,
            colorIndex = rng.nextInt(autumnColors.size)
        )
        else -> Particle(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0)
    }
}

// ── Particle update ────────────────────────────────────────────────────────

private fun updateParticle(
    p: Particle,
    dt: Float,
    effect: SeasonalEffect,
    screenWidth: Float,
    screenHeight: Float
) {
    val sway = sin(p.phase + p.swayFrequency * 6.28f) * p.swayAmplitude * dt
    p.x += sway
    p.phase += p.swayFrequency * dt

    if (effect == SeasonalEffect.SUMMER) {
        p.y -= p.speed * dt  // upward
        // Twinkle alpha
        p.alpha = (0.3f + 0.5f * ((sin(p.phase * p.alphaSpeed) + 1f) / 2f))
        if (p.y < -20f) {
            val fresh = spawnParticle(effect, screenWidth, screenHeight, randomY = false)
            p.x = fresh.x; p.y = fresh.y; p.size = fresh.size; p.phase = fresh.phase
            p.alpha = fresh.alpha; p.colorIndex = fresh.colorIndex
        }
    } else {
        p.y += p.speed * dt  // downward
        if (p.y > screenHeight + 20f) {
            val fresh = spawnParticle(effect, screenWidth, screenHeight, randomY = false)
            p.x = fresh.x; p.y = fresh.y; p.size = fresh.size; p.phase = fresh.phase
            p.alpha = fresh.alpha; p.colorIndex = fresh.colorIndex
        }
    }

    p.rotation += p.rotationSpeed * dt

    // Wrap horizontally
    if (p.x < -30f) p.x = screenWidth + 20f
    if (p.x > screenWidth + 30f) p.x = -20f
}

// ── Particle drawing ───────────────────────────────────────────────────────

private fun DrawScope.drawParticle(p: Particle, effect: SeasonalEffect) {
    val palette = when (effect) {
        SeasonalEffect.WINTER -> winterColors
        SeasonalEffect.SPRING -> springColors
        SeasonalEffect.SUMMER -> summerColors
        SeasonalEffect.AUTUMN -> autumnColors
        else -> return
    }
    val color = palette[p.colorIndex % palette.size].copy(alpha = p.alpha)

    when (effect) {
        SeasonalEffect.WINTER -> {
            // Simple circles for snowflakes
            drawCircle(color = color, radius = p.size, center = Offset(p.x, p.y))
        }
        SeasonalEffect.SPRING -> {
            // Rotated ovals for cherry blossom petals
            withTransform({
                rotate(degrees = p.rotation, pivot = Offset(p.x, p.y))
            }) {
                drawOval(
                    color = color,
                    topLeft = Offset(p.x - p.size, p.y - p.size * 0.6f),
                    size = Size(p.size * 2f, p.size * 1.2f)
                )
            }
        }
        SeasonalEffect.SUMMER -> {
            // Small glowing circles for sparkles
            drawCircle(color = color, radius = p.size, center = Offset(p.x, p.y))
            // Outer glow
            drawCircle(
                color = color.copy(alpha = p.alpha * 0.3f),
                radius = p.size * 1.8f,
                center = Offset(p.x, p.y)
            )
        }
        SeasonalEffect.AUTUMN -> {
            // Leaf-like shapes: rotated diamond
            withTransform({
                rotate(degrees = p.rotation, pivot = Offset(p.x, p.y))
            }) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p.x, p.y - p.size)
                    quadraticTo(p.x + p.size * 0.8f, p.y - p.size * 0.2f, p.x, p.y + p.size)
                    quadraticTo(p.x - p.size * 0.8f, p.y - p.size * 0.2f, p.x, p.y - p.size)
                    close()
                }
                drawPath(path, color = color)
            }
        }
        else -> {}
    }
}
