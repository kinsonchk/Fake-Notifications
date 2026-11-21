package com.codejam.taptation.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TapDispatcherTest {
    @Test fun metadataDispatchFallsBackToLegacyHandler() {
        withHandler(object : TapHandler {
            override fun onTap(packageName: String) { received = packageName }
        }) {
            TapDispatcher.dispatch(NotificationContext("example.app", text = "sale"))
            assertEquals("example.app", received)
        }
    }

    @Test fun legacyEntryPointStillDeliversPackageName() {
        withHandler(object : TapHandler {
            override fun onTap(packageName: String) { received = packageName }
        }) {
            TapDispatcher.dispatch("example.app")
            assertEquals("example.app", received)
        }
    }

    @Test fun metadataReachesNewHandlersIntact() {
        val context = NotificationContext("example.app", "key", "title", "text", "promo", 2)
        var receivedContext: NotificationContext? = null
        withHandler(object : TapHandler {
            override fun onTap(packageName: String) { error("Metadata was dropped") }
            override fun onTap(context: NotificationContext) { receivedContext = context }
        }) {
            TapDispatcher.dispatch(context)
            assertEquals(context, receivedContext)
        }
    }

    private var received: String? = null
    private fun withHandler(handler: TapHandler, action: () -> Unit) {
        val previous = TapDispatcher.handler
        try {
            TapDispatcher.handler = handler
            action()
        } finally {
            TapDispatcher.handler = previous
        }
    }
}
