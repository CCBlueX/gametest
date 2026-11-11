package net.ccbluex.gametest.plugin.isolation;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.ccbluex.gametest.plugin.session.Session;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps a parsed command inside its session, because tests of other runs share the server: only commands a test
 * needs are there, entity selectors see only the session's worlds and its player, a player named outright has to be
 * that player, and the three vanilla dimension names stand for the session's worlds.
 */
public final class Confinement {

    /** What a session may run. A command a new Minecraft version brings stays out until it has been looked at. */
    private static final Set<String> SESSION_ROOTS = Set.of(
        "advancement", "attribute", "bossbar", "clear", "clone", "damage", "data", "dialog", "difficulty", "effect",
        "enchant", "execute", "experience", "xp", "fill", "fillbiome", "forceload", "gamemode", "gamerule", "give",
        "help", "item", "kill", "loot", "msg", "tell", "w", "particle", "place", "playsound", "recipe", "return",
        "ride", "rotate", "scoreboard", "seed", "setblock", "setworldspawn", "spawnpoint", "spectate",
        "spreadplayers", "stopsound", "summon", "swing", "tag", "team", "teleport", "tp", "tellraw", "time",
        "title", "trigger", "version", "waypoint", "weather", "worldborder", "opensign"
    );
    /** Chat that reaches every player, typed by a test player itself. */
    private static final Set<String> PLAYER_REFUSED_ROOTS = Set.of("me", "teammsg", "tm", "list", "random");
    /**
     * Parts of allowed commands that reach beyond the session: {@code execute on} finds owners and attackers in any
     * world, a display slot shows on every client, jigsaw structures can keep the server busy for seconds.
     */
    private static final Map<String, Set<String>> REFUSED_LITERALS = Map.of(
        "execute", Set.of("on", "function"),
        "scoreboard", Set.of("setdisplay"),
        "place", Set.of("jigsaw")
    );
    /** Game rules that bound what one command may cost, and how far a session may raise them. */
    private static final Map<String, Integer> GAME_RULE_LIMITS = Map.of(
        "max_block_modifications", 32768,
        "max_command_forks", 1024,
        "max_command_sequence_length", 65536,
        "random_tick_speed", 100
    );
    /** Makes a sign resolve its text with the server's view of every player. */
    private static final String OP_FEATURES = "allow_op_features";
    private static final Pattern UUID_TEXT = Pattern.compile("^\\p{XDigit}+(-\\p{XDigit}+){4}$");
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

    public static boolean isSessionRoot(String name) {
        return SESSION_ROOTS.contains(withoutPrefix(name));
    }

    public static boolean isRefusedForPlayers(String name) {
        return PLAYER_REFUSED_ROOTS.contains(withoutPrefix(name));
    }

    private static String withoutPrefix(String name) {
        return name.startsWith(VANILLA_PREFIX) ? name.substring(VANILLA_PREFIX.length()) : name;
    }

    /** Confines the command in place. Returns why it must not run at all, or null if it may. */
    public @Nullable String confine(ParseResults<CommandSourceStack> parse) {
        String command = null;
        // `execute ... run` starts a command over in a child context; `execute as` and the like continue the same
        // command in a child context rooted at `execute`
        for (var context = parse.getContext(); context != null; context = context.getChild()) {
            var nodes = context.getNodes();
            if (nodes.isEmpty()) {
                continue;
            }
            if (context.getRootNode() == root) {
                command = withoutPrefix(nodes.getFirst().getNode().getName());
                if (!SESSION_ROOTS.contains(command)) {
                    return "'" + command + "' is not for game test sessions";
                }
            }
            var refusal = confineCommand(command, context, nodes);
            if (refusal != null) {
                return refusal;
            }
            for (var parsed : nodes) {
                if (parsed.getNode() instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                    refusal = confine(context, argument, parse.getReader().getString());
                    if (refusal != null) {
                        return refusal;
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable String confineCommand(
        @Nullable String command,
        CommandContextBuilder<CommandSourceStack> context,
        List<ParsedCommandNode<CommandSourceStack>> nodes
    ) {
        var refused = REFUSED_LITERALS.getOrDefault(command, Set.of());
        for (var parsed : nodes) {
            if (parsed.getNode() instanceof LiteralCommandNode<?> literal && refused.contains(literal.getLiteral())) {
                return "'" + command + " ... " + literal.getLiteral() + "' is not for game test sessions";
            }
        }
        if ("gamerule".equals(command) && nodes.size() > 2) {
            var rule = withoutPrefix(nodes.get(1).getNode().getName());
            var limit = GAME_RULE_LIMITS.get(rule);
            var value = context.getArguments().get("value");
            if (limit != null && value != null && value.getResult() instanceof Integer number && number > limit) {
                return "Game rule " + rule + " goes up to " + limit + " in game test sessions";
            }
            if (rule.equals("global_sound_events") && value != null && Boolean.TRUE.equals(value.getResult())) {
                return "Sounds of one session must not reach the others";
            }
        }
        return null;
    }

    private @Nullable String confine(
        CommandContextBuilder<CommandSourceStack> context, ArgumentCommandNode<CommandSourceStack, ?> argument,
        String input
    ) {
        var parsed = context.getArguments().get(argument.getName());
        if (parsed == null) {
            return null;
        }
        if (argument.getType() instanceof ScoreHolderArgument) {
            return parsed.getResult() instanceof ScoreHolderArgument.SelectorResult holders
                ? confine(Internals.selector(holders))
                : confineScoreHolder(parsed.getRange().get(input));
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
            case MessageArgument.Message message ->
                firstOf(Stream.of(message.parts()).map(part -> confine(part.selector())));
            case Component component -> confine(component);
            case Tag tag -> confine(tag.toString());
            case BlockInput block -> block.tag == null ? null : confine(block.tag.toString());
            case NbtPathArgument.NbtPath path -> confine(path.toString());
            case null, default -> null;
        };
    }

    /** Op features let a sign look up selectors with the server's view of every player. */
    private static @Nullable String confine(String data) {
        return data.contains(OP_FEATURES) ? "'" + OP_FEATURES + "' is not for game test sessions" : null;
    }

    /** A plain name, {@code *} or a UUID finds score holders across the whole server. */
    private @Nullable String confineScoreHolder(String holder) {
        if (holder.equals("*")) {
            return "'*' would reach the score holders of every session";
        }
        if (UUID_TEXT.matcher(holder).matches()) {
            return "Score holder " + holder + " is not in this session";
        }
        var player = Bukkit.getPlayerExact(holder);
        return player == null || player.getUniqueId().equals(session.playerId())
            ? null
            : "Player " + holder + " is not this session's player";
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

    /** The session's player, and anything but players in the session's worlds: spectators watch, they are no fixture. */
    private boolean isInSession(Entity entity) {
        if (entity instanceof Player) {
            return entity.getUUID().equals(session.playerId());
        }
        return entity.level() instanceof ServerLevel level && session.owns(level);
    }

    /** Text components carry selectors of their own, which the server looks up when it sends the text. */
    private @Nullable String confine(Component component) {
        var contents = switch (component.getContents()) {
            case SelectorContents selector -> firstOf(Stream.of(
                confine(selector.selector().compiled()), confine(selector.separator())));
            case ScoreContents score -> score.name().left().map(name -> confine(name.compiled())).orElse(null);
            // Interpreted NBT is parsed as text again, and that text is never seen here
            case NbtContents nbt when nbt.interpreting() -> "Interpreted NBT text is not for game test sessions";
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
