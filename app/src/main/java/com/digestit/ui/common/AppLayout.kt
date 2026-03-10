package com.digestit.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWidthSize {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun rememberAppWidthSize(): AppWidthSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 1000 -> AppWidthSize.Expanded
        widthDp >= 600 -> AppWidthSize.Medium
        else -> AppWidthSize.Compact
    }
}

fun AppWidthSize.horizontalPadding(): Dp = when (this) {
    AppWidthSize.Compact -> 20.dp
    AppWidthSize.Medium -> 28.dp
    AppWidthSize.Expanded -> 36.dp
}

fun AppWidthSize.contentSpacing(): Dp = when (this) {
    AppWidthSize.Compact -> 16.dp
    AppWidthSize.Medium -> 20.dp
    AppWidthSize.Expanded -> 24.dp
}

@Composable
fun ScreenContentFrame(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 1120.dp,
    content: @Composable BoxScope.(AppWidthSize) -> Unit,
) {
    val widthSize = rememberAppWidthSize()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.background,
                            colors.surfaceContainerLowest,
                            colors.background,
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .widthIn(max = maxWidth)
                .padding(
                    horizontal = widthSize.horizontalPadding(),
                    vertical = widthSize.contentSpacing(),
                )
        ) {
            content(widthSize)
        }
    }
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    detail: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        eyebrow?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    EditorialCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LabelPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = containerColor.copy(alpha = 0.9f),
                shape = RoundedCornerShape(999.dp),
            )
            .background(
                color = containerColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
fun AdaptiveColumns(
    widthSize: AppWidthSize,
    modifier: Modifier = Modifier,
    leading: @Composable (Modifier) -> Unit,
    trailing: @Composable (Modifier) -> Unit,
) {
    if (widthSize == AppWidthSize.Compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(widthSize.contentSpacing()),
        ) {
            leading(Modifier.fillMaxWidth())
            trailing(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(widthSize.contentSpacing()),
            verticalAlignment = Alignment.Top,
        ) {
            leading(Modifier.weight(0.38f))
            trailing(Modifier.weight(0.62f))
        }
    }
}
