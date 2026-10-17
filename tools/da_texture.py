#!/usr/bin/env python3
"""Генератор текстуры тестового блока Spellcraft (GeckoLib, `da.png`).

Зачем он нужен
--------------
Модель разворачивается в текстуру раскладкой *box UV*, которую считает
GeckoLib в `BakedModelFactory#buildQuad`: каждая грань каждого куба попадает
в свой прямоугольник. Таких граней около сотни, они перекрываются, а после
правки геометрии пересчитать раскладку руками уже нельзя. Поэтому текстура
собирается кодом из описания модели: правишь `da.geo.json` — перезапускаешь
скрипт.

    python3 tools/da_texture.py             # текстура + превью в build/
    python3 tools/da_texture.py --no-preview

Стиль взят с текстур самого мода (`textures/block/*.png`): тёмно-синяя база,
фиолетовый корпус, бирюзовое свечение, стальные балки и тёплый янтарь как
единственный тёплый акцент.

Раскладка для куба W×H×D с началом UV (u, v):

      u                          u+D
      ┌──────┬──────┐            ┌──────┐
   v  │ EAST │ NORTH│         v  │  UP  │  высота D
      ├──────┼──────┤            ├──────┼──────┐
 v+D  │ WEST │ SOUTH│        v+D │ DOWN │      │  высота H
      └──────┴──────┘            └──────┴──────┘
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GEO = ROOT / "src/main/resources/assets/spellcraft/geo/da.geo.json"
TEXTURE = ROOT / "src/main/resources/assets/spellcraft/textures/entity/da.png"
PREVIEW = ROOT / "build/da-preview.png"

# --------------------------------------------------------------------------
# Палитра: значения сняты с текстур мода, а не выдуманы.
# --------------------------------------------------------------------------

INK = (0x07, 0x09, 0x18)
NAVY = (0x0A, 0x0C, 0x1C)
NAVY2 = (0x0E, 0x11, 0x24)
PANEL = (0x14, 0x16, 0x30)
PANEL2 = (0x1C, 0x20, 0x44)
PURPLE_D = (0x16, 0x0E, 0x28)
PURPLE = (0x37, 0x23, 0x5B)
PURPLE_L = (0x6C, 0x36, 0x90)
MAGENTA = (0xB7, 0x47, 0xBE)
TEAL_D = (0x10, 0x2A, 0x40)
TEAL = (0x12, 0x49, 0x57)
TEAL_M = (0x1B, 0x87, 0x91)
TEAL_L = (0x33, 0xB1, 0xBE)
CYAN = (0x7B, 0xE7, 0xDA)
STEEL_D = (0x22, 0x25, 0x2B)
STEEL = (0x3E, 0x41, 0x48)
STEEL_M = (0x5A, 0x5A, 0x60)
STEEL_L = (0x7B, 0x7B, 0x7F)
STEEL_H = (0x8D, 0x8D, 0x91)
WARM = (0xFA, 0x96, 0x3C)
WARM_L = (0xFF, 0xD2, 0x78)

FACES = ("UP", "DOWN", "EAST", "NORTH", "WEST", "SOUTH")


class Canvas:
    """Холст с циклической адресацией.

    Один куб в `da.geo.json` начинается с отрицательного UV (u = -3):
    такие тексели wrap'ятся в правый край, и рисовать их надо тоже.
    """

    def __init__(self, size: int, fill=NAVY):
        self.size = size
        self.img = Image.new("RGBA", (size, size), tuple(fill) + (255,))

    def px(self, x: int, y: int, color) -> None:
        self.img.putpixel((x % self.size, y % self.size), tuple(color) + (255,))

    def rect(self, x0, y0, x1, y1, color) -> None:
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path)


def rect_of(face: str, cube: dict) -> tuple[int, int, int, int]:
    """Прямоугольник грани в пикселях текстуры: (x0, y0, x1, y1)."""
    uv = cube["uv"]
    w, h, d = (math.floor(v) for v in cube["size"])
    if isinstance(uv, dict):  # развёртка по граням, а не box UV
        spec = uv[face.lower()]
        fu, fv = spec["uv"]
        sw, sh = spec["uv_size"]
        x0, x1 = sorted((fu, fu + sw))
        y0, y1 = sorted((fv, fv + sh))
        return math.floor(x0), math.floor(y0), math.floor(x1), math.floor(y1)
    u, v = int(uv[0]), int(uv[1])
    table = {
        "UP": (u + d, v, u + d + w, v + d),
        "DOWN": (u + d + w, v, u + d + 2 * w, v + d),
        "EAST": (u, v + d, u + d, v + d + h),
        "NORTH": (u + d, v + d, u + d + w, v + d + h),
        "WEST": (u + d + w, v + d, u + d + w + d, v + d + h),
        "SOUTH": (u + d + w + d, v + d, u + d + 2 * w + d, v + d + h),
    }
    return table[face]


# --------------------------------------------------------------------------
# Кисти. Рисунки симметричны относительно центра грани: GeckoLib разворачивает
# U для сущностей (mirror = false), поэтому левый и правый край у граней
# местами меняются, и асимметрия была бы видна глазом.
# --------------------------------------------------------------------------


def bevel(c: Canvas, r, fill, hi, lo) -> None:
    """Плоскость: освещённая верхняя и левая кромка, тень снизу и справа."""
    x0, y0, x1, y1 = r
    c.rect(x0, y0, x1, y1, fill)
    if x1 - x0 < 2 or y1 - y0 < 2:
        return
    for x in range(x0, x1):
        c.px(x, y0, hi)
        c.px(x, y1 - 1, lo)
    for y in range(y0, y1):
        c.px(x0, y, hi)
        c.px(x1 - 1, y, lo)


def vents(c: Canvas, r, slot=1, gap=1, color=STEEL_D) -> None:
    x0, y0, x1, y1 = r
    y = y0
    while y + slot <= y1:
        c.rect(x0, y, x1, y + slot, color)
        y += slot + gap


def glow_slot(c: Canvas, r, color, dim) -> None:
    """Горизонтальное окно с подсветкой — «энергия бездны» внутри корпуса."""
    x0, y0, x1, y1 = r
    if x1 - x0 < 4 or y1 - y0 < 3:
        return
    mid = (y0 + y1) // 2
    c.rect(x0 + 1, mid, x1 - 1, mid + 1, dim)
    c.rect(x0 + 2, mid, x1 - 2, mid + 1, color)


def rivets(c: Canvas, r, color) -> None:
    for x, y in ((r[0], r[1]), (r[2] - 1, r[1]), (r[0], r[3] - 1), (r[2] - 1, r[3] - 1)):
        c.px(x, y, color)


def circuit(c: Canvas, r, trace, node) -> None:
    """Схема из рельс и перемычек — «порты» на внутренней стенке.

    Именно рельсы, а не произвольные линии: на узкой грани любой рисунок
    сходится в пятно, а пара шин с перемычками читается даже в три строки.
    """
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w < 8 or h < 5:
        return
    bevel(c, r, PANEL, PURPLE_D, INK)
    upper = y0 + max(1, h // 3)
    lower = y1 - max(1, h // 3) - 1
    for bus in (upper, lower):
        c.rect(x0 + 1, bus, x1 - 1, bus + 1, trace)
    for fx in (0.2, 0.5, 0.8):
        x = x0 + round(w * fx)
        c.rect(x, upper, x + 1, lower + 1, trace)
        c.rect(x, upper - 1, x + 1, upper, node)
        c.rect(x, lower + 1, x + 1, lower + 2, node)


def screen_ui(c: Canvas, r, edge, glyph, dim, ink) -> None:
    """Интерфейс на экране: рамка, эквалайзер, разделитель, строка состояния.

    Симметричен относительно вертикальной оси, иначе зеркальный разворот UV
    показывал бы перевёрнутый интерфейс.
    """
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w < 10 or h < 7:
        return
    c.rect(x0, y0, x1, y1, ink)
    for x in range(x0, x1):
        c.px(x, y0, edge)
        c.px(x, y1 - 1, edge)
    for y in range(y0, y1):
        c.px(x0, y, edge)
        c.px(x1 - 1, y, edge)

    baseline = y1 - 4          # «земля», от которой растут столбики
    heights = (2, 3, 1, 4, 1, 3, 2)  # зеркально относительно центра
    pitch = max(2, (w - 2) // len(heights))
    for i, bar in enumerate(heights):
        bx = x0 + 1 + i * pitch
        if bx + 1 >= x1:
            break
        c.rect(bx, baseline - bar + 1, bx + 1, baseline + 1, glyph)
    for x in range(x0 + 1, x1 - 1):
        c.px(x, baseline + 1, dim)
    for fx in (0.15, 0.35, 0.65, 0.85):
        x = x0 + round(w * fx)
        if x0 < x < x1 - 1:
            c.px(x, baseline + 2, dim)


# --------------------------------------------------------------------------
# Материалы. Ключ — (кость, номер куба внутри кости).
# --------------------------------------------------------------------------


def steel(c: Canvas, r, face, accent=False):
    """Балка каркаса. Заливка тёмная, светлая только кромка — иначе сталь
    забивает собой весь мод, а в моде сталь это акцент, а не фон."""
    bevel(c, r, STEEL, STEEL_M, STEEL_D)
    if face == "UP":
        c.rect(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, STEEL_D)
    if accent:
        glow_slot(c, r, TEAL_L, TEAL)
    if r[2] - r[0] >= 3 and r[3] - r[1] >= 3:
        rivets(c, r, STEEL_M)


def casing(c: Canvas, r, face, accent=TEAL_M):
    """Плоскость корпуса: тёмно-синяя с фиолетовой кромкой."""
    bevel(c, r, PANEL, PURPLE, PURPLE_D)
    if face == "UP":
        c.rect(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, PANEL2)
    if accent:
        glow_slot(c, r, accent, TEAL_D)
    if r[2] - r[0] >= 4 and r[3] - r[1] >= 4:
        rivets(c, r, PURPLE_L)


def plinth(c: Canvas, r, face):
    """Нижняя плита корпуса: сверху пол, снизу крышка с ножками, сбоку цоколь."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if face == "DOWN":
        c.rect(x0, y0, x1, y1, NAVY)
        for x in range(x0, x1):
            c.px(x, y0, STEEL_D)
        if w >= 6 and h >= 6:
            for fx, fy in ((0.1, 0.1), (0.9, 0.1), (0.1, 0.9), (0.9, 0.9)):
                x = x0 + int(w * fx)
                y = y0 + int(h * fy)
                c.rect(x - 1, y - 1, x + 2, y + 2, STEEL_D)
        return
    if face == "UP":
        c.rect(x0, y0, x1, y1, PANEL)
        for x in range(x0, x1):
            c.px(x, y0, PURPLE_D)
            c.px(x, y1 - 1, PURPLE_D)
        return
    bevel(c, r, STEEL_M, STEEL_H, STEEL_D)
    glow_slot(c, r, TEAL_M, TEAL_D)


