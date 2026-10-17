package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.energy.AbyssEnergy;
import com.github.fireprime5.spellcraft.spell.SpellContext;
import com.github.fireprime5.spellcraft.spell.SpellExecutor;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import com.github.fireprime5.spellcraft.spell.ShieldModules;
import com.github.fireprime5.spellcraft.spell.UniqueModules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Что модули делают, пока броня надета.
 *
 * <p>Модули брони не исполняются как заклинание целиком: у них нет «запуска»,
 * они висят на груди постоянно. Поэтому схема читается, а не кастуется —
 * триггер «когда ударили» срабатывает от события урона, а полёт действует, пока
 * модуль стоит в сетке.
 *
 * <p>Без заряда бездны поведение выключается целиком. Иначе броня была бы
 * бесплатной: игрок собрал бы схему один раз и держал её вечно.
 */
public final class ArmorEvents {

    /** Поле «броня надета в этом тике»: событие урона может прийти в том же тике. */
    private static final java.util.Set<java.util.UUID> CHARGED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Игроки, которым МОД выдал право полёта (mayfly).
     *
     * <p>Нужно, чтобы снимать ровно то, что включили сами: иначе при снятии
     * брони мод затирал бы ванильный креативный полёт.
     */
    private static final java.util.Set<java.util.UUID> FLYING =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Исходное право полёта игрока до вмешательства мода (по UUID).
     *
     * <p>В креативе {@code mayfly} изначально true, в выживании — false. Когда
     * броня перестаёт давать полёт, возвращаем именно сохранённое значение, а
     * не всегда false: иначе настройка креативного полёта молча ломалась бы.
     */
    private static final java.util.Map<java.util.UUID, Boolean> SAVED_MAYFLY =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Защита игрока в этом тике (множитель входящего урона), по UUID.
     *
     * <p>Множитель считает прогон схемы брони в тике, а применяется он в событии
     * урона ({@link #onIncomingDamage}): урон делится на это число.
     */
    private static final java.util.Map<java.util.UUID, Double> PROTECTION =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Последнее состояние боевых модификаторов для тихого диагностического лога. */
    private static final java.util.Map<java.util.UUID, String> LAST_MODS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Стабильные id модификаторов атрибутов.
     *
     * <p>Модификаторы транзиентные (transient — не сохраняются в NBT) и каждый тик
     * перезаписываются по одному и тому же id, поэтому накапливаться не могут.
     */
    private static final ResourceLocation SPEED_ID =
            com.github.fireprime5.spellcraft.Spellcraft.id("armor_speed");
    private static final ResourceLocation DAMAGE_ID =
            com.github.fireprime5.spellcraft.Spellcraft.id("armor_damage");

    /**
     * Как часто (в тиках) списывается налог бездны за броню. 40 тиков = 2 секунды.
     *
     * <p>Раньше налог шёл каждый тик, и кошелёк таял за секунды: в тики, когда
     * энергии не хватало, эффекты снимались и снова накладывались — скорость мигала,
     * полёт «не взлетал». Теперь налог редкий, а эффекты держатся, пока нагрудник
     * надет, и не зависят от покадровой бездны.
     */
    private static final int CHARGE_INTERVAL = 40;

    /** Тик последнего списания налога (по UUID) — для интервального списания. */
    private static final java.util.Map<java.util.UUID, Long> LAST_CHARGE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Игроки, у которых броня СЕЙЧАС работает (хватает бездны), по UUID.
     *
     * <p>Состояние пересчитывается раз в {@link #CHARGE_INTERVAL} тиков, а не
     * каждый тик. Благодаря этому броня не мигает: пока не подошёл срок проверки,
     * эффекты держатся, а при нехватке бездны отключаются ровно один раз.
     */
    private static final java.util.Set<java.util.UUID> ARMOR_ON =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Игроки, которым уже сказали, что «Вечность» не досталась из-за лимита.
     *
     * <p>Отдельно от {@link Eternity}: там хранится «защита работает», а здесь —
     * «игрок уже в курсе, что места нет». Без этого флага сообщение уходило бы
     * каждый тик и превращалось в спам на экране.
     */
    private static final java.util.Set<java.util.UUID> DENIED_NOTIFIED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** Сколько энергии бездны стоит один перехваченный удар. */
    private static final int ETERNITY_COST = 1;

    public static void register(IEventBus bus) {
        bus.register(ArmorEvents.class);
    }

    private ArmorEvents() {
    }

    /** Нагрудник игрока или null. */
    private static ItemStack chestplate(Player player) {
        ItemStack stack = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        return stack.isEmpty() ? null : stack;
    }

    private static ModularSchema schema(Player player) {
        ItemStack stack = chestplate(player);
        if (stack == null || !(stack.getItem() instanceof ModularChestplateItem)) {
            return ModularSchema.EMPTY;
        }
        return ModularChestplateItem.schemaOf(stack);
    }

    /**
     * Тик брони: редкий налог бездны и применение эффектов.
     *
     * <p>Решение «броня работает» принимается раз в {@link #CHARGE_INTERVAL} тиков
     * в {@link #chargeEnergy} (по наличию бездны), а не каждый тик. Поэтому броня не
     * мигает: пока не подошёл срок проверки, эффекты держатся; при нехватке бездны
     * они снимаются ровно один раз и не возвращаются, пока игрок не добудет энергию.
     *
     * <p>Способности полёта мод трогает ТОЛЬКО для своего нагрудника: чужой/пустой
     * игрок свой креативный полёт не теряет.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        ItemStack stack = chestplate(player);
        boolean wearingModular = stack != null && stack.getItem() instanceof ModularChestplateItem;
        if (!wearingModular) {
            // Нагрудник снят (или это не наш): снимаем ВСЁ, что давали.
            restoreFlight(player);
            clearCombat(player);
            ARMOR_ON.remove(player.getUUID());
            LAST_CHARGE.remove(player.getUUID());
            CHARGED.remove(player.getUUID());
            updateEternity(player, false);
            return;
        }

        ModularSchema schema = ModularChestplateItem.schemaOf(stack);
        chargeEnergy(player, schema);

        // Бездны нет — броня молчит (эффекты сняты ещё в chargeEnergy при отключении).
        if (!ARMOR_ON.contains(player.getUUID())) {
            restoreFlight(player);
            clearCombat(player);
            CHARGED.remove(player.getUUID());
            updateEternity(player, false);
            return;
        }

        // «Вечность» требует живого владельца: у трупа на экране смерти
        // место занимать незачем, иначе погибший держал бы лимит до респауна.
        updateEternity(player, player.isAlive() && schema.has(UniqueModules.ETERNITY));
        updateBarrier(player, schema);

        // Броня работает: накладываем/обновляем эффекты по схеме.
        if (schema.has(ArmorModuleIds.CREATIVE_FLIGHT)) {
            grantFlight(player);
        } else {
            restoreFlight(player);
        }
        SpellContext ctx = new SpellContext(player.level(), player, player.getEyePosition());
        SpellExecutor.runInto(schema.view(), ctx);
        applyCombat(player, ctx.armorModifier(ArmorModuleIds.MODIFIER_SPEED),
                ctx.armorModifier(ArmorModuleIds.MODIFIER_DAMAGE),
                ctx.armorModifier(ArmorModuleIds.MODIFIER_PROTECTION));
        CHARGED.add(player.getUUID());
    }

    /**
     * Барьер в броне: держится, пока нагрудник работает, и снимается сам.
     *
     * <p>Срок поднимается заново каждый тик — это и есть «пока работает». Отдельно
     * следим, чтобы защита не пережила снятие брони: когда модуля в схеме больше
     * нет, никто её не продлит, и через несколько тиков она снимется сама.
     */
    private static void updateBarrier(Player player, ModularSchema schema) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer серверный)) {
            return;
        }
        long now = player.level().getGameTime();
        if (!schema.has(ShieldModules.BARRIER)) {
            ShieldEvents.clearExpiring(серверный, BARRIER_RAISED_AT.getOrDefault(
                    player.getUUID(), now));
            return;
        }
        BARRIER_RAISED_AT.put(player.getUUID(), now);
        ShieldEvents.refreshFromArmor(серверный, Shield.Shape.SPHERE,
                hasProjectilesOnly(schema));
    }

