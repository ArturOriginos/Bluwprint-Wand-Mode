package com.example.blueprintwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
            msg(event.getLevel().isClientSide(), player, "Выделение сброшено");
            return;
        }
        Selection sel = Selection.get(player.getUUID());
        sel.setPos1(event.getPos(), event.getLevel().dimension());
        msg(event.getLevel().isClientSide(), player, describe("Точка 1", event.getPos(), sel));
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
        msg(event.getLevel().isClientSide(), player, describe("Точка 2", event.getPos(), sel));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        SchemCommand.register(event.getDispatcher());
    }

    private static String describe(String label, BlockPos pos, Selection sel) {
        String s = label + ": " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (sel.complete()) {
            BlockPos a = sel.min(), b = sel.max();
            s += "  |  размер " + (b.getX() - a.getX() + 1) + "×" + (b.getY() - a.getY() + 1) + "×" + (b.getZ() - a.getZ() + 1)
                    + " (" + sel.volume() + " блоков)";
        }
        return s;
    }

    /** Сообщение показываем один раз — с серверной стороны. */
    private static void msg(boolean clientSide, Player player, String text) {
        if (!clientSide) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }
}
