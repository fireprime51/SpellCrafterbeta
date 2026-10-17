# Карта кода

Путь к каждому исходнику и коротко о том, что в нём находится. Порядок
совпадает с деревом пакетов, так что по названию файла можно догадаться,
где искать остальное. Подробности — в javadoc самого файла.

## Основной код

### `com/github/fireprime5/spellcraft/armor/ArmorEvents.java`

класс `ArmorEvents`, пакет `com.github.fireprime5.spellcraft.armor` — 549 строк

Что модули делают, пока броня надета. Модули брони не исполняются как заклинание целиком: у них нет «запуска», они висят на груди постоянно. Поэтому схема читается, а не кастуется — триггер «когда ударили» срабатывает от события урона, а полёт действует, пока модуль стоит в сетке.

### `com/github/fireprime5/spellcraft/armor/ArmorModuleIds.java`

класс `ArmorModuleIds`, пакет `com.github.fireprime5.spellcraft.armor` — 14 строк

Stable identifiers shared by the armor schema and its runtime hooks.

### `com/github/fireprime5/spellcraft/armor/Eternity.java`

класс `Eternity`, пакет `com.github.fireprime5.spellcraft.armor` — 197 строк

Состояние личного модуля «Вечность»: кто сейчас под защитой. Модуль не тикает и не исполняется как заклинание — он висит на нагруднике и отменяет урон из события. Поэтому здесь лежит только «кто включён», а всю работу делает ArmorEvents.

### `com/github/fireprime5/spellcraft/armor/ModArmorComponents.java`

класс `ModArmorComponents`, пакет `com.github.fireprime5.spellcraft.armor` — 43 строки

Компонент данных со схемой модулей брони. Отдельный компонент, а не NBT-поле: NBT не синхронизируется с клиентом без ручной отправки, и подсказка в чате показала бы пустую броню. С компонентом синхронизация бесплатна, а стек с модулями остаётся копируемым.

### `com/github/fireprime5/spellcraft/armor/ModularChestplateItem.java`

класс `ModularChestplateItem`, пакет `com.github.fireprime5.spellcraft.armor` — 86 строк

Модульный нагрудник: броня со схемой модулей внутри. Схема живёт в компоненте данных предмета, а не в сущности брони: нагрудник можно положить в сундук, передать другому игроку и снять — схема едет вместе с вещью. Хранение в сущности сломало бы всё из этого.

### `com/github/fireprime5/spellcraft/armor/ModularSchema.java`

класс `ModularSchema`, пакет `com.github.fireprime5.spellcraft.armor` — 225 строк

Схема модулей брони: тот же граф, что и у заклинания, но со своим набором.

### `com/github/fireprime5/spellcraft/armor/ShieldEvents.java`

класс `ShieldEvents`, пакет `com.github.fireprime5.spellcraft.armor` — 215 строк

Что щит и барьер делают, пока подняты. Три вещи: погасить урон из-под защиты, отдать снаряд обратно и нарисовать контур. Контур — частицами по кромке, а не заливкой: заливка в дугу съела бы треть экрана, а игроку и нужно знать одно — где граница.

### `com/github/fireprime5/spellcraft/armor/Shield.java`

класс `Shield`, пакет `com.github.fireprime5.spellcraft.armor` — 191 строка

Щит и барьер: защита, которую игрок поднимает вокруг себя. Оба работают одинаково — просто разной формы. Щит вырастает дугой там, куда игрок смотрит, и держит удар только спереди, как настоящий щит: если атакующий стоит за спиной, щит не поможет. Барьер вырастает сферой вокруг игрока и держит удар отовсюду.

### `com/github/fireprime5/spellcraft/client/ClientArmorState.java`

класс `ClientArmorState`, пакет `com.github.fireprime5.spellcraft.client` — 37 строк

Схема брони, ждущая правки на клиенте. Отдельное состояние, а не общее с заклинанием: иначе конструктор брони открылся бы поверх заклинания игрока и записал модули не туда.

### `com/github/fireprime5/spellcraft/client/ClientBridgeInstaller.java`

класс `ClientBridgeInstaller`, пакет `com.github.fireprime5.spellcraft.client` — 176 строк

Мост между клиентским кодом мода и NeoForge: регистрирует клавиши, экраны, блочные рендереры и сетевые обработчики. Единственное место, где клиентская часть цепляется к шине событий.

### `com/github/fireprime5/spellcraft/client/ClientBridge.java`

класс `ClientBridge`, пакет `com.github.fireprime5.spellcraft.client` — 97 строк

Мост между общими сетевыми обработчиками и клиентским кодом. Общий класс не содержит ссылок на классы клиента, поэтому безопасен для выделенного сервера. Реальная реализация ставится только на клиенте (см. ClientBridgeInstaller).

### `com/github/fireprime5/spellcraft/client/ClientMachineState.java`

класс `ClientMachineState`, пакет `com.github.fireprime5.spellcraft.client` — 49 строк

Сетка машины, ждущая правки на клиенте. Отдельное состояние от заклинания и от брони: три схемы живут в трёх разных местах, и общая переменная для них означала бы, что правка одной затирает другую.

### `com/github/fireprime5/spellcraft/client/ClientRuntimeEvents.java`

класс `ClientRuntimeEvents`, пакет `com.github.fireprime5.spellcraft.client` — 28 строк

Клиентские события вне установщика: тик клиента и прочее, что не требует регистрации через шину.

### `com/github/fireprime5/spellcraft/client/ClientSpellState.java`

класс `ClientSpellState`, пакет `com.github.fireprime5.spellcraft.client` — 114 строк

Клиентское зеркало состояния заклинаний: текущий граф, результат каста, энергия бездны. Класс не содержит клиентских зависимостей, поэтому его безопасно читать из общих классов (например, из тикера для синхронизации энергии бездны).

### `com/github/fireprime5/spellcraft/client/DaAnimatedModel.java`

класс `DaAnimatedModel`, пакет `com.github.fireprime5.spellcraft.client` — 28 строк

GeckoLib resource mapping for the user-provided DA model.

### `com/github/fireprime5/spellcraft/client/DaAnimatedRenderer.java`

класс `DaAnimatedRenderer`, пакет `com.github.fireprime5.spellcraft.client` — 12 строк

Client renderer for the animated DA test block.

### `com/github/fireprime5/spellcraft/client/screen/LibraryPanel.java`

класс `LibraryPanel`, пакет `com.github.fireprime5.spellcraft.client.screen` — 193 строки

Панель библиотеки заклинаний (клавиша B): сохранение текущего заклинания под именем, загрузка и удаление сохранённых.

### `com/github/fireprime5/spellcraft/client/screen/ModifierHint.java`

класс `ModifierHint`, пакет `com.github.fireprime5.spellcraft.client.screen` — 73 строки

Подсказка для входного порта открытого модуля. Отдельный класс, а не метод экрана, потому что здесь ловится падение из чужого багрепорта: удаление модуля добычи роняло экран с IndexOutOfBoundsException. Меню модификаторов живо, пока его не закрыли, и каждый кадр спрашивает подсказку у узла по индексу . Индекс протухает на каждом шагу:.

### `com/github/fireprime5/spellcraft/client/screen/ModifierMenu.java`

класс `ModifierMenu`, пакет `com.github.fireprime5.spellcraft.client.screen` — 352 строки

Меню модификаторов модуля (открывается по ПКМ по модулю на холсте). Галочки у модификаторов: можно включить несколько сразу, но конфликтные (шёлк/удача и по области/мгновенная добыча; не ломать блоки/широкий радиус и поджиг/отбрасывание) одновременно нельзя. Неизвестный модификатор покупается за энергию бездны со шансом.

### `com/github/fireprime5/spellcraft/client/screen/ModuleFrame.java`

класс `ModuleFrame`, пакет `com.github.fireprime5.spellcraft.client.screen` — 224 строки

Отрисовка панели модуля: скошенные углы, вырез под шапку, штриховка. Рисунок один и тот же для игры и для PNG-превью: #draw работает через Surface, реализации которого живут в клиенте (GuiSurface) и в тестах (AwtSurface). Две копии рисунка разъезжаются при первой же правке, и превью перестаёт быть правдой.

### `com/github/fireprime5/spellcraft/client/screen/ModulePalette.java`

класс `ModulePalette`, пакет `com.github.fireprime5.spellcraft.client.screen` — 752 строки

Палитра модулей (всплывающая панель в стиле Psi): вкладки категорий, рабочее поле поиска, скролл-список, описание выбранного модуля внизу панели.

### `com/github/fireprime5/spellcraft/client/screen/ModuleStyle.java`

