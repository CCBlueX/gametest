package net.ccbluex.gametest.plugin.isolation;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.ccbluex.gametest.plugin.session.Session;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.data.EntityDataSource;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Keeps a parsed command inside its session, because tests of other runs share the server: entity selectors
 * see only the session's worlds and its player, a player named outright has to be that player, and the three
 * vanilla dimension names stand for the session's worlds.
 */
public final class Confinement {

    /** Server wide effects, the server's own state, and chat that reaches every player. */
    private static final Set<String> REFUSED_ROOTS = Set.of(
        "reload", "datapack", "defaultgamemode", "say", "function", "schedule", "debug", "perf", "jfr", "tick",
        "save-all", "save-on", "save-off", "stop", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick",
        "whitelist", "publish", "transfer",
        "me", "teammsg", "tm", "list"
    );
    private static final String VANILLA_PREFIX = "minecraft:";

    private final CommandNode<CommandSourceStack> root;
    private final Session session;
    private final Function<ResourceKey<Level>, @Nullable ServerLevel> dimensions;

    /** @param dimensions the session's world for a vanilla dimension, created if need be, or null for any other */
    public Confinement(
        CommandNode<CommandSourceStack> root,
        Session session,
        Function<ResourceKey<Level>, @Nullable ServerLevel> dimensions
    ) {
        this.root = root;
        this.session = session;
        this.dimensions = dimensions;
    }

    public static boolean isRefusedRoot(String name) {
        return REFUSED_ROOTS.contains(name.startsWith(VANILLA_PREFIX) ? name.substring(VANILLA_PREFIX.length()) : name);
    }

    /** Confines the command in place. Returns why it must not run at all, or null if it may. */
    public @Nullable String confine(ParseResults<CommandSourceStack> parse) {
        // `execute ... run` and aliases continue in a child context
        for (var context = parse.getContext(); context != null; context = context.getChild()) {
            var nodes = context.getNodes();
            var isCommand = context.getRootNode() == root && !nodes.isEmpty();
            if (isCommand && isRefusedRoot(nodes.getFirst().getNode().getName())) {
                return "'" + nodes.getFirst().getNode().getName() + "' is not for game test sessions";
            }
            for (var parsed : nodes) {
                if (parsed.getNode() instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                    var refusal = confine(context, argument);
                    if (refusal != null) {
                        return refusal;
                    }
                }
            }
        }
        return null;
    }

    private @Nullable String confine(
        CommandContextBuilder<CommandSourceStack> context, ArgumentCommandNode<CommandSourceStack, ?> argument
    ) {
        var parsed = context.getArguments().get(argument.getName());
        if (parsed == null) {
            return null;
        }
        if (!(argument.getType() instanceof DimensionArgument)) {
            return confine(parsed.getResult());
        }

        var named = (Identifier) parsed.getResult();
        var level = dimensions.apply(ResourceKey.create(Registries.DIMENSION, named));
        if (level == null) {
            return "Dimension " + named + " is not one of this session's:"
                + " minecraft:overworld, minecraft:the_nether, minecraft:the_end";
        }
        var range = parsed.getRange();
        var actual = new ParsedArgument<CommandSourceStack, Identifier>(
            range.getStart(), range.getEnd(), level.dimension().identifier());
        context.withArgument(argument.getName(), actual);
        return null;
    }

    private @Nullable String confine(@Nullable Object argument) {
        return switch (argument) {
            case EntitySelector selector -> confine(selector);
            case ScoreHolderArgument.SelectorResult holders -> confine(Internals.selector(holders));
            case MessageArgument.Message message ->
                firstOf(Stream.of(message.parts()).map(part -> confine(part.selector())));
            case Component component -> confine(component);
            case null, default -> null;
        };
    }

    private @Nullable String confine(EntitySelector selector) {
        var name = Internals.playerName(selector);
        var id = Internals.entityId(selector);
        if (name != null) {
            var own = name.equalsIgnoreCase(session.playerName());
            return own ? null : "Player " + name + " is not this session's player";
        }
        if (id != null) {
            var known = id.equals(session.playerId())
                || session.worlds().stream().anyMatch(world -> Session.level(world).getEntity(id) != null);
            return known ? null : "Entity " + id + " is not in this session's worlds";
        }
        Internals.restrict(selector, this::isInSession);
        return null;
    }

    /** The session's player counts wherever it is, so that selectors find it while it still waits in the lobby. */
    private boolean isInSession(Entity entity) {
        return entity.getUUID().equals(session.playerId())
            || entity.level() instanceof ServerLevel level && session.owns(level);
    }

    /** Text components carry selectors of their own, which the server looks up when it sends the text. */
    private @Nullable String confine(Component component) {
        var contents = switch (component.getContents()) {
            case SelectorContents selector -> firstOf(Stream.of(
                confine(selector.selector().compiled()), confine(selector.separator())));
            case ScoreContents score -> score.name().left().map(name -> confine(name.compiled())).orElse(null);
            case NbtContents nbt -> firstOf(Stream.of(
                nbt.dataSource() instanceof EntityDataSource entity ? confine(entity.selector().compiled()) : null,
                confine(nbt.separator())));
            case TranslatableContents translatable -> firstOf(Stream.of(translatable.getArgs())
                .map(value -> value instanceof Component text ? confine(text) : null));
            default -> null;
        };
        var hover = component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component text)
            ? confine(text)
            : null;
        return firstOf(Stream.concat(Stream.of(contents, hover), component.getSiblings().stream().map(this::confine)));
    }

    private @Nullable String confine(Optional<Component> component) {
        return component.map(this::confine).orElse(null);
    }

    private static @Nullable String firstOf(Stream<@Nullable String> refusals) {
        return refusals.filter(Objects::nonNull).findFirst().orElse(null);
    }

}
