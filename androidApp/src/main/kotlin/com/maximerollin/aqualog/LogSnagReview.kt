package com.maximerollin.aqualog

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** Only a public token restricted to the reviews channel may be supplied to this client. */
internal fun notifyLogSnagReview() {
    val token = BuildConfig.LOGSNAG_REVIEW_PUBLIC_TOKEN
    val project = BuildConfig.LOGSNAG_PROJECT
    if (BuildConfig.DEBUG || token.isBlank() || !project.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) return
    val channel = BuildConfig.DISTRIBUTION_TRACK
    if (channel !in setOf("internal", "alpha", "production")) return
    val connection = URL("https://api.logsnag.com/v1/log").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject().put("project", project).put("channel", "reviews")
            .put("event", "Demande de flux d’avis").put("icon", "⭐").put("notify", channel == "production")
            .put("tags", JSONObject().put("release-channel", channel))
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        connection.responseCode
    } finally { connection.disconnect() }
}
