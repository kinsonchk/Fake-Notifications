package com.codejam.taptation.ai

import com.codejam.taptation.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Cloudflare Workers AI REST client (free plan: 10,000 Neurons a day). Blocking: call it
 * off the main thread. Errors carry only the HTTP status, never the request or response body.
 */
class WorkersAi(
    private val accountId: String = BuildConfig.CF_ACCOUNT_ID,
    private val apiToken: String = BuildConfig.CF_API_TOKEN,
    private val timeoutMillis: Int = 8_000,
) {
    val isConfigured: Boolean get() = accountId.isNotBlank() && apiToken.isNotBlank()

    /** POSTs [body] to `ai/run/[model]` and returns the response's `result` object. */
    fun run(model: String, body: JSONObject): JSONObject {
        val url = URL("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$model")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(response).getJSONObject("result")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val TAG = "FakeNotifs"
    }
}
