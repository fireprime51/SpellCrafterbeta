package com.github.fireprime5.spellcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер -> Клиент: открыть модульную сетку машины.
 *
 * <p>Отдельный пакет от {@link OpenBuilderPayload}, а не флаг внутри него:
 * экран машины и экран заклинания отличаются набором модулей и тем, что
 * изменения уходят обратно в блок, а не в книгу. Смешивать их в одном пакете
 * означало бы таскать через сеть поля, которые нужны только одному из двух.
 *
 * @param pos где стоит машина
 * @param kind тип машины: smelter, extractor, tank
 */
public record OpenMachineGridPayload(BlockPos pos, String kind)
        implements CustomPacketPayload {

    public static final Type<OpenMachineGridPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("spellcraft", "open_machine_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMachineGridPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, OpenMachineGridPayload::pos,
                    ByteBufCodecs.stringUtf8(NetworkLimits.MAX_KIND_LENGTH), OpenMachineGridPayload::kind,
                    OpenMachineGridPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