класс `ModuleStyle`, пакет `com.github.fireprime5.spellcraft.client.screen` — 88 строк

Внешний вид модулей на холсте и в палитре. Вынесено отдельно, чтобы холст в игре и превью в PNG рисовали одно и то же: раньше вид модуля был размазан по ProgramCanvas, и «приукрасить» его можно было только запустив игру.

### `com/github/fireprime5/spellcraft/client/screen/PaletteCatalog.java`

класс `PaletteCatalog`, пакет `com.github.fireprime5.spellcraft.client.screen` — 146 строк

Переключение палитры модулей в игре. Читает assets/spellcraft/palette.json из classpath: файл лежит в resources, поэтому варианты уезжают в jar вместе с модом, и источник правды остаётся один — тот же файл читает генератор текстур и сверяет PaletteTest.

### `com/github/fireprime5/spellcraft/client/screen/PortShapes.java`

класс `PortShapes`, пакет `com.github.fireprime5.spellcraft.client.screen` — 97 строк

Рисование маркеров портов разной формы: круг (сущность), ромб (точка), квадрат (вектор), треугольник (число), крест (предмет), шестиугольник (место — точка вместе с измерением).

### `com/github/fireprime5/spellcraft/client/screen/ProgramCanvas.java`

класс `ProgramCanvas`, пакет `com.github.fireprime5.spellcraft.client.screen` — 672 строки

Бесконечный холст программы в стиле Upload Labs / Psi: сетка без границ, панорамирование мышью, зум колесом, блоки с цветной шапкой, входами слева и выходами справа, соединения — ортогональные ломаные со стрелками.

### `com/github/fireprime5/spellcraft/client/screen/SidePanelWidget.java`

класс `SidePanelWidget`, пакет `com.github.fireprime5.spellcraft.client.screen` — 305 строк

Правая колонка конструктора: модуль, стек, предпросмотр, ошибки. Разбита на секции с шапкой-полосой. Шапка рисуется фоном до текста и повторяется при прокрутке — иначе при длинном графе заголовки уезжали за край и становилось непонятно, к какой секции относится строка.

### `com/github/fireprime5/spellcraft/client/screen/SmelterScreen.java`

класс `SmelterScreen`, пакет `com.github.fireprime5.spellcraft.client.screen` — 122 строки

Экран плавильни. Нарисован сам, а не взят из ванильного сундука: сундуок не подходит по смыслу (у него девять слотов и другое расположение), а свой стиль совпадает с остальным интерфейсом мода.

### `com/github/fireprime5/spellcraft/client/screen/SpellBuilderScreen.java`

класс `SpellBuilderScreen`, пакет `com.github.fireprime5.spellcraft.client.screen` — 1888 строк

Построитель заклинаний в стиле Upload Labs / Psi. Виртуальный холст (vw x vh = размер окна / целый масштаб), поэтому интерфейс всегда помещается на экран.

### `com/github/fireprime5/spellcraft/client/screen/StackPreview.java`

класс `StackPreview`, пакет `com.github.fireprime5.spellcraft.client.screen` — 108 строк

Предпросмотр стека на клиенте — считается без каста, только по «чистым» модулям (константы и арифметика). Позволяет видеть, что получится, ещё до применения заклинания. Модули, зависящие от мира, дают null.

### `com/github/fireprime5/spellcraft/client/screen/UiKit.java`

класс `UiKit`, пакет `com.github.fireprime5.spellcraft.client.screen` — 196 строк

Набор примитивов отрисовки в стиле Upload Labs / Psi. Прозрачность панелей настраивается конфигом (uiOpacity): 0 — видно игру насквозь, 1 — полностью непрозрачные панели. Цвета хранятся в mutable полях и пересчитываются через applyOpacity(), поэтому все виджеты используют их без единой правки.

### `com/github/fireprime5/spellcraft/client/WireRenderer.java`

класс `WireRenderer`, пакет `com.github.fireprime5.spellcraft.client` — 300 строк

Рисует линии блока сети. Обычная blockstate-модель тут не годится: в блоке до трёх линий, у каждой шесть подключений и свой цвет, и такой набор вариантов не помещается ни в какой JSON. Поэтому меш собирается здесь, из коробок NetworkGeometry, по тем же координатам, что и форма блока: что видно, то и выделяется.

### `com/github/fireprime5/spellcraft/command/ModCommands.java`

класс `ModCommands`, пакет `com.github.fireprime5.spellcraft.command` — 213 строк

Команды для отладки заклинаний: /spellcraft cast|info|clear|abyss|infmana|modules|give.

### `com/github/fireprime5/spellcraft/command/ModuleLookup.java`

класс `ModuleLookup`, пакет `com.github.fireprime5.spellcraft.command` — 189 строк

Поиск модуля по тому, что игрок вводит в команду. Порядок от точного к размытому: сначала полный идентификатор, потом путь без пространства имён, потом точное название, и только потом неоднозначный поиск по началу строки. Так «spellcraft:unique_resonance» и «Резонанс» находят одно и то же, а опечатка не выдаёт молча другой модуль.

### `com/github/fireprime5/spellcraft/config/SpellConfig.java`

класс `SpellConfig`, пакет `com.github.fireprime5.spellcraft.config` — 173 строки

Конфиг мода (config/spellcraft.json). Один файл на всё: и параметры интерфейса (клиент), и баланс энергии бездны с заклинаниями (сервер). Загружается на обеих сторонах — иначе серверные значения не дойдут до вычислителя заклинаний.

### `com/github/fireprime5/spellcraft/data/PlayerKnowledge.java`

класс `PlayerKnowledge`, пакет `com.github.fireprime5.spellcraft.data` — 415 строк

Знания игрока: какие модули изучены и что уже просканировано. Очков прогресса больше нет: сканирование сразу даёт конкретный модуль, а усиленные версии добываются модулями-модификациями за энергию бездны.

### `com/github/fireprime5/spellcraft/data/PlayerSpellStore.java`

класс `PlayerSpellStore`, пакет `com.github.fireprime5.spellcraft.data` — 48 строк

Хранилище графа заклинания игрока. Используется NeoForge Attachment с сериализацией в NBT: граф переживает перезаход в мир, смену измерения и смерть (раньше он хранился в persistentData сущности и терялся при пересоздании игрока).

### `com/github/fireprime5/spellcraft/data/SavedSpell.java`

запись `SavedSpell`, пакет `com.github.fireprime5.spellcraft.data` — 22 строки

Сохранённое заклинание: имя + граф. Вынесено отдельно от реестра вложений, чтобы кодек можно было использовать и проверять в тестах.

### `com/github/fireprime5/spellcraft/data/SpellLibrary.java`

класс `SpellLibrary`, пакет `com.github.fireprime5.spellcraft.data` — 121 строка

Библиотека заклинаний игрока: именованные сохранённые заклинания (8 слотов). Хранится в NeoForge Attachment, поэтому переживает релог и смену мира.

### `com/github/fireprime5/spellcraft/energy/AbyssDecay.java`

класс `AbyssDecay`, пакет `com.github.fireprime5.spellcraft.energy` — 123 строки

Что происходит с игроком, у которого кончилась энергия бездны. Энергия бездны — не просто ресурс заклинаний, а то, чем игрок держится за мир. Нулевая энергия бездны означает, что он больше не принадлежит этому измерению, и тело начинает рассыпаться: накладываются слабость и голод, а через пять минут непрерывной пустоты игрок умирает.

### `com/github/fireprime5/spellcraft/energy/AbyssEnergy.java`

класс `AbyssEnergy`, пакет `com.github.fireprime5.spellcraft.energy` — 342 строки

Кошелёк энергии бездны игрока. Важно: энергия бездны НЕ накапливается сама — её добывают модулем «Извлечение». Потолок кошелька задаёт конфиг, а сосуды рядом с игроком добавляют к нему ёмкость и запас (см. AbyssPool).

### `com/github/fireprime5/spellcraft/energy/AbyssExtractorBlock.java`

класс `AbyssExtractorBlock`, пакет `com.github.fireprime5.spellcraft.energy` — 174 строки

Извлекатель энергии бездны: единственный блок, который даёт энергию бездны без заклинания.

### `com/github/fireprime5/spellcraft/energy/AbyssPool.java`

класс `AbyssPool`, пакет `com.github.fireprime5.spellcraft.energy` — 179 строк

Пул энергии бездны игрока: кошелёк плюс сосуды рядом. Сосуды в радиусе #RADIUS суммируются и работают как один кошелёк: игрок тратит энергию бездны из сосудов, когда своего не хватает, и излишки добычи падают в сосуд, а не пропадают.

### `com/github/fireprime5/spellcraft/energy/AbyssVesselBlock.java`