def plate(c: Canvas, r, face):
    """Боковина с жалюзи — читается как вентиляция."""
    bevel(c, r, NAVY2, PURPLE, PURPLE_D)
    if r[2] - r[0] >= 5 and r[3] - r[1] >= 4:
        vents(c, (r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1), 1, 1, PANEL)


def keycap(c: Canvas, r, face):
    """Клавиша: верхняя грань светится, бока — корпус."""
    if face == "UP":
        c.rect(*r, CYAN)
    elif face == "DOWN":
        c.rect(*r, TEAL_D)
    elif face == "NORTH":
        c.rect(*r, TEAL_L)
    else:
        c.rect(*r, TEAL)


def rod(c: Canvas, r, face):
    """Шток движущегося элемента: полированный стержень с бирюзовой нитью."""
    if face == "UP":
        c.rect(*r, STEEL_H)
    elif face == "DOWN":
        c.rect(*r, STEEL_D)
    else:
        bevel(c, r, STEEL_M, STEEL_H, STEEL_D)
        if r[2] - r[0] >= 2:
            c.rect(r[0], r[3] - 1, r[2], r[3], TEAL_L)


def screen_face(c: Canvas, r, face):
    """Пластина экрана: большие грани — интерфейс, торцы — рамка."""
    if face in ("EAST", "WEST"):
        screen_ui(c, r, TEAL_M, CYAN, TEAL, NAVY)
    else:
        bevel(c, r, NAVY2, PURPLE_L, PURPLE_D)


