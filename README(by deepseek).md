# Mouse XButton Fix

Клиентский мод для Minecraft **1.8.9** и **1.12.2** (Forge), который исправляет баг LWJGL:
при зажатом **Shift** (а также Ctrl, Alt или любой другой кнопке мыши) нажатие
**Mouse4** приходит в игру как **Mouse5** и срабатывает то, на что назначен Mouse5
(например, «Toggle Perspective» / смена вида от третьего лица).

## Причина

Баг в `org.lwjgl.opengl.WindowsDisplay` (LWJGL 2.9.4, используется в обеих версиях):

```java
case WM_XBUTTONDOWN:
    if ((wParam & 0xFF) == MK_XBUTTON1) handleMouseButton(3, 1, millis);
    else                               handleMouseButton(4, 1, millis);
```

Младшее слово `wParam` содержит не только `MK_XBUTTON1` (`0x20`), но и состояние
модификаторов: `MK_SHIFT` (`0x04`), `MK_CONTROL` (`0x08`), `MK_LBUTTON` (`0x01`),
`MK_RBUTTON` (`0x02`), `MK_MBUTTON` (`0x10`). Как только выставлен любой лишний бит,
сравнение не проходит — и Mouse4 уходит в игру как Mouse5.

Отпускание обрабатывается через старшее слово и **корректно**:

```java
case WM_XBUTTONUP:
    if ((wParam >> 16) == XBUTTON1) handleMouseButton(3, 0, millis);
    else                            handleMouseButton(4, 0, millis);
```

Из-за этой асимметрии LWJGL ещё и «залипает»: после Shift+Mouse4 состояние Mouse5
навсегда остаётся «нажато», пока не нажать/отпустить настоящий Mouse5.

## Как исправляется

Классы `org.lwjgl.*` в Forge загружает системный загрузчик, поэтому патчить их
трансформером нельзя. Мод вместо этого подменяет `Mouse.implementation` (поле
`private static InputImplementation`) на динамический прокси, который:

* **`readMouse`** — если пришло событие «Mouse5 нажата», а физически нажата
  **Mouse4** (проверяется через `user32!GetAsyncKeyState`, JNA уже есть в игре),
  событие переписывается в «Mouse4 нажата»;
* **`pollMouse`** — состояние кнопок 3 и 4 берётся из реального состояния железа,
  что заодно убирает «залипший» Mouse5.

Всё остальное (клавиатура, курсор, создание/удаление мыши, дисплей) просто
проксируется без изменений. Правится **только 4 → 3**, никогда наоборот, и только
при наличии однозначных доказательств: настоящий Mouse5 продолжает работать всегда
(в том числе с зажатым Shift).

Установка происходит в `FMLInitializationEvent` и подстраховывается каждый тик, так
что пересоздание дисплея тоже обрабатывается.

## Установка

1. Установите Forge для нужной версии (1.8.9 или 1.12.2).
2. Положите **один** jar в папку `mods`:
   * `MouseXButtonFix-1.0.0-mc1.8.9.jar` — для 1.8.9;
   * `MouseXButtonFix-1.0.0-mc1.12.2.jar` — для 1.12.2.
3. Запустите игру. В логе должно появиться:

   ```
   [MouseXButtonFix] Physical side button state available (user32!GetAsyncKeyState).
   [MouseXButtonFix] Mouse button fix installed over org.lwjgl.opengl.WindowsDisplay.
   ```

Мод клиентский: на сервер его ставить не нужно, на геймплей и античит не влияет.
Работает только на Windows (в других ОС баг отсутствует, мод просто ничего не делает).

## Сборка

Нужен JDK 8 (или JDK 9+ — тогда компиляция идёт с `--release 8`).
Minecraft и Gradle не требуются: мод компилируется против Forge universal, LWJGL и JNA.

```powershell
.\build.ps1 -Test
```

* зависимости скачиваются в `libs\` при первом запуске;
* готовые jar-ы появляются в `dist\`;
* `-Test` дополнительно собирает и запускает юнит-тесты.

Можно указать конкретный JDK: `.\build.ps1 -JavaHome "C:\Program Files\Java\jdk1.8.0_xxx"`.

## Тесты

* `com.mousexbuttonfix.WrapperTest` — 22 проверки логики на точной эмуляции
  поведения LWJGL (`WM_XBUTTONUP` корректен, `WM_XBUTTONDOWN` — нет): Shift+Mouse4,
  Shift+Mouse5, обе кнопки в одном кадре, Mouse4 зажат + нажат Mouse5, отпускание
  (снятие «залипания»), быстрые клики, отсутствие JNA, проброс остальных методов.
* `com.mousexbuttonfix.DisplayTest` — сквозная проверка на настоящем LWJGL с
  настоящим окном: создаёт окно, шлёт `PostMessage(WM_XBUTTONDOWN/UP)` с теми же
  `wParam`, что присылает Windows, и проверяет, что игра видит правильные события.
  Для запуска нужны нативные библиотеки LWJGL:

  ```powershell
  # lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar -> lwjgl64.dll
  java -Djava.library.path=<папка_с_dll> -cp "build\test-classes;build\classes-1.12.2;libs\lwjgl.jar;libs\forge-1.12.2-universal.jar;libs\jna-4.4.0.jar" com.mousexbuttonfix.DisplayTest
  ```

* `com.mousexbuttonfix.LoaderTest` — проверка в условиях, как у Forge: классы мода
  грузятся дочерним загрузчиком из готового jar-а, а LWJGL — родительским. Убеждается,
  что подмена `Mouse.implementation` работает через границу загрузчиков.

  ```powershell
  java -Djava.library.path=<папка_с_dll> -cp "build\test-classes;libs\lwjgl.jar;libs\forge-1.12.2-universal.jar;libs\jna-4.4.0.jar" com.mousexbuttonfix.LoaderTest "dist\MouseXButtonFix-1.0.0-mc1.12.2.jar"
  ```

* `com.mousexbuttonfix.PhysicalStateProbe 15` — ручная проверка: 15 секунд печатает
  изменения физического состояния Mouse4/Mouse5 (удобно убедиться, что JNA и
  `GetAsyncKeyState` видят вашу мышь).

## Ограничения

* Клик, который целиком укладывается между двумя опросами мыши (нажатие и отпускание
  в одном кадре, обычно < 16 мс), по железу уже не отличить; такие клики чинятся
  резервной эвристикой (пара «нажатие Mouse5 → отпускание Mouse4»), но 100 % гарантии
  для них нет. Обычное нажатие с удержанием исправляется точно.
* Если JNA/user32 недоступны (крайне редко), мод не ломает Mouse5, а лишь частично
  исправляет события — об этом будет предупреждение в логе.

## Структура

```
src/main/java/com/mousexbuttonfix/
  MouseXButtonFix.java       @Mod, clientSideOnly
  ClientTickHandler.java     держит фикс установленным
  MouseFixInstaller.java     подмена Mouse.implementation
  XButtonMouseWrapper.java   прокси: readMouse / pollMouse
  PhysicalState.java         интерфейс источника состояния
  JnaPhysicalState.java      user32!GetAsyncKeyState через JNA
  Log.java                   логирование
src/main/resources/
  1.8.9/mcmod.info, 1.12.2/mcmod.info, MANIFEST.MF
tests/java/com/mousexbuttonfix/
  WrapperTest.java, FakeInputImplementation.java, DisplayTest.java, PhysicalStateProbe.java
build.ps1                    сборка обоих jar-ов
libs/                        зависимости для сборки (скачиваются скриптом)
dist/                        готовые jar-ы
```
