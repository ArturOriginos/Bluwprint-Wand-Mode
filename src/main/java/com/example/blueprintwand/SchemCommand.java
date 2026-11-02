package com.example.blueprintwand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class SchemCommand {
    private static final long MAX_BLOCKS = 1_000_000L;
    private static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=?@^~<>()[]{}!;:/|";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("schem")
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> save(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource()))));
    }

    private static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("schematics");
    }

    private static int list(CommandSourceStack src) {
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                List<String> names = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted().toList();
                src.sendSuccess(() -> Component.literal(names.isEmpty()
                        ? "Схематик пока нет. Папка: " + dir()
                        : "Схематики (" + dir() + "):\n" + String.join("\n", names)), false);
            }
        } catch (IOException e) {
            src.sendFailure(Component.literal("Ошибка: " + e.getMessage()));
        }
        return 1;
    }

    private static int save(CommandSourceStack src, String rawName) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Selection sel = Selection.peek(player.getUUID());
        if (sel == null || !sel.complete()) {
            src.sendFailure(Component.literal("Сначала выдели область жезлом: ЛКМ — точка 1, ПКМ — точка 2."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (sel.dim != level.dimension()) {
            src.sendFailure(Component.literal("Выделение сделано в другом измерении."));
            return 0;
        }
        long volume = sel.volume();
        if (volume > MAX_BLOCKS) {
            src.sendFailure(Component.literal("Слишком большая область: " + volume + " блоков (максимум " + MAX_BLOCKS + ")."));
            return 0;
        }

        String name = rawName.replaceAll("[^A-Za-z0-9_\\-]", "_");
        BlockPos min = sel.min(), max = sel.max();
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;

        Map<BlockState, Character> palette = new LinkedHashMap<>();
        List<String> blockEntities = new ArrayList<>();
        StringBuilder layers = new StringBuilder();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long nonAir = 0;

        for (int y = 0; y < sy; y++) {
            layers.append("    {\"y\": ").append(y).append(", \"rows\": [\n");
            for (int z = 0; z < sz; z++) {
                StringBuilder row = new StringBuilder(sx);
                for (int x = 0; x < sx; x++) {
                    mp.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BlockState st = level.getBlockState(mp);
                    if (st.isAir()) {
                        row.append('.');
                        continue;
                    }
                    nonAir++;
                    Character c = palette.get(st);
                    if (c == null) {
                        c = symbol(palette.size());
                        palette.put(st, c);
                    }
                    row.append(c.charValue());
                    if (blockEntities.size() < 2000) {
                        BlockEntity be = level.getBlockEntity(mp);
                        if (be != null) {
                            blockEntities.add("{\"x\": " + x + ", \"y\": " + y + ", \"z\": " + z + ", \"type\": "
                                    + GSON.toJson(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()))) + "}");
                        }
                    }
                }
                layers.append("      ").append(GSON.toJson(row.toString()));
                layers.append(z < sz - 1 ? ",\n" : "\n");
            }
            layers.append("    ]}").append(y < sy - 1 ? ",\n" : "\n");
        }

        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"format\": \"blueprintwand-1\",\n");
        out.append("  \"name\": ").append(GSON.toJson(name)).append(",\n");
        out.append("  \"minecraft_version\": ").append(GSON.toJson(SharedConstants.getCurrentVersion().getName())).append(",\n");
        out.append("  \"dimension\": ").append(GSON.toJson(level.dimension().location().toString())).append(",\n");
        out.append("  \"size\": {\"x\": ").append(sx).append(", \"y\": ").append(sy).append(", \"z\": ").append(sz).append("},\n");
        out.append("  \"origin_world\": {\"x\": ").append(min.getX()).append(", \"y\": ").append(min.getY()).append(", \"z\": ").append(min.getZ()).append("},\n");
        out.append("  \"non_air_blocks\": ").append(nonAir).append(",\n");
        out.append("  \"how_to_read\": \"layers[i] is the horizontal slice at y=i (0 = bottom). rows[j] is z=j (0 = north, smaller z). Character index k inside a row is x=k (0 = west, smaller x). Each character is a key of palette; '.' is air. Coordinates are relative to origin_world.\",\n");
        out.append("  \"palette\": {\n");
        out.append("    \".\": \"minecraft:air\"");
        for (Map.Entry<BlockState, Character> e : palette.entrySet()) {
            out.append(",\n    ").append(GSON.toJson(String.valueOf(e.getValue()))).append(": ").append(GSON.toJson(stateToString(e.getKey())));
        }
        out.append("\n  },\n");
        out.append("  \"block_entities\": [").append(String.join(", ", blockEntities)).append("],\n");
        out.append("  \"layers\": [\n").append(layers).append("  ]\n");
        out.append("}\n");

        try {
            Files.createDirectories(dir());
            Path file = dir().resolve(name + ".json");
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            final long count = nonAir;
            src.sendSuccess(() -> Component.literal("Схематика «" + name + "» сохранена: " + sx + "×" + sy + "×" + sz
                    + ", блоков: " + count + "\n" + file), false);
        } catch (IOException e) {
            src.sendFailure(Component.literal("Не удалось записать файл: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    private static char symbol(int index) {
        if (index < POOL.length()) return POOL.charAt(index);
        return (char) (0x0100 + (index - POOL.length()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String stateToString(BlockState state) {
        StringBuilder sb = new StringBuilder(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        List<Map.Entry<Property<?>, Comparable<?>>> props = new ArrayList<>(state.getValues().entrySet());
        if (props.isEmpty()) return sb.toString();
        props.sort(Comparator.comparing(e -> e.getKey().getName()));
        sb.append('[');
        for (int i = 0; i < props.size(); i++) {
            Property p = props.get(i).getKey();
            Comparable v = props.get(i).getValue();
            if (i > 0) sb.append(',');
            sb.append(p.getName()).append('=').append(p.getName(v));
        }
        return sb.append(']').toString();
    }
}
