package com.codejam.taptation.service

import com.codejam.taptation.logic.NotificationContext

/**
 * Debug-only overrides carried in the extras of our own test notification, so a notification we
 * post can stand in for another app's. [TapListenerService] applies them only in debuggable builds
 * and only on [TapDetection.OWN_TEST_CHANNEL_ID]; release builds never read them.
 *
 * Package, channel name, conversation flag and app category can't be set for real on a
 * notification we post, so they travel here. Title, text, category and actions are set on the
 * notification itself.
 */
data class DebugSimulation(
    val packageName: String? = null,
    val channelName: String? = null,
    val isConversation: Boolean? = null,
    val appCategory: Int? = null,
) {
    fun applyTo(context: NotificationContext): NotificationContext = context.copy(
        packageName = packageName ?: context.packageName,
        channelName = channelName ?: context.channelName,
        isConversation = isConversation ?: context.isConversation,
        appCategory = appCategory ?: context.appCategory,
    )

    /** Pairs to put in the notification extras. */
    fun toExtras(): Map<String, String> = buildMap {
        packageName?.let { put(EXTRA_PACKAGE, it) }
        channelName?.let { put(EXTRA_CHANNEL_NAME, it) }
        isConversation?.let { put(EXTRA_CONVERSATION, it.toString()) }
        appCategory?.let { put(EXTRA_APP_CATEGORY, it.toString()) }
    }

    companion object {
        const val EXTRA_PACKAGE = "com.codejam.taptation.debug.SIM_PACKAGE"
        const val EXTRA_CHANNEL_NAME = "com.codejam.taptation.debug.SIM_CHANNEL_NAME"
        const val EXTRA_CONVERSATION = "com.codejam.taptation.debug.SIM_CONVERSATION"
        const val EXTRA_APP_CATEGORY = "com.codejam.taptation.debug.SIM_APP_CATEGORY"

        /** Reads overrides through [extra] (e.g. Bundle::getString). Null when none are set. */
        fun from(extra: (String) -> String?): DebugSimulation? {
            val simulation = DebugSimulation(
                packageName = extra(EXTRA_PACKAGE)?.trim()?.takeIf(String::isNotEmpty),
                channelName = extra(EXTRA_CHANNEL_NAME)?.trim()?.takeIf(String::isNotEmpty),
                isConversation = extra(EXTRA_CONVERSATION)?.toBooleanStrictOrNull(),
                appCategory = extra(EXTRA_APP_CATEGORY)?.trim()?.toIntOrNull(),
            )
            return simulation.takeIf { it != DebugSimulation() }
        }
    }
}
