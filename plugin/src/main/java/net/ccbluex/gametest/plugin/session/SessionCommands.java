package net.ccbluex.gametest.plugin.session;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.ccbluex.gametest.plugin.control.Control;
import net.ccbluex.gametest.plugin.isolation.Confinement;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Runs the commands of sessions. They are parsed against vanilla's commands and this plugin's own, never the
 * server's dispatcher, so no other plugin's command can be reached from a session.
 */
public final class SessionCommands implements Listener {

    private static final SimpleCommandExceptionType NO_SIGN =
        new SimpleCommandExceptionType(Component.literal("There is no sign at that position"));

    private final Plugin plugin;
    private final MinecraftServer server;
    private final SessionWorlds worlds;
    private Commands commands;

    public SessionCommands(Plugin plugin, SessionWorlds worlds) {
        this.plugin = plugin;
        this.server = ((CraftServer) plugin.getServer()).getServer();
        this.worlds = worlds;
        this.commands = build();
    }

    /** Commands hold on to the registries they were built with, and a reload of the data packs replaces those. */
    @EventHandler
    public void onReload(ServerResourcesReloadedEvent event) {
        commands = build();
    }

    private Commands build() {
        // Loot tables, predicates and item modifiers are only in the reloadable registries
        var registries = server.reloadableRegistries().lookup();
        var context = CommandBuildContext.simple(registries, server.getWorldData().enabledFeatures());
        var commands = new Commands(Commands.CommandSelection.DEDICATED, context, true);
        registerOpenSign(commands.getDispatcher());
        ForceLoad.register(commands.getDispatcher());
        return commands;
    }

    /**
     * Runs one line as the session's console and tells the player's harness what came of it. Returns what the
     * command started that the session's next commands have to wait for.
     */
    CompletableFuture<?> run(Session session, Player player, int id, String line) {
        var command = Commands.trimOptionalPrefix(line);
        var name = command.split(" ", 2)[0];
        if (!Confinement.isSessionRoot(name)) {
            Control.refused(player, id, "'" + name + "' is not for game test sessions");
            return CompletableFuture.completedFuture(null);
        }

        var console = new SessionConsole(session, player, id);
        var source = new CommandSourceStack(
            console, Vec3.ZERO, Vec2.ZERO, Session.level(session.overworld()), LevelBasedPermissionSet.GAMEMASTER,
            Component.literal("Server"), server
        ).withCallback(console);
        var parse = commands.getDispatcher().parse(command, source);
        var root = commands.getDispatcher().getRoot();
        var confinement = new Confinement(root, session, dimension -> level(session, dimension));
        String refusal;
        try {
            refusal = confinement.confine(parse);
        } catch (RuntimeException exception) {
            plugin.getSLF4JLogger().warn("Could not confine '{}' of session {}", command, session.id(), exception);
            refusal = "The command could not be checked: " + exception.getMessage();
        }
        if (refusal != null) {
            Control.refused(player, id, refusal);
            return CompletableFuture.completedFuture(null);
        }

        commands.performCommand(parse, command);
        Control.done(player, id, console.result());
        return console.work();
    }

    private @Nullable ServerLevel level(Session session, ResourceKey<Level> dimension) {
        var environment = dimension == Level.OVERWORLD ? World.Environment.NORMAL
            : dimension == Level.NETHER ? World.Environment.NETHER
            : dimension == Level.END ? World.Environment.THE_END
            : null;
        return environment == null ? null : Session.level(worlds.of(session, environment));
    }

    /** {@code opensign <player> <pos>}: opens a sign's editor out of the blue, the way trap plugins do. */
    private static void registerOpenSign(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("opensign")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(context -> openSign(
                        EntityArgument.getPlayer(context, "player"), BlockPosArgument.getBlockPos(context, "pos"))))));
    }

    private static int openSign(ServerPlayer player, BlockPos position) throws CommandSyntaxException {
        if (!(CraftBlock.at(player.level(), position).getState() instanceof Sign sign)) {
            throw NO_SIGN.create();
        }
        player.getBukkitEntity().openSign(sign, Side.FRONT);
        return 1;
    }

}
