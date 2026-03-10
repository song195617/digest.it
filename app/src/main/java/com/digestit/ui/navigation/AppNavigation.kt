package com.digestit.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.material3.Scaffold
import com.digestit.ui.chat.ChatScreen
import com.digestit.ui.home.HomeScreen
import com.digestit.ui.player.GlobalAudioPlayerBar
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
    object Transcript : Screen("transcript/{episodeId}?timestampMs={timestampMs}") {
        fun createRoute(episodeId: String, timestampMs: Long? = null): String {
            return if (timestampMs != null) {
                "transcript/$episodeId?timestampMs=$timestampMs"
            } else {
                "transcript/$episodeId?timestampMs=-1"
            }
        }
    }
    object Chat : Screen("chat/{episodeId}") {
        fun createRoute(episodeId: String) = "chat/$episodeId"
    }
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Settings.route) {
                GlobalAudioPlayerBar()
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToProcessing = { jobId ->
                        navController.navigate(Screen.Processing.createRoute(jobId))
                    },
                    onNavigateToSummary = { episodeId ->
                        navController.navigate(Screen.Summary.createRoute(episodeId))
                    },
                    onNavigateToTranscript = { episodeId, timestampMs ->
                        navController.navigate(Screen.Transcript.createRoute(episodeId, timestampMs))
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
                    onNavigateToProcessing = { nextJobId ->
                        navController.navigate(Screen.Processing.createRoute(nextJobId)) {
                            popUpTo(Screen.Processing.route) { inclusive = true }
                        }
                    },
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Summary.route) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
                SummaryScreen(
                    episodeId = episodeId,
                    onNavigateToTranscript = { timestampMs ->
                        navController.navigate(Screen.Transcript.createRoute(episodeId, timestampMs))
                    },
                    onNavigateToChat = {
                        navController.navigate(Screen.Chat.createRoute(episodeId))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Transcript.route,
                arguments = listOf(navArgument("timestampMs") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
                val timestampArg = backStackEntry.arguments?.getLong("timestampMs") ?: -1L
                TranscriptScreen(
                    episodeId = episodeId,
                    initialTimestampMs = timestampArg.takeIf { it >= 0L },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Chat.route) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
                ChatScreen(
                    episodeId = episodeId,
                    onNavigateToTranscript = { timestampMs ->
                        navController.navigate(Screen.Transcript.createRoute(episodeId, timestampMs))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
