package dev.aurelium.auraskills.bukkit.skills.healing;

import dev.aurelium.auraskills.api.ability.Abilities;
import dev.aurelium.auraskills.api.stat.StatModifier;
import dev.aurelium.auraskills.api.stat.Stats;
import dev.aurelium.auraskills.api.util.AuraSkillsModifier.Operation;
import dev.aurelium.auraskills.api.util.NumberUtil;
import dev.aurelium.auraskills.bukkit.AuraSkills;
import dev.aurelium.auraskills.bukkit.ability.BukkitAbilityImpl;
import dev.aurelium.auraskills.common.message.type.AbilityMessage;
import dev.aurelium.auraskills.common.user.User;
import dev.aurelium.auraskills.common.util.text.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class HealingAbilities extends BukkitAbilityImpl {

    private static final String REVIVAL_HEALTH_MODIFIER_NAME = "AureliumSkills.Ability.Revival.Health";
    private static final String REVIVAL_REGEN_MODIFIER_NAME = "AureliumSkills.Ability.Revival.Regeneration";

    public HealingAbilities(AuraSkills plugin) {
        super(plugin, Abilities.LIFE_ESSENCE, Abilities.HEALER, Abilities.LIFE_STEAL, Abilities.GOLDEN_HEART, Abilities.REVIVAL);
    }

    @EventHandler
    public void lifeEssence(EntityRegainHealthEvent event) {
        var ability = Abilities.LIFE_ESSENCE;

        if (isDisabled(ability)) return;

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (failsChecks(player, ability)) return;

        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.MAGIC) {
            return;
        }

        User user = plugin.getUser(player);

        double multiplier = 1 + getValue(ability, user) / 100;
        event.setAmount(event.getAmount() * multiplier);
    }

    @EventHandler
    public void revival(PlayerRespawnEvent event) {
        var ability = Abilities.REVIVAL;

        if (isDisabled(ability)) return;

        Player player = event.getPlayer();

        if (failsChecks(player, ability)) return;

        User user = plugin.getUser(player);

        double healthBonus = getValue(ability, user);
        double regenerationBonus = getSecondaryValue(ability, user);

        StatModifier healthModifier = new StatModifier(REVIVAL_HEALTH_MODIFIER_NAME, Stats.HEALTH, healthBonus, Operation.ADD);
        StatModifier regenerationModifier = new StatModifier(REVIVAL_REGEN_MODIFIER_NAME, Stats.REGENERATION, regenerationBonus, Operation.ADD);

        user.addStatModifier(healthModifier);
        user.addStatModifier(regenerationModifier);
        if (ability.optionBoolean("enable_message", true)) {
            Locale locale = user.getLocale();
            plugin.getAbilityManager().sendMessage(player,
                    TextUtil.replace(plugin.getMsg(AbilityMessage.REVIVAL_MESSAGE, locale),
                            "{value}", NumberUtil.format1(healthBonus),
                            "{value_2}", NumberUtil.format1(regenerationBonus)));
        }

        plugin.getScheduler().scheduleAtEntity(player, () -> {
            user.removeStatModifier(REVIVAL_HEALTH_MODIFIER_NAME);
            user.removeStatModifier(REVIVAL_REGEN_MODIFIER_NAME);
        }, 30, TimeUnit.SECONDS);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void revivalLeave(PlayerQuitEvent event) {
        User user = plugin.getUser(event.getPlayer());

        user.removeStatModifier(REVIVAL_HEALTH_MODIFIER_NAME);
        user.removeStatModifier(REVIVAL_REGEN_MODIFIER_NAME);
    }

}
