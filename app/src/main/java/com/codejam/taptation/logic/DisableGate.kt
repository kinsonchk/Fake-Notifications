package com.codejam.taptation.logic

/** Monotonic, single-use confirmation. A restart or cancellation requires a fresh wait. */
class DisableGate(private val elapsedMillis: () -> Long) {
    data class Challenge(val id: String, val readyAtMillis: Long)
    private var challenge: Challenge? = null
    fun begin(): Challenge = Challenge(java.util.UUID.randomUUID().toString(), elapsedMillis() + WAIT_MILLIS)
        .also { challenge = it }
    fun confirm(id: String) {
        val current = checkNotNull(challenge) { "Start a new disable confirmation" }
        check(id == current.id && elapsedMillis() >= current.readyAtMillis) { "Wait before confirming disable" }
        challenge = null
    }
    fun cancel() { challenge = null }
    companion object { const val WAIT_MILLIS = 10_000L }
}
