# 🤖 Stone Seller Bot — Fabric 1.21.4
ВИДЕО С РАБОТОЙ БОТА - https://www.youtube.com/watch?v=JQYX3QSkOf8
Клиентский мод-бот для Minecraft, который автоматически:
- Находит ближайший сундук и забирает из него камень
- Перекладывает камень на хотбар через инвентарь
- Продаёт каждую стопку командой `/ah sell <цена>`
- Ждёт 15 секунд после 8 продаж и повторяет
- Если камень закончился — ждёт 20 секунд пока воронка наполнит сундук

---

## ⌨️ Управление

| Клавиша | Действие |
|---------|----------|
| **X** | Запустить / Остановить бота |

### Смена цены через чат
Напиши в чат (сообщение **не уйдёт на сервер**, только ты увидишь):
```
@seller 4000
```
Бот ответит: `[Bot] Цена обновлена: /ah sell 4000`

Примеры:
```
@seller 500
@seller 10000
@seller 99999
```

---

## 🔄 Алгоритм работы

```
Запуск (X)
    │
    ▼
[Найти сундук в радиусе 10 блоков]
    │
    ▼
[Открыть сундук → Shift+Click весь камень в инвентарь]
    │
    ▼
[Закрыть сундук]
    │
    ▼
[Открыть инвентарь → переложить камень из осн. инвентаря на хотбар → закрыть]
    │
    ▼
[Продавать хотбар слева направо: каждый слот с 64 камнями → /ah sell <цена>]
    │
    ├── Если на хотбаре закончились стопки, но в инвентаре ещё есть:
    │       → снова открыть инвентарь и переложить следующую партию
    │
    ├── После 8 продаж → ждать 15 секунд → продолжить
    │
    └── Если камень закончился везде → ждать 20 сек (воронка) → снова открыть сундук
```

---

## ⚙️ Настройки

### 📌 Кнопка активации
Файл: `src/main/java/com/stoneseller/StoneSeller.java`

```java
toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
        "key.stoneseller.toggle",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_X,      // ← МЕНЯЙ ЗДЕСЬ
        "Stone Seller Bot"
));
```

Популярные значения:
| Клавиша | Константа |
|---------|-----------|
| X | `GLFW.GLFW_KEY_X` |
| Z | `GLFW.GLFW_KEY_Z` |
| G | `GLFW.GLFW_KEY_G` |
| H | `GLFW.GLFW_KEY_H` |
| F6 | `GLFW.GLFW_KEY_F6` |
| F7 | `GLFW.GLFW_KEY_F7` |
| INSERT | `GLFW.GLFW_KEY_INSERT` |
| HOME | `GLFW.GLFW_KEY_HOME` |

---

### 📌 Настройки бота
Файл: `src/main/java/com/stoneseller/StoneSellerBot.java`

В самом начале класса блок `// ============ НАСТРОЙКИ ============`:

```java
// ============ НАСТРОЙКИ ============
private static final Item SELL_ITEM         = Items.STONE;    // что продаём
private static final int  DEFAULT_PRICE     = 10000;          // цена по умолчанию
private static final int  SELLS_PER_SERIES  = 8;              // кол-во продаж до паузы
private static final int  MIN_STACK_SIZE    = 64;             // мин. кол-во в стопке для продажи
private static final int  WAIT_SERIES_TICKS = 300;            // пауза между сериями (тики)
private static final int  WAIT_REFILL_TICKS = 400;            // ожидание воронки (тики)

private static final int DELAY_OPEN_CHEST  = 5;   // задержка открытия сундука
private static final int DELAY_WAIT_SCREEN = 5;   // ожидание появления экрана
private static final int DELAY_LOOT        = 1;   // задержка между shift-click
private static final int DELAY_CLOSE       = 3;   // задержка закрытия экрана
private static final int DELAY_SELL        = 5;   // задержка после команды продажи
private static final int DELAY_MOVE        = 2;   // задержка при перекладывании стопок
// ===================================
```

#### Таблица всех параметров:

