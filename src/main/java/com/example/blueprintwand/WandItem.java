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
        tooltip.add(Msg.t("tooltip.point1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Msg.t("tooltip.point2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Msg.t("tooltip.reset").withStyle(ChatFormatting.GRAY));
        tooltip.add(Msg.t("tooltip.save").withStyle(ChatFormatting.AQUA));
    }
}
