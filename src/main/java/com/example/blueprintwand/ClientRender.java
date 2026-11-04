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

/**
 * Рамки:
 *  - зелёная — выделенная область (пока жезл в руке), синяя/красная — точки 1 и 2;
 *  - жёлтая — предпросмотр вставки (/schem preview), видна всегда, пока не /schem confirm или /schem cancel.
 */
@EventBusSubscriber(modid = BlueprintWand.MODID, value = Dist.CLIENT)
public class ClientRender {

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        boolean holding = player.getMainHandItem().getItem() instanceof WandItem
                || player.getOffhandItem().getItem() instanceof WandItem;

        Selection sel = holding ? Selection.peek(player.getUUID()) : null;
        if (sel != null && sel.dim != mc.level.dimension()) sel = null;

        PreviewPayload pv = PreviewPayload.current;
        if (pv != null && !pv.dim().equals(mc.level.dimension().location())) pv = null;

        if (sel == null && pv == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        if (sel != null) {
            if (sel.complete()) {
                BlockPos a = sel.min(), b = sel.max();
                AABB box = new AABB(a.getX(), a.getY(), a.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1).inflate(0.003);
                DebugRenderer.renderFilledBox(ps, buf, box, 0.2f, 0.9f, 0.4f, 0.12f);
                LevelRenderer.renderLineBox(ps, buf.getBuffer(RenderType.lines()), box, 0.2f, 1.0f, 0.4f, 1.0f);
            }
            VertexConsumer lines = buf.getBuffer(RenderType.lines());
            if (sel.pos1 != null) {
                LevelRenderer.renderLineBox(ps, lines, new AABB(sel.pos1).inflate(0.01), 0.2f, 0.5f, 1.0f, 1.0f);
            }
            if (sel.pos2 != null) {
                LevelRenderer.renderLineBox(ps, lines, new AABB(sel.pos2).inflate(0.01), 1.0f, 0.3f, 0.3f, 1.0f);
            }
        }

        if (pv != null) {
            BlockPos o = pv.origin();
            AABB box = new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + pv.sx(), o.getY() + pv.sy(), o.getZ() + pv.sz()).inflate(0.004);
            DebugRenderer.renderFilledBox(ps, buf, box, 1.0f, 0.8f, 0.1f, 0.14f);
            LevelRenderer.renderLineBox(ps, buf.getBuffer(RenderType.lines()), box, 1.0f, 0.85f, 0.1f, 1.0f);
        }

        ps.popPose();
        buf.endBatch(RenderType.debugFilledBox());
        buf.endBatch(RenderType.lines());
    }
}
