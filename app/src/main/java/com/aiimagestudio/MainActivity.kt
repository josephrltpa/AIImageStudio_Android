package com.aiimagestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aiimagestudio.presentation.navigation.AppNavigation
import com.aiimagestudio.presentation.theme.AIImageStudioTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All screens are Composables reached through
 * [AppNavigation]; there is no other entry point into the app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIImageStudioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
