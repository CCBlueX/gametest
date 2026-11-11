package net.ccbluex.gametest.harness.connection

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.gametest.harness.verdict.ConsoleLine
import net.ccbluex.gametest.harness.verdict.ServerReport
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import org.apache.logging.log4j.LogManager
import java.nio.file.Path
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.appendText
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

/**
 * A test's session on the game test server, driven through its console the way an admin would. Everything the
 * server sends about the session lands in [console], in order, and in the file [log] together with what the
 * harness sent.
 */
class ServerSession internal constructor(private val context: ClientGameTestContext, val log: Path) {

    val address = GameTestEnvironment.address

    val console: List<ConsoleLine>
        get() = lines

    /** Whether the client is on the server right now. */
    @Volatile
    var connected = false
        private set

    /** The server's last `ready` or `busy`, its answers to `open`. */
    @Volatile
    internal var answer: JsonObject? = null

    /** Whether the server has confirmed `close`. */
    @Volatile
    internal var closed = false
        private set

    private val lines = CopyOnWriteArrayList<ConsoleLine>()
    private val results = ConcurrentHashMap<Int, Int>()
    private val commands = AtomicInteger()
    private val clientThread = context.computeOnClient<Thread, RuntimeException> { Thread.currentThread() }

    init {
        log.createParentDirectories().writeText("")
    }

    /** Runs [command] as the session's console and returns its id. Its feedback follows as console lines. */
    fun run(command: String): Int {
        val id = commands.incrementAndGet()
        send("command") {
            addProperty("id", id)
            addProperty("line", command)
        }
        return id
    }

    /** What command [id] returned, once it is done: Brigadier's result, 0 when it failed. */
    fun result(id: Int): Int? = results[id]

    /** Whether the last command is not done yet. The server runs commands in order and answers them in order. */
    internal val hasPendingCommands: Boolean
        get() = commands.get().let { it > 0 && !results.containsKey(it) }

    internal fun send(type: String, fields: JsonObject.() -> Unit = {}) {
        check(connected) { "The client is not on $address any more, see $log" }
        val message = JsonObject().apply { addProperty("type", type) }.apply(fields).toString()
        record("> $message")
        if (Thread.currentThread() === clientThread) {
            ClientPlayNetworking.send(ControlPayload(message))
        } else {
            context.runOnClient<RuntimeException> { ClientPlayNetworking.send(ControlPayload(message)) }
        }
    }

    internal fun receive(json: String) {
        val message = JsonParser.parseString(json).asJsonObject
        val text = message["message"]?.asString.orEmpty()
        when (val type = message["type"].asString) {
            "ready", "busy" -> {
                record("< $json")
                answer = message
            }
            "closed" -> {
                record("< $json")
                closed = true
            }
            "output" -> add(ConsoleLine("", text, command = message["id"].asInt))
            "done" -> {
                record("< $json")
                results[message["id"].asInt] = message["result"].asInt
            }
            "report" -> add(ConsoleLine("", text, ServerReport.valueOf(message["report"].asString)))
            "log" -> add(ConsoleLine(message["level"].asString, text))
            "error" -> add(ConsoleLine("", text, ServerReport.ERROR, message["id"]?.asInt))
            else -> add(ConsoleLine("", "unknown message '$type': $json", ServerReport.ERROR))
        }
    }

    internal fun joined() {
        connected = true
    }

    internal fun disconnected() {
        if (connected) {
            add(ConsoleLine("", "the client lost its connection", ServerReport.QUIT))
            connected = false
        }
    }

    private fun add(line: ConsoleLine) {
        record("< $line")
        lines += line
        if (line.report == ServerReport.NOTE) {
            logger.warn("{} in {}", line, log)
        }
    }

    private companion object {
        val logger = LogManager.getLogger("GameTest")
    }

    @Synchronized
    private fun record(line: String) = log.appendText("${LocalTime.now().truncatedTo(ChronoUnit.MILLIS)} $line\n")

}
