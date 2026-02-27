package com.digestit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.digestit.ui.chat.ChatScreen
import com.digestit.ui.home.HomeScreen
import com.digestit.ui.processing.ProcessingScreen
import com.digestit.ui.settings.SettingsScreen
import com.digestit.ui.summary.SummaryScreen
import com.digestit.ui.transcript.TranscriptScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Processing : Screen("processing/{jobId}") {
        fun createRoute(jobId: String) = "processing/$jobId"
    }
    object Summary : Screen("summary/{episodeId}") {
        fun createRoute(episodeId: String) = "summary/$episodeId"
    }
    object Transcript : Screen("transcript/{episodeId}") {
        fun createRoute(episodeId: String) = "transcript/$episodeId"
    }
    object Chat : Screen("chat/{episodeId}") {
        fun createRoute(episodeId: String) = "chat/$episodeId"
    }
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProcessing = { jobId ->
                    navController.navigate(Screen.Processing.createRoute(jobId))
                },
                onNavigateToSummary = { episodeId ->
                    navController.navigate(Screen.Summary.createRoute(episodeId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Processing.route) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
            ProcessingScreen(
                jobId = jobId,
                onProcessingComplete = { episodeId ->
                    navController.navigate(Screen.Summary.createRoute(episodeId)) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Summary.route) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
            SummaryScreen(
                episodeId = episodeId,
                onNavigateToTranscript = {
                    navController.navigate(Screen.Transcript.createRoute(episodeId))
                },
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.createRoute(episodeId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Transcript.route) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
            TranscriptScreen(
                episodeId = episodeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Chat.route) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
            ChatScreen(
                episodeId = episodeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
