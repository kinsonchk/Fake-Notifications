package com.codejam.taptation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.codejam.taptation.ui.TaptationApp
import com.codejam.taptation.ui.theme.TaptationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaptationTheme {
                // Debug and Notifications tabs only exist in debug builds.
                TaptationApp(debug = BuildConfig.DEBUG)
            }
        }
    }
}
