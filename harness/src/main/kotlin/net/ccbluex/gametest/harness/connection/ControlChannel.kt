package net.ccbluex.gametest.harness.connection

import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.concurrent.CompletableFuture

private const val NAMESPACE = "gametest"

/** One message of the `gametest:control` channel: a JSON object as the whole payload. */
internal class ControlPayload(val json: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ControlPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ControlPayload>(Identifier.fromNamespaceAndPath(NAMESPACE, "control"))

        val CODEC: StreamCodec<FriendlyByteBuf, ControlPayload> = StreamCodec.of(
            { buffer, payload -> buffer.writeBytes(payload.json.toByteArray()) },
            { buffer -> ControlPayload(buffer.readCharSequence(buffer.readableBytes(), Charsets.UTF_8).toString()) },
        )
    }

}

/**
 * The client's end of the game test server's two plugin channels. The proxy in front of the public server asks
 * a joining bot for a ticket on `gametest:login`; the server then talks to the harness on `gametest:control`.
 */
internal object ControlChannel {

    /** The session of the running test. Without one, no ticket is given and messages are dropped. */
    @Volatile
    var session: ServerSession? = null

    init {
        PayloadTypeRegistry.serverboundPlay().register(ControlPayload.TYPE, ControlPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(ControlPayload.TYPE, ControlPayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.TYPE) { payload, _ ->
            session?.receive(payload.json)
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> session?.joined() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> session?.disconnected() }
        ClientLoginNetworking.registerGlobalReceiver(Identifier.fromNamespaceAndPath(NAMESPACE, "login")) {
                minecraft, _, buffer, _ ->
            val nonce = ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            if (session == null) {
                CompletableFuture.completedFuture(null)
            } else {
                Ticket.request(minecraft.user.name, nonce).thenApply { ticket ->
                    ticket?.let { FriendlyByteBuf(Unpooled.wrappedBuffer(it.toByteArray())) }
                }
            }
        }
    }

}
