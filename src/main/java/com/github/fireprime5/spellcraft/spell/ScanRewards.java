package com.github.fireprime5.spellcraft.spell;

import com.github.fireprime5.spellcraft.Spellcraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Таблица наград за сканирование: что за объект → какой модуль открывается.
 *
 * Сканирование блока: id из реестра блоков (например "minecraft:obsidian").
 * Сканирование существа: id из реестра мобов (например "minecraft:creeper").
 */
public final class ScanRewards {

    private ScanRewards() {
    }

    /** Цели, которые учат модификатор, а не отдельный модуль. */
    private static final Map<String, String> MODIFIER_BLOCKS = new HashMap<>();

    private static final Map<String, String> BLOCKS = new HashMap<>();
    private static final Map<String, String> MOBS = new HashMap<>();

    private static void block(String id, String module) {
        BLOCKS.put("minecraft:" + id, module);
    }

    private static void mob(String id, String module) {
        MOBS.put("minecraft:" + id, module);
    }

    static {
        // древний мусор учит мгновенной добыче (это модификатор, а не модуль)
        MODIFIER_BLOCKS.put("minecraft:ancient_debris", "instant");
        // --- разведка (самое первое, что игрок делает) ---
        block("stone", "sparkle");
        block("cobblestone", "sparkle");
        block("deepslate", "sparkle");
        block("calcite", "sparkle");
        block("tuff", "sparkle");
        block("dirt", "is_air");
        block("grass_block", "is_air");
        block("podzol", "is_air");
        block("snow", "is_air");
        block("sand", "impulse");
        block("red_sand", "impulse");
        block("gravel", "impulse");
        block("clay", "impulse");

        // --- постройка ---
        block("oak_log", "place_block");
        block("birch_log", "place_block");
        block("spruce_log", "place_block");
        block("jungle_log", "place_block");
        block("acacia_log", "place_block");
        block("dark_oak_log", "place_block");
        block("oak_planks", "place_block");
        block("cobblestone_wall", "place_block");
        block("stone_bricks", "place_block");

        // --- жидкости и огонь ---
        block("water", "drain_liquids");
        block("lava", "drain_liquids");
        block("netherrack", "ignite");
        block("soul_sand", "ignite");
        block("basalt", "ignite");
        block("blackstone", "ignite");
        block("magma_block", "ignite");
        block("cactus", "effect_poison");
        block("campfire", "sound");
        block("lantern", "block_light");
        block("glowstone", "block_light");
        block("sea_lantern", "block_light");
        block("torch", "block_light");
        block("redstone_ore", "effect_speed");
        block("coal_ore", "damage");
        block("deepslate_coal_ore", "damage");
        block("copper_ore", "effect_strength");

        // --- руды и редкости ---
        block("iron_ore", "effect_strength");
        block("deepslate_iron_ore", "effect_strength");
        block("gold_ore", "heal_percent");
        block("deepslate_gold_ore", "heal_percent");
        block("lapis_ore", "negate");
        block("deepslate_lapis_ore", "negate");
        block("diamond_ore", "fireball");
        block("deepslate_diamond_ore", "fireball");
        block("emerald_ore", "select_random");
        block("deepslate_emerald_ore", "select_random");
        block("obsidian", "break_block");
        block("crying_obsidian", "break_block");
        block("netherite_block", "effect_speed");
        block("beacon", "effect_regeneration");
        block("conduit", "effect_water_breathing");
        block("enchanting_table", "duplicate");
        block("anvil", "set_health");
        block("bell", "sound_at");
        block("spawner", "lightning");
        block("end_portal_frame", "world_spawn");
        block("lodestone", "is_player");
        block("chain", "impulse");
        block("ladder", "teleport");
        block("vine", "teleport");
        block("honey_block", "heal_percent");
        block("slime_block", "effect_jump_boost");
        block("honeycomb_block", "effect_regeneration");
        block("amethyst_block", "stack_dup");
        block("amethyst_cluster", "stack_dup");
        block("sculk", "nearest_entity");
        block("sculk_catalyst", "nearest_entity");
        block("sculk_shrieker", "select");
        block("trial_spawner", "effect_trial_omen");
        block("vault", "stack_size");
        block("chest", "item_count");
        block("barrel", "item_count");
        block("crafting_table", "place_held_block");
        block("furnace", "sparkle");
        block("smoker", "sparkle");
        block("blast_furnace", "sparkle");
        block("stonecutter", "break_block");
        block("crafter", "stack_push");
        block("pumpkin", "sound");
        block("melon", "stack_pop");
        block("carved_pumpkin", "sound");
        block("snow_block", "effect_slow_falling");

        // --- векторы и геометрия ---
        block("compass", "vector_from_coords");
        block("spyglass", "vector_length");
        block("spectral_arrow", "vector_normalize");
        block("tripwire_hook", "vector_scale");
        block("rail", "vector_add");
        block("hopper", "vector_subtract");
        block("observer", "vector_dot");
        block("end_rod", "vector_cross");
        block("target", "vector_angle");
        block("dispenser", "vector_rotate_yaw");
        block("crossbow", "vector_rotate_pitch");
        block("lightning_rod", "vector_flat");
        block("firework_rocket", "vector_cone");
        block("clock", "wrap_angle");
        block("tripwire", "point_distance");
        block("end_crystal", "point_direction");
        block("hay_block", "point_midpoint");
        block("piston", "point_offset");
        block("sculk_sensor", "line_blocks");
        block("note_block", "sin");
        block("daylight_detector", "cos");
        block("piston_head", "lerp");

        // --- циклы и ячейки ---
        block("repeater", "stack_repeat");
        block("jukebox", "stack_swap");
        block("dropper", "stack_drop_n");
        block("lever", "stack_exit");
        block("brewing_stand", "stack_skip");
        block("shulker_box", "stack_store");
        block("bookshelf", "stack_load");
        block("lectern", "stack_slots_used");
        block("decorated_pot", "stack_clear_slots");

        // --- предметы и блоки ---
        block("iron_block", "block_at");
        block("cauldron", "fluid_at");
        block("cartography_table", "held_slot");
        block("grindstone", "empty_hand");
        block("smithing_table", "offhand_item");
        block("bricks", "swap_blocks");
        block("quartz_block", "fill_region");
        block("sponge", "clear_block");
        block("chiseled_bookshelf", "give_item");
        block("beehive", "drop_item");
        block("composter", "consume_item");
    }

