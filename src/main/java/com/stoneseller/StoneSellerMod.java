package com.stoneseller;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoneSellerMod implements ModInitializer {

    public static final String MOD_ID = "stoneseller";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Серверная сторона — ничего не делаем, весь код на клиенте
        LOGGER.info("[StoneSeller] Мод инициализирован.");
    }
}
