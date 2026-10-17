#!/usr/bin/env python3
"""Генератор спрайтов цветных проводов Spellcraft (`textures/item/wire_*.png`).

Зачем он нужен
--------------
Спрайт предмета рисуется один раз и должен совпадать с цветом, который потом
ставит блок: игрок берёт «красный провод» в инвентарь, видит иконку и решает,
что это за линия. Цвета поэтому берутся из одной таблицы с ванильными
красителями: поправил оттенок здесь — поправился и красный провод, и его
перевод, и рецепт с `minecraft:red_dye`.

    python3 tools/wire_item_texture.py             # 16 спрайтов + превью в build/
    python3 tools/wire_item_texture.py --no-preview

Спрайт рисуется как кабель: тёмная оплётка сверху и снизу, жилка красителя
посередине и ответвление вниз — тем же силуэтом, что и блок в мире. Фон
прозрачный: предмет рисуется на квадратном quad, и непрозрачная заливка дала бы
в инвентаре тёмный квадрат вместо вещи.

Смещение цвета внутри спрайта детерминированное (хеш от координат): без него
PIL рисовал бы ровные полосы, а провод выглядел бы как полоска бумаги. Проверка
на мутации (`WireColorItemsTest`) меряет средний тон спрайта, поэтому палитра
каждого цвета должна оставаться узнаваемой после затемнения.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/spellcraft/textures/item"
PREVIEW = ROOT / "build/wire-items-preview.png"

# --------------------------------------------------------------------------
# Палитра: ванильные красители, приведённые к стилю текстур мода.
# --------------------------------------------------------------------------

INK = (0x12, 0x12, 0x14)
CASING = (0x2C, 0x2C, 0x30)
METAL = (0x96, 0x98, 0xA0)
METAL_DARK = (0x62, 0x64, 0x6C)

#: id предмета -> ванильный краситель. Порядок совпадает с DyeColor.
DYES = {
    "white": 0xF9FFFE,
    "orange": 0xF9801D,
    "magenta": 0xC74EBD,
    "light_blue": 0x3AB3DA,
    "yellow": 0xFED83D,
    "lime": 0x80C71F,
    "pink": 0xF38BAA,
    "gray": 0x474F52,
    "light_gray": 0x9D9D97,
    "cyan": 0x169C9C,
    "purple": 0x8932B8,
    "blue": 0x3C44AA,
    "brown": 0x835432,
    "green": 0x5E7C16,
    "red": 0xB02E26,
    "black": 0x1D1D21,
}


def shade(dye: int, factor: float, floor: int = 0x12) -> tuple[int, int, int]:
    """Краситель, приглушённый в factor раз. Пол темнее — иначе спрайт светится."""
    return tuple(max(floor, min(0xFF, round(channel * factor))) for channel in
                 ((dye >> 16) & 0xFF, (dye >> 8) & 0xFF, dye & 0xFF))


def towards_white(rgb: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    """Смесь цвета с белым: блик на жилке, оттенок при этом почти не меняется."""
    return tuple(round(channel + (0xFF - channel) * amount) for channel in rgb)


def jitter(x: int, y: int, rgb: tuple[int, int, int]) -> tuple[int, int, int]:
    """Детерминированный шум на пиксель: спрайт перестаёт быть полосатым."""
    seed = (x * 73856093) ^ (y * 19349663)
    out = []
    for i, channel in enumerate(rgb):
        seed = (seed * 1103515245 + 12345 + i) & 0x7FFFFFFF
        delta = ((seed >> 16) % 9) - 4
        out.append(max(0, min(0xFF, channel + delta)))
    return tuple(out)


def sprite(dye: int) -> Image.Image:
    """Спрайт кабеля заданного цвета, 16x16, RGBA."""
    core = shade(dye, 0.72)
    lit = towards_white(core, 0.40)
    dark = shade(dye, 0.44)
    lit = towards_white(lit, 0.20)

    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()

    def put(x: int, y: int, rgb: tuple[int, int, int], alpha: int = 255) -> None:
        if 0 <= x < 16 and 0 <= y < 16:
            pixels[x, y] = jitter(x, y, rgb) + (alpha,)

    # кабель: жилка во всю толщину, оплётка в один пиксель сверху и снизу,
    # металл на срезе по краям — так цвет читается с первого пикселя
    for x in range(2, 14):
        put(x, 4, INK)
        put(x, 10, INK)
    for x in range(2, 14):
        put(x, 5, lit)
        put(x, 6, core)
        put(x, 7, core)
        put(x, 8, dark)
        put(x, 9, CASING)
    for y in range(5, 10):
        put(1, y, METAL)
        put(14, y, METAL_DARK)

    # ответвление вниз — так же, как блок уходит к соседу
    put(6, 12, INK)
    put(9, 12, INK)
    put(6, 13, INK)
    put(9, 13, INK)
    for y, tone in ((11, lit), (12, core), (13, dark)):
        put(7, y, tone)
        put(8, y, tone)

    return image


def preview(images: dict[str, Image.Image]) -> None:
    """Лист со всеми спрайтами: их разом видно на превью."""
    sheet = Image.new("RGBA", (16 * 8, 16 * 2), (0x1A, 0x1A, 0x20, 255))
    for index, name in enumerate(DYES):
        sheet.paste(images[name], (16 * (index % 8), 16 * (index // 8)))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print(f"превью: {PREVIEW}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Спрайты цветных проводов")
    parser.add_argument("--no-preview", action="store_true",
                        help="не собирать лист-превью в build/")
    args = parser.parse_args()

    ITEMS.mkdir(parents=True, exist_ok=True)
    images = {}
    for name, dye in DYES.items():
        image = sprite(dye)
        path = ITEMS / f"wire_{name}.png"
        image.save(path)
        images[name] = image
        print(f"{path.relative_to(ROOT)}")

    if not args.no_preview:
        preview(images)


if __name__ == "__main__":
    main()