| Параметр | Значение | Описание |
|----------|----------|----------|
| `SELL_ITEM` | `Items.STONE` | Предмет для продажи. Меняй на `Items.COBBLESTONE`, `Items.DIRT` и т.д. |
| `DEFAULT_PRICE` | `10000` | Цена по умолчанию при запуске |
| `SELLS_PER_SERIES` | `8` | Сколько стопок продать перед паузой в 15 сек. Например `4` или `16` |
| `MIN_STACK_SIZE` | `64` | Минимум предметов в стопке чтобы продать. `64` = только полные стопки |
| `WAIT_SERIES_TICKS` | `300` | Пауза между сериями продаж. **1 секунда = 20 тиков**. `300` = 15 сек |
| `WAIT_REFILL_TICKS` | `400` | Ожидание воронки когда сундук пуст. `400` = 20 сек |
| `DELAY_OPEN_CHEST` | `5` | Задержка перед открытием сундука (тики). Увеличь если сундук не открывается |
| `DELAY_WAIT_SCREEN` | `5` | Ожидание появления экрана сундука/инвентаря |
| `DELAY_LOOT` | `1` | Задержка между каждым shift-click при грабеже сундука |
| `DELAY_CLOSE` | `3` | Задержка после закрытия экрана |
| `DELAY_SELL` | `5` | Задержка после отправки команды `/ah sell` |
| `DELAY_MOVE` | `2` | Задержка между перекладыванием стопок в инвентаре |

#### Перевод тиков в секунды:
```
тики ÷ 20 = секунды
20  тиков = 1  сек
100 тиков = 5  сек
200 тиков = 10 сек
300 тиков = 15 сек
400 тиков = 20 сек
600 тиков = 30 сек
```

#### Пример — замедлить бота (для лагучих серверов):
```java
private static final int DELAY_OPEN_CHEST  = 10;
private static final int DELAY_WAIT_SCREEN = 10;
private static final int DELAY_LOOT        = 3;
private static final int DELAY_CLOSE       = 5;
private static final int DELAY_SELL        = 10;
private static final int DELAY_MOVE        = 4;
```

#### Пример — изменить количество продаж до паузы:
```java
private static final int SELLS_PER_SERIES = 4;   // пауза каждые 4 продажи
private static final int SELLS_PER_SERIES = 16;  // пауза каждые 16 продаж
```

#### Пример — продавать кобблстоун вместо камня:
```java
private static final Item SELL_ITEM = Items.COBBLESTONE;
```

---

## 🔨 Сборка

### Требования
- Java 21+
- Minecraft 1.21.4 с Fabric Loader 0.16.10+
- Fabric API 0.119.3+1.21.4

### Windows
```bat
cd stone-seller-mod
gradlew.bat build
```

### Linux / Mac
```bash
cd stone-seller-mod
./gradlew build
```

Готовый файл: `build/libs/stone-seller-1.0.0.jar`

### Установка
1. Установи [Fabric Loader](https://fabricmc.net/) для Minecraft 1.21.4
2. Скачай [Fabric API](https://modrinth.com/mod/fabric-api) для 1.21.4
3. Скопируй оба `.jar` в папку `.minecraft/mods/`

---

## 💬 Сообщения бота в чате

| Сообщение | Смысл |
|-----------|-------|
| `§a[Bot] Запущен!` | Бот стартовал |
| `§c[Bot] Остановлен.` | Бот остановлен клавишей X |
| `§a[Bot] Сундук найден: x y z` | Нашёл сундук |
| `§c[Bot] Сундук далеко!` | Сундук вне радиуса 5 блоков |
| `§b[Bot] Продажа 1/8 слот 0 цена 10000` | Продаёт стопку |
| `§e[Bot] 8 продаж. Жду 15 сек...` | Пауза между сериями |
| `§a[Bot] Беру следующую партию из инвентаря...` | Перекладывает следующие стопки |
| `§e[Bot] Камень закончился. Жду воронку 20 сек...` | Ждёт пока воронка наполнит сундук |
| `§a[Bot] Проверяю сундук...` | Снова открывает сундук после ожидания |
| `§a[Bot] Цена обновлена: /ah sell 4000` | Цена успешно изменена через @seller |
"# AutoStoneSeller" 
