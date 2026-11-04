package com.example.blueprintwand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * Экран «Список схематик». Выбираешь проект, поворот и место -> «Предпросмотр» (жёлтая рамка в мире)
 * -> «Вставить». Кнопки просто отправляют команды /schem ..., поэтому права и логика те же, что у команд.
 */
public class SchematicListScreen extends Screen {
    private static final int PER_PAGE = 6;

    private final List<SchematicListPayload.Entry> entries;
    private final BlockPos lookPos; // блок рядом с гранью, на которую смотрел игрок (может быть null)

    private int page = 0;
    private int rotation = 0;
    private boolean withAir = false;
    private boolean atLook;
    private String selected;

    public SchematicListScreen(List<SchematicListPayload.Entry> entries) {
        super(Msg.t("gui.title"));
        this.entries = entries;
        Minecraft mc = Minecraft.getInstance();
        BlockPos look = null;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            look = hit.getBlockPos().relative(hit.getDirection());
        }
        this.lookPos = look;
        this.atLook = look != null;
    }

    private boolean previewActive() {
        return PreviewPayload.current != null;
    }

    private BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        if (atLook && lookPos != null) return lookPos;
        return mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
    }

    @Override
    protected void init() {
        int left = width / 2 - 215;
        int right = width / 2 + 15;
        int top = 34;

        // --- список проектов (постранично)
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) break;
            SchematicListPayload.Entry e = entries.get(idx);
            String label = (e.name().equals(selected) ? "> " : "") + e.name();
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = e.name();
                rebuildWidgets();
            }).bounds(left, top + i * 22, 200, 20).build());
        }
        int py = top + PER_PAGE * 22 + 2;
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, py, 40, 20).build());
        prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 160, py, 40, 20).build());
        next.active = page < pages - 1;

        // --- настройки и действия
        int y = top;
        addRenderableWidget(Button.builder(Msg.t("gui.rotation", rotation), b -> {
            rotation = (rotation + 90) % 360;
            rebuildWidgets();
        }).bounds(right, y, 200, 20).build());
        y += 22;

        addRenderableWidget(Button.builder(Msg.t(withAir ? "gui.air_on" : "gui.air_off"), b -> {
            withAir = !withAir;
            rebuildWidgets();
        }).bounds(right, y, 200, 20).build());
        y += 22;

        Button pos = addRenderableWidget(Button.builder(Msg.t(atLook ? "gui.pos_look" : "gui.pos_self"), b -> {
            atLook = !atLook;
            rebuildWidgets();
        }).bounds(right, y, 200, 20).build());
        pos.active = lookPos != null;
        y += 22;

        Button preview = addRenderableWidget(Button.builder(Msg.t("gui.preview"), b -> sendPreview())
                .bounds(right, y, 200, 20).build());
        preview.active = selected != null;
        y += 22;

        Button place = addRenderableWidget(Button.builder(Msg.t("gui.place"), b -> {
            send("schem confirm");
            onClose();
        }).bounds(right, y, 98, 20).build());
        place.active = previewActive();
        Button cancel = addRenderableWidget(Button.builder(Msg.t("gui.cancel"), b -> {
            send("schem cancel");
            rebuildWidgets();
        }).bounds(right + 102, y, 98, 20).build());
        cancel.active = previewActive();
        y += 22;

        addRenderableWidget(Button.builder(Msg.t("gui.undo"), b -> send("schem undo"))
                .bounds(right, y, 98, 20).build());
        addRenderableWidget(Button.builder(Msg.t("gui.close"), b -> onClose())
                .bounds(right + 102, y, 98, 20).build());
    }

    private void sendPreview() {
        if (selected == null) return;
        BlockPos t = target();
        String cmd = "schem preview " + selected
                + (rotation == 0 ? "" : " r" + rotation)
                + " " + t.getX() + " " + t.getY() + " " + t.getZ()
                + (withAir ? " with_air" : "");
        send(cmd);
        onClose(); // закрываем, чтобы было видно рамку в мире
    }

    private void send(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int left = width / 2 - 215;
        int right = width / 2 + 15;
        int top = 34;

        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);

        if (entries.isEmpty()) {
            g.drawString(font, Msg.t("gui.empty"), left, top + 4, 0xFFAAAA);
        }

        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int py = top + PER_PAGE * 22 + 2;
        g.drawCenteredString(font, Msg.t("gui.page", page + 1, pages), left + 100, py + 6, 0xAAAAAA);

        // информация о выбранном проекте, цель и статус предпросмотра
        int infoY = top + 6 * 22 + 4;
        SchematicListPayload.Entry sel = findSelected();
        if (sel != null) {
            g.drawString(font, Msg.t("gui.selected", sel.name(), sel.sx(), sel.sy(), sel.sz(), sel.blocks()), right, infoY, 0xFFFFFF);
        } else {
            g.drawString(font, Msg.t("gui.none"), right, infoY, 0xAAAAAA);
        }
        BlockPos t = target();
        g.drawString(font, Msg.t("gui.target", t.getX(), t.getY(), t.getZ()), right, infoY + 11, 0xAAAAAA);
        if (previewActive()) {
            g.drawString(font, Msg.t("gui.status_preview"), right, infoY + 22, 0xFFDD55);
        }

        // как вставляются проекты
        int hy = Math.max(top + PER_PAGE * 22 + 34, height - 40);
        g.drawCenteredString(font, Msg.t("gui.howto1"), width / 2, hy, 0xCCCCCC);
        g.drawCenteredString(font, Msg.t("gui.howto2"), width / 2, hy + 11, 0xCCCCCC);
        g.drawCenteredString(font, Msg.t("gui.howto3"), width / 2, hy + 22, 0x999999);
    }

    private SchematicListPayload.Entry findSelected() {
        if (selected == null) return null;
        for (SchematicListPayload.Entry e : entries) {
            if (e.name().equals(selected)) return e;
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
