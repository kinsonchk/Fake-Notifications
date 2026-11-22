package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.Difficulty
import com.codejam.taptation.ui.settings.chanceSummary
import com.codejam.taptation.ui.settings.listSummary
import com.codejam.taptation.ui.settings.timeLimitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLabelsTest {
    @Test fun notificationPickerHidesDisabledAppsWithoutChangingItsSourceOrSavedRules() {
        val apps = listOf(InstalledApp("enabled", "Calendar", true), InstalledApp("disabled", "Camera", false))
        assertEquals(listOf(apps[0]), AppLabels.notifyingApps(apps, "ca"))
        assertTrue(AppLabels.notifyingApps(apps, "Camera").isEmpty())
        assertEquals(2, apps.size)
        assertEquals(listOf(apps[1].copy(notificationsEnabled = true)),
            AppLabels.notifyingApps(listOf(apps[1].copy(notificationsEnabled = true)), ""))
    }
    @Test fun fallbackLabelSkipsGenericPackageParts() {
        assertEquals("Instagram", AppLabels.fallbackLabel("com.instagram.android"))
        assertEquals("Calendar", AppLabels.fallbackLabel("com.google.android.calendar"))
        assertEquals("Deskclock", AppLabels.fallbackLabel("com.google.android.deskclock"))
        assertEquals("Discord", AppLabels.fallbackLabel("com.discord"))
        assertEquals("Fake", AppLabels.fallbackLabel("com.test.fake"))
        assertEquals("Single", AppLabels.fallbackLabel("single"))
        assertEquals("Cool app", AppLabels.fallbackLabel("net.example.cool_app"))
    }

    @Test fun searchMatchesAppNamesNotPackageNames() {
        val apps = listOf(InstalledApp("com.google.android.calendar", "Calendar"), InstalledApp("com.android.camera2", "Camera"),
            InstalledApp("com.android.chrome", "Chrome"))
        assertEquals(listOf("Calendar", "Camera"), AppLabels.filter(apps, "ca").map { it.label })
        assertEquals(listOf("Chrome"), AppLabels.filter(apps, " CHR ").map { it.label })
        assertTrue(AppLabels.filter(apps, "google").isEmpty())
        assertEquals(apps, AppLabels.filter(apps, "  "))
    }
}

class SettingsTextTest {
    @Test fun chanceIsExplainedInPlainNumbers() {
        assertEquals("About 1 in 5 distracting taps. Important notifications are never caught.", chanceSummary(Difficulty.Easy))
        assertTrue(chanceSummary(Difficulty.Medium).startsWith("About 2 in 5"))
        assertTrue(chanceSummary(Difficulty.Hard).startsWith("About 7 in 10"))
    }

    @Test fun timeLimitReadsNaturally() {
        assertEquals("30 seconds", timeLimitText(30))
        assertEquals("1 minute", timeLimitText(60))
        assertEquals("10 minutes", timeLimitText(600))
        assertEquals("1 hour", timeLimitText(3600))
        assertEquals("2 hours", timeLimitText(7200))
    }

    @Test fun summariesTruncateAndHandleEmpty() {
        assertEquals("No one added yet", listSummary(emptySet(), "No one added yet"))
        assertEquals("Alex, Mom", listSummary(setOf("mom".replaceFirstChar { it.uppercase() }, "Alex"), "x"))
        assertEquals("A, B, C +2", listSummary(setOf("A", "B", "C", "D", "E"), "x"))
    }
}
