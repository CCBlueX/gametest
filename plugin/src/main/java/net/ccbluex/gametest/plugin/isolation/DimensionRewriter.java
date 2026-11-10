package net.ccbluex.gametest.plugin.isolation;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.ccbluex.gametest.plugin.session.Sessions;
import net.kyori.adventure.key.Key;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

/** Rewrites the dimension names in the packets that carry them, on their way to a test player. */
@ChannelHandler.Sharable
public final class DimensionRewriter extends ChannelOutboundHandlerAdapter {

    private static final Key LISTENER = Key.key("gametest", "dimensions");
    private static final String HANDLER = "gametest_dimensions";

    private final Sessions sessions;

    private DimensionRewriter(Sessions sessions) {
        this.sessions = sessions;
    }

    public static void install(Sessions sessions) {
        var rewriter = new DimensionRewriter(sessions);
        ChannelInitializeListenerHolder.addListener(LISTENER, channel ->
            channel.pipeline().addBefore(HandlerNames.PACKET_HANDLER, HANDLER, rewriter));
    }

    public static void uninstall() {
        ChannelInitializeListenerHolder.removeListener(LISTENER);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        var shown = switch (message) {
            case ClientboundLoginPacket packet when view(context) instanceof DimensionView view -> view.show(packet);
            case ClientboundRespawnPacket packet when view(context) instanceof DimensionView view -> view.show(packet);
            case ClientboundSetDefaultSpawnPositionPacket packet when view(context) instanceof DimensionView view ->
                view.show(packet);
            default -> message;
        };
        super.write(context, shown, promise);
    }

    private @Nullable DimensionView view(ChannelHandlerContext context) {
        return context.pipeline().get(HandlerNames.PACKET_HANDLER) instanceof Connection connection
            && connection.getPacketListener() instanceof ServerGamePacketListenerImpl game
            ? sessions.view(game.player.getUUID(), game.player.getGameProfile().name())
            : null;
    }

}