def bezel(c: Canvas, r, face):
    bevel(c, r, NAVY2, PURPLE_L, PURPLE_D)
    glow_slot(c, r, TEAL_L, TEAL_D)


def diag_support(c: Canvas, r, face):
    """Длинная грань наклонной стойки монитора: сталь с бирюзовой нитью."""
    x0, y0, x1, y1 = r
    if x1 - x0 >= 8:
        c.rect(x0, y0, x1, y1, STEEL_M)
        c.rect(x0, y0, x1, y0 + 1, STEEL_H)
        c.rect(x0, y1 - 1, x1, y1, STEEL_D)
        c.rect(x0, (y0 + y1) // 2, x1, (y0 + y1) // 2 + 1,
               TEAL_L if (x1 - x0) > 14 else TEAL)
    else:
        bevel(c, r, STEEL_M, STEEL_H, STEEL_D)


def rail(c: Canvas, r, face):
    """Рельс движущегося блока: тёмный паз со светящейся нитью."""
    x0, y0, x1, y1 = r
    c.rect(*r, PANEL)
    for x in range(x0, x1):
        c.px(x, y0, PURPLE)
        c.px(x, y1 - 1, PURPLE_D)
    if x1 - x0 >= 6:
        mid = (y0 + y1) // 2
        c.rect(x0 + 1, mid, x1 - 1, mid + 1, TEAL_L)


