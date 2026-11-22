package com.phrasepocket.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional AI definition/example generation through any OpenAI-compatible
 * endpoint (hosted API or a local Ollama server). The user supplies their own
 * base URL, key and model — no AI cost is ever billed to the app.
 */
object AiHelper {

    private const val PREFS = "ai_config"

    fun saveConfig(context: Context, baseUrl: String, apiKey: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("base_url", baseUrl.trim().trimEnd('/'))
            .putString("api_key", apiKey.trim())
            .putString("model", model.trim())
            .apply()
    }

    fun config(context: Context): Triple<String, String, String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            p.getString("base_url", "").orEmpty(),
            p.getString("api_key", "").orEmpty(),
            p.getString("model", "").orEmpty()
        )
    }

    fun isConfigured(context: Context): Boolean {
        val (baseUrl, _, model) = config(context)
        return baseUrl.isNotBlank() && model.isNotBlank()
    }

    suspend fun lookup(context: Context, text: String): String = withContext(Dispatchers.IO) {
        val (baseUrl, apiKey, model) = config(context)
        require(baseUrl.isNotBlank() && model.isNotBlank()) { "AI 未配置" }

        val prompt = "为英文表达 \"$text\" 给出简洁的中文释义，并给一个英文例句及其中文翻译。" +
            "用三行返回：释义 / 例句 / 例句翻译，不要其他内容。"

        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
        }

        val connection = URL("$baseUrl/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.readBytes()?.decodeToString().orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${response.take(200)}")

            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } finally {
            connection.disconnect()
        }
    }
}
