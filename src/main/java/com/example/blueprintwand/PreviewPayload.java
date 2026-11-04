package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер -> клиент: рамка предпросмотра вставки (с учётом поворота). */
public record PreviewPayload(boolean active, ResourceLocation dim, BlockPos origin, int sx, int sy, int sz)
        implements CustomPacketPayload {

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
            },
            buf -> new PreviewPayload(buf.readBoolean(), buf.readResourceLocation(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    /** Текущий предпросмотр на клиенте (null — нет). */
    public static volatile PreviewPayload current;

    public static PreviewPayload clear() {
        return new PreviewPayload(false, ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, 0, 0, 0);
    }

    public static void handle(PreviewPayload payload, IPayloadContext ctx) {
        current = payload.active() ? payload : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
