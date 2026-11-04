package com.example.blueprintwand;

import net.minecraft.client.Minecraft;

import java.util.List;

/** Клиентская точка входа: нужна, чтобы серверный код не трогал клиентские классы. */
public final class ListScreenOpener {
    private ListScreenOpener() {}

    public static void open(List<SchematicListPayload.Entry> entries) {
        Minecraft.getInstance().setScreen(new SchematicListScreen(entries));
    }
}
