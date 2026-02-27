package com.digestit.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.digestit.ui.theme.DigestItTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        viewModel.onUrlReceived(sharedText)

        setContent {
            DigestItTheme {
                ShareBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    onJobStarted = { finish() }
                )
            }
        }
    }
}
