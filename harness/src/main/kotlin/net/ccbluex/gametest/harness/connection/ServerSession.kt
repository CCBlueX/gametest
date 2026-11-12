package net.ccbluex.gametest.harness.connection

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.gametest.harness.verdict.ConsoleLine
import net.ccbluex.gametest.harness.verdict.ServerReport
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
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
 * harness sent. An [integrated] session runs its commands on the client's integrated server instead, as a console
 * like the game test server's, and hears from it the same way.
 */
class ServerSession internal constructor(
    private val context: ClientGameTestContext,
    val log: Path,
    internal val integrated: Boolean,
) {

    val address = if (integrated) "the integrated server" else GameTestEnvironment.address

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

    /** The integrated server while the session's world is open on it. */
    @Volatile
    internal var integratedServer: TestServerContext? = null

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
        val integratedServer = integratedServer
        if (integratedServer == null) {
            send("command") {
                addProperty("id", id)
                addProperty("line", command)
            }
        } else {
            record("> #$id $command")
            integratedServer.runOnServer<RuntimeException> { perform(it, id, command) }
        }
        return id
    }

    /** Runs [command] like the game test server runs a session's: a console at the overworld's origin. */
    private fun perform(server: MinecraftServer, id: Int, command: String) {
        val console = IntegratedConsole(id)
        val source = CommandSourceStack(
            console, Vec3.ZERO, Vec2.ZERO, server.overworld(), LevelBasedPermissionSet.GAMEMASTER,
            Component.literal("Server"), server,
        ).withCallback(console)
        server.commands.performPrefixedCommand(source, command)
        results[id] = console.result
        record("< #$id done: ${console.result}")
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
            "report" -> {
                val report = ServerReport.entries.firstOrNull { it.name == message["report"].asString }
                add(report?.let { ConsoleLine("", text, it, anticheat = message["anticheat"]?.asString) }
                    ?: ConsoleLine("", "unknown report: $json", ServerReport.ERROR))
            }
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

    /** A line of the integrated server's log that names the player, as the game test server forwards them. */
    internal fun log(level: String, text: String) = add(ConsoleLine(level, text))

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

    /** Hears what command [id] says and counts what it returns, like the game test server's session console. */
    private inner class IntegratedConsole(private val id: Int) : CommandSource, CommandResultCallback {

        private var results = 0
        private var successes = 0
        private var lastResult = 0

        /** Brigadier's return value: what the command returned, or how many of its forks succeeded. */
        val result get() = if (results == 1) lastResult else successes

        override fun sendSystemMessage(message: Component) =
            message.string.lines().forEach { add(ConsoleLine("", it, command = id)) }

        override fun acceptsSuccess() = true

        override fun acceptsFailure() = true

        override fun shouldInformAdmins() = false

        override fun onResult(success: Boolean, result: Int) {
            results++
            if (success) {
                successes++
                lastResult = result
            }
        }

    }

    @Synchronized
    private fun record(line: String) = log.appendText("${LocalTime.now().truncatedTo(ChronoUnit.MILLIS)} $line\n")

}
