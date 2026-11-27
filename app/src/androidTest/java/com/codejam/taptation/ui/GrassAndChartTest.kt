package com.codejam.taptation.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.codejam.taptation.stats.DailyCatches
import com.codejam.taptation.ui.stats.DailyChart
import com.codejam.taptation.ui.stats.OpenedSplitBar
import com.codejam.taptation.ui.theme.TaptationTheme
import org.junit.Rule
import org.junit.Test

class GrassAndChartTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyChartSelectsZeroCountDatesAndSplitBarExplainsCounts() {
        val days = (16..22).map { day -> DailyCatches("2026-11-$day", if (day == 20) 4 else 0) }
        compose.setContent { TaptationTheme {
            Surface { Column(Modifier.padding(24.dp)) {
                OpenedSplitBar(16, 8)
                DailyChart(days, noActivity = false, month = false)
            } }
        } }
        compose.onNodeWithContentDescription("16 opened, 8 swiped. 67 percent opened.").assertIsDisplayed()
        compose.onNodeWithTag("daily-chart").performTouchInput { click(Offset(width / 14f, height / 2f)) }
        compose.onNodeWithText("Mon, Nov 16 · 0 catches").assertIsDisplayed()
        compose.onNodeWithContentDescription("Later day").performClick()
        compose.onNodeWithText("Tue, Nov 17 · 0 catches").assertIsDisplayed()
    }
}