    /** Тик, когда барьер в броне поднимали последний раз. */
    private static final java.util.Map<java.util.UUID, Long> BARRIER_RAISED_AT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Включён ли «только снаряды» — галочка на модуле барьера. */
    private static boolean hasProjectilesOnly(ModularSchema schema) {
        return schema.modifiersOf(ShieldModules.BARRIER).contains(
                com.github.fireprime5.spellcraft.spell.SpellModifiers.PROJECTILES_ONLY.id());
    }

    /**
     * Пересчитать «Вечность» для игрока и один раз сказать, если места не нашлось.
     *
     * <p>Проверка идёт каждый тик, а сообщение — только на переходе «хотел и не
     * получил» → «получил». Иначе игрок без «Вечности» читал бы её отказ каждый
     * тик, и поверх экрана поехала бы простыня.
     */
    private static void updateEternity(Player player, boolean wants) {
        java.util.UUID id = player.getUUID();
        if (Eternity.refresh(player, wants) == Eternity.State.DENIED) {
            if (DENIED_NOTIFIED.add(id)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.spellcraft.eternity_full"), true);
            }
        } else {
            DENIED_NOTIFIED.remove(id);
        }
    }

    /**
     * Раз в {@link #CHARGE_INTERVAL} тиков проверяет и списывает налог бездны.
     *
     * <p>Хватает — броня включена (эффекты применятся в этом же тике). Не хватает —
     * броня выключена (эффекты снимутся в этом же тике). Между проверками состояние
     * не меняется, поэтому мигания нет.
     */
    private static void chargeEnergy(Player player, ModularSchema schema) {
        // Расход в секунду, переведённый в налоговый период. Иначе «тридцать в
        // секунду» пришлось бы умножать на длину периода в трёх местах, и одна
        // из правок рано или поздно забылась бы, а налог тихо поехал бы.
        int cost = (int) Math.ceil(
                schema.armorUpkeepPerSecond() * (CHARGE_INTERVAL / 20.0));
        if (cost <= 0) {
            // облагать нечего: налога нет, работать всё равно нечему, кроме
            // «Вечности», а она проверяет бездну сама на каждом ударе
            ARMOR_ON.add(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_CHARGE.get(player.getUUID());
        if (last == null) {
            LAST_CHARGE.put(player.getUUID(), now);
            // первый тик после надевания: определяем состояние по текущей бездне
            if (AbyssEnergy.has(player, cost)) {
                ARMOR_ON.add(player.getUUID());
            } else {
                ARMOR_ON.remove(player.getUUID());
            }
            return;
        }
        if (now - last < CHARGE_INTERVAL) {
            // между интервалами решение не меняется — броня не мигает
            return;
        }
        LAST_CHARGE.put(player.getUUID(), now);
        if (AbyssEnergy.has(player, cost)) {
            AbyssEnergy.consume(player, cost);
            ARMOR_ON.add(player.getUUID());
        } else {
            // бездна кончилась: броня отключается ровно один раз
            ARMOR_ON.remove(player.getUUID());
        }
    }

    /**
     * Применить боевые множители к игроку.
     *
     * <p>Скорость и урон идут через атрибуты (умножение общего итога), защита — в
     * карту {@link #PROTECTION}, откуда событие урона потом её читает. Значение 1.0
     * (пустой вход или модуля нет) означает «без изменений» и снимает модификатор.
     */
    private static void applyCombat(Player player, double speed, double damage, double protection) {
        setMultiply(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speed);
        setMultiply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage);
        PROTECTION.put(player.getUUID(), protection);
        // лог только при изменении состояния, чтобы не спамить каждый тик
        String current = String.format("speed=%.2f damage=%.2f protection=%.2f", speed, damage, protection);
        if (!current.equals(LAST_MODS.put(player.getUUID(), current))) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Armor modifiers for {}: {}", player.getName().getString(), current);
        }
    }

