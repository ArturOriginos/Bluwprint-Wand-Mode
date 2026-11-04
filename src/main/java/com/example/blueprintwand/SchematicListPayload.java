package com.example.blueprintwand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Сервер -> клиент: список схематик из серверной папки schematics (открывает экран «Список»). */
public record SchematicListPayload(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(String name, int sx, int sy, int sz, int blocks) {}

    public static final Type<SchematicListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlueprintWand.MODID, "schematic_list"));

    public static final StreamCodec<FriendlyByteBuf, SchematicListPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries().size());
                for (Entry e : p.entries()) {
                    buf.writeUtf(e.name(), 128);
                    buf.writeVarInt(e.sx());
                    buf.writeVarInt(e.sy());
                    buf.writeVarInt(e.sz());
                    buf.writeVarInt(e.blocks());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Entry(buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new SchematicListPayload(list);
            });

    public static void handle(SchematicListPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist.isClient()) {
            ListScreenOpener.open(payload.entries());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