класс `AbyssVesselBlock`, пакет `com.github.fireprime5.spellcraft.energy` — 151 строка

Сосуд для энергии бездны: блок, который копит энергию бездны. Сосуд нужен из-за извлекателя: тот даёт энергию бездны постоянно, а кошелёк игрока ограничен, и без сосуда излишки просто пропадали бы. Сосуды рядом с игроком суммируются и работают как один большой кошелёк (см. AbyssPool).

### `com/github/fireprime5/spellcraft/energy/DebugEnergySourceBlock.java`

класс `DebugEnergySourceBlock`, пакет `com.github.fireprime5.spellcraft.energy` — 175 строк

Отладочный источник энергии: безостановочно отдаёт энергию в небольшом радиусе.

### `com/github/fireprime5/spellcraft/energy/MachineEnergy.java`

класс `MachineEnergy`, пакет `com.github.fireprime5.spellcraft.energy` — 172 строки

Накопитель энергии для машин. Собственная реализация, а не EnergyStorage напрямую, по двумым причинам:.

### `com/github/fireprime5/spellcraft/energy/SmelterBlock.java`

класс `SmelterBlock`, пакет `com.github.fireprime5.spellcraft.energy` — 468 строк

Плавильня: переплавляет предметы, тратя энергию. Смысл машины простой — показать, что линия проводов обслуживает не одну машину. До плавильни в моде был только извлекатель, и провод нельзя было проверить на чём-то, кроме «светится ли полоска энергии».

### `com/github/fireprime5/spellcraft/energy/SmelterMenu.java`

класс `SmelterMenu`, пакет `com.github.fireprime5.spellcraft.energy` — 206 строк

Меню плавильни: вход, выход и инвентарь игрока. Слоты блока берутся из блочной сущности напрямую, а не через capability: так перетаскивание мышью работает без лишнего слоя, а сервер всё равно проверяет всё сам.

### `com/github/fireprime5/spellcraft/fluid/AbyssPort.java`

интерфейс `AbyssPort`, пакет `com.github.fireprime5.spellcraft.fluid` — 62 строки

Порт энергии бездны: блок, который что-то отдаёт или принимает бездну.

### `com/github/fireprime5/spellcraft/fluid/AbyssTankBlock.java`

класс `AbyssTankBlock`, пакет `com.github.fireprime5.spellcraft.fluid` — 130 строк

Бак: хранит энергию бездны. Это единственное место, где бездна копится в приличном объёме. Труба переносит, машина расходует, бак служит складом — без него бездну некуда сливать, а извлекатель без бака работал бы впустую.

### `com/github/fireprime5/spellcraft/fluid/ConveyorBlock.java`

класс `ConveyorBlock`, пакет `com.github.fireprime5.spellcraft.fluid` — 313 строк

Транспортёр: переносит предметы в одну сторону. Одно место предмета и один перенос за тик — сознательно минимально. Скорость важнее для линии на двадцать блоков, чем для перевозки тысяч предметов: игрок всё равно упирается в то, успевает ли опустошить конец.

### `com/github/fireprime5/spellcraft/item/SpellbookItem.java`

класс `SpellbookItem`, пакет `com.github.fireprime5.spellcraft.item` — 66 строк

Основной инструмент каста заклинаний — гримуар. Shift + ПКМ: открыть построитель заклинаний. ПКМ: применить сохранённое заклинание.

### `com/github/fireprime5/spellcraft/machine/MachineGrid.java`

класс `MachineGrid`, пакет `com.github.fireprime5.spellcraft.machine` — 234 строки

Модульная сетка машины: какие модули в неё разрешено ставить. У каждой машины своя сетка — ограниченный набор модулей. Плавильне нужны вещи, которыми управляют огнём, извлекателю — теми, чем наполняют. Общая сетка означала бы, что в плавильне можно поставить телепорт, и это было бы нелепо.

### `com/github/fireprime5/spellcraft/network/AbyssEnergySyncPayload.java`

запись `AbyssEnergySyncPayload`, пакет `com.github.fireprime5.spellcraft.network` — 79 строк

Сервер -> Клиент: энергия бездны игрока и прогресс анализа (для панели и палитры).

### `com/github/fireprime5/spellcraft/network/DeleteSpellPayload.java`

запись `DeleteSpellPayload`, пакет `com.github.fireprime5.spellcraft.network` — 37 строк

Клиент -> Сервер: удалить заклинание из библиотеки.

### `com/github/fireprime5/spellcraft/network/LibrarySyncPayload.java`

запись `LibrarySyncPayload`, пакет `com.github.fireprime5.spellcraft.network` — 65 строк

Сервер -> Клиент: список заклинаний в библиотеке (только имена и числа модулей).

### `com/github/fireprime5/spellcraft/network/LibrarySyncRequest.java`

запись `LibrarySyncRequest`, пакет `com.github.fireprime5.spellcraft.network` — 35 строк

Клиент -> Сервер: просто запросить список заклинаний библиотеки.

### `com/github/fireprime5/spellcraft/network/LineKind.java`

перечисление `LineKind`, пакет `com.github.fireprime5.spellcraft.network` — 43 строки

Вид линии: по виду она ищет соседей и берёт ресурс. Энергия и жидкость — разные валюты, и путать их в одном блоке нельзя: иначе труба начнёт тянуть Forge Energy, а энергетическая труба — жидкость, и игрок получит сеть, которая работает через раз.

### `com/github/fireprime5/spellcraft/network/LoadSpellPayload.java`

запись `LoadSpellPayload`, пакет `com.github.fireprime5.spellcraft.network` — 39 строк

Клиент -> Сервер: загрузить заклинание из библиотеки в текущий редактор.

### `com/github/fireprime5/spellcraft/network/MachineAccess.java`

класс `MachineAccess`, пакет `com.github.fireprime5.spellcraft.network` — 63 строки

Доступ к сетке машины с проверкой прав. Единственное место, где сервер решает, можно ли игроку трогать эту машину. И запрос, и сохранение идут через него — иначе правила разъедутся: запрос проверяет расстояние, а сохранение проходит мимо.

### `com/github/fireprime5/spellcraft/network/ModifierLearnPayload.java`

запись `ModifierLearnPayload`, пакет `com.github.fireprime5.spellcraft.network` — 62 строки

Клиент -> Сервер: попытка изучить модификатор за энергию бездны (со шансом).

### `com/github/fireprime5/spellcraft/network/ModNetwork.java`

класс `ModNetwork`, пакет `com.github.fireprime5.spellcraft.network` — 185 строк

Регистрация всех payload'ов. Регистрация нужна на обеих сторонах: отправитель кодирует пакет по зарегистрированному кодеку. S2C-обработчики просто прокидывают пакеты в ClientBridge — клиентский код в этом классе не упоминается, поэтому сервер не ломается.

### `com/github/fireprime5/spellcraft/network/NetworkBlockEntity.java`

класс `NetworkBlockEntity`, пакет `com.github.fireprime5.spellcraft.network` — 484 строки

Блок-сущность сети: до трёх линий, у каждой свой слот и свои подключения.

### `com/github/fireprime5/spellcraft/network/NetworkEnergy.java`

класс `NetworkEnergy`, пакет `com.github.fireprime5.spellcraft.network` — 243 строки

Перенос энергии по линиям блока сети. Главное отличие от прежней версии: линий в блоке до трёх, и у соседа нужно спрашивать все линии того же вида и цвета. Раньше в блоке жил ровно один провод, поэтому спрашивался один; с несколькими линиями вторая и третья были бы не видны переносу, и сеть «теряла» бы цвет на стыке.

### `com/github/fireprime5/spellcraft/network/NetworkFluids.java`

класс `NetworkFluids`, пакет `com.github.fireprime5.spellcraft.network` — 303 строки

Перенос жидкости по трубам блока сети. Труба одна на все жидкости, и какой она — узнаёт от соседа: предмет fluid_pipe ничего не говорит о содержимом. Поэтому линия помнит жидкость, а не цвет, и трубы разной жидкости не смешиваются: смена жидкости на непустой трубе запрещена.

### `com/github/fireprime5/spellcraft/network/NetworkGeometry.java`

класс `NetworkGeometry`, пакет `com.github.fireprime5.spellcraft.network` — 292 строки

Геометрия линий в блоке сети: слоты, точки входа в грани и отрезки. Слотов три, и они смещены по диагонали блока: центр линии стоит в (5, 5, 5), (8, 8, 8) или (11, 11, 11) пикселях, а в грани блока линия входит в ту же точку, но по двум осям. Отсюда и нужный вид: три линии разных цветов входят в одну грань машины в трёх разных точках и не пересекаются дальше — коридор из нескольких линий в одном блоке не сваливается в клубок.

