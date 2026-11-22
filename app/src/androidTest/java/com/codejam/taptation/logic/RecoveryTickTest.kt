package com.codejam.taptation.logic

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Health recovers while nothing calls refresh(): no screen, no tap. Uses the 1-minute debug
 * interval, so it takes about a minute. Isolated preferences keep the real game untouched.
 */
class RecoveryTickTest {
    private val original = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "recovery-test-${java.util.UUID.randomUUID()}-"
    private val isolated = object : ContextWrapper(original) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
    }

    @After fun cleanUp() { original.deleteSharedPreferences(prefix + "taptation_game") }

    @Test fun healthRecoversOnItsOwnWithoutAnyRefreshCall() {
        val repository = GameRepository(isolated)
        repository.setHealthForDebug(50).get(5, TimeUnit.SECONDS)
        repository.setDebugRecoveryInterval(1).get(5, TimeUnit.SECONDS)
        val recoveryAmount = repository.state.value!!.config.recoveryAmount
        val deadline = SystemClock.elapsedRealtime() + 75_000
        while (repository.state.value!!.health == 50 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(500)
        assertEquals(50 + recoveryAmount, repository.state.value!!.health)
    }
}
