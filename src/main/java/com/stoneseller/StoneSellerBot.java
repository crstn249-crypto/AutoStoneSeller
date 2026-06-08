package com.stoneseller;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class StoneSellerBot {

    // ============ НАСТРОЙКИ ============
    private static final Item SELL_ITEM         = Items.STONE;
    private static final int  DEFAULT_PRICE     = 10000;
    private static final int  SELLS_PER_SERIES  = 8;
    private static final int  MIN_STACK_SIZE    = 64;
    /** 15 секунд = 300 тиков */
    private static final int  WAIT_SERIES_TICKS = 300;
    /** 20 секунд = 400 тиков — ждём воронку */
    private static final int  WAIT_REFILL_TICKS = 400;

    private static final int DELAY_OPEN_CHEST  = 5;
    private static final int DELAY_WAIT_SCREEN = 5;
    private static final int DELAY_LOOT        = 1;
    private static final int DELAY_CLOSE       = 3;
    private static final int DELAY_SELL        = 5;
    private static final int DELAY_MOVE        = 2;

    private static final double CHEST_REACH = 5.0;
    // ===================================

    private static StoneSellerBot INSTANCE;
    public static StoneSellerBot getInstance() {
        if (INSTANCE == null) INSTANCE = new StoneSellerBot();
        return INSTANCE;
    }

    private enum State {
        IDLE,
        FIND_CHEST,
        OPEN_CHEST,
        WAIT_SCREEN,
        LOOT_CHEST,
        CLOSE_CHEST,
        OPEN_INV,
        WAIT_INV_OPEN,
        FILL_HOTBAR,       // shift-click камень в хотбар пока инвентарь открыт
        CLOSE_INV,
        SELL_HOTBAR,       // продаём слоты хотбара по очереди
        SELL_COMMAND,      // отправляем /ah sell
        AFTER_SELL,
        WAIT_SERIES,
        WAIT_REFILL
    }

    private boolean  running       = false;
    private State    state         = State.IDLE;
    private int      timer         = 0;
    private int      sellCount     = 0;
    private int      sellSlot      = 0;  // текущий слот хотбара при продаже
    private BlockPos chestPos      = null;
    private int      chestScanSlot = 0;
    private int      invScanSlot   = 0;  // текущий слот при заполнении хотбара
    private int      sellPrice     = DEFAULT_PRICE;

    public boolean isRunning() { return running; }
    public void setPrice(int price) { this.sellPrice = price; }
    public int  getPrice()          { return sellPrice; }

    public void start(MinecraftClient mc) {
        running       = true;
        state         = State.FIND_CHEST;
        timer         = 0;
        sellCount     = 0;
        sellSlot      = 0;
        chestScanSlot = 0;
        msg(mc, "§a[Bot] Запущен! §7(X = стоп)  Цена: §b" + sellPrice + " §7(@seller <цена>)");
    }

    public void stop(MinecraftClient mc) {
        running = false;
        state   = State.IDLE;
        timer   = 0;
        msg(mc, "§c[Bot] Остановлен.");
    }

    public void tick(MinecraftClient mc) {
        if (!running) return;
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        switch (state) {
            case FIND_CHEST    -> stepFindChest(mc);
            case OPEN_CHEST    -> stepOpenChest(mc);
            case WAIT_SCREEN   -> stepWaitScreen(mc);
            case LOOT_CHEST    -> stepLootChest(mc);
            case CLOSE_CHEST   -> stepCloseChest(mc);
            case OPEN_INV      -> stepOpenInv(mc);
            case WAIT_INV_OPEN -> stepWaitInvOpen(mc);
            case FILL_HOTBAR   -> stepFillHotbar(mc);
            case CLOSE_INV     -> stepCloseInv(mc);
            case SELL_HOTBAR   -> stepSellHotbar(mc);
            case SELL_COMMAND  -> stepSellCommand(mc);
            case AFTER_SELL    -> stepAfterSell(mc);
            case WAIT_SERIES   -> stepWaitSeries(mc);
            case WAIT_REFILL   -> stepWaitRefill(mc);
            default -> {}
        }
    }

    // ─── 1. Найти сундук ───
    private void stepFindChest(MinecraftClient mc) {
        BlockPos center = mc.player.getBlockPos();
        Vec3d    eye    = mc.player.getEyePos();
        BlockPos best   = null;
        double   bestD  = Double.MAX_VALUE;

        for (BlockPos bp : BlockPos.iterate(
                center.add(-10, -5, -10),
                center.add( 10,  5, 10))) {
            BlockEntity be = mc.world.getBlockEntity(bp);
            if (!(be instanceof ChestBlockEntity)) continue;
            double d = eye.squaredDistanceTo(bp.getX()+0.5, bp.getY()+0.5, bp.getZ()+0.5);
            if (d < bestD) { bestD = d; best = bp.toImmutable(); }
        }

        if (best == null) {
            msg(mc, "§c[Bot] Сундук не найден! Повтор через 3 сек...");
            timer = 60;
            return;
        }

        chestPos      = best;
        chestScanSlot = 0;
        state = State.OPEN_CHEST;
        timer = DELAY_OPEN_CHEST;
    }

    // ─── 2. Открыть сундук ───
    private void stepOpenChest(MinecraftClient mc) {
        Vec3d chest = Vec3d.ofCenter(chestPos);
        if (mc.player.getEyePos().distanceTo(chest) > CHEST_REACH) {
            msg(mc, "§c[Bot] Сундук далеко! Ищу заново...");
            state = State.FIND_CHEST;
            return;
        }
        lookAt(mc, chest);
        mc.interactionManager.interactBlock(
                mc.player, Hand.MAIN_HAND,
                new BlockHitResult(chest, Direction.UP, chestPos, false));
        state = State.WAIT_SCREEN;
        timer = DELAY_WAIT_SCREEN;
    }

    // ─── 3. Ждём экрана сундука ───
    private void stepWaitScreen(MinecraftClient mc) {
        if (mc.currentScreen instanceof GenericContainerScreen) {
            state = State.LOOT_CHEST;
            timer = 0;
        } else {
            state = State.OPEN_CHEST;
            timer = DELAY_WAIT_SCREEN;
        }
    }

    // ─── 4. Shift-click весь камень из сундука ───
    private void stepLootChest(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen scr)) {
            state = State.OPEN_CHEST;
            timer = DELAY_OPEN_CHEST;
            return;
        }

        ScreenHandler h         = scr.getScreenHandler();
        int           chestSize = h.slots.size() - 36;

        for (int i = chestScanSlot; i < chestSize; i++) {
            if (h.slots.get(i).getStack().isOf(SELL_ITEM)) {
                mc.interactionManager.clickSlot(
                        h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                chestScanSlot = i + 1;
                timer = DELAY_LOOT;
                return;
            }
        }

        state = State.CLOSE_CHEST;
        timer = DELAY_CLOSE;
    }

    // ─── 5. Закрыть сундук ───
    private void stepCloseChest(MinecraftClient mc) {
        if (mc.currentScreen != null) {
            mc.player.networkHandler.sendPacket(
                    new CloseHandledScreenC2SPacket(
                            mc.player.currentScreenHandler.syncId));
            mc.setScreen(null);
        }
        state = State.OPEN_INV;
        timer = DELAY_CLOSE;
    }

    // ─── 6. Открыть инвентарь ───
    private void stepOpenInv(MinecraftClient mc) {
        mc.setScreen(new InventoryScreen(mc.player));
        state = State.WAIT_INV_OPEN;
        timer = 3;
    }

    // ─── 7. Ждём пока инвентарь откроется ───
    private void stepWaitInvOpen(MinecraftClient mc) {
        if (mc.currentScreen instanceof InventoryScreen) {
            invScanSlot = 0; // сканируем весь инвентарь 0-35
            state = State.FILL_HOTBAR;
            timer = 0;
        } else {
            // Не открылся — пробуем ещё раз
            state = State.OPEN_INV;
            timer = 3;
        }
    }

    // ─── 8. Перекладываем камень из инвентаря на хотбар ───
    // В открытом InventoryScreen в playerScreenHandler индексы:
    //   крафт-выход:  0
    //   крафт-сетка:  1-4
    //   броня:        5-8
    //   осн.инв.:     9-35   (соответствует inventory slots 9-35)
    //   хотбар:       36-44  (соответствует inventory slots 0-8)
    //
    // QUICK_MOVE на слот основного инвентаря (9-35) переложит на хотбар,
    // QUICK_MOVE на слот хотбара (36-44) переложит в основной инвентарь.
    //
    // Мы хотим переложить из основного инвентаря (9-35) на хотбар:
    // handlerSlot = invSlot (для 9-35) — это уже правильный индекс.
    private void stepFillHotbar(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            state = State.CLOSE_INV;
            timer = DELAY_CLOSE;
            return;
        }

        ScreenHandler h = mc.player.playerScreenHandler;

        // Сначала проверяем: есть ли вообще что перекладывать (камень в осн. инвентаре 9-35)
        // и есть ли свободное место на хотбаре
        boolean hotbarFull = true;
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(SELL_ITEM)) { hotbarFull = false; break; }
        }

        if (hotbarFull) {
            // Хотбар уже полон камнем — закрываем
            state = State.CLOSE_INV;
            timer = DELAY_CLOSE;
            return;
        }

        // Ищем камень в основном инвентаре (invSlot 9-35, handlerSlot тот же)
        for (int invSlot = Math.max(invScanSlot, 9); invSlot < 36; invSlot++) {
            var stack = mc.player.getInventory().getStack(invSlot);
            if (stack.isOf(SELL_ITEM)) {
                // QUICK_MOVE из основного инвентаря переместит на хотбар
                mc.interactionManager.clickSlot(
                        h.syncId,
                        invSlot,   // handlerSlot = invSlot для осн. инвентаря (9-35)
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player);
                invScanSlot = invSlot + 1;
                timer = DELAY_MOVE;
                return;
            }
        }

        // Больше нечего перекладывать — закрываем инвентарь
        state = State.CLOSE_INV;
        timer = DELAY_CLOSE;
    }

    // ─── 9. Закрыть инвентарь ───
    private void stepCloseInv(MinecraftClient mc) {
        if (mc.currentScreen != null) {
            mc.player.networkHandler.sendPacket(
                    new CloseHandledScreenC2SPacket(
                            mc.player.currentScreenHandler.syncId));
            mc.setScreen(null);
        }
        sellSlot = 0;
        state = State.SELL_HOTBAR;
        timer = DELAY_CLOSE;
    }

    // ─── 10. Продаём слоты хотбара по очереди ───
    private void stepSellHotbar(MinecraftClient mc) {
        var inv = mc.player.getInventory();

        // Ищем следующий слот хотбара (0-8) с полной стопкой
        for (int i = sellSlot; i < 9; i++) {
            var stack = inv.getStack(i);
            if (stack.isOf(SELL_ITEM) && stack.getCount() >= MIN_STACK_SIZE) {
                sellSlot = i;
                inv.selectedSlot = sellSlot;
                mc.player.networkHandler.sendPacket(
                        new UpdateSelectedSlotC2SPacket(sellSlot));
                state = State.SELL_COMMAND;
                timer = 3;
                return;
            }
        }

        // На хотбаре не осталось полных стопок
        // Проверяем — есть ли ещё камень в основном инвентаре?
        boolean hasMoreInInv = false;
        for (int i = 9; i < 36; i++) {
            var s = inv.getStack(i);
            if (s.isOf(SELL_ITEM) && s.getCount() >= MIN_STACK_SIZE) {
                hasMoreInInv = true;
                break;
            }
        }

        if (hasMoreInInv) {
            // Есть ещё — открываем инвентарь и перекладываем следующую партию
            msg(mc, "§a[Bot] Беру следующую партию из инвентаря...");
            state = State.OPEN_INV;
            timer = DELAY_CLOSE;
        } else {
            // Камень закончился — идём к сундуку
            int total = countStone(mc);
            if (total == 0) {
                msg(mc, "§e[Bot] Камень закончился. Жду воронку 20 сек...");
                state = State.WAIT_REFILL;
                timer = WAIT_REFILL_TICKS;
            } else {
                msg(mc, "§e[Bot] Нет полных стопок (осталось " + total + " шт). Жду воронку 20 сек...");
                state = State.WAIT_REFILL;
                timer = WAIT_REFILL_TICKS;
            }
        }
    }

    // ─── 11. Отправляем команду /ah sell ───
    private void stepSellCommand(MinecraftClient mc) {
        var inv   = mc.player.getInventory();
        var stack = inv.getStack(sellSlot);

        if (!stack.isOf(SELL_ITEM) || stack.getCount() < MIN_STACK_SIZE) {
            // Что-то пошло не так — ищем дальше
            sellSlot++;
            state = State.SELL_HOTBAR;
            timer = 0;
            return;
        }

        mc.player.networkHandler.sendChatCommand("ah sell " + sellPrice);
        sellCount++;

        msg(mc, "§b[Bot] §fПродажа §b" + sellCount + "/" + SELLS_PER_SERIES
                + "§f  слот §b" + sellSlot
                + "§f  цена §b" + sellPrice
                + "§f  кол-во: §b" + stack.getCount());

        sellSlot++;
        state = State.AFTER_SELL;
        timer = DELAY_SELL;
    }

    // ─── 12. После продажи ───
    private void stepAfterSell(MinecraftClient mc) {
        if (sellCount >= SELLS_PER_SERIES) {
            sellCount = 0;
            msg(mc, "§e[Bot] 8 продаж. Жду 15 сек...");
            state = State.WAIT_SERIES;
            timer = WAIT_SERIES_TICKS;
        } else {
            state = State.SELL_HOTBAR;
            timer = 0;
        }
    }

    // ─── 13. После 15 сек ───
    private void stepWaitSeries(MinecraftClient mc) {
        msg(mc, "§a[Bot] Продолжаю!");
        // Проверяем хотбар
        boolean hasOnHotbar = false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var s = inv.getStack(i);
            if (s.isOf(SELL_ITEM) && s.getCount() >= MIN_STACK_SIZE) {
                hasOnHotbar = true;
                break;
            }
        }

        if (hasOnHotbar) {
            sellSlot = 0;
            state = State.SELL_HOTBAR;
        } else {
            // Перекладываем из инвентаря
            state = State.OPEN_INV;
        }
        timer = 0;
    }

    // ─── 14. Ждём воронку 20 сек ───
    private void stepWaitRefill(MinecraftClient mc) {
        msg(mc, "§a[Bot] Проверяю сундук...");
        chestScanSlot = 0;
        sellSlot      = 0;
        if (chestPos != null) {
            state = State.OPEN_CHEST;
        } else {
            state = State.FIND_CHEST;
        }
        timer = DELAY_OPEN_CHEST;
    }

    // ════════ Утилиты ════════

    private int countStone(MinecraftClient mc) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            var s = mc.player.getInventory().getStack(i);
            if (s.isOf(SELL_ITEM)) total += s.getCount();
        }
        return total;
    }

    private void lookAt(MinecraftClient mc, Vec3d target) {
        Vec3d  diff  = target.subtract(mc.player.getEyePos());
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        mc.player.setYaw  ((float) Math.toDegrees( Math.atan2(-diff.x, diff.z)));
        mc.player.setPitch((float) Math.toDegrees(-Math.atan2( diff.y, hDist)));
    }

    private void msg(MinecraftClient mc, String text) {
        if (mc.player != null)
            mc.player.sendMessage(Text.literal(text), false);
    }
}