### `com/github/fireprime5/spellcraft/network/NetworkItem.java`

класс `NetworkItem`, пакет `com.github.fireprime5.spellcraft.network` — 175 строк

Предмет-линия: кладёт в блок сети провод своего цвета или трубу бездны.

### `com/github/fireprime5/spellcraft/network/NetworkItems.java`

класс `NetworkItems`, пакет `com.github.fireprime5.spellcraft.network` — 63 строки

Предметы линий: по линии находится предмет, которым её кладут и снимают.

### `com/github/fireprime5/spellcraft/network/NetworkLimits.java`

класс `NetworkLimits`, пакет `com.github.fireprime5.spellcraft.network` — 10 строк

Bounds for attacker-controlled packet fields (independent of graph node policy).

### `com/github/fireprime5/spellcraft/network/NetworkLine.java`

класс `NetworkLine`, пакет `com.github.fireprime5.spellcraft.network` — 305 строк

Одна линия внутри блока сети: провод одного цвета или труба бездны. Линий в блоке до трёх, у каждой свой слот и свой набор подключений. Именно это и даёт то, ради чего всё затевалось: в одну грань машины входят три линии — энергетические трубы разных цветов и жидкостная, — и они стоят в разных точках грани и не сливаются.

### `com/github/fireprime5/spellcraft/network/NetworkLines.java`

класс `NetworkLines`, пакет `com.github.fireprime5.spellcraft.network` — 273 строки

Линии блока сети: до трёх, по одной на слот. Отдельный класс, а не поле в блок-сущности, по двум причинам. Первая: правило «сколько линий может жить в блоке» — это и есть смысл блока, и оно должно проверяться без мира. Вторая: блок-сущность создаётся только миром, а список линий создаётся чем угодно, и его можно крутить в тестах как обычный объект.

### `com/github/fireprime5/spellcraft/network/NetworkNode.java`

интерфейс `NetworkNode`, пакет `com.github.fireprime5.spellcraft.network` — 23 строки

Отметка блока, который входит в сеть проводов. Провод соединяется не только с проводом. Машина, стоящая вплотную к линии, должна быть её частью: иначе провод к ней не присоединяется, а «Отправить» с лучом на машину падает с «здесь нет провода».

### `com/github/fireprime5/spellcraft/network/OpenArmorBuilderPayload.java`

запись `OpenArmorBuilderPayload`, пакет `com.github.fireprime5.spellcraft.network` — 29 строк

Сервер -> Клиент: открыть конструктор модулей брони. Рука едет вместе с пакетом, чтобы экран знал, куда потом сохранять схему.

### `com/github/fireprime5/spellcraft/network/OpenBuilderPayload.java`

запись `OpenBuilderPayload`, пакет `com.github.fireprime5.spellcraft.network` — 27 строк

Сервер -> Клиент: открыть построитель заклинаний. Сам экран открывается в клиентском обработчике (см. ClientBridge).

### `com/github/fireprime5/spellcraft/network/OpenMachineGridPayload.java`

запись `OpenMachineGridPayload`, пакет `com.github.fireprime5.spellcraft.network` — 36 строк

Сервер -> Клиент: открыть модульную сетку машины. Отдельный пакет от OpenBuilderPayload, а не флаг внутри него: экран машины и экран заклинания отличаются набором модулей и тем, что изменения уходят обратно в блок, а не в книгу. Смешивать их в одном пакете означало бы таскать через сеть поля, которые нужны только одному из двух.

### `com/github/fireprime5/spellcraft/network/PacketRateLimiter.java`

класс `PacketRateLimiter`, пакет `com.github.fireprime5.spellcraft.network` — 54 строки

Окно частоты пакетов на игрока: сколько раз в секунду игрок может прислать пакет, который иначе пришлось бы обрабатывать без ограничений.

### `com/github/fireprime5/spellcraft/network/RequestAbyssEnergyPayload.java`

запись `RequestAbyssEnergyPayload`, пакет `com.github.fireprime5.spellcraft.network` — 36 строк

Клиент -> Сервер: просто обновить количество энергии бездны (для полоски в GUI). Граф при этом не передаётся, поэтому правки игрока не могут откатиться.

### `com/github/fireprime5/spellcraft/network/RequestArmorGraphPayload.java`

запись `RequestArmorGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 54 строки

Клиент -> Сервер: «правый клик по модульному нагруднику». Сервер отвечает схемой и командой открыть конструктор. Клиент не присылает свою схему: она лежит в компоненте предмета, и единственный честный источник правды — сервер, который видит настоящий стек.

### `com/github/fireprime5/spellcraft/network/RequestMachineGridPayload.java`

запись `RequestMachineGridPayload`, пакет `com.github.fireprime5.spellcraft.network` — 53 строки

Клиент -> Сервер: «хочу настроить сетку машины». Клиент присылает позицию и тип, но не схему: он не знает ни что за блок там стоит, ни имеет ли игрок на него право. Проверяет всё сервер.

### `com/github/fireprime5/spellcraft/network/RequestSpellGraphPayload.java`

запись `RequestSpellGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 56 строк

Клиент -> Сервер: «открыл построитель» — сервер один раз присылает сохранённый граф и энергию бездны, а потом клиент просто шлёт изменения.

### `com/github/fireprime5/spellcraft/network/SaveArmorGraphPayload.java`

запись `SaveArmorGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 76 строк

Клиент -> Сервер: сохранить схему модулей нагрудника. Схема кладётся прямо в стек на сервере, а не в отдельное хранилище игрока. Иначе нагрудник, снятый и переданный другому игроку, принёс бы чужие модули с собой — а это ровно то, чего броня делать не должна.

### `com/github/fireprime5/spellcraft/network/SaveMachineGridPayload.java`

запись `SaveMachineGridPayload`, пакет `com.github.fireprime5.spellcraft.network` — 60 строк

Клиент -> Сервер: сохранить схему модулей машины. Схема пришла с клиента, поэтому сервер проверяет её целиком: и расстояние до машины, и то, что там вообще машина, и что все модули разрешены её набором. Иначе игрок отправил бы в блок что угодно, включая модули, которых в плавильне быть не должно.

### `com/github/fireprime5/spellcraft/network/SaveSpellPayload.java`

запись `SaveSpellPayload`, пакет `com.github.fireprime5.spellcraft.network` — 45 строк

Клиент -> Сервер: сохранить заклинание в библиотеку под именем.

### `com/github/fireprime5/spellcraft/network/SpellResultPayload.java`

запись `SpellResultPayload`, пакет `com.github.fireprime5.spellcraft.network` — 89 строк

Сервер -> Клиент: результат последнего каста — трасса, стек, ошибки.

### `com/github/fireprime5/spellcraft/network/SyncArmorGraphPayload.java`

запись `SyncArmorGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 45 строк

Сервер -> Клиент: схема модулей нагрудника для показа в конструкторе. Идёт перед OpenArmorBuilderPayload: клиент сначала получает схему, потом команду открыть экран, иначе конструктор открылся бы пустым.

### `com/github/fireprime5/spellcraft/network/SyncMachineGridPayload.java`

запись `SyncMachineGridPayload`, пакет `com.github.fireprime5.spellcraft.network` — 42 строки

Сервер -> Клиент: схема модулей машины для показа в конструкторе. Идёт перед OpenMachineGridPayload: клиент сначала получает схему, потом команду открыть экран, иначе конструктор открылся бы пустым.

### `com/github/fireprime5/spellcraft/network/SyncSpellGraphPayload.java`

