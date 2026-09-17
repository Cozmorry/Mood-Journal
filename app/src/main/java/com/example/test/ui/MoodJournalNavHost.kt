package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.test.repository.JournalRepository
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.trend.MoodTrendScreen

object Routes {
    const val ENTRY_LIST = "entryList"
    const val ENTRY_EDITOR = "entryEditor"
    const val ENTRY_EDITOR_ARG_ID = "entryId"
    const val ENTRY_EDITOR_NEW = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=0"
    const val MOOD_TREND = "moodTrend"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
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
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = { navController.navigate(Routes.MOOD_TREND) },
            )
        }
        composable(
            route = "${Routes.ENTRY_EDITOR}?${Routes.ENTRY_EDITOR_ARG_ID}={${Routes.ENTRY_EDITOR_ARG_ID}}",
            arguments = listOf(
                navArgument(Routes.ENTRY_EDITOR_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong(Routes.ENTRY_EDITOR_ARG_ID) ?: 0L
            EntryEditorScreen(
                repository = repository,
                entryId = entryId,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.MOOD_TREND) {
            MoodTrendScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
