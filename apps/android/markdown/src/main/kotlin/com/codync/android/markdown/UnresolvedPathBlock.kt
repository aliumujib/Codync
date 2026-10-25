package com.codync.android.markdown

import com.codync.android.design.Palette
import com.codync.android.design.Symbol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UnresolvedPathBlock(raw: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = MarkdownRadiusPercent.block),
        color = Palette.surface,
        border = BorderStroke(1.dp, Palette.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val markerColor = Palette.secondary
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = markerColor,
                            cornerRadius = CornerRadius(
                                size.minDimension * MarkdownRadiusPercent.chipMarker / 100f,
                            ),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f)),
                            ),
                        )
                    },
            )
            Text(
                text = raw,
                modifier = Modifier.weight(1f),
                color = Palette.text,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    lineHeight = 18.sp,
                ),
            )
            val copyInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(COPY_TARGET_SIZE)
                    .markdownPressFeedback(copyInteractionSource)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        interactionSource = copyInteractionSource,
                        indication = null,
                        onClick = onCopy,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Symbol("doc.on.doc", size = COPY_GLYPH_SIZE, tint = Palette.secondary, contentDescription = "Copy path")
            }
        }
    }
}

private val COPY_TARGET_SIZE = 30.dp
private val COPY_GLYPH_SIZE = 14.dp
