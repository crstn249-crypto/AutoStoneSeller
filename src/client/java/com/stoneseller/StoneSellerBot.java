package com.stoneseller;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoneSellerBot {

    // ──────────────── Настройки ────────────────
    private static final Item SELL_ITEM              = Items.STONE;
    private static final int  SELL_PRICE             = 10000;
    private static final String SELL_COMMAND         = "ah sell " + SELL_PRICE;
    private static final int  SELLS_PER_SERIES       = 8;
    /** 30 секунд = 600 тиков */
    private static final int  WAIT_BETWEEN_SERIES    = 600;
    /** 2 минуты = 2400 тиков */
    private static final int  WAIT_FULL_CYCLE        = 2400;
    private static final int  ACTION_DELAY           = 10;
    private static final int  CHEST_OPEN_DELAY       = 20;
    private static final int  SELL_DELAY             = 10;
    private static final double CHEST_REACH          = 5.0;
    // ───────────────────────────────────────────

    public static final Logger LOGGER = LoggerFactory.getLogger("stoneseller");

    private static StoneSellerBot INSTANCE;
    public static StoneSellerBot getInstance() {
        if (INSTANCE == null) INSTANCE = new StoneSellerBot();
        return INSTANCE;
    }

    // ──────── Состояние ────────
    private boolean running     = false;
    private BotState state      = BotState.IDLE;
    private int tickTimer       = 0;
    private int sellCount       = 0;
    private int seriesCount     = 0;
    private BlockPos targetChest = null;
    private int chestSlotIndex  = 0;

    private enum BotState {
        IDLE,
        FIND_CHEST,
        OPEN_CHEST,
        WAIT_CHEST_OPEN,
        LOOT_CHEST,
        CLOSE_CHEST,
        PREPARE_SELL,
        SEND_COMMAND,
        WAIT_AFTER_COMMAND,
        WAIT_BETWEEN_SERIES,
        WAIT_FULL_CYCLE
    }

    public boolean isRunning() { return running; }

    public void start(MinecraftClient client) {
        running      = true;
        state        = BotState.FIND_CHEST;
        tickTimer    = 0;
        sellCount    = 0;
        seriesCount  = 0;
        chestSlotIndex = 0;
        msg(client, "§a[StoneSeller] §fБот §aзапущен§f! HOME = стоп, END = статус.");
        LOGGER.info("[StoneSeller] Бот запущен.");
    }

    public void stop(MinecraftClient client) {
        running = false;
        state   = BotState.IDLE;
        msg(client, "§c[StoneSeller] §fБот §cостановлен§f.");
        LOGGER.info("[StoneSeller] Бот остановлен.");
    }

    public void printStatus(MinecraftClient client) {
        msg(client, "§e[StoneSeller] §fСостояние: §b" + state
                + " §f| Продаж: §b" + sellCount + "/" + SELLS_PER_SERIES
                + " §f| Серий: §b" + seriesCount
                + " §f| Таймер: §b" + tickTimer);
    }

    // ─── Главный тик ───
    public void tick(MinecraftClient client) {
        if (!running) return;
        if (client.player == null || client.world == null) return;

        if (tickTimer > 0) { tickTimer--; return; }

        switch (state) {
            case FIND_CHEST         -> findChest(client);
            case OPEN_CHEST         -> openChest(client);
            case WAIT_CHEST_OPEN    -> waitChestOpen(client);
            case LOOT_CHEST         -> lootChest(client);
            case CLOSE_CHEST        -> closeChest(client);
            case PREPARE_SELL       -> prepareSell(client);
            case SEND_COMMAND       -> sendCommand(client);
            case WAIT_AFTER_COMMAND -> afterCommand(client);
            case WAIT_BETWEEN_SERIES -> startSecondSeries(client);
            case WAIT_FULL_CYCLE    -> startNewCycle(client);
            default -> {}
        }
    }

    // ──────────────────── Шаги ────────────────────

    private void findChest(MinecraftClient client) {
        World world = client.world;
        Vec3d pos   = client.player.getPos();
        BlockPos center = client.player.getBlockPos();

        BlockPos found = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos bp : BlockPos.iterate(
                center.add(-10, -5, -10),
                center.add( 10,  5, 10))) {
            BlockEntity be = world.getBlockEntity(bp);
            if (be instanceof ChestBlockEntity) {
                double d = pos.squaredDistanceTo(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                if (d < bestDist) { bestDist = d; found = bp.toImmutable(); }
            }
        }

        if (found == null) {
            msg(client, "§c[StoneSeller] Сундук не найден! Повтор через 5 сек...");
            tickTimer = 100;
            return;
        }

        targetChest    = found;
        chestSlotIndex = 0;
        msg(client, "§a[StoneSeller] Сундук найден: " + targetChest.toShortString());
        state     = BotState.OPEN_CHEST;
        tickTimer = ACTION_DELAY;
    }

    private void openChest(MinecraftClient client) {
        if (targetChest == null) { state = BotState.FIND_CHEST; return; }

        Vec3d eye   = client.player.getEyePos();
        Vec3d chest = Vec3d.ofCenter(targetChest);

        if (eye.distanceTo(chest) > CHEST_REACH) {
            msg(client, "§c[StoneSeller] Сундук слишком далеко! Ищу заново...");
            state = BotState.FIND_CHEST;
            return;
        }

        lookAt(client, chest);

        BlockHitResult hit = new BlockHitResult(chest, Direction.UP, targetChest, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);

        state     = BotState.WAIT_CHEST_OPEN;
        tickTimer = CHEST_OPEN_DELAY;
        LOGGER.info("[StoneSeller] Открываем сундук {}...", targetChest.toShortString());
    }

    private void waitChestOpen(MinecraftClient client) {
        if (client.currentScreen instanceof GenericContainerScreen) {
            state     = BotState.LOOT_CHEST;
            tickTimer = ACTION_DELAY;
            LOGGER.info("[StoneSeller] Сундук открыт, начинаем лут.");
        } else {
            // Экран ещё не появился — пробуем открыть заново
            msg(client, "§e[StoneSeller] Жду открытия сундука...");
            state     = BotState.OPEN_CHEST;
            tickTimer = CHEST_OPEN_DELAY;
        }
    }

    private void lootChest(MinecraftClient client) {
        if (!(client.currentScreen instanceof GenericContainerScreen chestScreen)) {
            msg(client, "§c[StoneSeller] Экран закрылся, переоткрываю...");
            state     = BotState.OPEN_CHEST;
            tickTimer = ACTION_DELAY;
            return;
        }

        ScreenHandler handler = chestScreen.getScreenHandler();
        int chestSize = handler.slots.size() - 36; // слоты самого сундука

        for (int i = chestSlotIndex; i < chestSize; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isOf(SELL_ITEM)) {
                client.interactionManager.clickSlot(
                        handler.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                chestSlotIndex = i + 1;
                tickTimer = 3;
                return;
            }
        }

        // Весь камень взят
        msg(client, "§a[StoneSeller] Камень из сундука взят. Закрываю...");
        state     = BotState.CLOSE_CHEST;
        tickTimer = ACTION_DELAY;
    }

    private void closeChest(MinecraftClient client) {
        if (client.currentScreen != null) {
            client.player.networkHandler.sendPacket(
                    new CloseHandledScreenC2SPacket(
                            client.player.currentScreenHandler.syncId));
            client.setScreen(null);
        }

        if (hasStone(client)) {
            msg(client, "§a[StoneSeller] Начинаю продажи!");
            state       = BotState.PREPARE_SELL;
            sellCount   = 0;
            seriesCount = 0;
        } else {
            msg(client, "§c[StoneSeller] В инвентаре нет камня, повторяю поиск...");
            state = BotState.FIND_CHEST;
        }
        tickTimer = ACTION_DELAY;
    }

    private void prepareSell(MinecraftClient client) {
        if (!hasStone(client)) {
            msg(client, "§e[StoneSeller] Камень кончился! Иду за новым...");
            state     = BotState.FIND_CHEST;
            tickTimer = ACTION_DELAY;
            return;
        }
        moveStoneToSlot0(client);
        state     = BotState.SEND_COMMAND;
        tickTimer = ACTION_DELAY;
    }

    private void sendCommand(MinecraftClient client) {
        // Убеждаемся что в слоте 0 хотбара есть камень
        ItemStack slot0 = client.player.getInventory().getStack(0);
        if (!slot0.isOf(SELL_ITEM)) {
            state     = BotState.PREPARE_SELL;
            tickTimer = ACTION_DELAY;
            return;
        }

        client.player.getInventory().selectedSlot = 0;
        client.player.networkHandler.sendChatCommand(SELL_COMMAND);
        sellCount++;

        LOGGER.info("[StoneSeller] Продажа #{} серия {}/{}", sellCount, seriesCount + 1, 2);
        msg(client, "§b[StoneSeller] §fПродажа §b" + sellCount + "/" + SELLS_PER_SERIES
                + " §f| серия §b" + (seriesCount + 1) + "/2");

        state     = BotState.WAIT_AFTER_COMMAND;
        tickTimer = SELL_DELAY;
    }

    private void afterCommand(MinecraftClient client) {
        if (sellCount < SELLS_PER_SERIES) {
            // Ещё не все продажи в серии
            state     = BotState.PREPARE_SELL;
            tickTimer = ACTION_DELAY;
            return;
        }

        seriesCount++;
        sellCount = 0;

        if (seriesCount == 1) {
            msg(client, "§e[StoneSeller] Серия 1 завершена. Жду 30 секунд...");
            state     = BotState.WAIT_BETWEEN_SERIES;
            tickTimer = WAIT_BETWEEN_SERIES;
        } else {
            // seriesCount == 2
            msg(client, "§e[StoneSeller] Обе серии завершены. Жду 2 минуты...");
            seriesCount = 0;
            state       = BotState.WAIT_FULL_CYCLE;
            tickTimer   = WAIT_FULL_CYCLE;
        }
    }

    private void startSecondSeries(MinecraftClient client) {
        msg(client, "§a[StoneSeller] Начинаю серию 2!");
        state     = BotState.PREPARE_SELL;
        tickTimer = ACTION_DELAY;
    }

    private void startNewCycle(MinecraftClient client) {
        msg(client, "§a[StoneSeller] Новый цикл! Проверяю инвентарь...");
        if (hasStone(client)) {
            state = BotState.PREPARE_SELL;
        } else {
            msg(client, "§e[StoneSeller] Камень кончился, иду к сундуку.");
            state          = BotState.FIND_CHEST;
            chestSlotIndex = 0;
        }
        tickTimer = ACTION_DELAY;
    }

    // ──────────────────── Утилиты ────────────────────

    private boolean hasStone(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).isOf(SELL_ITEM)) return true;
        }
        return false;
    }

    /**
     * Перекладывает камень в слот 0 хотбара.
     * Если камень уже в хотбаре — выбирает тот слот.
     * Если в основном инвентаре — свапает с слотом 0.
     */
    private void moveStoneToSlot0(MinecraftClient client) {
        var inv = client.player.getInventory();

        // Уже в слоте 0?
        if (inv.getStack(0).isOf(SELL_ITEM)) {
            inv.selectedSlot = 0;
            return;
        }

        // Ищем камень
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(SELL_ITEM)) continue;

            if (i < 9) {
                // В хотбаре — просто выбираем
                inv.selectedSlot = i;
            } else {
                // В основном инвентаре — свапаем с хотбар-слотом 0
                // В playerScreenHandler: основной инвентарь = слоты 9..35 (handler index = i)
                // Хотбар = слоты 36..44 (handler index = 36 + hotbarSlot)
                // SWAP action с button = номер хотбар-слота (0-8)
                ScreenHandler handler = client.player.playerScreenHandler;
                // handler slot для inventory[i] при i>=9: handler.slots[i] = основной инвентарь
                client.interactionManager.clickSlot(
                        handler.syncId,
                        i,      // слот в handler (9..35 = основной инвентарь)
                        0,      // button = хотбар слот 0
                        SlotActionType.SWAP,
                        client.player);
                inv.selectedSlot = 0;
            }
            return;
        }
    }

    private void lookAt(MinecraftClient client, Vec3d target) {
        Vec3d diff  = target.subtract(client.player.getEyePos());
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, hDist));
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }

    private void msg(MinecraftClient client, String text) {
        if (client.player != null)
            client.player.sendMessage(Text.literal(text), false);
    }
}
