package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.data.PlayerKnowledge;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Общая логика открытия модулей: сканирование и модификации.
 */
public final class KnowledgeHooks {

    private KnowledgeHooks() {
    }

    /**
     * Сканирование цели.
     *
     * @return 1 — открыт новый модуль, 0 — эта цель уже изучена
     */
    public static double scan(Player player, String targetId, SpellContext ctx) {
        // некоторые цели учат модификатор, а не отдельный модуль
        String modifierId = ScanRewards.modifierFor(targetId);
        if (modifierId != null) {
            PlayerKnowledge.markScanned(player, targetId);
            if (PlayerKnowledge.learnModifier(player, modifierId)) {
                var modifier = SpellModifiers.byId(modifierId);
                ctx.trace("NEW MODIFIER: " + modifierId);
                playDiscoveryFx(ctx);
                return 1;
            }
            return 0;
        }
        var rewardId = ScanRewards.rewardId(targetId);
        if (rewardId == null) {
            // сканируем, но ничего не даём — «пустая» цель
            PlayerKnowledge.markScanned(player, targetId);
            ctx.trace("nothing new in " + targetId);
            return 0;
        }
        boolean firstTime = PlayerKnowledge.markScanned(player, targetId);
        var type = SpellModules.get(rewardId);
        if (type == null) {
            return 0;
        }
        boolean learned = PlayerKnowledge.learn(player, rewardId);
        if (learned) {
            ctx.trace("NEW MODULE: " + type.id().getPath() + " (from " + targetId + ")");
            playDiscoveryFx(ctx);
        } else {
            ctx.trace("already known: " + type.id().getPath());
        }
        return (firstTime || learned) ? 1 : 0;
    }

    /**
     * Изучение модификатора: тратит ману и с шансом открывает его навсегда.
     *
     * @return 1 — успех, 0 — не повезло
     */
    public static double learnModifier(Player player, String modifierId, SpellContext ctx) {
        var modifier = SpellModifiers.byId(modifierId);
        if (modifier == null) {
            throw new SpellContext.SpellException("cast.unknown_modifier", modifierId);
        }
        if (PlayerKnowledge.knowsModifier(player, modifierId)) {
            return 1;
        }
        int price = SpellModifiers.learnCost(modifier, SpellModifiers.knownCount(player));
        if (!com.github.fireprime5.spellcraft.energy.ManaCapability.has(player, price)) {
            throw new SpellContext.SpellException("mana.insufficient", price);
        }
        com.github.fireprime5.spellcraft.energy.ManaCapability.consumeMana(player, price);
        ctx.spendMana(price);

        boolean success = player.level().getRandom().nextDouble() < modifier.learnChance();
        if (success) {
            PlayerKnowledge.learnModifier(player, modifierId);
            ctx.trace("MODIFIER LEARNED: " + modifierId);
            playDiscoveryFx(ctx);
        } else {
            ctx.trace("modifier failed: " + modifierId + " (-" + price + " mana)");
        }
        return success ? 1 : 0;
    }

    /** Стоимость изучения модификатора для игрока (0 если уже изучен). */
    public static int learnPrice(Player player, SpellModifier modifier) {
        return PlayerKnowledge.knowsModifier(player, modifier.id())
                ? 0
                : SpellModifiers.learnCost(modifier, SpellModifiers.knownCount(player));
    }

    private static void playDiscoveryFx(SpellContext ctx) {
        if (ctx.level() instanceof net.minecraft.server.level.ServerLevel server) {
            var pos = ctx.caster().position();
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.2, pos.z, 40, 0.6, 0.8, 0.6, 0.05);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    pos.x, pos.y + 1.0, pos.z, 12, 0.4, 0.5, 0.4, 0.02);
            server.playSound(null, pos.x, pos.y + 1, pos.z,
                    net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.6f);
        }
    }
}