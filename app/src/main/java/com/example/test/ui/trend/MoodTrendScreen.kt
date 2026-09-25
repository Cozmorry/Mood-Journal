package com.example.test.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.data.Mood
import com.example.test.repository.JournalRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Y_AXIS_LABEL_WIDTH = 32.dp
private const val SCORE_BANDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTrendScreen(
    repository: JournalRepository,
    onBack: () -> Unit,
    viewModel: MoodTrendViewModel = viewModel(
        factory = viewModelFactory { initializer { MoodTrendViewModel(repository) } },
    ),
) {
    val points by viewModel.points.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood trend") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (points.size < 2) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Add a few more entries to see your trend.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            val primaryColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            val surfaceColor = MaterialTheme.colorScheme.surface
            val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.width(Y_AXIS_LABEL_WIDTH).fillMaxHeight(),
                    ) {
                        Mood.entries.forEach { mood ->
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(mood.emoji, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 8.dp),
                    ) {
                        val minT = points.first().timestampMillis
                        val spanT = (points.last().timestampMillis - minT).coerceAtLeast(1L)
                        val maxScore = SCORE_BANDS.toFloat()

                        val offsets = points.map { point ->
                            val xFraction = (point.timestampMillis - minT).toFloat() / spanT.toFloat()
                            val yFraction = point.score / maxScore
                            Offset(
                                x = xFraction * size.width,
                                y = size.height - (yFraction * size.height),
                            )
                        }

                        for (band in 0..SCORE_BANDS) {
                            val y = size.height * band / SCORE_BANDS
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }

                        val linePath = Path().apply {
                            moveTo(offsets.first().x, offsets.first().y)
                            for (i in 1 until offsets.size) lineTo(offsets[i].x, offsets[i].y)
                        }
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(offsets.last().x, size.height)
                            lineTo(offsets.first().x, size.height)
                            close()
                        }
                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0f)),
                            startY = offsets.minOf { it.y },
                            endY = size.height,
                        )
                        drawPath(fillPath, brush = fillBrush)
                        drawPath(
                            linePath,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        offsets.forEach { offset ->
                            drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = offset)
                            drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = offset)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Spacer(modifier = Modifier.width(Y_AXIS_LABEL_WIDTH + 8.dp))
                    val midPoint = points[points.size / 2]
                    Text(
                        dateFormat.format(Date(points.first().timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        dateFormat.format(Date(midPoint.timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        dateFormat.format(Date(points.last().timestampMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