запись `SyncSpellGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 38 строк

Сервер -> Клиент: полная копия графа заклинания игрока (при открытии GUI).

### `com/github/fireprime5/spellcraft/network/UpdateSpellGraphPayload.java`

запись `UpdateSpellGraphPayload`, пакет `com.github.fireprime5.spellcraft.network` — 87 строк

Клиент -> Сервер: сохранить/обновить граф заклинания.

### `com/github/fireprime5/spellcraft/network/WireBlock.java`

класс `WireBlock`, пакет `com.github.fireprime5.spellcraft.network` — 222 строки

Блок сети: до трёх линий в одном блоке.

### `com/github/fireprime5/spellcraft/palette/Palette.java`

класс `Palette`, пакет `com.github.fireprime5.spellcraft.palette` — 228 строк

Палитра мода — единственный набор цветов для текстур, блоков и интерфейса.

### `com/github/fireprime5/spellcraft/portal/PortalBlock.java`

класс `PortalBlock`, пакет `com.github.fireprime5.spellcraft.portal` — 213 строк

Блок портала: один блок, который переносит вошедшего в другое место. Одиночный блок, а не рамка как у портала Незера. Причины:.

### `com/github/fireprime5/spellcraft/portal/PortalEvents.java`

класс `PortalEvents`, пакет `com.github.fireprime5.spellcraft.portal` — 99 строк

Работа порталов: переход по входу и уборка истёкших. Сам переход живёт в блоке (PortalBlock#entityInside), а сюда вынесена только уборка истёкших порталов — раз в секунду по ServerTickEvent.Post.

### `com/github/fireprime5/spellcraft/portal/PortalRegistry.java`

класс `PortalRegistry`, пакет `com.github.fireprime5.spellcraft.portal` — 362 строки

Реестр всех порталов на сервере. Хранится как SavedData обычного мира: портал может связывать измерения, а данные измерений раздельные, поэтому держать реестр в Незере или в Энде было бы негде. Обычный мир — единственное хранилище, видимое всем.

### `com/github/fireprime5/spellcraft/registry/ModBlocks.java`

класс `ModBlocks`, пакет `com.github.fireprime5.spellcraft.registry` — 233 строки

Блоки мода. Блок один — портал. В творческой вкладке его нет намеренно: портал ставится кастом, а не кладётся в сумку, поэтому и выдавать его вручную незачем.

### `com/github/fireprime5/spellcraft/registry/ModCreativeTabs.java`

класс `ModCreativeTabs`, пакет `com.github.fireprime5.spellcraft.registry` — 66 строк

Содержимое креативной вкладки мода: блоки, предметы и то, что показывается только при включённой отладке.

### `com/github/fireprime5/spellcraft/registry/ModItems.java`

класс `ModItems`, пакет `com.github.fireprime5.spellcraft.registry` — 179 строк

Реестр предметов мода: гримуар, броня, машины и предметы линий. Все id пишутся литералами — реестр проверяется тестом.

### `com/github/fireprime5/spellcraft/registry/ModMenus.java`

класс `ModMenus`, пакет `com.github.fireprime5.spellcraft.registry` — 37 строк

Меню интерфейсов мода. Отдельный реестр нужен потому, что конструктор меню на клиенте не знает позиции блока: сервер отправляет только идентификатор, а клиент собирает меню заново уже без мира. Позиция приходит отдельно, когда игрок открывает панель.

### `com/github/fireprime5/spellcraft/spell/AnalysisModules.java`

класс `AnalysisModules`, пакет `com.github.fireprime5.spellcraft.spell` — 329 строк

Модули «Анализ» и «Извлечение» — основа прогрессии и энергии. Анализ: сканирует блок или существо, даёт очки прогресса и открывает новые модули. Извлечение: забирает энергию бездны из блока или существа (у каждого своя цена). Оба модуля бесплатны по энергии бездны — иначе нельзя было бы начать.

### `com/github/fireprime5/spellcraft/spell/ArmorModules.java`

класс `ArmorModules`, пакет `com.github.fireprime5.spellcraft.spell` — 91 строка

Модули модульной брони. Это не заклинания: у них нет «запуска», они висят на груди и срабатывают от событий. Поэтому портов у них нет вовсе, а цена — постоянный налог на бездну, а не списание за каст (см. ArmorEvents).

### `com/github/fireprime5/spellcraft/spell/BuiltinModules.java`

класс `BuiltinModules`, пакет `com.github.fireprime5.spellcraft.spell` — 469 строк

Встроенные модули заклинаний. Конструктор: (id, категория, цвет).

### `com/github/fireprime5/spellcraft/spell/Comparisons.java`

класс `Comparisons`, пакет `com.github.fireprime5.spellcraft.spell` — 53 строки

Сами сравнения, отдельно от их регистрации. Раньше условие было лямбдой прямо в теле модуля, и проверять его было нечем: чтобы вызвать execute, нужен живой ServerLevel. А перевёрнутый оператор — самая тихая из поломок: модуль зарегистрирован, порты верные, тесты зелёные, а «больше» считает «меньше», и заметить это можно только в игре на конкретном заклинании.

### `com/github/fireprime5/spellcraft/Spellcraft.java`

класс `Spellcraft`, пакет `com.github.fireprime5.spellcraft` — 14 строк

Общие константы мода и логгер: идентификатор, версия, имя логгера. Место, куда сходятся все общие для пакета вещи.

### `com/github/fireprime5/spellcraft/SpellcraftMod.java`

класс `SpellcraftMod`, пакет `com.github.fireprime5.spellcraft` — 207 строк

Точка входа мода: регистрирует блоки, предметы, меню, события общей шины и capability энергии и жидкости.

### `com/github/fireprime5/spellcraft/spell/DelayedValues.java`

класс `DelayedValues`, пакет `com.github.fireprime5.spellcraft.spell` — 105 строк

Отложенные значения: очередь, из которой что-то становится доступным не сразу.

### `com/github/fireprime5/spellcraft/spell/ExtraModules.java`

класс `ExtraModules`, пакет `com.github.fireprime5.spellcraft.spell` — 1031 строка

Расширенный набор модулей: математика, сущности, мир, логика. Здесь больше нет модулей предметов («Предмет в руке», «Предмет в ячейке хотбара», «Блок на месте», «Выдать предмет» и прочие). Вместе они давали бесконечные предметы: стопка копировалась из мира и инвентаря и тут же попадала обратно, ничего не тратя. Постройка осталась только из блока, который игрок и так держит в руке (см. WorldModules#place_held_block).

### `com/github/fireprime5/spellcraft/spell/FrozenEvents.java`

класс `FrozenEvents`, пакет `com.github.fireprime5.spellcraft.spell` — 46 строк

Держит замороженные сущности на месте. Гасить движение один раз при наложении нельзя: через тик моб снова набирает скорость и ползёт. Поэтому заморозка это не «эффект», а требование, выполняемое каждый тик, пока держит срок.

### `com/github/fireprime5/spellcraft/spell/Frozen.java`

класс `Frozen`, пакет `com.github.fireprime5.spellcraft.spell` — 92 строки

Замороженные сущности: кто стоит на месте и почему. Заморозка — не эффект зелья, а принудительное обнуление движения каждый тик. Зельем её не сделать: слизость и прыгучесть — это модификаторы, а моби двигается всё равно. Приходится брать движение под контроль напрямую.

### `com/github/fireprime5/spellcraft/spell/GeometryModules.java`

класс `GeometryModules`, пакет `com.github.fireprime5.spellcraft.spell` — 444 строки

Модули векторов и геометрии. До этих модулей вектор в заклинании можно было только получить одним способом — посмотреть взглядом (entity_look). Дальше с ним нельзя было ничего сделать: ни посчитать длину, ни развернуть, ни сложить два вектора. А точка бралась только как готовая координата блока, и сдвинуть её на «свои» три блока тоже было нечем.

### `com/github/fireprime5/spellcraft/spell/KnowledgeHooks.java`

класс `KnowledgeHooks`, пакет `com.github.fireprime5.spellcraft.spell` — 106 строк

Общая логика открытия модулей: сканирование и модификации.

### `com/github/fireprime5/spellcraft/spell/PortalModules.java`

класс `PortalModules`, пакет `com.github.fireprime5.spellcraft.spell` — 310 строк

Модули порталов и адресов. Портал — это блок в мире плюс запись в PortalRegistry, общем для всех измерений. Модуль лишь создаёт эту пару; всё дальнейшее (переход, истечение срока, снос) делает сам блок.

### `com/github/fireprime5/spellcraft/spell/PortType.java`

перечисление `PortType`, пакет `com.github.fireprime5.spellcraft.spell` — 84 строки

Тип значения, которое течёт по порту модуля. Показывается в GUI (цвет порта, подсказка, правая панель) и проверяется при соединении модулей.

### `com/github/fireprime5/spellcraft/spell/ScanRewards.java`

класс `ScanRewards`, пакет `com.github.fireprime5.spellcraft.spell` — 342 строки

Таблица наград за сканирование: что за объект → какой модуль открывается.

### `com/github/fireprime5/spellcraft/spell/ShieldModules.java`

класс `ShieldModules`, пакет `com.github.fireprime5.spellcraft.spell` — 109 строк

«Щит» и «Барьер» — защита, которую игрок поднимает ненадолго. Разница только в форме: щит вырастает дугой там, куда игрок смотрит и держит удар спереди , барьер — сферой вокруг игрока и держит отовсюду. Правило одно и то же, поэтому и модулей два, а не четыре: «щит со всех сторон» и «дуга на 360°» — это ровно то же самое, просто визуально иное.

### `com/github/fireprime5/spellcraft/spell/SpellContext.java`

класс `SpellContext`, пакет `com.github.fireprime5.spellcraft.spell` — 595 строк

Контекст выполнения заклинания: стек значений, ошибки, трасса, энергия бездны.

### `com/github/fireprime5/spellcraft/spell/SpellExecutor.java`

класс `SpellExecutor`, пакет `com.github.fireprime5.spellcraft.spell` — 286 строк

Исполнитель графа заклинания. Возвращает результат (стек, ошибки, трасса) и отправляет его игроку в GUI.

### `com/github/fireprime5/spellcraft/spell/SpellGraph.java`

класс `SpellGraph`, пакет `com.github.fireprime5.spellcraft.spell` — 450 строк

Граф заклинания: набор узлов и соединений между ними.

### `com/github/fireprime5/spellcraft/spell/SpellIssue.java`

запись `SpellIssue`, пакет `com.github.fireprime5.spellcraft.spell` — 36 строк

Проблема в заклинании: ключ перевода + аргумент. Хранится и передаётся по сети как строка "key\0arg".

### `com/github/fireprime5/spellcraft/spell/SpellModifier.java`

запись `SpellModifier`, пакет `com.github.fireprime5.spellcraft.spell` — 35 строк

Модификатор модуля — дополнительное действие, которое можно включить галочкой в меню модуля (ПКМ по модулю).

### `com/github/fireprime5/spellcraft/spell/SpellModifiers.java`

класс `SpellModifiers`, пакет `com.github.fireprime5.spellcraft.spell` — 131 строка

Все модификаторы модулей. Группа "A": удача и шёлковое касание — взаимоисключающие. Группа "B": по области и мгновенная добыча — взаимоисключающие. Можно взять по одному из каждой группы: например «шёлк + по области».

### `com/github/fireprime5/spellcraft/spell/SpellModules.java`

класс `SpellModules`, пакет `com.github.fireprime5.spellcraft.spell` — 325 строк

Реестр всех доступных типов модулей + поиск для палитры.

### `com/github/fireprime5/spellcraft/spell/SpellModuleType.java`

класс `SpellModuleType`, пакет `com.github.fireprime5.spellcraft.spell` — 259 строк

Тип модуля заклинания — узел блок-графа (в стиле Psi / Upload Labs).

### `com/github/fireprime5/spellcraft/spell/SpellNode.java`

класс `SpellNode`, пакет `com.github.fireprime5.spellcraft.spell` — 119 строк

Узел графа: конкретный размещённый модуль с позицией в сетке.

### `com/github/fireprime5/spellcraft/spell/SpellPrerequisites.java`

класс `SpellPrerequisites`, пакет `com.github.fireprime5.spellcraft.spell` — 169 строк

Предпосылки модулей: что нужно знать, чтобы открылось следующее. Сканирование открывает модули, но модулей больше, чем целей, которые игрок встречает. Раньше это означало, что 51 модуль из 127 был недостижим никогда — включая базовую арифметику: её нельзя было получить ни сканированием, ни командой (команда только у оператора).

### `com/github/fireprime5/spellcraft/spell/SpellSubsections.java`

класс `SpellSubsections`, пакет `com.github.fireprime5.spellcraft.spell` — 322 строки

Подразделы модулей внутри категории. Категория отвечает на вопрос «о чём модуль» (число, сущность, действие, логика), а подраздел — «что он делает с тем, что пришло». Без деления список в палитре выглядел сплошной полосой из сорока с лишним названий: чтобы найти «Взять из стека», приходилось прокручивать весь «Логику».

### `com/github/fireprime5/spellcraft/spell/SpellValue.java`

запись `SpellValue`, пакет `com.github.fireprime5.spellcraft.spell` — 249 строк

Значение в стеке заклинания: число, сущность, позиция, вектор или предмет.

### `com/github/fireprime5/spellcraft/spell/StackModules.java`

класс `StackModules`, пакет `com.github.fireprime5.spellcraft.spell` — 447 строк

Модули работы со стеком заклинания (как в Hex Casting). Стек — это значения, которые копятся во время каста. Обычные модули передают значения по соединениям, а эти кладут и берут их из стека.

### `com/github/fireprime5/spellcraft/spell/StartModules.java`

класс `StartModules`, пакет `com.github.fireprime5.spellcraft.spell` — 47 строк

Стартовые модули: доступны с самого начала, без изучения. Это вся цепочка, которой игрок добывает первую энергию бездны и первые знания: Кастер → Взгляд → Луч → Анализ / Извлечение. Всё бесплатно.

### `com/github/fireprime5/spellcraft/spell/UniqueModules.java`

класс `UniqueModules`, пакет `com.github.fireprime5.spellcraft.spell` — 324 строки

Личные модули игрока. Розыгрыш идёт один раз при первом входе в мир, но игрок узнаёт свой модуль только когда проанализирует себя модулем «Анализ себя». До этого модуль назначен, но скрыт: он не появляется в палитре и не известен игроку. В новом мире розыгрыш проходит заново и выдаёт другой модуль.

### `com/github/fireprime5/spellcraft/spell/validation/SchemaPolicy.java`

запись `SchemaPolicy`, пакет `com.github.fireprime5.spellcraft.spell.validation` — 26 строк

Contextual restrictions; ordinary spells have no gameplay node limit.

### `com/github/fireprime5/spellcraft/spell/WorldModules.java`

класс `WorldModules`, пакет `com.github.fireprime5.spellcraft.spell` — 834 строки

Модули добычи и работы с блоками. У всех модулей добычи есть НЕОБЯЗАТЕЛЬНЫЙ вход «область» (число): без него модуль работает с одним блоком, а с подключённым числом — с областью вокруг точки (радиус = значение).

### `com/github/fireprime5/spellcraft/visual/DaAnimatedBlock.java`

класс `DaAnimatedBlock`, пакет `com.github.fireprime5.spellcraft.visual` — 82 строки

Тестовый блок для просмотра пользовательской модели и анимации GeckoLib.

## Тесты

Тесты не поднимают Minecraft: проверяется то, что можно проверить
без мира, — правила, кодеки, ассеты и структура исходников. Всё, что
ходит в мир, проверяется в игре.

### `com/github/fireprime5/spellcraft/armor/ArmorEventsTest.java`

класс `ArmorEventsTest`, пакет `com.github.fireprime5.spellcraft.armor` — 73 строки

Source-level regression checks, following InfiniteAbyssEnergyTest: the unit test harness cannot create live Minecraft players. In-game checks are still needed for ability packets, logout persistence, and integrated-server ticks.

### `com/github/fireprime5/spellcraft/armor/EternityTest.java`

класс `EternityTest`, пакет `com.github.fireprime5.spellcraft.armor` — 378 строк

«Вечность»: лимит в три игрока и правила, из-за которых она вообще конечна.

### `com/github/fireprime5/spellcraft/armor/ModularSchemaTest.java`

класс `ModularSchemaTest`, пакет `com.github.fireprime5.spellcraft.armor` — 226 строк

Схема модулей брони. Схема едет в предмете, и это главный её смысл: снял, передал, положил в сундук — модули остались с бронёй. Плюс она приходит с клиента, поэтому всё, что в неё пришло, проверяется на сервере ещё раз.

### `com/github/fireprime5/spellcraft/armor/ShieldTest.java`

класс `ShieldTest`, пакет `com.github.fireprime5.spellcraft.armor` — 203 строки

Щит и барьер: геометрия и состояние. Проверяется всё, что не требует ServerLevel: держит ли защита удар спереди, сзади и сбоку, сколько живёт и кто ею пользуется. Сам перехват урона в тестах не поднять — там нужен живой игрок.

### `com/github/fireprime5/spellcraft/assets/BlockModelTest.java`

класс `BlockModelTest`, пакет `com.github.fireprime5.spellcraft.assets` — 89 строк

Модели блоков разбираются и ссылаются на существующие текстуры. Единственный способ узнать, что модель не загрузится, — разобрать её самому. Ошибка стоила фиолетово-чёрной шахматки вместо провода: у элементов не было граней, Minecraft отказывался грузить модель целиком, а игра подставляла заглушку. Модель при этом была валидным json, и никакой другой тест этого не показывал.

### `com/github/fireprime5/spellcraft/assets/DaAnimatedAssetTest.java`

класс `DaAnimatedAssetTest`, пакет `com.github.fireprime5.spellcraft.assets` — 31 строка

Проверяет, что тестовая GeckoLib-модель поставлена полным комплектом ресурсов.

### `com/github/fireprime5/spellcraft/assets/ItemSpriteTest.java`

класс `ItemSpriteTest`, пакет `com.github.fireprime5.spellcraft.assets` — 128 строк

Проверки спрайтов предметов. Предметные текстуры рисуются тем же генератором, что и блочные, поэтому и требования те же: файл существует, размер -- степень двойки. Про uv здесь можно не думать: у minecraft:item/generated quad всегда занимает весь спрайт (uv = [0,0,16,16]), независимо от разрешения.

### `com/github/fireprime5/spellcraft/assets/RegisteredAssetTest.java`

класс `RegisteredAssetTest`, пакет `com.github.fireprime5.spellcraft.assets` — 355 строк

Repository-only checks for the assets promised by the source registries.

### `com/github/fireprime5/spellcraft/client/screen/ModifierHintTest.java`

класс `ModifierHintTest`, пакет `com.github.fireprime5.spellcraft.client.screen` — 138 строк

Подсказка порта открытого модуля не должна ронять экран. Регрессия с чужого багрепорта: удаление модуля добычи вызывало IndexOutOfBoundsException прямо во время отрисовки. Меню модификаторов держало в замыкании индекс узла и дёргало подсказку каждый кадр, а graph.nodes после удаления сжимался — индекс уезжал за конец списка.

### `com/github/fireprime5/spellcraft/client/screen/PaletteTabLayoutTest.java`

класс `PaletteTabLayoutTest`, пакет `com.github.fireprime5.spellcraft.client.screen` — 135 строк

Полоса вкладок палитры не должна вылезать за панель. Ширина вкладок зависит от шрифта и языка, поэтому проверить её в ModulePalette нельзя: там нужен живой Font. Здесь считается та же арифметика на подставных метриках шрифта — этого достаточно, чтобы поймать возврат бага с целочисленными константами в коде.

### `com/github/fireprime5/spellcraft/client/screen/PaletteVisibilityTest.java`

класс `PaletteVisibilityTest`, пакет `com.github.fireprime5.spellcraft.client.screen` — 179 строк

Что палитра показывает, а что прячет. Правило «неоткрытые модули скрыты» держится на одном методе без графики, поэтому его и проверяем здесь. Раньше закрытые модули показывались серыми, и палитра по сути была списком всего, что есть в игре.

### `com/github/fireprime5/spellcraft/client/screen/PortShapeAccessibilityTest.java`

класс `PortShapeAccessibilityTest`, пакет `com.github.fireprime5.spellcraft.client.screen` — 28 строк

Guards the non-color cues used by the port legend and canvas markers.

### `com/github/fireprime5/spellcraft/command/ModuleLookupTest.java`

класс `ModuleLookupTest`, пакет `com.github.fireprime5.spellcraft.command` — 169 строк

Поиск модуля для /spellcraft give. Команду целиком проверить нечем: CommandSourceStack в тестах не создаётся. Но вся её решающая часть — как строка превращается в модуль — живёт в ModuleLookup и проверяется здесь по-настоящему.

### `com/github/fireprime5/spellcraft/config/SpellConfigTest.java`

класс `SpellConfigTest`, пакет `com.github.fireprime5.spellcraft.config` — 63 строки

Конфиг: значения по умолчанию должны быть рабочими, а мусор из файла — обрезаться до допустимых границ.

### `com/github/fireprime5/spellcraft/data/ModuleGrantTest.java`

класс `ModuleGrantTest`, пакет `com.github.fireprime5.spellcraft.data` — 50 строк

Список модулей для отладочной команды: он должен совпадать с реестром.

### `com/github/fireprime5/spellcraft/data/PlayerKnowledgeTest.java`

класс `PlayerKnowledgeTest`, пакет `com.github.fireprime5.spellcraft.data` — 280 строк

Система знаний: стартовые модули, награды за сканирование, модификации.

### `com/github/fireprime5/spellcraft/data/SpellLibraryTest.java`

класс `SpellLibraryTest`, пакет `com.github.fireprime5.spellcraft.data` — 62 строки

Библиотека заклинаний: кодек, сохранение, лимит слотов.

### `com/github/fireprime5/spellcraft/energy/AbyssDecayTest.java`

класс `AbyssDecayTest`, пакет `com.github.fireprime5.spellcraft.energy` — 71 строка

Правила энергии бездны, которые можно проверить без мира. Сама AbyssEnergy работает через persistentData игрока, а игрока в тестах не создать. Поэтому здесь проверяются только числа и соотношения: стартовая энергия бездны, энергия бездны после смерти, время до смерти от пустоты. Всё остальное живёт в бою.

### `com/github/fireprime5/spellcraft/energy/AbyssPoolRecursionTest.java`

класс `AbyssPoolRecursionTest`, пакет `com.github.fireprime5.spellcraft.energy` — 113 строк

Две ошибки, найденные при первом же запуске с сосудами. Обе приводят к падению игры, а не к неправильному числу, поэтому поймать их обычными тестами нельзя: нужен игрок с сосудом и загруженные ресурсы. Здесь фиксируется форма, в которой они повториться не смогут.

### `com/github/fireprime5/spellcraft/energy/AbyssVesselTest.java`

класс `AbyssVesselTest`, пакет `com.github.fireprime5.spellcraft.energy` — 82 строки

Сосуд энергии бездны: ёмкость и правила хранения. Сама блочная сущность в тестах не создаётся — нужен мир. Здесь проверяются числа, от которых зависит игра: ёмкость сосуда относительно кошелька, радиус связи с игроком и то, что сосуд достаточно велик, чтобы извлекателю было куда складывать излишки.

### `com/github/fireprime5/spellcraft/energy/EyeDiscountTest.java`

класс `EyeDiscountTest`, пакет `com.github.fireprime5.spellcraft.energy` — 132 строки

«Глаз» — личный модуль, снижающий все расходы бездны в десять раз. Формула скидки вынесена в AbyssEnergy#applyRate без игрока, поэтому проверяется по-настоящему: проверка «есть ли у игрока „Глаз“» ходит в знания, а для неё нужен живой Player, которого в тестах нет.

### `com/github/fireprime5/spellcraft/energy/InfiniteAbyssEnergyTest.java`

класс `InfiniteAbyssEnergyTest`, пакет `com.github.fireprime5.spellcraft.energy` — 144 строки

Бесконечная энергия бездны: точки, где режим обязан учитываться. Сам Player в тестах не создаётся (реестр Minecraft не поднят), а проверять тут нужно именно то, что режим нельзя обойти стороной. Для этого берутся потребители энергии бездны в исходниках: если в одном из них появится сравнение с текущим счётом в обход AbyssEnergy#has, режим перестанет действовать там молча, и это не поймает ни один обычный тест.

### `com/github/fireprime5/spellcraft/energy/MachineEnergyTest.java`

класс `MachineEnergyTest`, пакет `com.github.fireprime5.spellcraft.energy` — 229 строк

Энергия машин: списание, приём и защита от битого NBT. MachineEnergy намеренно не трогает реестр Minecraft — это чистая арифметика поверх EnergyStorage, поэтому её можно проверить целиком. Блок-сущность в тестах не создаётся: для неё нужен мир.

### `com/github/fireprime5/spellcraft/energy/SmelterBlockTest.java`

класс `SmelterBlockTest`, пакет `com.github.fireprime5.spellcraft.energy` — 70 строк

Плавильня: согласованность чисел. Сам переплав и раздача предметов проверяются в игре: рецепты живут в реестре, а блочная сущность без мира не создаётся. Здесь зафиксированы соотношения, из-за которых машина либо работает, либо нет.

### `com/github/fireprime5/spellcraft/machine/MachineGridTest.java`

класс `MachineGridTest`, пакет `com.github.fireprime5.spellcraft.machine` — 135 строк

Модульные сетки машин. Сетка ограничивает не только набор модулей, но и то, что происходит при чистке сохранённого графа. Раньше чистка стирала всё, кроме первого узла: игрок терял схему молча, без единого сообщения.

### `com/github/fireprime5/spellcraft/network/NetworkBlockTest.java`

класс `NetworkBlockTest`, пакет `com.github.fireprime5.spellcraft.network` — 469 строк

Блок сети: три линии, подключения, снятие линии. Мир в тестах не поднимается, поэтому сам ход пересчёта подключений проверить нечем: он ходит в соседей. Проверяется всё, что можно проверить без мира, — правила на уровне исходника и чистых функций.

### `com/github/fireprime5/spellcraft/network/NetworkEnergyTest.java`

класс `NetworkEnergyTest`, пакет `com.github.fireprime5.spellcraft.network` — 155 строк

Провод как кабель энергии. Сам перенос проверяется в игре: NetworkEnergy#transferEnergy ходит в capability соседей, а реестр Minecraft в тестах не поднимается. Здесь зафиксированы числа и свойства, из-за которых перенос либо работает, либо нет.

### `com/github/fireprime5/spellcraft/network/NetworkLineTest.java`

класс `NetworkLineTest`, пакет `com.github.fireprime5.spellcraft.network` — 788 строк

Линии в блоке: слоты, подключения, сериализация. Три линии в одном блоке — ради них всё и затевалось, и дешевле всего проверяется именно геометрия: блок-сущность и рендерер в тестах не поднять, а слоты, порты и маски считаются чистыми функциями.

### `com/github/fireprime5/spellcraft/network/WireBlockTest.java`

класс `WireBlockTest`, пакет `com.github.fireprime5.spellcraft.network` — 51 строка

Провод присоединяется к машинам. Здесь нет ни каналов, ни значений в сети: провод остался просто проводом. Проверяется ровно то, что оказалось сломано при первом появлении блока: провод соединялся только с проводами, а машина не считалась соседом, и линия висела на ней оборванным концом.

### `com/github/fireprime5/spellcraft/network/WireColorItemsTest.java`

класс `WireColorItemsTest`, пакет `com.github.fireprime5.spellcraft.network` — 451 строка

Цветные провода: предмет, ассеты, рецепт. Провод без цвета не годится: сеть определяется цветом, и без шестнадцати предметов линии рядом не различить. Проверяется всё, чем игрок пользуется: id в реестре, цвет в переводе, спрайт своего оттенка, место во вкладке и рецепт крашения.

### `com/github/fireprime5/spellcraft/palette/PaletteTest.java`

класс `PaletteTest`, пакет `com.github.fireprime5.spellcraft.palette` — 277 строк

Палитра должна быть одна. Источник правды — tools/models/palette.json: генератор текстур читает его, Palette является его зеркалом. Проверка ловит то, что случилось до появления палитры: пятнадцать произвольных оттенков в PortType, свои числа в UiKit и отдельные цвета у каждого модуля, из-за чего мод выглядел разноцветным при вполне рабочем коде.

### `com/github/fireprime5/spellcraft/palette/PaletteVariantPreviewTest.java`

класс `PaletteVariantPreviewTest`, пакет `com.github.fireprime5.spellcraft.palette` — 154 строки

Лист вариантов палитры: один png со всеми шкалами категорий. Сделано как тест, а не как отдельный скрипт, потому что рисунок модуля должен быть ровно тем же кодом, что и в игре: ModuleFrame#draw принимает акцент параметром, поэтому подставлять любые цвета можно, не трогая саму палитру. Выбранный вариант переносится в tools/models/palette.json, а этот тест либо удаляется, либо остаётся как визуальный регресс.

### `com/github/fireprime5/spellcraft/portal/PortalEntryTest.java`

класс `PortalEntryTest`, пакет `com.github.fireprime5.spellcraft.portal` — 151 строка

Когда именно портал переносит сущность. Здесь воспроизводится тот сбой, из-за которого двусторонние врата «телепортировали сами в себя»: сущность переносилась туда-обратно каждые 20 тиков, потому что единственной защитой был таймер, а «я уже стою внутри» никто не запоминал.

### `com/github/fireprime5/spellcraft/portal/PortalRegistryTest.java`

класс `PortalRegistryTest`, пакет `com.github.fireprime5.spellcraft.portal` — 366 строк

Система порталов: реестр, срок жизни и правила модулей. Проверяется то, что не требует мира: реестр целиком работает на NBT и чистой геометрии, а модули проверяются как объявления. Сам переход и блок в мире живут без тестов — для них нужен сервер.

### `com/github/fireprime5/spellcraft/spell/DelayedValuesTest.java`

класс `DelayedValuesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 107 строк

