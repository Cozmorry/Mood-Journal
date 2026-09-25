package com.example.test.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.test.repository.JournalRepository
import com.example.test.repository.PhotoStorage
import com.example.test.ui.editor.EntryEditorScreen
import com.example.test.ui.list.EntryListScreen
import com.example.test.ui.trend.MoodTrendScreen
import com.example.test.ui.viewer.PhotoViewerScreen

object Routes {
    const val ENTRY_LIST = "entryList"
    const val ENTRY_EDITOR = "entryEditor"
    const val ENTRY_EDITOR_ARG_ID = "entryId"
    const val ENTRY_EDITOR_NEW = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=0"
    const val MOOD_TREND = "moodTrend"
    const val PHOTO_VIEWER = "photoViewer"
    const val PHOTO_VIEWER_ARG_PATH = "photoPath"
    fun entryEditorEdit(id: Long) = "$ENTRY_EDITOR?$ENTRY_EDITOR_ARG_ID=$id"
    fun photoViewer(photoPath: String) = "$PHOTO_VIEWER/$photoPath"
}

@Composable
fun MoodJournalNavHost(
    repository: JournalRepository,
    photoStorage: PhotoStorage,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.ENTRY_LIST) {
        composable(Routes.ENTRY_LIST) {
            EntryListScreen(
                repository = repository,
                photoStorage = photoStorage,
                onAddEntry = { navController.navigate(Routes.ENTRY_EDITOR_NEW) },
                onEntryClick = { id -> navController.navigate(Routes.entryEditorEdit(id)) },
                onShowTrend = { navController.navigate(Routes.MOOD_TREND) },
                onPhotoClick = { path -> navController.navigate(Routes.photoViewer(path)) },
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
                photoStorage = photoStorage,
                entryId = entryId,
                onDone = { navController.popBackStack(Routes.ENTRY_LIST, inclusive = false) },
                onViewPhoto = { path -> navController.navigate(Routes.photoViewer(path)) },
            )
        }
        composable(Routes.MOOD_TREND) {
            MoodTrendScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.PHOTO_VIEWER}/{${Routes.PHOTO_VIEWER_ARG_PATH}}",
            arguments = listOf(navArgument(Routes.PHOTO_VIEWER_ARG_PATH) { type = NavType.StringType }),
        ) { backStackEntry ->
            val photoPath = backStackEntry.arguments?.getString(Routes.PHOTO_VIEWER_ARG_PATH).orEmpty()
            PhotoViewerScreen(
                photoStorage = photoStorage,
                photoPath = photoPath,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