def keyboard_deck(c: Canvas, r, face):
    """Площадка клавиатуры: тёмная, с рядами светящихся полей."""
    x0, y0, x1, y1 = r
    c.rect(*r, NAVY2)
    for x in range(x0, x1):
        c.px(x, y0, PURPLE)
        c.px(x, y1 - 1, PURPLE_D)
    if x1 - x0 >= 4:
        for y in range(y0 + 1, y1 - 1, 2):
            c.rect(x0 + 1, y, x1 - 1, y + 1, TEAL_D)


def raw(color):
    return lambda c, r, f: c.rect(*r, color)


MATERIAL = {
    ("Каркас", 0): lambda c, r, f: steel(c, r, f, accent=True),
    ("Каркас", 1): steel,
    ("Каркас", 2): steel,
    ("Каркас", 3): plinth,
    ("Каркас", 4): steel,
    ("Каркас", 5): steel,
    ("Каркас", 6): steel,
    ("Каркас", 7): steel,
    ("Каркас", 8): steel,
    ("Каркас", 9): lambda c, r, f: steel(c, r, f, accent=True),
    ("Каркас", 10): steel,
    ("Каркас", 11): steel,
    ("Каркас", 12): steel,
    ("Каркас", 13): lambda c, r, f: circuit(c, r, PURPLE_L, MAGENTA),
    ("Каркас", 14): plate,
    ("Каркас", 15): plate,
    ("Каркас", 16): plate,
    ("Каркас", 17): lambda c, r, f: casing(c, r, f, accent=TEAL_L),
    ("Каркас", 18): lambda c, r, f: casing(c, r, f, accent=TEAL_L),
    ("Каркас", 19): steel,
    ("bb_main", 0): keyboard_deck,
    ("bb_main", 1): casing,
    ("bb_main", 2): lambda c, r, f: casing(c, r, f, accent=WARM),
    ("Основа монитора", 0): steel,
    ("Основа монитора", 1): steel,
    ("Основа монитора", 2): diag_support,
    ("Основа монитора", 3): steel,
    ("Основа монитора", 4): steel,
    ("Монитор", 0): screen_face,
    ("Монитор", 1): bezel,
    ("Монитор", 2): bezel,
    ("Двигающиеся элементы", 0): rail,
    ("bone", 0): rod,
    ("bone", 1): rod,
    ("bone", 2): rod,
    ("bone2", 0): rod,
    ("bone2", 1): rod,
}
for _key in range(3, 15):
    MATERIAL[(f"bone{_key}", 0)] = keycap


