package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер -> клиент: предпросмотр вставки. Размеры sx/sy/sz уже с учётом поворота.
 * ghost — содержимое схематики для полупрозрачного отображения блоков (null, если схематика слишком большая).
 */
public record PreviewPayload(boolean active, ResourceLocation dim, BlockPos origin, int sx, int sy, int sz, Ghost ghost)
        implements CustomPacketPayload {

    /**
     * Блоки в виде палитры и RLE-прогонов. Ячейки идут по порядку: x (быстрее всего), затем z, затем y.
     * Значение прогона 0 — пусто, n >= 1 — palette[n - 1] (id состояния блока, Block.getId).
     */
    public record Ghost(int[] palette, int[] runValue, int[] runLength) {}

    public static final Type<PreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlueprintWand.MODID, "preview"));

    public static final StreamCodec<FriendlyByteBuf, PreviewPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.active());
                buf.writeResourceLocation(p.dim());
                buf.writeBlockPos(p.origin());
                buf.writeVarInt(p.sx());
                buf.writeVarInt(p.sy());
                buf.writeVarInt(p.sz());
                Ghost g = p.ghost();
                buf.writeBoolean(g != null);
                if (g != null) {
                    buf.writeVarInt(g.palette().length);
                    for (int id : g.palette()) buf.writeVarInt(id);
                    buf.writeVarInt(g.runValue().length);
                    for (int i = 0; i < g.runValue().length; i++) {
                        buf.writeVarInt(g.runValue()[i]);
                        buf.writeVarInt(g.runLength()[i]);
                    }
                }
            },
            buf -> {
                boolean active = buf.readBoolean();
                ResourceLocation dim = buf.readResourceLocation();
                BlockPos origin = buf.readBlockPos();
                int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt();
                Ghost ghost = null;
                if (buf.readBoolean()) {
                    int[] palette = new int[buf.readVarInt()];
                    for (int i = 0; i < palette.length; i++) palette[i] = buf.readVarInt();
                    int runs = buf.readVarInt();
                    int[] values = new int[runs];
                    int[] lengths = new int[runs];
                    for (int i = 0; i < runs; i++) {
                        values[i] = buf.readVarInt();
                        lengths[i] = buf.readVarInt();
                    }
                    ghost = new Ghost(palette, values, lengths);
                }
                return new PreviewPayload(active, dim, origin, sx, sy, sz, ghost);
            });

    /** Текущий предпросмотр на клиенте (null — нет). */
    public static volatile PreviewPayload current;
    /** Разобранные блоки для отрисовки (null — только рамка). */
    public static volatile GhostBlocks ghostBlocks;

    public static PreviewPayload clear() {
        return new PreviewPayload(false, ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, 0, 0, 0, null);
    }

    public static void handle(PreviewPayload payload, IPayloadContext ctx) {
        if (payload.active()) {
            current = payload;
            ghostBlocks = GhostBlocks.decode(payload);
        } else {
            current = null;
            ghostBlocks = null;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
