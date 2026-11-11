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
import java.util.concurrent.TimeUnit

/**
 * A bot proves where it comes from with a ticket the LiquidBounce API signs for the owner of an API token: valid
 * for two minutes, for one name and for the nonce the proxy just sent. The token itself never crosses the bot's
 * connection, which is unencrypted in offline mode.
 */
internal object Ticket {

    private const val URL = "https://api.liquidbounce.net/api/v3/gametest/ticket"
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MILLIS = 1000L

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
        return send(request, ATTEMPTS)
    }

    private fun send(request: HttpRequest, attempts: Int): CompletableFuture<String?> =
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle { response, failure ->
            when {
                // A connection the network dropped is worth another try; a refusal is not
                failure != null && attempts > 1 -> {
                    logger.warn("Could not ask for a game test ticket, trying again: {}", failure.toString())
                    val delayed = CompletableFuture.delayedExecutor(RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    CompletableFuture.supplyAsync({ }, delayed).thenCompose { send(request, attempts - 1) }
                }
                failure != null -> CompletableFuture.completedFuture<String?>(null).also {
                    logger.error("Could not ask for a game test ticket", failure)
                }
                response.statusCode() != 200 -> CompletableFuture.completedFuture<String?>(null).also {
                    logger.error("The LiquidBounce API refused a game test ticket ({}): {}",
                        response.statusCode(), response.body())
                }
                else -> CompletableFuture.completedFuture<String?>(
                    JsonParser.parseString(response.body()).asJsonObject["ticket"].asString
                )
            }
        }.thenCompose { it }

}