def build() -> Image.Image:
    geo = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    canvas = Canvas(geo["description"]["texture_width"])
    for bone in geo["bones"]:
        for index, cube in enumerate(bone.get("cubes", [])):
            key = (bone["name"], index)
            painter = MATERIAL.get(key)
            # Молча красить неизвестный куб фоном нельзя: так опечатка в имени
            # кости остаётся незамеченной, и деталь просто исчезает с модели.
            if painter is None:
                raise KeyError(f"нет материала для куба {key}")
            for face in FACES:
                r = rect_of(face, cube)
                if r[2] <= r[0] or r[3] <= r[1]:
                    continue  # вырожденная грань: ширина или глубина куба 0
                painter(canvas, r, face)
    return canvas.img


# --------------------------------------------------------------------------
# Превью: программная отрисовка модели в покое — результат видно без Minecraft.
# --------------------------------------------------------------------------

# Порядок вершин — как в GeckoLib GeoCube.VertexSet#verticesForQuad.
# Углы заданы как (ось X, ось Y, ось Z), где 1 — «плюс» по этой оси.
# Важно: WEST/UP начинаются с x = 0, EAST/DOWN — с x = 1, и порядок z
# внутри грани тоже разный. Ошибка здесь даёт UV, срезённые по диагонали.
QUAD_CORNERS = {
    "NORTH": ((0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)),
    "SOUTH": ((1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)),
    "WEST":  ((0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)),
    "EAST":  ((1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)),
    "UP":    ((0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    "DOWN":  ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)),
}

# Условный свет: верх ярче всего, низ в тени.
SHADE = {"UP": 1.30, "NORTH": 0.78, "SOUTH": 0.95, "EAST": 1.08,
         "WEST": 0.68, "DOWN": 0.52}


