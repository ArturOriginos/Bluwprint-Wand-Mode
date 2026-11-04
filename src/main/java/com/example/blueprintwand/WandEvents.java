package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = BlueprintWand.MODID)
public class WandEvents {

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof WandItem)) return;
        event.setCanceled(true); // не ломаем блок
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;

        Player player = event.getEntity();
        if (player.isShiftKeyDown()) {
            Selection.clear(player.getUUID());
            msg(event.getLevel().isClientSide(), player, Msg.t("msg.cleared"));
            return;
        }
        Selection sel = Selection.get(player.getUUID());
        sel.setPos1(event.getPos(), event.getLevel().dimension());
        msg(event.getLevel().isClientSide(), player, describe(true, event.getPos(), sel));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getItemStack().getItem() instanceof WandItem)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Player player = event.getEntity();
        Selection sel = Selection.get(player.getUUID());
        sel.setPos2(event.getPos(), event.getLevel().dimension());
        msg(event.getLevel().isClientSide(), player, describe(false, event.getPos(), sel));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        SchemCommand.register(event.getDispatcher());
    }

    private static Component describe(boolean first, BlockPos pos, Selection sel) {
        MutableComponent c = Msg.t(first ? "msg.point1" : "msg.point2", pos.getX(), pos.getY(), pos.getZ());
        if (sel.complete()) {
            BlockPos a = sel.min(), b = sel.max();
            c.append(Msg.t("msg.size", b.getX() - a.getX() + 1, b.getY() - a.getY() + 1, b.getZ() - a.getZ() + 1, sel.volume()));
        }
        return c;
    }

    /** Сообщение показываем один раз — с серверной стороны. */
    private static void msg(boolean clientSide, Player player, Component text) {
        if (!clientSide) {
            player.displayClientMessage(text, true);
        }
    }
}
