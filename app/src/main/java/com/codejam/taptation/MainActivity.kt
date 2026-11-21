package com.codejam.taptation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.codejam.taptation.debug.DebugScreen
import com.codejam.taptation.ui.theme.TaptationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaptationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (BuildConfig.DEBUG) {
                        // TODO(Person 3): replace with real app navigation; keep DebugScreen reachable.
                        DebugScreen(contentPadding = innerPadding)
                    } else {
                        Text("Taptation")
                    }
                }
            }
        }
    }
}

