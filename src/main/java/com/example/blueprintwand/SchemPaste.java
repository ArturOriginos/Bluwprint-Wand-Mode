package com.example.blueprintwand;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Вставка схематик из schematics/<имя>.json и откат последней вставки. */
public class SchemPaste {
    private static final long MAX_BLOCKS = 1_000_000L;
    /** 2 = отправить клиентам, 16 = без обновления соседей (ничего не отваливается и не падает). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Undo(ResourceKey<Level> dim, List<BlockPos> positions, List<BlockState> states) {}

    private static final Map<UUID, Undo> UNDO = new ConcurrentHashMap<>();

    public static int paste(CommandSourceStack src, String rawName, BlockPos origin, boolean withAir) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = src.getLevel();

        String name = rawName.replaceAll("[^A-Za-z0-9_\\-]", "_");
        Path file = SchemCommand.dir().resolve(name + ".json");
        if (!Files.exists(file)) {
            return fail(src, "Файл не найден: " + file + ". Список: /schem list");
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return fail(src, "Не удалось прочитать файл: " + e.getMessage());
        }

        int sx, sy, sz;
        JsonArray layers;
        JsonObject paletteJson;
        try {
            if (!root.has("format") || !"blueprintwand-1".equals(root.get("format").getAsString())) {
                return fail(src, "Неизвестный формат файла (ожидается blueprintwand-1).");
            }
            JsonObject size = root.getAsJsonObject("size");
            sx = size.get("x").getAsInt();
            sy = size.get("y").getAsInt();
            sz = size.get("z").getAsInt();
            layers = root.getAsJsonArray("layers");
            paletteJson = root.getAsJsonObject("palette");
        } catch (RuntimeException e) {
            return fail(src, "Файл повреждён: нет size / layers / palette.");
        }
        if ((long) sx * sy * sz > MAX_BLOCKS) {
            return fail(src, "Слишком большая схематика (максимум " + MAX_BLOCKS + " блоков).");
        }

        // Палитра: символ -> состояние блока. Неизвестные блоки (например, из отсутствующего мода) пропускаем.
        HolderLookup<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<Character, BlockState> palette = new HashMap<>();
        Set<String> missing = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> e : paletteJson.entrySet()) {
            if (e.getKey().length() != 1) continue;
            char key = e.getKey().charAt(0);
            if (key == '.') {
                palette.put('.', Blocks.AIR.defaultBlockState());
                continue;
            }
            String id = e.getValue().getAsString();
            try {
                palette.put(key, BlockStateParser.parseForBlock(blocks, id, false).blockState());
            } catch (CommandSyntaxException ex) {
                missing.add(id);
            }
        }
        palette.putIfAbsent('.', Blocks.AIR.defaultBlockState());

        // Проверка размеров до начала вставки, чтобы не оставить недоделанную постройку.
        if (layers.size() != sy) {
            return fail(src, "Файл повреждён: число слоёв " + layers.size() + " != " + sy);
        }
        List<String[]> grid = new ArrayList<>(sy);
        for (int y = 0; y < sy; y++) {
            JsonArray rows = layers.get(y).getAsJsonObject().getAsJsonArray("rows");
            if (rows.size() != sz) {
                return fail(src, "Файл повреждён: слой " + y + " имеет " + rows.size() + " строк вместо " + sz);
            }
            String[] r = new String[sz];
            for (int z = 0; z < sz; z++) {
                r[z] = rows.get(z).getAsString();
                if (r[z].length() != sx) {
                    return fail(src, "Файл повреждён: слой " + y + ", строка " + z + " имеет длину " + r[z].length() + " вместо " + sx);
                }
            }
            grid.add(r);
        }

        List<BlockPos> undoPos = new ArrayList<>();
        List<BlockState> undoStates = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long placed = 0, unknown = 0;

        for (int y = 0; y < sy; y++) {
            String[] rows = grid.get(y);
            for (int z = 0; z < sz; z++) {
                String row = rows[z];
                for (int x = 0; x < sx; x++) {
                    BlockState st = palette.get(row.charAt(x));
                    if (st == null) {
                        unknown++;
                        continue;
                    }
                    if (st.isAir() && !withAir) continue;
                    mp.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
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

        final long count = placed;
        final long skipped = unknown;
        StringBuilder msg = new StringBuilder("Вставлено «" + name + "» в " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                + " (" + sx + "×" + sy + "×" + sz + "), изменено блоков: " + count + ". Откат: /schem undo");
        if (!missing.isEmpty()) {
            msg.append("\nПропущены неизвестные блоки: ")
                    .append(String.join(", ", missing.stream().limit(8).toList()))
                    .append(missing.size() > 8 ? " …" : "");
        }
        if (skipped > 0) {
            msg.append("\nСимволов без записи в палитре: ").append(skipped);
        }
        src.sendSuccess(() -> Component.literal(msg.toString()), true);
        return 1;
    }

    public static int undo(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Undo u = UNDO.remove(player.getUUID());
        if (u == null) {
            return fail(src, "Нечего откатывать.");
        }
        ServerLevel level = src.getServer().getLevel(u.dim());
        if (level == null) {
            return fail(src, "Измерение недоступно.");
        }
        // Откат в обратном порядке.
        for (int i = u.positions().size() - 1; i >= 0; i--) {
            level.setBlock(u.positions().get(i), u.states().get(i), FLAGS);
        }
        final int n = u.positions().size();
        src.sendSuccess(() -> Component.literal("Откат выполнен, блоков восстановлено: " + n
                + " (содержимое сундуков и прочие данные блоков не восстанавливаются)."), true);
        return 1;
    }

    private static int fail(CommandSourceStack src, String text) {
        src.sendFailure(Component.literal(text));
        return 0;
    }
}
