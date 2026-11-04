package com.example.blueprintwand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SchemCommand {
    static final long MAX_BLOCKS = 1_000_000L;
    private static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=?@^~<>()[]{}!;:/|";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @FunctionalInterface
    interface Action {
        int run(CommandSourceStack src, String name, int rotation, BlockPos pos, boolean withAir) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("schem")
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> save(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(pasteTree("paste", SchemPaste::paste))
                .then(pasteTree("preview", SchemPaste::preview))
                .then(Commands.literal("confirm")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> SchemPaste.confirm(ctx.getSource())))
                .then(Commands.literal("cancel")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> SchemPaste.cancel(ctx.getSource())))
                .then(Commands.literal("undo")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> SchemPaste.undo(ctx.getSource()))));
    }

    /** /schem <word> <name> [r90|r180|r270] [x y z] [with_air] */
    private static LiteralArgumentBuilder<CommandSourceStack> pasteTree(String word, Action action) {
        RequiredArgumentBuilder<CommandSourceStack, String> name = Commands.argument("name", StringArgumentType.word())
                .suggests(SCHEMATICS);
        addTail(name, action, 0);
        for (int rot : new int[]{90, 180, 270}) {
            LiteralArgumentBuilder<CommandSourceStack> r = Commands.literal("r" + rot);
            addTail(r, action, rot);
            name.then(r);
        }
        return Commands.literal(word).requires(s -> s.hasPermission(2)).then(name);
    }

    private static void addTail(ArgumentBuilder<CommandSourceStack, ?> node, Action action, int rot) {
        node.executes(c -> action.run(c.getSource(), nameOf(c), rot, here(c), false));
        node.then(Commands.literal("with_air")
                .executes(c -> action.run(c.getSource(), nameOf(c), rot, here(c), true)));
        node.then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(c -> action.run(c.getSource(), nameOf(c), rot, BlockPosArgument.getBlockPos(c, "pos"), false))
                .then(Commands.literal("with_air")
                        .executes(c -> action.run(c.getSource(), nameOf(c), rot, BlockPosArgument.getBlockPos(c, "pos"), true))));
    }

    private static String nameOf(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "name");
    }

    private static BlockPos here(CommandContext<CommandSourceStack> c) {
        return BlockPos.containing(c.getSource().getPosition());
    }

    /** Подсказки: имена всех сохранённых схематик из папки schematics (без .json). */
    private static final SuggestionProvider<CommandSourceStack> SCHEMATICS = (ctx, builder) -> {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException ignored) {
            // папки ещё нет — подсказок нет
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final Pattern SIZE = Pattern.compile("\"size\":\\s*\\{\\s*\"x\":\\s*(\\d+),\\s*\"y\":\\s*(\\d+),\\s*\"z\":\\s*(\\d+)");
    private static final Pattern NON_AIR = Pattern.compile("\"non_air_blocks\":\\s*(\\d+)");

    /** Все схематики из папки schematics с размерами (читаем только начало файла). */
    static List<SchematicListPayload.Entry> listEntries() {
        List<SchematicListPayload.Entry> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir())) {
            List<Path> sorted = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().limit(200).toList();
            for (Path file : sorted) {
                String fn = file.getFileName().toString();
                String name = fn.substring(0, fn.length() - ".json".length());
                if (!name.matches("[A-Za-z0-9_\\-]+")) continue; // такие имена нельзя ввести в команду
                int sx = 0, sy = 0, sz = 0, blocks = 0;
                try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    char[] buf = new char[1500];
                    int n = r.read(buf);
                    String head = new String(buf, 0, Math.max(n, 0));
                    Matcher m = SIZE.matcher(head);
                    if (m.find()) {
                        sx = Integer.parseInt(m.group(1));
                        sy = Integer.parseInt(m.group(2));
                        sz = Integer.parseInt(m.group(3));
                    }
                    Matcher b = NON_AIR.matcher(head);
                    if (b.find()) {
                        blocks = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(b.group(1)));
                    }
                } catch (IOException | RuntimeException ignored) {
                    // оставляем нули — проект всё равно покажем
                }
                result.add(new SchematicListPayload.Entry(name, sx, sy, sz, blocks));
            }
        } catch (IOException ignored) {
            // папки ещё нет
        }
        return result;
    }

    static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("schematics");
    }

    private static int list(CommandSourceStack src) {
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                List<String> names = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted().toList();
                src.sendSuccess(() -> names.isEmpty()
                        ? Msg.t("cmd.list_empty", dir().toString())
                        : Msg.t("cmd.list", dir().toString(), String.join("\n", names)), false);
            }
        } catch (IOException e) {
            src.sendFailure(Msg.t("cmd.error", String.valueOf(e.getMessage())));
        }
        return 1;
    }

    private static int save(CommandSourceStack src, String rawName) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Selection sel = Selection.peek(player.getUUID());
        if (sel == null || !sel.complete()) {
            src.sendFailure(Msg.t("cmd.no_selection"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (sel.dim != level.dimension()) {
            src.sendFailure(Msg.t("cmd.other_dimension"));
            return 0;
        }
        long volume = sel.volume();
        if (volume > MAX_BLOCKS) {
            src.sendFailure(Msg.t("cmd.too_big", volume, MAX_BLOCKS));
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
            src.sendSuccess(() -> Msg.t("cmd.saved", name, sx, sy, sz, count, file.toString()), false);
        } catch (IOException e) {
            src.sendFailure(Msg.t("cmd.write_failed", String.valueOf(e.getMessage())));
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
