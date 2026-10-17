# Пост для GitHub — Spellcraft

Готовый текст: заголовок, описание, фичи, как запустить. Русский вариант ниже,
английский — в блоке `<!-- en -->` в конце файла.

---

## Заголовок

**Spellcraft 1.0.0 — заклинание как программа, провода на три линии в блоке**

## Короткое описание (для описания релиза / About)

Мод для Minecraft 1.21.1 (NeoForge), в котором заклинание — это программа из
типизированных модулей, а машины питаются по энергетической сети, где один блок
провода держит три независимые линии.

## Текст поста

**Spellcraft** — мод, где заклинание не выбирается из списка, а пишется.

Ты ставишь модули на сетку, соединяешь их портами и жмёшь каст: движок
разворачивает схему в ациклический граф и выполняет её. Скрафтил гримуар —
свёл модуль на извлечение энергии бездны и второй на урон, соединил, и получил
 заклинание, которое работает. Копишь и сканируешь блоки — открываются новые
модули и модификаторы.

Энергия бездны при этом единственный кошелёк игрока и цена каждого каста. Её ноль
означает, что игрок рассыпается: на нулевой бездне накладываются слабость и
голод, а ровно через пять минут непрерывной пустоты приходит смерть. Любая единица
энергии бездны сбрасывает таймер сразу, поэтому после смерти и воскрешения важна
каждая.

Вокруг заклинаний выросли машины: извлекатель бездны, плавильня, транспортёр,
сосуд и бак для бездны, модульные сетки и модульный нагрудник, у которого схема
модулей хранится в самом предмете.

**Главное в этом релизе — провода.**

Раньше один блок провода держал ровно одну линию: цвет жил в свойстве блока,
подключения — ещё в шести булевых свойствах. Из-за этого в одну грань машины
входил только один кабель, и «три цвета с одной стороны» сделать было нельзя в
принципе — соседняя грань занята другим блоком.

Теперь всё как в Modern Industrialization:

* **В одном блоке живут три линии** — три провода разных цветов и/или труба
  бездны.
* **Слоты смещены по диагонали блока** (центры в 5/16, 8/16 и 11/16 при сечении
  2 пикселя), поэтому три линии входят в грань машины в трёх разных точках и не
  пересекаются дальше. Линии рядом стоят рядом, а не сливаются в клубок.
* **Цвет по-прежнему разделяет сети**: красный и синий провод, сошедшиеся в одном
  блоке, остаются разными сетями. Машина цвета не имеет и берёт провод любого
  цвета.
* **Углы острые**, как в MI: 45°-вставки, из-за которых линии свисали кубиками в
  пустоте, убраны.
* **Подключения живут в NBT**, а не в свойствах блока: шесть бит на линию плюс
  цвета в blockstate не помещаются (три линии дали бы больше тысячи вариантов, и
  часть состояний просто не нашлась бы). Линии рисует BlockEntityRenderer из тех
  же коробок, по которым считается рамка выделения: что видно, то и выделяется.

Пользоваться просто: **правый клик** предметом-проводом по уже стоящему блоку
сети докладывает линию в свободный слот, **Shift + правый клик** по самой линии
снимает её и роняет предмет, разбор блока роняет все линии, которые в нём жили.

## Что ещё в релизе

* Заклинания, конструктор, гримуар, сканирование блоков, прогрессия, библиотека.
* Энергия бездны, машины, транспортёр, сосуд и бак бездны.
* Модульная броня и модульные сетки.
* 16 цветов проводов с переводами, спрайтами и рецептами крашения
  (4 провода + краситель → 4 провода этого цвета).
* 487 тестов, все зелёные; каждый фикс этого релиза закрыт проверкой, которая
  падает при откате правки.

## Установка

1. Minecraft **1.21.1**, загрузчик **NeoForge 21.1.95**, Java **21**.
2. Положить jar мода в `mods/`.
3. Зависимость — только **GeckoLib 4.7.2** (анимированный тестовый блок); своего
   GUI-фреймворка нет, интерфейс рисуется на `GuiGraphics`.

## Сборка из исходников

```
./gradlew build --offline
```

## Лицензия

MIT.

<!-- en -->

# Post for GitHub — Spellcraft

## Title

**Spellcraft 1.0.0 — spells as programs, three-line wire blocks**

## Short description

A Minecraft 1.21.1 (NeoForge) mod where a spell is a program of typed modules,
and machines are powered over an energy network whose wire block carries three
independent lines.

## Post body

**Spellcraft** is a mod where spells are not picked from a list but written.

You place modules on a grid, wire their ports together and hit cast: the engine
flattens the schema into an acyclic graph and runs it. Craft a spellbook, draw
energy out of the abyss into one module and damage into another, connect them,
and you have a spell that actually works. Copy and scan blocks to unlock new
modules and modifiers.

Abyss energy is the player's only wallet and the price of every cast. Zero abyss
means the player crumbles: weakness and hunger at zero, and death after five
minutes of continuous emptiness. A single unit resets the timer, so every point
matters after a respawn.

Machines grew around the spells: an abyss extractor, a smelter, a conveyor, an
abyss vessel and tank, module grids, and a modular chestplate that stores its own
schema.

**The headline of this release is the wires.**

A wire block used to hold exactly one line: the colour lived in a blockstate
property and the connections in six boolean properties. That made a single cable
per machine face the hard limit — "three colours from one side" was impossible in
principle, because the neighbouring face is taken by another block.

Now it works the way Modern Industrialization does it:

* **Three lines per block** — three coloured wires and/or the abyss pipe.
* **Slots offset diagonally** inside the block (centres at 5/16, 8/16 and 11/16
  with a 2 px section), so three lines enter the same machine face at three
  different points and never cross. Parallel lines run side by side instead of
  collapsing into a knot.
* **Colour still separates networks**: a red and a blue wire meeting in one block
  stay different networks, and machines accept any colour.
* **Corners are sharp**, like in MI: the 45° inserts that left cubes hanging in
  mid-air are gone.
* **Connections live in NBT**, not in blockstate: six bits per line plus colours
  do not fit there (three lines would need a thousand variants and some states
  simply would not exist). A BlockEntityRenderer draws the lines from the same
  boxes the selection shape is built from — what you see is what you highlight.

Using it: **right-click** a wire item on an existing network block to add a line to
a free slot, **shift + right-click** the line itself to remove and drop it, and
breaking the block drops every line it held.

## Also in this release

* Spells, the builder, the spellbook, block scanning, progression and a library.
* Abyss energy, machines, conveyor, abyss vessel and tank.
* Modular armour and module grids.
* 16 wire colours with translations, sprites and dyeing recipes
  (4 wires + 1 dye → 4 wires of that colour).
* 487 tests, all green; every fix in this release is covered by a check that fails
  when the fix is reverted.

## Install

1. Minecraft **1.21.1**, **NeoForge 21.1.95**, Java **21**.
2. Drop the jar into `mods/`.
3. The only dependency is **GeckoLib 4.7.2** (animated test block); the GUI is
   drawn with `GuiGraphics`, no GUI framework of its own.

## Build from source

```
./gradlew build --offline
```

## License

MIT.
