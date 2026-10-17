package com.github.fireprime5.spellcraft.armor;

import com.github.fireprime5.spellcraft.Spellcraft;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Компонент данных со схемой модулей брони.
 *
 * <p>Отдельный компонент, а не NBT-поле: NBT не синхронизируется с клиентом
 * без ручной отправки, и подсказка в чате показала бы пустую броню. С
 * компонентом синхронизация бесплатна, а стек с модулями остаётся копируемым.
 */
public final class ModArmorComponents {

    public static final DeferredRegister.DataComponents REGISTRY =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Spellcraft.MODID);

    private static final Codec<ModularSchema> CODEC =
            CompoundTag.CODEC.xmap(ModularSchema::load, ModularSchema::save);

    private static final StreamCodec<RegistryFriendlyByteBuf, ModularSchema> STREAM_CODEC =
            StreamCodec.of(
                    (buf, schema) -> buf.writeNbt(schema.save()),
                    buf -> ModularSchema.load(buf.readNbt()));

    /** Схема модулей. Отсутствует — броня без модулей. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ModularSchema>> SCHEMA =
            REGISTRY.registerComponentType("module_schema",
                    builder -> builder
                            .persistent(CODEC)
                            .networkSynchronized(STREAM_CODEC));

    private ModArmorComponents() {
    }
}