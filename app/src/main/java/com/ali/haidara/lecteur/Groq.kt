package com.ali.haidara.lecteur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Option facultative : avec une clé Groq et internet, le texte est mieux corrigé et découpé. */
object Groq {
    private const val SYSTEM =
        "Tu aides un enfant à apprendre. Le texte vient d'un cahier d'école lu par OCR, il peut avoir des fautes. " +
        "Corrige les fautes de lecture sans changer le sens, puis découpe le texte en petits morceaux simples " +
        "de 1 à 3 phrases. Sépare chaque morceau par une ligne vide. Réponds seulement avec le texte, sans commentaire."

    suspend fun simplify(key: String, text: String): List<String>? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            val body = JSONObject()
                .put("model", "llama-3.1-8b-instant")
                .put("temperature", 0.2)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM))
                    .put(JSONObject().put("role", "user").put("content", text)))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return@withContext null
            val resp = c.inputStream.bufferedReader().readText()
            val content = JSONObject(resp).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content")
            content.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
