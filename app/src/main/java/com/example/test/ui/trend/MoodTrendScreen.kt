package com.example.test.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.repository.JournalRepository

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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (points.size < 2) {
            Text(
                "Add a few more entries to see your trend.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                val minX = points.first().timestampMillis.toFloat()
                val maxX = points.last().timestampMillis.toFloat()
                val spanX = (maxX - minX).coerceAtLeast(1f)
                val maxScore = 5f

                val offsets = points.map { point ->
                    val xFraction = (point.timestampMillis - minX) / spanX
                    val yFraction = point.score / maxScore
                    Offset(
                        x = xFraction * size.width,
                        y = size.height - (yFraction * size.height),
                    )
                }

                for (i in 0 until offsets.size - 1) {
                    drawLine(
                        color = Color(0xFF6750A4),
                        start = offsets[i],
                        end = offsets[i + 1],
                        strokeWidth = 4f,
                    )
                }
                offsets.forEach { offset ->
                    drawCircle(color = Color(0xFF6750A4), radius = 6f, center = offset)
                }
            }
        }
    }
}
