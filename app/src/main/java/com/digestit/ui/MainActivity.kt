package com.digestit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.digestit.ui.navigation.AppNavigation
import com.digestit.ui.theme.DigestItTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigestItTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
