package com.example.blueprintwand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Рисует рамку выделения, пока жезл в руке. Это и есть «территория, которая будет сохранена». */
@EventBusSubscriber(modid = BlueprintWand.MODID, value = Dist.CLIENT)
public class ClientRender {

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (!(player.getMainHandItem().getItem() instanceof WandItem) && !(player.getOffhandItem().getItem() instanceof WandItem)) return;

        Selection sel = Selection.peek(player.getUUID());
        if (sel == null || sel.dim != mc.level.dimension()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        if (sel.complete()) {
            BlockPos a = sel.min(), b = sel.max();
            AABB box = new AABB(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1).inflate(0.003);
            // полупрозрачная заливка
            DebugRenderer.renderFilledBox(ps, buf, box, 0.2f, 0.9f, 0.4f, 0.12f);
            // контур области
            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(ps, lines, box, 0.2f, 1.0f, 0.4f, 1.0f);
        }
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        if (sel.pos1 != null) {
            LevelRenderer.renderLineBox(ps, lines, new AABB(sel.pos1).inflate(0.01), 0.2f, 0.5f, 1.0f, 1.0f); // синяя — точка 1
        }
        if (sel.pos2 != null) {
            LevelRenderer.renderLineBox(ps, lines, new AABB(sel.pos2).inflate(0.01), 1.0f, 0.3f, 0.3f, 1.0f); // красная — точка 2
        }

        ps.popPose();
        buf.endBatch(RenderType.debugFilledBox());
        buf.endBatch(RenderType.lines());
    }
}
