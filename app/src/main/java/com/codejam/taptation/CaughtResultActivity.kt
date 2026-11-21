package com.codejam.taptation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.feedback.CaughtFeedbackManager
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.TapOutcome
import com.codejam.taptation.ui.theme.TaptationTheme

/** In-app details destination opened from the catch overlay or its fallback notification. */
class CaughtResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaughtFeedbackManager.clearPendingResult(this)
        val game = (application as TaptationApplication).game
        setContent {
            TaptationTheme {
                CaughtResultContent(game, onDone = { finish() })
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, CaughtResultActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun CaughtResultContent(game: GameRepository, onDone: () -> Unit) {
    val state by game.state.collectAsStateWithLifecycle()
    val currentState = state
    val catch = currentState?.taps?.lastOrNull { it.outcome == TapOutcome.Caught }
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("You got caught", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text(
            when {
                currentState == null -> "Loading your saved result…"
                catch == null -> "The catch was saved. Check your current tree health below."
                else -> "You lost ${catch.damage} health. Your tree is now at ${currentState.health}/100."
            },
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        currentState?.let { Text("Catches so far: ${it.totalCatches}") }
        Button(onClick = onDone, modifier = Modifier.padding(top = 24.dp)) {
            Text("Return to the app you opened")
        }
    }
}
