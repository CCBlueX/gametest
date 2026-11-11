package net.ccbluex.gametest.plugin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.ccbluex.gametest.plugin.anticheat.Probe;
import net.ccbluex.gametest.plugin.anticheat.Sandbox;
import net.ccbluex.gametest.plugin.control.Control;
import net.ccbluex.gametest.plugin.control.ControlListener;
import net.ccbluex.gametest.plugin.control.LogForwarder;
import net.ccbluex.gametest.plugin.isolation.DimensionRewriter;
import net.ccbluex.gametest.plugin.isolation.Internals;
import net.ccbluex.gametest.plugin.isolation.Isolation;
import net.ccbluex.gametest.plugin.session.SessionCommands;
import net.ccbluex.gametest.plugin.session.SessionWorlds;
import net.ccbluex.gametest.plugin.session.Sessions;
import net.ccbluex.gametest.plugin.session.SharedState;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class GameTestServer extends JavaPlugin {

    private final TestPlayers players = new TestPlayers();
    private @Nullable LogForwarder logs;

    @Override
    public void onEnable() {
        Probe probe;
        try {
            Internals.verify();
            probe = Probe.start(this, players);
        } catch (CommandSyntaxException | ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        saveDefaultConfig();

        var lobby = Lobby.prepare(getServer());
        var worlds = new SessionWorlds(this, lobby);
        worlds.deleteLeftovers();
        var commands = new SessionCommands(this, worlds);
        var sessions = new Sessions(this, lobby, worlds, commands, getConfig().getInt("max-sessions"));

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(players, this);
        plugins.registerEvents(Sandbox.start(), this);
        plugins.registerEvents(commands, this);
        plugins.registerEvents(sessions, this);
        plugins.registerEvents(sessions.budget(), this);
        plugins.registerEvents(new Isolation(this, lobby, sessions, worlds), this);
        plugins.registerEvents(probe, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, Control.CHANNEL, new ControlListener(sessions));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
            WatchCommand.create(sessions), "Lists the running game test sessions or takes you to one."));
        DimensionRewriter.install(sessions);
        logs = LogForwarder.start(players, sessions, getSLF4JLogger().getName());
        SharedState.clear();
    }

    @Override
    public void onDisable() {
        DimensionRewriter.uninstall();
        if (logs != null) {
            logs.shutDown();
        }
    }

}