    static {
        // --- существа ---
        mob("zombie", "damage");
        mob("husk", "damage");
        mob("zombie_villager", "damage");
        mob("drowned", "damage");
        mob("zombie_horse", "damage");
        mob("skeleton", "impulse");
        mob("stray", "impulse");
        mob("bogged", "impulse");
        mob("wither_skeleton", "ignite");
        mob("creeper", "explode");
        mob("tnt", "explode");
        mob("tnt_minecart", "explode");
        mob("enderman", "teleport");
        mob("endermite", "teleport");
        mob("spider", "effect_speed");
        mob("cave_spider", "effect_speed");
        mob("slime", "effect_jump_boost");
        mob("magma_cube", "ignite");
        mob("blaze", "fireball");
        mob("ghast", "fireball");
        mob("witch", "duplicate");
        mob("pillager", "damage");
        mob("vindicator", "damage");
        mob("ravager", "impulse");
        mob("evoker", "stack_clear");
        mob("illusioner", "stack_clear");
        mob("phantom", "effect_night_vision");
        mob("guardian", "effect_water_breathing");
        mob("elder_guardian", "heal_percent");
        mob("wolf", "heal_percent");
        mob("iron_golem", "heal_percent");
        mob("axolotl", "heal_percent");
        mob("bee", "entity_under_crosshair");
        mob("bat", "effect_night_vision");
        mob("villager", "item_count");
        mob("horse", "impulse");
        mob("donkey", "impulse");
        mob("llama", "held_item");
        mob("cat", "is_player");
        mob("allay", "duplicate");
        mob("shulker", "teleport");
        mob("wither", "effect_wither");
        mob("ender_dragon", "lightning");
        mob("ender_dragon_part", "lightning");
        mob("wither_skull", "effect_wither");
        mob("snow_golem", "effect_regeneration");
        mob("vex", "effect_speed");
        mob("zoglin", "ignite");
        mob("hoglin", "impulse");
        mob("piglin", "damage");
        mob("piglin_brute", "damage");
        mob("strider", "stack_push");
    }

    /** Модификатор, который даёт сканирование цели, либо null. */
    public static String modifierFor(String targetId) {
        return MODIFIER_BLOCKS.get(targetId);
    }

    /** Модуль, который даёт сканирование цели, либо null. */
    public static String rewardFor(String targetId) {
        String reward = BLOCKS.get(targetId);
        if (reward != null) {
            return reward;
        }
        return MOBS.get(targetId);
    }

    /** ResourceLocation модуля-награды (или null). */
    public static ResourceLocation rewardId(String targetId) {
        String reward = rewardFor(targetId);
        return reward == null ? null : Spellcraft.id(reward);
    }

    public static Map<String, String> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    public static Map<String, String> mobs() {
        return Collections.unmodifiableMap(MOBS);
    }

    public static int size() {
        return BLOCKS.size() + MOBS.size();
    }
}