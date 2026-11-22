package com.codejam.taptation.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePickerTest {
    private val calendar = InstalledApp("cal", "Calendar", notificationsEnabled = true)
    private val camera = InstalledApp("cam", "Camera", notificationsEnabled = false)
    private val config = GameConfig(appRules = mapOf("cal" to AppRule.AlwaysImportant, "gone" to AppRule.AlwaysBait))

    @Test fun onlyAppsAllowedToNotifyAreListedWithTheirRule() {
        val picker = RulePicker.build(AppList(listOf(calendar, camera)), config, notificationFilterSupported = true)
        assertEquals(listOf(RuleApp(calendar, AppRule.AlwaysImportant)), picker.apps)
    }

    @Test fun withoutTheAndroid13FilterEveryAppIsListed() {
        val picker = RulePicker.build(AppList(listOf(calendar, camera)), config, notificationFilterSupported = false)
        assertEquals(listOf("cal", "cam"), picker.apps!!.map { it.app.packageName })
        assertEquals(AppRule.LetUsDecide, picker.apps!![1].rule)
    }

    @Test fun rulesOfUninstalledAppsAreReportedButDisabledAppsAreNot() {
        val picker = RulePicker.build(AppList(listOf(calendar, camera)),
            config.copy(appRules = config.appRules + ("cam" to AppRule.AlwaysBait)), notificationFilterSupported = true)
        assertEquals(listOf("gone"), picker.missingRules)
    }

    @Test fun searchMatchesNamesOnly() {
        val picker = RulePicker.build(AppList(listOf(calendar, camera)), config, notificationFilterSupported = false)
        assertEquals(listOf("Calendar", "Camera"), picker.search(" ca ").map { it.app.label })
        assertTrue(picker.search("cal.").isEmpty())
        assertEquals(2, picker.search("").size)
    }

    @Test fun loadingAndErrorsPassThrough() {
        assertNull(RulePicker.build(AppList(), config, true).apps)
        assertEquals("boom", RulePicker.build(AppList(error = "boom"), config, true).error)
    }
}
