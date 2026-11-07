package com.example.blueprintwand;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Предпросмотр, вставка (с поворотом) и откат схематик из schematics/<имя>.json. */
public class SchemPaste {
    /** 2 = отправить клиентам, 16 = без обновления соседей (ничего не отваливается и не падает). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Максимум непустых блоков, которые отправляются клиенту для полупрозрачного предпросмотра. */
    static final int GHOST_MAX_BLOCKS = 40_000;

    private record Schematic(String name, int sx, int sy, int sz, String[][] grid,
                             Map<Character, BlockState> palette, Set<String> missing) {}

    private record Pending(String name, BlockPos origin, int rotation, boolean withAir, ResourceKey<Level> dim) {}

    private record Undo(ResourceKey<Level> dim, List<BlockPos> positions, List<BlockState> states) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Undo> UNDO = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- команды

    public static int paste(CommandSourceStack src, String rawName, int rotation, BlockPos origin, boolean withAir) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        Schematic s = load(src, level, rawName);
        if (s == null) return 0;
        return place(src, player, level, s, origin, rotation, withAir);
    }

    public static int preview(CommandSourceStack src, String rawName, int rotation, BlockPos origin, boolean withAir) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        Schematic s = load(src, level, rawName);
        if (s == null) return 0;

        boolean swap = rotation == 90 || rotation == 270;
        int rsx = swap ? s.sz() : s.sx();
        int rsz = swap ? s.sx() : s.sz();

        PreviewPayload.Ghost ghost = buildGhost(s, rotation, rsx, rsz);
        PENDING.put(player.getUUID(), new Pending(s.name(), origin, rotation, withAir, level.dimension()));
        PacketDistributor.sendToPlayer(player,
                new PreviewPayload(true, level.dimension().location(), origin, rsx, s.sy(), rsz, ghost));
        MutableComponent msg = Msg.t("cmd.preview", s.name(), origin.getX(), origin.getY(), origin.getZ(), rsx, s.sy(), rsz);
        if (ghost == null) {
            msg.append(Msg.t("cmd.preview_no_ghost", GHOST_MAX_BLOCKS));
        }
        src.sendSuccess(() -> msg, false);
        return 1;
    }

    public static int confirm(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Pending p = PENDING.remove(player.getUUID());
        if (p == null) {
            return fail(src, Msg.t("cmd.no_preview"));
        }
        PacketDistributor.sendToPlayer(player, PreviewPayload.clear());
        ServerLevel level = src.getLevel();
        if (level.dimension() != p.dim()) {
            return fail(src, Msg.t("cmd.preview_dimension"));
        }
        Schematic s = load(src, level, p.name());
        if (s == null) return 0;
        return place(src, player, level, s, p.origin(), p.rotation(), p.withAir());
    }

    public static int cancel(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PENDING.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, PreviewPayload.clear());
        src.sendSuccess(() -> Msg.t("cmd.cancelled"), false);
        return 1;
    }

    public static int undo(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Undo u = UNDO.remove(player.getUUID());
        if (u == null) {
            return fail(src, Msg.t("cmd.undo_nothing"));
        }
        ServerLevel level = src.getServer().getLevel(u.dim());
        if (level == null) {
            return fail(src, Msg.t("cmd.undo_dimension"));
        }
        for (int i = u.positions().size() - 1; i >= 0; i--) {
            level.setBlock(u.positions().get(i), u.states().get(i), FLAGS);
        }
        final int n = u.positions().size();
        src.sendSuccess(() -> Msg.t("cmd.undone", n), true);
        return 1;
    }

    // ---------------------------------------------------------------- загрузка

    /** Читает и проверяет файл. При ошибке сам отправляет сообщение и возвращает null. */
    private static Schematic load(CommandSourceStack src, ServerLevel level, String rawName) {
        String name = rawName.replaceAll("[^A-Za-z0-9_\\-]", "_");
        Path file = SchemCommand.dir().resolve(name + ".json");
        if (!Files.exists(file)) {
            fail(src, Msg.t("cmd.not_found", file.toString()));
            return null;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            fail(src, Msg.t("cmd.read_failed", String.valueOf(e.getMessage())));
            return null;
        }

        int sx, sy, sz;
        JsonArray layers;
        JsonObject paletteJson;
        try {
            if (!root.has("format") || !"blueprintwand-1".equals(root.get("format").getAsString())) {
                fail(src, Msg.t("cmd.bad_format"));
                return null;
            }
            JsonObject size = root.getAsJsonObject("size");
            sx = size.get("x").getAsInt();
            sy = size.get("y").getAsInt();
            sz = size.get("z").getAsInt();
            layers = root.getAsJsonArray("layers");
            paletteJson = root.getAsJsonObject("palette");
        } catch (RuntimeException e) {
            fail(src, Msg.t("cmd.corrupt"));
            return null;
        }
        if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > SchemCommand.MAX_BLOCKS) {
            fail(src, Msg.t("cmd.paste_too_big", SchemCommand.MAX_BLOCKS));
            return null;
        }

        // Палитра: символ -> состояние. Неизвестные блоки (например, из отсутствующего мода) пропускаем.
        HolderLookup<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<Character, BlockState> palette = new HashMap<>();
        Set<String> missing = new LinkedHashSet<>();
        try {
            for (Map.Entry<String, JsonElement> e : paletteJson.entrySet()) {
                if (e.getKey().length() != 1) continue;
                char key = e.getKey().charAt(0);
                if (key == '.') continue;
                String id = e.getValue().getAsString();
                try {
                    palette.put(key, BlockStateParser.parseForBlock(blocks, id, false).blockState());
                } catch (CommandSyntaxException ex) {
                    missing.add(id);
                }
            }
        } catch (RuntimeException e) {
            fail(src, Msg.t("cmd.corrupt"));
            return null;
        }
        palette.put('.', Blocks.AIR.defaultBlockState());

        // Проверка размеров до начала вставки, чтобы не оставить недоделанную постройку.
        if (layers.size() != sy) {
            fail(src, Msg.t("cmd.corrupt_layers", layers.size(), sy));
            return null;
        }
        String[][] grid = new String[sy][];
        for (int y = 0; y < sy; y++) {
            JsonArray rows;
            try {
                rows = layers.get(y).getAsJsonObject().getAsJsonArray("rows");
            } catch (RuntimeException e) {
                fail(src, Msg.t("cmd.corrupt"));
                return null;
            }
            if (rows.size() != sz) {
                fail(src, Msg.t("cmd.corrupt_rows", y, rows.size(), sz));
                return null;
            }
            grid[y] = new String[sz];
            for (int z = 0; z < sz; z++) {
                grid[y][z] = rows.get(z).getAsString();
                if (grid[y][z].length() != sx) {
                    fail(src, Msg.t("cmd.corrupt_len", y, z, grid[y][z].length(), sx));
                    return null;
                }
            }
        }
        return new Schematic(name, sx, sy, sz, grid, palette, missing);
    }

    // ---------------------------------------------------------------- вставка

    private static int place(CommandSourceStack src, ServerPlayer player, ServerLevel level, Schematic s,
                             BlockPos origin, int rotation, boolean withAir) {
        Rotation rot = switch (rotation) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int sx = s.sx(), sy = s.sy(), sz = s.sz();
        boolean swap = rotation == 90 || rotation == 270;
        int rsx = swap ? sz : sx;
        int rsz = swap ? sx : sz;

        Map<Character, BlockState> palette = new HashMap<>();
        for (Map.Entry<Character, BlockState> e : s.palette().entrySet()) {
            palette.put(e.getKey(), e.getValue().rotate(rot));
        }

        List<BlockPos> undoPos = new ArrayList<>();
        List<BlockState> undoStates = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long placed = 0, unknown = 0;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                String row = s.grid()[y][z];
                for (int x = 0; x < sx; x++) {
                    BlockState st = palette.get(row.charAt(x));
                    if (st == null) {
                        unknown++;
                        continue;
                    }
                    if (st.isAir() && !withAir) continue;

                    int nx, nz;
                    switch (rotation) {
                        case 90 -> { nx = sz - 1 - z; nz = x; }
                        case 180 -> { nx = sx - 1 - x; nz = sz - 1 - z; }
                        case 270 -> { nx = z; nz = sx - 1 - x; }
                        default -> { nx = x; nz = z; }
                    }
                    mp.set(origin.getX() + nx, origin.getY() + y, origin.getZ() + nz);
                    if (level.isOutsideBuildHeight(mp)) continue;
                    BlockState old = level.getBlockState(mp);
                    if (old == st) continue;
                    undoPos.add(mp.immutable());
                    undoStates.add(old);
                    level.setBlock(mp, st, FLAGS);
                    placed++;
                }
            }
        }

        UNDO.put(player.getUUID(), new Undo(level.dimension(), undoPos, undoStates));

        MutableComponent msg = Msg.t("cmd.pasted", s.name(), origin.getX(), origin.getY(), origin.getZ(), rsx, sy, rsz, placed);
        if (!s.missing().isEmpty()) {
            String list = String.join(", ", s.missing().stream().limit(8).toList()) + (s.missing().size() > 8 ? " …" : "");
            msg.append(Msg.t("cmd.missing", list));
        }
        if (unknown > 0) {
            msg.append(Msg.t("cmd.unknown_symbols", unknown));
        }
        src.sendSuccess(() -> msg, true);
        return 1;
    }

    private static int rotX(int rot, int x, int z, int sx, int sz) {
        return switch (rot) {
            case 90 -> sz - 1 - z;
            case 180 -> sx - 1 - x;
            case 270 -> z;
            default -> x;
        };
    }

    private static int rotZ(int rot, int x, int z, int sx, int sz) {
        return switch (rot) {
            case 90 -> x;
            case 180 -> sz - 1 - z;
            case 270 -> sx - 1 - x;
            default -> z;
        };
    }

    /** Палитра + RLE ячеек (с поворотом) для полупрозрачного предпросмотра. null — схематика слишком большая. */
    private static PreviewPayload.Ghost buildGhost(Schematic s, int rotation, int rsx, int rsz) {
        Rotation rot = switch (rotation) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int sx = s.sx(), sy = s.sy(), sz = s.sz();
        int[] cells = new int[rsx * sy * rsz];
        Map<BlockState, Integer> ids = new LinkedHashMap<>();
        IntArrayList paletteIds = new IntArrayList();
        int count = 0;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                String row = s.grid()[y][z];
                for (int x = 0; x < sx; x++) {
                    BlockState st = s.palette().get(row.charAt(x));
                    if (st == null || st.isAir()) continue;
                    if (++count > GHOST_MAX_BLOCKS) return null;
                    BlockState rs = st.rotate(rot);
                    Integer idx = ids.get(rs);
                    if (idx == null) {
                        idx = ids.size() + 1;
                        ids.put(rs, idx);
                        paletteIds.add(Block.getId(rs));
                    }
                    int nx = rotX(rotation, x, z, sx, sz);
                    int nz = rotZ(rotation, x, z, sx, sz);
                    cells[(y * rsz + nz) * rsx + nx] = idx;
                }
            }
        }

        IntArrayList values = new IntArrayList();
        IntArrayList lengths = new IntArrayList();
        int i = 0;
        while (i < cells.length) {
            int v = cells[i];
            int j = i + 1;
            while (j < cells.length && cells[j] == v) j++;
            values.add(v);
            lengths.add(j - i);
            i = j;
        }
        return new PreviewPayload.Ghost(paletteIds.toIntArray(), values.toIntArray(), lengths.toIntArray());
    }

    private static int fail(CommandSourceStack src, MutableComponent text) {
        src.sendFailure(text);
        return 0;
    }
}
