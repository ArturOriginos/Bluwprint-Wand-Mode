package com.example.blueprintwand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class WandItem extends Item {
    public WandItem(Properties properties) {
        super(properties);
    }

    /** Жезл не ломает блоки (в творческом тоже). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("ЛКМ по блоку — точка 1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("ПКМ по блоку — точка 2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift + ЛКМ — сбросить выделение").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("/schem save <имя> — сохранить").withStyle(ChatFormatting.AQUA));
    }
}
