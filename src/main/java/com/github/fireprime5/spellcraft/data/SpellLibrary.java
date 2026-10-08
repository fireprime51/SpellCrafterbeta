package com.github.fireprime5.spellcraft.data;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.github.fireprime5.spellcraft.spell.SpellGraph;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Библиотека заклинаний игрока: именованные сохранённые заклинания (8 слотов).
 * Хранится в NeoForge Attachment, поэтому переживает релог и смену мира.
 */
public final class SpellLibrary {

    public static final int MAX_SPELLS = 8;

    /** Максимальная длина имени заклинания. */
    public static final int MAX_NAME_LENGTH = 24;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries
                    .ATTACHMENT_TYPES, Spellcraft.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<List<com.github.fireprime5.spellcraft.data.SavedSpell>>> TYPES =
            ATTACHMENTS.register("spell_library", () -> AttachmentType
                    .<List<com.github.fireprime5.spellcraft.data.SavedSpell>>builder((java.util.function.Supplier<List<com.github.fireprime5.spellcraft.data.SavedSpell>>)
                            ArrayList::new)
                    .serialize(com.github.fireprime5.spellcraft.data.SavedSpell.LIST_CODEC)
                    .build());

    private SpellLibrary() {
    }

    public static List<com.github.fireprime5.spellcraft.data.SavedSpell> get(Player player) {
        return player.getData(TYPES.get());
    }

    public static void set(Player player, List<com.github.fireprime5.spellcraft.data.SavedSpell> spells) {
        player.setData(TYPES.get(), new ArrayList<>(spells));
    }

    /**
     * Сохраняет заклинание: если слот с таким именем уже есть — перезаписывает
     * его, иначе занимает свободный слот.
     *
     * @return false — свободных слотов нет (чужие заклинания не трогаем)
     */
    public static boolean save(Player player, String rawName, SpellGraph graph) {
        String name = (rawName == null ? "" : rawName).trim();
        if (name.isEmpty()) {
            name = defaultName(player, graph);
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        List<com.github.fireprime5.spellcraft.data.SavedSpell> list = new ArrayList<>(get(player));
        SavedSpell spell = new SavedSpell(name, graph.copy());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) {
                list.set(i, spell);
                set(player, list);
                return true;
            }
        }
        if (list.size() >= MAX_SPELLS) {
            return false;
        }
        list.add(spell);
        set(player, list);
        return true;
    }

    public static void load(Player player, int index) {
        List<com.github.fireprime5.spellcraft.data.SavedSpell> list = get(player);
        if (index >= 0 && index < list.size()) {
            PlayerSpellStore.set(player, list.get(index).graph().copy());
        }
    }

    public static void delete(Player player, int index) {
        List<com.github.fireprime5.spellcraft.data.SavedSpell> list = new ArrayList<>(get(player));
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            set(player, list);
        }
    }

    /**
     * Имя по умолчанию: «Заклинание N» для первого свободного номера.
     * Номер сравнивается по шаблону, а не по готовой строке, поэтому не важно,
     * какой язык выбран у сервера.
     */
    private static String defaultName(Player player, SpellGraph graph) {
        for (int i = 1; i <= MAX_SPELLS; i++) {
            if (isFreeSlot(player, i)) {
                return defaultSlotName(i);
            }
        }
        return defaultSlotName(graph.nodes.size());
    }

    private static String defaultSlotName(int index) {
        return Component.translatable("library.spellcraft.default_name", index).getString();
    }

    /** Занял ли кто-то слот с авто-именем «Заклинание N». */
    private static boolean isFreeSlot(Player player, int index) {
        String auto = defaultSlotName(index);
        for (SavedSpell spell : get(player)) {
            if (spell.name().equalsIgnoreCase(auto)) {
                return false;
            }
        }
        return true;
    }
}