Отложенные значения — очередь задержки между кастами. Графа, который умеет ждать, в моде нет и быть не должно: пауза посреди каста подвесила бы кадр у всех, кто рядом. Задержка сделана очередью, и вся её логика проверяется здесь обычным тестом — DelayedValues не касается мира.

### `com/github/fireprime5/spellcraft/spell/EntitiesInRadiusTest.java`

класс `EntitiesInRadiusTest`, пакет `com.github.fireprime5.spellcraft.spell` — 165 строк

Модули поиска существ по радиусу. Сам поиск по миру не проверяется: ServerLevel в тестах не создаётся. Но всё, что в нём решается без мира — сколько целей просят, какой радиус взять, как отсортировать и обрезать список — вынесено в обычные методы, и здесь проверяется по-настоящему.

### `com/github/fireprime5/spellcraft/spell/GeometryModulesTest.java`

класс `GeometryModulesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 586 строк

Векторы и геометрия: регистрация модулей, порты и сама арифметика. В отличие от модулей сети эти ничего не меняют в мире — они только считают, поэтому их исполнение проверяется здесь полностью, без игры.

### `com/github/fireprime5/spellcraft/spell/NewModulesTest.java`

класс `NewModulesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 264 строки

Модули, добавленные по разбору пробелов: сравнения, сортировка сущностей, заморозка, векторные недочёты, задержка.

