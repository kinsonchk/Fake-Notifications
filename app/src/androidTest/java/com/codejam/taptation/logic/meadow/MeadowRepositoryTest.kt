package com.codejam.taptation.logic.meadow

import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class MeadowRepositoryTest {
    @Test fun growthAndDiscoveriesSurviveRepositoryRestartAndLaterCatches()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="meadow-instrumentation-${System.nanoTime()}"
        val game=MutableStateFlow<GameState?>(GameState())
        val monitoring=MutableStateFlow<MonitoringStatus?>(MonitoringStatus.Monitoring(Difficulty.Easy))
        val now=AtomicLong(1_000_000)
        var job=SupervisorJob();var scope=CoroutineScope(job+Dispatchers.Default)
        try{
            var repo=MeadowRepository(context,game,monitoring,scope,now::get,name)
            await{repo.state.value?.checkpointMillis==now.get()}
            assertEquals(MeadowCameo.Fish,repo.sceneFor("same-catch"))
            now.addAndGet(360*MeadowState.MINUTE);repo.refresh()
            await{repo.state.value?.acornStage==3}
            repo.discover(MeadowDiscovery.Hedgehog)
            await{MeadowDiscovery.Hedgehog in repo.state.value!!.discoveries}
            assertEquals("A catch must keep its scene when more visitors unlock",MeadowCameo.Fish,repo.sceneFor("same-catch"))
            job.cancelAndJoin();job=SupervisorJob();scope=CoroutineScope(job+Dispatchers.Default)
            repo=MeadowRepository(context,game,monitoring,scope,now::get,name)
            await{repo.state.value?.acornStage==3}
            assertTrue(MeadowDiscovery.Hedgehog in repo.state.value!!.discoveries)
            now.addAndGet(10*MeadowState.MINUTE)
            game.value=GameState(health=80,healthHistory=listOf(HealthRecord(now.get(),100,80,"caught")))
            await{repo.state.value?.checkpointFull==false}
            val earned=repo.state.value!!.healthyMillis
            now.addAndGet(60*MeadowState.MINUTE);repo.refresh()
            await{repo.state.value?.checkpointMillis==now.get()}
            assertEquals(earned,repo.state.value!!.healthyMillis)
            assertEquals(3,repo.state.value!!.acornStage)
            assertTrue(MeadowDiscovery.Hedgehog in repo.state.value!!.discoveries)
        }finally{job.cancelAndJoin();context.getSharedPreferences(name,0).edit().clear().commit()}
    }
    private suspend fun await(predicate:()->Boolean){withTimeout(10_000){while(!predicate())delay(20)}}
}
