package dev.aurelium.auraskills.bukkit.loot.context;

import com.google.common.collect.Sets;
import dev.aurelium.auraskills.api.loot.LootContext;
import dev.aurelium.auraskills.common.loot.ContextProvider;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;

public class MobContextProvider extends ContextProvider {

    public MobContextProvider() {
        super("mobs");
    }

    @Override
    public @Nullable Set<LootContext> parseContext(ConfigurationNode config) throws SerializationException {
        Set<LootContext> contexts = Sets.newConcurrentHashSet();
        if (config.node("mobs").virtual()) {
            return contexts;
        }
        List<String> mobList = config.node("mobs").getList(String.class, new ArrayList<>());
        for (String name : mobList) {
            EntityType entityType = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            contexts.add(new MobContext(entityType));
        }
        return contexts;
    }

}
