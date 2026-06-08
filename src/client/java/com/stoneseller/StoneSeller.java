package com.stoneseller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class StoneSeller implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static KeyBinding statusKey;

    @Override
    public void onInitializeClient() {
        StoneSellerBot.LOGGER.info("[StoneSeller] Мод загружен! HOME = старт/стоп, END = статус.");

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stoneseller.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_HOME,
                "category.stoneseller"
        ));

        statusKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stoneseller.status",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_END,
                "category.stoneseller"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                StoneSellerBot bot = StoneSellerBot.getInstance();
                if (bot.isRunning()) {
                    bot.stop(client);
                } else {
                    bot.start(client);
                }
            }
            while (statusKey.wasPressed()) {
                StoneSellerBot.getInstance().printStatus(client);
            }

            StoneSellerBot.getInstance().tick(client);
        });
    }
}
