package com.example.blueprintwand;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Все тексты мода — переводимые (assets/blueprintwand/lang). */
public final class Msg {
    private Msg() {}

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable(BlueprintWand.MODID + "." + key, args);
    }
}
