package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** Разобранный предпросмотр: только блоки, у которых видна хотя бы одна грань. Только данные, клиентских классов нет. */
public final class GhostBlocks {
    public final int sx, sy, sz;
    /** Индекс 0 не используется. */
    public final BlockState[] palette;
    /** Номер ячейки: (y * sz + z) * sx + x. */
    public final int[] lin;
    public final int[] stateIdx;
    /** Бит d (порядок Direction.values()) — грань d видна. */
    public final byte[] mask;

    private GhostBlocks(int sx, int sy, int sz, BlockState[] palette, int[] lin, int[] stateIdx, byte[] mask) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.lin = lin;
        this.stateIdx = stateIdx;
        this.mask = mask;
    }

    public static GhostBlocks decode(PreviewPayload p) {
        PreviewPayload.Ghost g = p.ghost();
        if (g == null) return null;
        int sx = p.sx(), sy = p.sy(), sz = p.sz();
        long volL = (long) sx * sy * sz;
        if (volL <= 0 || volL > 4_000_000L) return null;
        int vol = (int) volL;

        int[] cells = new int[vol];
        int pos = 0;
        for (int i = 0; i < g.runValue().length; i++) {
            int len = g.runLength()[i];
            if (len < 0 || pos + len > vol) return null;
            if (g.runValue()[i] != 0) Arrays.fill(cells, pos, pos + len, g.runValue()[i]);
            pos += len;
        }

        BlockState[] palette = new BlockState[g.palette().length + 1];
        boolean[] opaqueFull = new boolean[palette.length];
        for (int i = 0; i < g.palette().length; i++) {
            BlockState st = Block.stateById(g.palette()[i]);
            palette[i + 1] = st;
            opaqueFull[i + 1] = st.canOcclude()
                    && Block.isShapeFullBlock(st.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        }

        Direction[] dirs = Direction.values();
        int count = 0;
        int[] tmpLin = new int[vol];
        int[] tmpState = new int[vol];
        byte[] tmpMask = new byte[vol];
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int idx = (y * sz + z) * sx + x;
                    int c = cells[idx];
                    if (c == 0 || c >= palette.length) continue;
                    int m = 0;
                    for (int d = 0; d < 6; d++) {
                        int nx = x + dirs[d].getStepX();
                        int ny = y + dirs[d].getStepY();
                        int nz = z + dirs[d].getStepZ();
                        boolean visible;
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                            visible = true;
                        } else {
                            int n = cells[(ny * sz + nz) * sx + nx];
                            visible = n == 0 || n >= palette.length || !opaqueFull[n];
                        }
                        if (visible) m |= 1 << d;
                    }
                    if (m == 0) continue; // полностью скрыт внутри постройки
                    tmpLin[count] = idx;
                    tmpState[count] = c;
                    tmpMask[count] = (byte) m;
                    count++;
                }
            }
        }
        return new GhostBlocks(sx, sy, sz, palette,
                Arrays.copyOf(tmpLin, count), Arrays.copyOf(tmpState, count), Arrays.copyOf(tmpMask, count));
    }
}
