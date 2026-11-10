package net.ccbluex.gametest.harness.connection

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture

/**
 * A bot proves where it comes from with a ticket the LiquidBounce API signs for the owner of an API token: valid
 * for two minutes, for one name and for the nonce the proxy just sent. The token itself never crosses the bot's
 * connection, which is unencrypted in offline mode.
 */
internal object Ticket {

    private const val URL = "https://api.liquidbounce.net/api/v3/gametest/ticket"

    private val logger = LogManager.getLogger("GameTest")
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    /** The ticket, or null when there is no token or the API refused. */
    fun request(name: String, nonce: ByteArray): CompletableFuture<String?> {
        val token = GameTestEnvironment.token ?: return CompletableFuture.completedFuture(null)
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("nonce", Base64.getEncoder().encodeToString(nonce))
        }
        val request = HttpRequest.newBuilder(URI(URL))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle { response, failure ->
            when {
                failure != null -> null.also { logger.error("Could not ask for a game test ticket", failure) }
                response.statusCode() != 200 -> null.also {
                    logger.error("The LiquidBounce API refused a game test ticket ({}): {}",
                        response.statusCode(), response.body())
                }
                else -> JsonParser.parseString(response.body()).asJsonObject["ticket"].asString
            }
        }
    }

}
