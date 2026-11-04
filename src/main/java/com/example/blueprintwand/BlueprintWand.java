package com.example.blueprintwand;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BlueprintWand.MODID)
public class BlueprintWand {
    public static final String MODID = "blueprintwand";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<Item> WAND = ITEMS.register("blueprint_wand",
            () -> new WandItem(new Item.Properties()
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredItem<Item> LIST = ITEMS.register("schematic_list",
            () -> new ListItem(new Item.Properties().stacksTo(1)));

    public BlueprintWand(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);
        modBus.addListener(this::registerPayloads);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WAND.get());
            event.accept(LIST.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.CODEC, PreviewPayload::handle);
        registrar.playToClient(SchematicListPayload.TYPE, SchematicListPayload.CODEC, SchematicListPayload::handle);
    }
}