def rotate(p, pivot, angles):
    """Поворот точки вокруг оси по правилам Blockbench: сначала X, потом Y, потом Z."""
    x, y, z = (p[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in angles)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def make_view(yaw, pitch, size):
    """Ракурс камеры: поворот мира и перевод вида в пиксели.

    Возвращает пару функций вместо общего состояния: раньше углы хранились в
    глобальном VIEW и менялись мутацией, из-за чего `project` мог посчитать
    вершины под одним ракурсом, а `to_screen` вывести их под другим — превью
    разъезжалось. Теперь углы живут только в замыкании.
    """
    ay, ap = math.radians(yaw), math.radians(pitch)
    cy, sy, cp, sp = math.cos(ay), math.sin(ay), math.cos(ap), math.sin(ap)
    scale = size * 0.5 / 14.0
    mid_y = 9.0

    def project(p):
        """Мировые координаты -> вид из камеры: (x, y, глубина)."""
        x, y, z = p
        rx = x * cy - z * sy
        rz = x * sy + z * cy
        return rx, y * cp - rz * sp, y * sp + rz * cp

    def to_screen(view):
        rx, ry, _ = view
        return (size / 2 + rx * scale, size / 2 - (ry - mid_y) * scale)

    return project, to_screen


VIEW_YAW, VIEW_PITCH, VIEW_SIZE = 38.0, 24.0, 560


def backfacing(pts, center) -> bool:
    """Грань отвёрнута от камеры: нормаль смотрит внутрь куба."""
    n = normal(pts)
    middle = tuple(sum(p[i] for p in pts) / 4 for i in range(3))
    return sum(n[i] * (middle[i] - center[i]) for i in range(3)) <= 0


def render(texture: Image.Image, yaw=38.0, pitch=24.0, size=560):
    geo = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    tw = th = geo["description"]["texture_width"]
    tex = texture.convert("RGBA")
    project, to_screen = make_view(yaw, pitch, size)

    faces = []
    for bone in geo["bones"]:
        for cube in bone.get("cubes", []):
            ox, oy, oz = cube["origin"]
            w, h, d = cube["size"]
            pivot = cube.get("pivot", [ox, oy, oz])
            rot = cube.get("rotation", [0, 0, 0])
            center = (ox + w / 2, oy + h / 2, oz + d / 2)

            def corner(ijk):
                i, j, k = ijk
                return rotate((ox + (w if i else 0),
                               oy + (h if j else 0),
                               oz + (d if k else 0)), pivot, rot)

            for face, pattern in QUAD_CORNERS.items():
                x0, y0, x1, y1 = rect_of(face, cube)
                if x1 <= x0 or y1 <= y0:
                    continue
                pts = [corner(ijk) for ijk in pattern]
                if backfacing(pts, center):
                    continue
                uvs = [(x1, y0), (x0, y0), (x0, y1), (x1, y1)]
                proj = [project(p) for p in pts]
                depth = sum(p[2] for p in proj) / 4
                faces.append((depth, face, proj, uvs))

    faces.sort(key=lambda f: f[0])

    img = Image.new("RGBA", (size, size), (0x18, 0x1C, 0x24, 255))
    px = img.load()

    for _, face, proj, uvs in faces:
        shade = SHADE[face]
        screen = [to_screen(p) for p in proj]
        pairs = list(zip(screen, uvs))
        fill_triangle(px, size, (pairs[0], pairs[1], pairs[2]), tex, tw, th, shade)
        fill_triangle(px, size, (pairs[0], pairs[2], pairs[3]), tex, tw, th, shade)

    return img


def normal(pts):
    ux = [pts[1][i] - pts[0][i] for i in range(3)]
    vx = [pts[3][i] - pts[0][i] for i in range(3)]
    n = (ux[1] * vx[2] - ux[2] * vx[1],
         ux[2] * vx[0] - ux[0] * vx[2],
         ux[0] * vx[1] - ux[1] * vx[0])
    length = math.sqrt(sum(c * c for c in n)) or 1.0
    return tuple(c / length for c in n)


def fill_triangle(px, size, tri, tex, tw, th, shade) -> None:
    """Заливает треугольник, интерполируя UV. `tri` — (screen, uv) парами."""
    (ax, ay), (bx, by), (cx, cy) = [p for p, _ in tri]
    (au, av), (bu, bv), (cu, cv) = [uv for _, uv in tri]
    for y in range(max(0, int(min(ay, by, cy))), min(size, int(max(ay, by, cy)) + 1)):
        for x in range(max(0, int(min(ax, bx, cx))), min(size, int(max(ax, bx, cx)) + 1)):
            weights = barycentric(((ax, ay), (bx, by), (cx, cy)), x + 0.5, y + 0.5)
            if weights is None:
                continue
            wa, wb, wc = weights
            tx = int(wa * au + wb * bu + wc * cu) % tw
            ty = int(wa * av + wb * bv + wc * cv) % th
            r, g, b, a = tex.getpixel((tx, ty))
            if a == 0:
                continue
            px[x, y] = (min(255, int(r * shade)),
                        min(255, int(g * shade)),
                        min(255, int(b * shade)), 255)


def barycentric(tri, x, y):
    """Веса вершин (w0, w1, w2) точки в треугольнике, либо None вне его.

    Возвращает веса **в порядке вершин**: результат[i] — доля tri[i].
    a и b ниже — это l0 и l1, поэтому возвращать надо (a, b, c); возврат
    (c, a, b) циклически сдвигает веса, и UV каждой вершины привязывается
    к соседней — картинка едет по диагонали.
    """
    (x0, y0), (x1, y1), (x2, y2) = tri
    det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(det) < 1e-9:
        return None
    w0 = ((y1 - y2) * (x - x2) + (x2 - x1) * (y - y2)) / det
    w1 = ((y2 - y0) * (x - x2) + (x0 - x2) * (y - y2)) / det
    w2 = 1 - w0 - w1
    if w0 < 0 or w1 < 0 or w2 < 0:
        return None
    return w0, w1, w2


def self_test() -> int:
    """Проверяет, что UV грани совпадают с UV её же вершин.

    Ловит класс ошибок, который уже встречался: если в `barycentric`
    перепутаны веса, UV каждой вершины привязывается к соседней и картинка
    едет по диагонали, хотя раскладка UV в самом файле верная.

    Проверять нужно **соответствие вершинам**, а не непрерывность поля.
    Циклическая перестановка весов даёт непрерывное аффинное поле, так что
    скачков нет нигде, — но UV в вершинах уезжают и картинка всё равно едет.
    Обход всей картинки тоже бесполезен: на стыке двух кубов соседние
    пиксели берут UV из разных мест текстуры, и скачок там законен.
    """
    geo = json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    project, _ = make_view(VIEW_YAW, VIEW_PITCH, VIEW_SIZE)  # ракурс превью
    checked, broken = 0, []

    for bone in geo["bones"]:
        for index, cube in enumerate(bone.get("cubes", [])):
            ox, oy, oz = cube["origin"]
            w, h, d = cube["size"]
            pivot = cube.get("pivot", [ox, oy, oz])
            rotation = cube.get("rotation", [0, 0, 0])
            center = (ox + w / 2, oy + h / 2, oz + d / 2)

            def corner(ijk, _p=pivot, _r=rotation, _o=(ox, oy, oz), _s=(w, h, d)):
                i, j, k = ijk
                return rotate((_o[0] + (_s[0] if i else 0),
                               _o[1] + (_s[1] if j else 0),
                               _o[2] + (_s[2] if k else 0)), _p, _r)

            for face, pattern in QUAD_CORNERS.items():
                x0, y0, x1, y1 = rect_of(face, cube)
                if x1 <= x0 or y1 <= y0:
                    continue  # вырожденная грань
                pts = [corner(ijk) for ijk in pattern]
                if backfacing(pts, center):
                    continue  # грань отвёрнута от камеры
                uvs = [(x1, y0), (x0, y0), (x0, y1), (x1, y1)]
                checked += 1

                # Грань рисуется двумя треугольниками, и диагональ 0-2
                # принадлежит обоим: UV должен сходиться на ней.
                for tri in ((0, 1, 2), (0, 2, 3)):
                    screen = [project(p)[:2] for p in pts]
                    for vertex in tri:
                        got = uv_at_vertex(screen, uvs, tri, vertex)
                        want = uvs[vertex]
                        if abs(got[0] - want[0]) > 0.51 or abs(got[1] - want[1]) > 0.51:
                            broken.append((bone["name"], index, face,
                                           tri, vertex, got, want))

    if broken:
        print("UV в вершине не совпадает с её собственным UV:")
        for item in broken[:12]:
            print("   кость=%s куб=%d грань=%s три=%s вершина=%d получили=%s ждали=%s" % item)
        print("всего расхождений:", len(broken))
        return 1
    print(f"self-test: {checked} граней, UV вершин сходится")
    return 0


def uv_at_vertex(screen, uvs, tri, vertex):
    """UV, которое даст интерполяция в экранной точке вершины `vertex`.

    Считаем веса треугольника в самой вершине: там они обязаны быть
    (1,0,0)/(0,1,0)/(0,0,1) по порядку, поэтому расхождение означает, что
    интерполяция привязывает UV не к той вершине.
    """
    a, b, c = (screen[i] for i in tri)
    weights = barycentric((a, b, c), screen[vertex][0], screen[vertex][1])
    if weights is None:  # вырожденный треугольник: проверить нечем
        return uvs[vertex]
    ua, ub, uc = (uvs[i] for i in tri)
    return (weights[0] * ua[0] + weights[1] * ub[0] + weights[2] * uc[0],
            weights[0] * ua[1] + weights[1] * ub[1] + weights[2] * uc[1])


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()
    texture = build()
    texture.save(TEXTURE)
    print(f"текстура: {TEXTURE.relative_to(ROOT)} {texture.size[0]}x{texture.size[1]}")
    if "--no-preview" not in sys.argv:
        PREVIEW.parent.mkdir(parents=True, exist_ok=True)
        render(texture).convert("RGB").save(PREVIEW)
        print(f"превью:   {PREVIEW.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
