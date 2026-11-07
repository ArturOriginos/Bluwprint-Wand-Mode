package com.example.blueprintwand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Рамки и призрак постройки:
 *  - зелёная рамка — выделенная область (пока жезл в руке), синяя/красная — точки 1 и 2;
 *  - жёлтая рамка + полупрозрачные блоки — предпросмотр вставки (/schem preview), виден, пока не /schem confirm или /schem cancel.
 */
@EventBusSubscriber(modid = BlueprintWand.MODID, value = Dist.CLIENT)
public class ClientRender {
    /** Прозрачность блоков предпросмотра (0..1). */
    private static final float GHOST_ALPHA = 0.55f;
    /** Дальше этого расстояния (в блоках) от камеры блоки предпросмотра не рисуются. */
    private static final double GHOST_RANGE = 64.0;

    private static final Map<BlockState, List<BakedQuad>[]> QUADS = new HashMap<>();
    private static GhostBlocks lastGhost;

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

        // --- полупрозрачные блоки предпросмотра
        GhostBlocks gb = pv != null ? PreviewPayload.ghostBlocks : null;
        if (gb != null) {
            renderGhost(mc, ps, buf, cam, pv.origin(), gb);
        }

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
            DebugRenderer.renderFilledBox(ps, buf, box, 1.0f, 0.8f, 0.1f, gb != null ? 0.05f : 0.14f);
            LevelRenderer.renderLineBox(ps, buf.getBuffer(RenderType.lines()), box, 1.0f, 0.85f, 0.1f, 1.0f);
        }

        ps.popPose();
        buf.endBatch(Sheets.translucentCullBlockSheet());
        buf.endBatch(RenderType.debugFilledBox());
        buf.endBatch(RenderType.lines());
    }

    private static void renderGhost(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
                                    BlockPos origin, GhostBlocks gb) {
        if (gb != lastGhost) {
            QUADS.clear();
            lastGhost = gb;
        }
        VertexConsumer vc = buf.getBuffer(Sheets.translucentCullBlockSheet());
        BlockPos.MutableBlockPos wp = new BlockPos.MutableBlockPos();
        double range2 = GHOST_RANGE * GHOST_RANGE;

        for (int i = 0; i < gb.lin.length; i++) {
            int idx = gb.lin[i];
            int x = idx % gb.sx;
            int z = (idx / gb.sx) % gb.sz;
            int y = idx / (gb.sx * gb.sz);
            int wx = origin.getX() + x, wy = origin.getY() + y, wz = origin.getZ() + z;

            double dx = wx + 0.5 - cam.x, dy = wy + 0.5 - cam.y, dz = wz + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > range2) continue;

            BlockState st = gb.palette[gb.stateIdx[i]];
            if (st.getRenderShape() != RenderShape.MODEL) continue;

            List<BakedQuad>[] quads = quadsFor(mc, st);
            int mask = gb.mask[i] & 0xFF;
            wp.set(wx, wy, wz);

            ps.pushPose();
            ps.translate(wx, wy, wz);
            for (int d = 0; d < 7; d++) {
                if (d < 6 && (mask & (1 << d)) == 0) continue;
                for (BakedQuad quad : quads[d]) {
                    float shade = mc.level.getShade(quad.getDirection(), quad.isShade());
                    float r = shade, g = shade, b = shade;
                    if (quad.isTinted()) {
                        int c = mc.getBlockColors().getColor(st, mc.level, wp, quad.getTintIndex());
                        if (c != -1) {
                            r *= ((c >> 16) & 255) / 255f;
                            g *= ((c >> 8) & 255) / 255f;
                            b *= (c & 255) / 255f;
                        }
                    }
                    vc.putBulkData(ps.last(), quad, r, g, b, GHOST_ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            }
            ps.popPose();
        }
    }

    /** Кэш полигонов модели блока: 6 граней + «общие» полигоны (индекс 6). */
    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] quadsFor(Minecraft mc, BlockState st) {
        List<BakedQuad>[] cached = QUADS.get(st);
        if (cached != null) return cached;
        BakedModel model = mc.getBlockRenderer().getBlockModel(st);
        RandomSource rnd = RandomSource.create(42L);
        List<BakedQuad>[] arr = new List[7];
        Direction[] dirs = Direction.values();
        for (int d = 0; d < 6; d++) {
            rnd.setSeed(42L);
            arr[d] = model.getQuads(st, dirs[d], rnd);
        }
        rnd.setSeed(42L);
        arr[6] = model.getQuads(st, null, rnd);
        QUADS.put(st, arr);
        return arr;
    }
}
