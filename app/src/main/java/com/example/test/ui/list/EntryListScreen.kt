package com.example.test.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test.repository.JournalRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    repository: JournalRepository,
    onAddEntry: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onShowTrend: () -> Unit,
    viewModel: EntryListViewModel = viewModel(
        factory = viewModelFactory { initializer { EntryListViewModel(repository) } },
    ),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Journal") },
                actions = {
                    IconButton(onClick = onShowTrend) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Mood trend")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "New entry")
            }
        },
    ) { padding ->
        val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.text.take(60)) },
                    supportingContent = { Text(dateFormat.format(Date(entry.createdAt))) },
                    leadingContent = { Text(entry.mood.emoji) },
                    modifier = Modifier.clickable { onEntryClick(entry.id) },
                )
            }
        }
    }
}
