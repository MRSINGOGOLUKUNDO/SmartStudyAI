package com.zut.smartstudy.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zut.smartstudy.ui.theme.ButtonBlue
import com.zut.smartstudy.ui.theme.ElectricCyan
import com.zut.smartstudy.ui.theme.Midnight
import com.zut.smartstudy.ui.theme.SoftWhite
import com.zut.smartstudy.ui.theme.StudyBlue
import kotlinx.coroutines.delay

private val PressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

/**
 * Soft, cushioned press: compresses to ~97% while held, then springs back.
 * Use with the same [interactionSource] that you pass to the clickable component.
 */
fun Modifier.softPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "softPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Blue button with a subtle brighter-blue highlight that sweeps across it.
 * Cycle: 5 s total, ~2 s sweep, then 3 s at the normal colour.
 * Press: shrinks to 97 %, shadow tightens, ripple, spring back.
 *
 * [shineDelayMs] staggers the sweep so two buttons never shine at the same moment.
 */
@Composable
fun ShineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shine: Boolean = true,
    shineDelayMs: Int = 0,
    outlined: Boolean = false,
    baseColor: Color = ButtonBlue
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = PressSpring,
        label = "buttonScale"
    )
    val restingElevation = if (enabled && !outlined) 8.dp else 0.dp
    val elevation by animateDpAsState(
        targetValue = if (pressed) restingElevation / 4 else restingElevation,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "buttonShadow"
    )

    val transition = rememberInfiniteTransition(label = "shine")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5000
                1f at 2000 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(shineDelayMs)
        ),
        label = "shineProgress"
    )

    val shineOn = shine && enabled && !outlined
    val highlight = lerp(baseColor, Color.White, 0.25f).copy(alpha = 0.75f)
    val contentColor = if (enabled) SoftWhite else SoftWhite.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape, ambientColor = baseColor, spotColor = baseColor)
            .clip(shape)
            .drawBehind {
                val fill = when {
                    outlined -> Color.Transparent
                    enabled -> baseColor
                    else -> baseColor.copy(alpha = 0.35f)
                }
                drawRect(fill)
                if (shineOn) {
                    val band = size.width * 0.45f
                    val x = -band + (size.width + band) * progress
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, highlight, Color.Transparent),
                            startX = x,
                            endX = x + band
                        ),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                }
            }
            .then(
                if (outlined) {
                    Modifier.border(
                        1.dp,
                        ElectricCyan.copy(alpha = if (enabled) 0.65f else 0.25f),
                        shape
                    )
                } else Modifier
            )
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

/** Study-blue card colours, slightly translucent so photography shows through softly. */
@Composable
fun studyCardColors() = CardDefaults.cardColors(
    containerColor = StudyBlue.copy(alpha = 0.88f),
    contentColor = SoftWhite
)

/**
 * Dark premium backdrop. With [showPhotos] it shows a slow crossfading slideshow of
 * drawables named study_1 … study_10 (if present) under a dark overlay so text stays readable.
 * If no photos have been added yet it falls back to a navy gradient, so nothing breaks.
 */
@Composable
fun StudyBackground(
    showPhotos: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Midnight)) {
        if (showPhotos) PhotoSlideshow()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Midnight.copy(alpha = if (showPhotos) 0.72f else 0f),
                            Midnight.copy(alpha = if (showPhotos) 0.88f else 0f),
                            Midnight.copy(alpha = if (showPhotos) 0.96f else 0f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(900f, 0f),
                        radius = 1200f
                    )
                )
        )
        content()
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun PhotoSlideshow() {
    val context = LocalContext.current
    val ids = remember {
        (1..10)
            .map { context.resources.getIdentifier("study_$it", "drawable", context.packageName) }
            .filter { it != 0 }
    }
    if (ids.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(ids) {
        while (ids.size > 1) {
            delay(7000)
            index = (index + 1) % ids.size
        }
    }

    Crossfade(
        targetState = index,
        animationSpec = tween(durationMillis = 1800),
        label = "slideshow"
    ) { i ->
        Image(
            painter = painterResource(ids[i]),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
