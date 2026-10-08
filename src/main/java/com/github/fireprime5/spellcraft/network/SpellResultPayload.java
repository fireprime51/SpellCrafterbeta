package com.github.fireprime5.spellcraft.network;

import com.github.fireprime5.spellcraft.spell.SpellIssue;
import com.github.fireprime5.spellcraft.spell.SpellValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервер -> Клиент: результат последнего каста — трасса, стек, ошибки.
 */
public record SpellResultPayload(List<String> trace, List<SpellValue> stack,
                                 List<SpellIssue> issues, boolean success, int manaSpent,
                                 List<Integer> errorNodes)
        implements CustomPacketPayload {

    public static final Type<SpellResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("spellcraft", "spell_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellResultPayload> STREAM_CODEC =
            StreamCodec.of(SpellResultPayload::write, SpellResultPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SpellResultPayload payload) {
        ListTag trace = new ListTag();
        payload.trace().forEach(t -> trace.add(StringTag.valueOf(t)));

        ListTag stack = new ListTag();
        for (SpellValue v : payload.stack()) {
            stack.add(v.save());
        }

        ListTag issues = new ListTag();
        for (SpellIssue issue : payload.issues()) {
            issues.add(StringTag.valueOf(issue.encode()));
        }

        ListTag errorNodes = new ListTag();
        for (int node : payload.errorNodes()) {
            errorNodes.add(net.minecraft.nbt.IntTag.valueOf(node));
        }

        CompoundTag tag = new CompoundTag();
        tag.put("trace", trace);
        tag.put("stack", stack);
        tag.put("issues", issues);
        tag.put("error_nodes", errorNodes);
        tag.putBoolean("success", payload.success());
        tag.putInt("mana", payload.manaSpent());
        buf.writeNbt(tag);
    }

    private static SpellResultPayload read(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            return new SpellResultPayload(List.of(), List.of(), List.of(), false, 0, List.of());
        }
        List<String> trace = new ArrayList<>();
        for (Tag t : tag.getList("trace", Tag.TAG_STRING)) {
            trace.add(t.getAsString());
        }
        List<SpellValue> stack = new ArrayList<>();
        for (Tag t : tag.getList("stack", Tag.TAG_COMPOUND)) {
            stack.add(SpellValue.load((CompoundTag) t));
        }
        List<SpellIssue> issues = new ArrayList<>();
        for (Tag t : tag.getList("issues", Tag.TAG_STRING)) {
            issues.add(SpellIssue.decode(t.getAsString()));
        }
        List<Integer> errorNodes = new ArrayList<>();
        for (Tag t : tag.getList("error_nodes", Tag.TAG_INT)) {
            errorNodes.add(((net.minecraft.nbt.IntTag) t).getAsInt());
        }
        return new SpellResultPayload(trace, stack, issues, tag.getBoolean("success"),
                tag.getInt("mana"), errorNodes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}