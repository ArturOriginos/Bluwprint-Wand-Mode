package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Выделение игрока. Событие кликов приходит и на клиент, и на сервер — каждая сторона ведёт свою копию. */
public final class Selection {
    private static final Map<UUID, Selection> MAP = new ConcurrentHashMap<>();

    public BlockPos pos1;
    public BlockPos pos2;
    public ResourceKey<Level> dim;

    public static Selection get(UUID id) {
        return MAP.computeIfAbsent(id, k -> new Selection());
    }

    public static Selection peek(UUID id) {
        return MAP.get(id);
    }

    public static void clear(UUID id) {
        MAP.remove(id);
    }

    public void setPos1(BlockPos pos, ResourceKey<Level> dimension) {
        if (dim != dimension) pos2 = null;
        dim = dimension;
        pos1 = pos.immutable();
    }

    public void setPos2(BlockPos pos, ResourceKey<Level> dimension) {
        if (dim != dimension) pos1 = null;
        dim = dimension;
        pos2 = pos.immutable();
    }

    public boolean complete() {
        return pos1 != null && pos2 != null;
    }

    public BlockPos min() {
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    public BlockPos max() {
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    public long volume() {
        BlockPos a = min(), b = max();
        return (long) (b.getX() - a.getX() + 1) * (b.getY() - a.getY() + 1) * (b.getZ() - a.getZ() + 1);
    }
}
