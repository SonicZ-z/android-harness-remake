package com.androidharness.app.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/** A restrained gradient derived from the active palette, not fixed brand colors. */
@Composable
fun auroraBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        val base = scheme.background
        val cool = scheme.primary.copy(alpha = 0.10f).compositeOver(base)
        val warm = scheme.tertiary.copy(alpha = 0.08f).compositeOver(base)
        Brush.linearGradient(
            colors = listOf(base, cool, base, warm, base),
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }
}

/** A rounded, gently tinted panel with a gradient edge and restrained size motion. */
@Composable
fun AuroraPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val fill = remember(scheme) {
        val base = scheme.surfaceContainerLow
        val cool = scheme.primary.copy(alpha = 0.12f).compositeOver(base)
        val warm = scheme.tertiary.copy(alpha = 0.10f).compositeOver(base)
        Brush.linearGradient(listOf(base, cool, scheme.surfaceContainer, warm, base), Offset.Zero, Offset.Infinite)
    }
    val edge = remember(scheme) {
        Brush.linearGradient(
            listOf(
                scheme.primary.copy(alpha = 0.35f),
                scheme.tertiary.copy(alpha = 0.26f),
                scheme.outlineVariant.copy(alpha = 0.48f),
            ),
            Offset.Zero,
            Offset.Infinite,
        )
    }
    Surface(
        modifier = modifier
            .animateContentSize(defaultSpatialSpec())
            .background(fill, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        contentColor = scheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
