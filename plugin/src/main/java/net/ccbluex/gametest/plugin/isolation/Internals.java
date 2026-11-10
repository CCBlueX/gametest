package net.ccbluex.gametest.plugin.isolation;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Private state of parsed commands that the server has no accessors for. */
public final class Internals {

    private static final Field PREDICATES = field(EntitySelector.class, "contextFreePredicates");
    private static final Field PLAYER_NAME = field(EntitySelector.class, "playerName");
    private static final Field ENTITY_UUID = field(EntitySelector.class, "entityUUID");
    private static final Field SCORE_HOLDER_SELECTOR = field(ScoreHolderArgument.SelectorResult.class, "selector");

    private Internals() {
    }

    /** Fails when the plugin enables on a server whose internals have changed, not at the first command. */
    public static void verify() throws CommandSyntaxException {
        var everything = new EntitySelectorParser(new StringReader("@e"), true).parse();
        var named = new EntitySelectorParser(new StringReader("Name"), true).parse();
        var id = UUID.randomUUID();
        var identified = new EntitySelectorParser(new StringReader(id.toString()), true).parse();

        Predicate<Entity> nothing = _ -> false;
        restrict(everything, nothing);
        var known = read(PREDICATES, everything) instanceof List<?> predicates && predicates.contains(nothing)
            && "Name".equals(playerName(named)) && id.equals(entityId(identified))
            && selector(new ScoreHolderArgument.SelectorResult(everything)) == everything;
        if (!known) {
            throw new IllegalStateException("The entity selectors of this server are not the ones this plugin knows");
        }
    }

    /** Makes the selector match only what it matched before and what {@code allowed} accepts. */
    @SuppressWarnings("unchecked")
    static void restrict(EntitySelector selector, Predicate<Entity> allowed) {
        var predicates = new ArrayList<>((List<Predicate<Entity>>) read(PREDICATES, selector));
        predicates.add(allowed);
        try {
            PREDICATES.set(selector, predicates);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static @Nullable String playerName(EntitySelector selector) {
        return (String) read(PLAYER_NAME, selector);
    }

    static @Nullable UUID entityId(EntitySelector selector) {
        return (UUID) read(ENTITY_UUID, selector);
    }

    static EntitySelector selector(ScoreHolderArgument.SelectorResult result) {
        return (EntitySelector) read(SCORE_HOLDER_SELECTOR, result);
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(owner.getName() + " has no field " + name, exception);
        }
    }

}