    /** Снять все боевые эффекты брони (нагрудник снят). */
    private static void clearCombat(Player player) {
        boolean hadAny = LAST_MODS.remove(player.getUUID()) != null;
        removeMultiply(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeMultiply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
        if (PROTECTION.remove(player.getUUID()) != null) {
            hadAny = true;
        }
        if (hadAny) {
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Armor: cleared combat modifiers from {}", player.getName().getString());
        }
    }

    /** Поставить/обновить транзиентный множитель атрибута (умножение общего итога). */
    private static void setMultiply(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                    ResourceLocation id, double multiplier) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        // 1.0 — без изменений: не держим лишний модификатор, а снимаем наш
        if (!Double.isFinite(multiplier) || Math.abs(multiplier - 1.0) < 1e-6 || multiplier <= 0.0) {
            inst.removeModifier(id);
            return;
        }
        // Именно addOrUpdate, а не add: броня перевыставляет множитель каждый тик,
        // а обычный addTransientModifier кидает IllegalArgumentException
        // «Modifier is already applied on this attribute!» со второго тика.
        inst.addOrUpdateTransientModifier(new AttributeModifier(id, multiplier - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeMultiply(Player player,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    /** Выдать полёт, если игрок изначально не мог летать. */
    private static void grantFlight(Player player) {
        // Игрок и так может летать (креатив) — не наше дело. Если лезть сюда,
        // мод каждый раз «забирает» чужой полёт при restoreFlight и роняет
        // игрока, а на клиенте это выглядит как «попытка взлёта не удаётся».
        if (player.getAbilities().mayfly) {
            return;
        }
        if (!FLYING.contains(player.getUUID())) {
            // исходно игрок летать не мог: запоминаем false и метим, что полёт
            // дали МЫ, чтобы вернуть именно false, а не оставить висеть навсегда
            SAVED_MAYFLY.put(player.getUUID(), false);
            FLYING.add(player.getUUID());
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Armor: granted flight to {}", player.getName().getString());
        }
        player.getAbilities().mayfly = true;
        // без этого клиент не узнаёт о новом праве, и двойной прыжок не взлетит
        player.onUpdateAbilities();
    }

    /** Вернуть игроку прежнее право полёта, если его менял мод. */
    private static void restoreFlight(Player player) {
        if (!FLYING.remove(player.getUUID())) {
            // мы не выдавали этот полёт — ничего не трогаем (в т.ч. креатив)
            return;
        }
        boolean original = Boolean.TRUE.equals(SAVED_MAYFLY.remove(player.getUUID()));
        // Режим мог смениться после выдачи полёта бронёй: теперь им владеет ваниль.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.getAbilities().mayfly != original) {
            player.getAbilities().mayfly = original;
            // право отозвано — игрок обязан упасть на землю, иначе повиснет в воздухе
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Armor: revoked flight from {}", player.getName().getString());
        }
    }

    /**
     * Урон по игроку: модуль «когда ударили».
     *
     * <p>Срабатывает на входящий урон, а не на атаку: броня защищает владельца,
     * и «когда ударили» в её пользу — это реакция на полученный урон.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCanceled()) {
            return;
        }
        // «Вечность» стоит ПЕРВОЙ: она отменяет урон целиком, поэтому дальше
        // защита брони и триггер «при уроне» работают вхолостую. И наоборот —
        // если энергии не хватило, защита падает и удар идёт дальше как обычно.
        if (Eternity.isActive(player) && absorbEternity(event, player)) {
            return;
        }
        // Защита брони: множитель посчитан прогоном схемы в тике и лежит в
        // PROTECTION. Работает от одного присутствия модуля «Защита», пока нагрудник
        // надет (иначе PROTECTION пуст), и не требует триггера «при уроне».
        double protection = PROTECTION.getOrDefault(player.getUUID(), 1.0);
        if (protection > 0.0001 && Math.abs(protection - 1.0) > 1e-6) {
            float reduced = (float) Math.max(0.0, event.getAmount() / protection);
            com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                    "Armor protection: {} incoming {} -> {} (x{})",
                    player.getName().getString(), event.getAmount(), reduced, protection);
            event.setAmount(reduced);
        }
        ModularSchema schema = schema(player);
        if (!schema.has(ArmorModuleIds.TRIGGER_ON_HURT)
                || !CHARGED.contains(player.getUUID())) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0) {
            return;
        }
        // поглощаем часть урона тем же исполнителем, что и заклинания: одна
        // логика модулей, чтобы «щит» работал одинаково от руки и от брони
        SpellGraph graph = schema.view();
        if (graph.nodes.isEmpty()) {
            return;
        }
        SpellExecutor.execute(graph, player.level(), player, player.getEyePosition());
        // исполнение не уменьшает урон само: модули читают и пишут состояние,
        // а урон остаётся ванильным. Модуль, который хочет помешать урону,
        // должен быть отдельным триггером с отменой события.
    }

    /**
     * Перехватить удар «Вечностью», заплатив одну энергию бездны.
     *
     * <p>Урон отменяется всегда, пока защита активна, — за него платит модуль.
     * А вот сама оплата не за каждый тик: контактный урон слини идёт непрерывно,
     * и без окна в {@link Eternity#CHARGE_INTERVAL} игрок стоял бы вплотную и
     * терял по единице двадцать раз в секунду.
     *
     * <p>Возвращает {@code false}, если бездны не хватило: защита снимается, а
     * урон остаётся неотменённым — именно этот удар игрока и убивает. Так «Вечность»
     * остаётся конечной: она отменяет урон, но не отменяет смерть.
     *
     * <p>Бесконечная бездна (креатив) не списывается: {@link AbyssEnergy#consume}
     * при ней и так ничего не делает, поэтому «Вечность» честно становится вечной.
     */
    private static boolean absorbEternity(LivingIncomingDamageEvent event, Player player) {
        if (!AbyssEnergy.isInfinite(player)) {
            java.util.UUID id = player.getUUID();
            long now = player.level().getGameTime();
            if (Eternity.chargeDue(id, now)) {
                // без скидки «Глаза»: плата за перехваченный удар не за действие,
                // а за то, что удар вообще был. Скидка на касты и налог брони
                // здесь превращала бы «Вечность» в бесплатную
                if (!AbyssEnergy.hasRaw(player, ETERNITY_COST)) {
                    Eternity.deactivate(player);
                    DENIED_NOTIFIED.remove(id);
                    return false;
                }
                AbyssEnergy.consumeRaw(player, ETERNITY_COST);
            }
        }
        event.setCanceled(true);
        return true;
    }

    /**
     * Урон, нанесённый игроком: модуль «когда ударил».
     *
     * <p>Именно этот триггер нужен для отражения и поджога — здесь игрок
     * выступает источником, иначе броня была бы бессильна в драке.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        // Post: урон уже посчитан, но ещё не применён. Источник урона —
        // это тот, кого мы ищем: игрок, ударивший моба или игрока
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!schema(player).has(ArmorModuleIds.TRIGGER_ON_HIT)
                || !CHARGED.contains(player.getUUID())) {
            return;
        }
        com.github.fireprime5.spellcraft.Spellcraft.LOGGER.debug(
                "Armor trigger_on_hit: {} hit {}", player.getName().getString(),
                event.getEntity().getName().getString());
        SpellGraph graph = schema(player).view();
        if (!graph.nodes.isEmpty()) {
            SpellExecutor.execute(graph, player.level(), player, player.getEyePosition());
        }
    }

    /** Сброс полёта при смерти и смене измерения. */
    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // новый игрок после смерти — это уже другой объект Player: сохранённое
        // право полёта и флаг «мы выдали» к нему не применимы, иначе mayfly
        // протек бы через респаун. Способности задаст ваниль по режиму игры.
        clearPlayerState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        // Не сохраняем выданный бронёй mayfly в данных вышедшего игрока.
        restoreFlight(player);
        clearCombat(player);
        clearPlayerState(player.getUUID());
    }

    private static void clearPlayerState(java.util.UUID id) {
        CHARGED.remove(id);
        PROTECTION.remove(id);
        FLYING.remove(id);
        SAVED_MAYFLY.remove(id);
        LAST_CHARGE.remove(id);
        ARMOR_ON.remove(id);
        LAST_MODS.remove(id);
        // без этого вышедший игрок держал бы одно из трёх мест «Вечности»
        // до конца сервера: тик у него больше не идёт, а разжал бы некому
        Eternity.clear(id);
        DENIED_NOTIFIED.remove(id);
    }
}
