package com.stoneseller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoneSeller implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("stoneseller");

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[StoneSeller] Мод загружен! X = запуск/стоп. Цена: @seller <цена>");

        // Кнопка X — запуск/стоп
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stoneseller.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "Stone Seller Bot"
        ));

        // Тик-обработчик
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                StoneSellerBot bot = StoneSellerBot.getInstance();
                if (bot.isRunning()) {
                    bot.stop(client);
                } else {
                    bot.start(client);
                }
            }
            StoneSellerBot.getInstance().tick(client);
        });

        // Перехват ИСХОДЯЩИХ сообщений чата — когда ТЫ пишешь "@seller <цена>"
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.trim().startsWith("@seller")) {
                handlePriceCommand(message.trim());
                return false; // НЕ отправляем сообщение на сервер
            }
            return true; // остальные сообщения отправляем как обычно
        });
    }

    /**
     * Обрабатывает команду "@seller <цена>"
     */
    private void handlePriceCommand(String text) {
        // Убираем "@seller" и берём число
        String after = text.substring(7).trim(); // 7 = длина "@seller"

        if (after.isEmpty()) {
            showMessage("§c[Bot] Укажи цену: @seller <цена>  §7Пример: @seller 4000");
            return;
        }

        // Берём первое слово
        String[] parts = after.split("\\s+");
        try {
            int newPrice = Integer.parseInt(parts[0]);
            if (newPrice <= 0) {
                showMessage("§c[Bot] Цена должна быть больше 0!");
                return;
            }
            StoneSellerBot.getInstance().setPrice(newPrice);
            showMessage("§a[Bot] §fЦена обновлена: §b/ah sell " + newPrice);
            LOGGER.info("[StoneSeller] Новая цена: {}", newPrice);
        } catch (NumberFormatException e) {
            showMessage("§c[Bot] '" + parts[0] + "' — не число! Пример: @seller 4000");
        }
    }

    private void showMessage(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(text), false);
        }
    }
}