### `com/github/fireprime5/spellcraft/spell/ProgressionTablesTest.java`

класс `ProgressionTablesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 215 строк

Согласованность игровых таблиц: награды за сканирование и модификаторы должны указывать на реально зарегистрированные модули, иначе игрок сканирует цель и ничего не получает.

### `com/github/fireprime5/spellcraft/spell/SpellContextRangeTest.java`

класс `SpellContextRangeTest`, пакет `com.github.fireprime5.spellcraft.spell` — 342 строки

Границы, в которых заклинание вообще имеет право что-то делать. Точка в схеме собирается арифметикой от произвольных чисел, а координаты в мире не ограничены. Без общей проверки «Добыча» ломала блоки по всей карте, «Создать лавы» — заливала чужие постройки, а «Извлечение» — вытягивало по 400 энергии бездны из ancient_debris на другом конце континента.

### `com/github/fireprime5/spellcraft/spell/SpellGraphRobustnessTest.java`

класс `SpellGraphRobustnessTest`, пакет `com.github.fireprime5.spellcraft.spell` — 200 строк

Устойчивость графа к битым данным: пакеты и буфер обмена приходят от клиента, поэтому ни один плохой индекс или тип не должен ронять сервер.

### `com/github/fireprime5/spellcraft/spell/SpellGraphTest.java`

класс `SpellGraphTest`, пакет `com.github.fireprime5.spellcraft.spell` — 360 строк

Проверка графа заклинания: соединения, валидация, топологический порядок.

### `com/github/fireprime5/spellcraft/spell/SpellModulesRegistryTest.java`

класс `SpellModulesRegistryTest`, пакет `com.github.fireprime5.spellcraft.spell` — 120 строк

Проверка реестра модулей: у всех есть порты, описания, нет дублей.

### `com/github/fireprime5/spellcraft/spell/SpellModulesSearchTest.java`

класс `SpellModulesSearchTest`, пакет `com.github.fireprime5.spellcraft.spell` — 99 строк

Поиск модулей в палитре: по локализованному названию и по id.

### `com/github/fireprime5/spellcraft/spell/SpellSubsectionsTest.java`

класс `SpellSubsectionsTest`, пакет `com.github.fireprime5.spellcraft.spell` — 153 строки

Подразделы модулей. Подраздел — это вторая ступень палитры: категория отвечает на вопрос «о чём модуль», подраздел — «что он делает». Без второй ступени список из сорока с лишним названий читался сплошной полосой.

### `com/github/fireprime5/spellcraft/spell/SpellValueCodecTest.java`

класс `SpellValueCodecTest`, пакет `com.github.fireprime5.spellcraft.spell` — 83 строки

Значения заклинания: сериализация для клиента и защита от мусора в пакетах.

### `com/github/fireprime5/spellcraft/spell/StackModulesTest.java`

класс `StackModulesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 369 строк

Модули стека: регистрация и семантика.

### `com/github/fireprime5/spellcraft/spell/UniqueModulesTest.java`

класс `UniqueModulesTest`, пакет `com.github.fireprime5.spellcraft.spell` — 249 строк

Проверки личных модулей. Механика держится на трёх обещаниях, и каждое из них ломается тихо: модуль выдаётся дважды, выдаётся чужой, или попадает в палитру игрока, у которого его нет. Поэтому проверяем именно их, а не «модули зарегистрированы».

### `com/github/fireprime5/spellcraft/ui/AwtSurface.java`

класс `AwtSurface`, пакет `com.github.fireprime5.spellcraft.ui` — 55 строк

Реализация ModuleFrame.Surface на Graphics2D. Нужна превью-тестам: GuiGraphics в тестах не создаётся, поэтому рисунок модуля приходится повторять на AWT. Общий адаптер стоит рядом с игровым не просто так — если рисунок разъедется, это будет видно на обоих превью сразу.

