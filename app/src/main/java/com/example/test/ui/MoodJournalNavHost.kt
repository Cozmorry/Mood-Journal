package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.test.repository.JournalRepository
import com.example.test.ui.list.EntryListScreen

object Routes {
    const val ENTRY_LIST = "entryList"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                onAddEntry = {},
                onEntryClick = {},
                onShowTrend = {},
            )
        }
    }
}
