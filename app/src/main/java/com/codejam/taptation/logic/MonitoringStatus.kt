package com.codejam.taptation.logic

/** What Taptation is really doing right now, for the one status area on Home and Settings. */
sealed interface MonitoringStatus {
    /** Notification access is off, so nothing is checked. */
    data object SetupNeeded : MonitoringStatus
    /** Access is on, but Android has stopped the notification listener. */
    data object ConnectionInterrupted : MonitoringStatus
    /** The user paused monitoring; [untilMillis] is null when paused until turned back on. */
    data class Paused(val untilMillis: Long?) : MonitoringStatus
    data class Monitoring(val difficulty: Difficulty) : MonitoringStatus

    val isWorking: Boolean get() = this is Monitoring

    companion object {
        /** Setup problems come first: pausing or resuming can't help if access is missing. */
        fun from(notificationAccess: Boolean, listenerConnected: Boolean, config: GameConfig, nowMillis: Long): MonitoringStatus = when {
            !notificationAccess -> SetupNeeded
            !config.isActive(nowMillis) -> Paused(config.disabledUntilMillis)
            !listenerConnected -> ConnectionInterrupted
            else -> Monitoring(config.difficulty)
        }
    }
}
