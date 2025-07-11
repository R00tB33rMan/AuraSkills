package dev.aurelium.auraskills.common.loot;

import com.google.common.collect.Sets;
import dev.aurelium.auraskills.api.loot.LootContext;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.source.XpSource;
import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.source.SourceTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;

public class SourceContextProvider extends ContextProvider {

    private final AuraSkillsPlugin plugin;

    public SourceContextProvider(AuraSkillsPlugin plugin) {
        super("sources");
        this.plugin = plugin;
    }

    @Override
    @Nullable
    public Set<LootContext> parseContext(ConfigurationNode config) throws SerializationException {
        Set<LootContext> contexts = Sets.newConcurrentHashSet();
        if (config.node("sources").virtual()) {
            return contexts;
        }
        List<String> sourcesList = config.node("sources").getList(String.class, new ArrayList<>());
        for (String name : sourcesList) {
            NamespacedId sourceId = NamespacedId.fromDefault(name);
            XpSource source = plugin.getSkillManager().getSourceById(sourceId);
            if (source != null) {
                contexts.add(new SourceContext(source));
            } else {
                try {
                    SourceTag tag = SourceTag.valueOf(name.toUpperCase(Locale.ROOT));
                    List<XpSource> sourceList = plugin.getSkillManager().getSourcesWithTag(tag);
                    for (XpSource tagSource : sourceList) {
                        contexts.add(new SourceContext(tagSource));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return contexts;
    }

}
