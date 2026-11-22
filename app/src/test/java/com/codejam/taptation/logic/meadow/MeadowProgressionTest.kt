package com.codejam.taptation.logic.meadow

import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.HealthRecord
import org.junit.Assert.*
import org.junit.Test

class MeadowProgressionTest {
    private val start=1_000_000L
    private val minute=MeadowState.MINUTE
    private fun advance(s:MeadowState,g:GameState=GameState(),working:Boolean=true,at:Long)=MeadowProgression.advance(s,g,working,at)
    private fun started()=advance(MeadowState(),at=start)

    @Test fun catchCameosOnlyIncludeEarnedVisitors() {
        assertEquals(listOf(MeadowCameo.Fish),MeadowState().catchCameos)
        val hidden=MeadowState(discoveries=setOf(MeadowDiscovery.Hedgehog))
        assertEquals(listOf(MeadowCameo.Fish,MeadowCameo.Hedgehog),hidden.catchCameos)
        val robin=MeadowState(healthyMillis=90*minute)
        assertTrue(MeadowCameo.Robin in robin.catchCameos)
        assertFalse(MeadowCameo.Squirrel in robin.catchCameos)
        val mature=hidden.copy(healthyMillis=360*minute)
        assertEquals(MeadowCameo.entries.toSet(),mature.catchCameos.toSet())
    }

    @Test fun firstInstallDoesNotInventHistoricalGrowth() {
        assertEquals(0,started().healthyMinutes)
    }
    @Test fun timeAwayEarnsRealFullHealthGrowthAndUnlocksStory() {
        val next=advance(started(),at=start+360*minute)
        assertEquals(360,next.healthyMinutes)
        assertEquals(7,next.flowers);assertEquals(3,next.nestStage);assertEquals(3,next.acornStage)
        assertTrue(next.fireflies);assertNull(next.nextMilestone)
    }
    @Test fun offlineCatchAndRecoveryOnlyCountTheFullHealthSegments() {
        val history=listOf(HealthRecord(start+10*minute,100,90,"caught"),HealthRecord(start+70*minute,90,100,"recovery"))
        val next=advance(started(),GameState(healthHistory=history),at=start+90*minute)
        assertEquals(30,next.healthyMinutes)
    }
    @Test fun caughtUserKeepsEverythingAlreadyEarned() {
        val old=advance(started(),at=start+360*minute).copy(discoveries=setOf(MeadowDiscovery.Hedgehog,MeadowDiscovery.Pond))
        val caught=GameState(health=80,lastCatchMillis=start+361*minute,
            healthHistory=listOf(HealthRecord(start+361*minute,100,80,"caught")))
        val next=advance(old,caught,at=start+400*minute)
        assertEquals(361,next.healthyMinutes);assertEquals(old.discoveries,next.discoveries)
        assertEquals(3,next.nestStage);assertEquals(3,next.acornStage)
    }
    @Test fun pausedAndDisconnectedTimeCannotFarmGrowth() {
        val off=advance(started(),working=false,at=start+60*minute)
        val resumed=advance(off,at=start+120*minute)
        assertEquals(0,resumed.healthyMinutes)
        assertEquals(30,advance(resumed,at=start+150*minute).healthyMinutes)
        assertEquals(0,advance(started(),GameState(config=GameConfig(active=false)),at=start+60*minute).healthyMinutes)
    }
    @Test fun missingTransitionHistoryIsConservativeAndClockRollbackCannotEraseRewards() {
        val grown=advance(started(),at=start+90*minute)
        val unknown=advance(grown,GameState(health=80),at=start+120*minute)
        assertEquals(90,unknown.healthyMinutes)
        assertEquals(unknown.healthyMillis,advance(unknown,at=start).healthyMillis)
    }
    @Test fun recoveryFromLowHealthStartsNewGrowthWithoutReducingPreviousRewards() {
        val low=advance(MeadowState(healthyMillis=30*minute),GameState(health=50),at=start)
        val full=advance(low,GameState(healthHistory=listOf(HealthRecord(start+10*minute,50,100,"recovery"))),at=start+25*minute)
        assertEquals(45,full.healthyMinutes)
    }

    // Valley reveal: clean streak, one stage back per catch, revealed landmarks kept.
    @Test fun cleanTimeRevealsTheValleyStageByStage() {
        val s=advance(started(),at=start+45*minute)
        assertEquals(45*minute,s.cleanMillis);assertEquals(ValleyStage.River,s.valleyStage)
        assertEquals(15*minute,s.valleyRemainingMillis);assertEquals(.5f,s.valleyProgress,.001f)
        assertEquals(ValleyStage.Valley,advance(s,at=start+480*minute).valleyStage)
    }
    @Test fun firstObservationDoesNotRollBackForOldCatches() {
        val s=advance(MeadowState(),GameState(totalCatches=12,lastCatchMillis=start-minute),at=start)
        assertEquals(0L,s.cleanMillis);assertEquals(12L,s.checkpointCatches)
    }
    @Test fun aCatchRollsTheViewBackOneStageButKeepsRevealedLandmarks() {
        val s=advance(started(),GameState(totalCatches=0),at=start+130*minute)
        assertEquals(ValleyStage.Bridge,s.valleyStage)
        val caught=GameState(health=80,totalCatches=1,lastCatchMillis=start+130*minute,
            healthHistory=listOf(HealthRecord(start+130*minute,100,80,"caught")))
        val after=advance(s,caught,at=start+140*minute)
        // Back to the start of Hills (60 min), plus the 10 clean minutes since the catch.
        assertEquals(70*minute,after.cleanMillis);assertEquals(ValleyStage.Hills,after.valleyStage)
        assertEquals(ValleyStage.Bridge,after.revealed)
    }
    @Test fun severalCatchesRollBackSeveralStagesAndNeverBelowZero() {
        val s=advance(started(),at=start+130*minute)
        val after=advance(s,GameState(totalCatches=3,lastCatchMillis=start+131*minute),at=start+131*minute)
        assertEquals(15*minute,after.cleanMillis)
        val many=advance(after,GameState(totalCatches=40,lastCatchMillis=start+132*minute),at=start+132*minute)
        assertEquals(0L,many.cleanMillis);assertEquals(ValleyStage.Bridge,many.revealed)
    }
    @Test fun pausedTimeNeitherEarnsNorResetsCleanTime() {
        val s=advance(started(),at=start+40*minute)
        val off=advance(s,working=false,at=start+100*minute)
        assertEquals(40*minute,off.cleanMillis)
        assertEquals(40*minute,advance(off,at=start+160*minute).cleanMillis)
    }
}
