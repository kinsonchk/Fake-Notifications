package com.codejam.taptation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.Appearance
import com.codejam.taptation.ui.theme.Night
import com.codejam.taptation.ui.TaptationApp
import com.codejam.taptation.ui.theme.TaptationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by (application as TaptationApplication).appearance.mode.collectAsStateWithLifecycle()
            if (appearance == null) {
                Box(Modifier.fillMaxSize().background(Night))
            } else {
                val dark = appearance != Appearance.Light
                SideEffect {
                    WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                TaptationTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        // Debug and Notifications tabs only exist in debug builds.
                        TaptationApp(debug = BuildConfig.DEBUG)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as TaptationApplication).feedback.setForeground(true)
    }

    override fun onPause() {
        (application as TaptationApplication).feedback.setForeground(false)
        super.onPause()
    }
}
