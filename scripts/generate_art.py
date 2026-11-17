"""ReTerra's pixel art, drawn on a 16px grid. Requires Pillow; no external artwork.

Keep edges on whole pixels, light from the upper left, and materials to the shared
palette below. Source shapes are intentionally explicit rather than noise filters.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/reterra"
P = {
    "ink": "#25302d", "deep": "#35423b", "shade": "#4e5a50",
    "stone": "#778073", "light": "#a6ac94", "edge": "#c3c5a9",
    "copper_dark": "#72503c", "copper": "#b47d50", "copper_light": "#deb078",
    "soil_dark": "#49392c", "soil": "#72553a", "soil_light": "#a28050",
    "leaf_dark": "#3f6045", "leaf": "#729552", "leaf_light": "#aecb72",
    "water_dark": "#315a65", "water": "#568e9d", "water_light": "#9bc7c0",
    "cream": "#e2d8ac", "white": "#f5ecd2", "amber": "#d8ab53",
}

def canvas():
    image = Image.new("RGBA", (16, 16))
    return image, ImageDraw.Draw(image)

def rect(d, box, color):
    d.rectangle(box, fill=P.get(color, color))

def line(d, points, color, width=1):
    d.line(points, fill=P.get(color, color), width=width)

def poly(d, points, color):
    d.polygon(points, fill=P.get(color, color))

def save(image, name, folder="item"):
    path = ASSETS / "textures" / folder / (name + ".png")
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def vial(fill, sample=None):
    im, d = canvas()
    poly(d, [(5, 1), (10, 1), (10, 4), (12, 6), (12, 13), (10, 15), (5, 15), (3, 13), (3, 6), (5, 4)], "ink")
    rect(d, (6, 4, 9, 5), "water_light")
    poly(d, [(5, 6), (10, 6), (11, 7), (11, 12), (9, 14), (6, 14), (4, 12), (4, 7)], "water_dark")
    rect(d, (5, 7, 10, 12), fill)
    line(d, [(5, 6), (4, 7), (4, 10)], "white")
    rect(d, (5, 2, 10, 3), "soil")
    rect(d, (6, 1, 9, 1), "soil_light")
    rect(d, (6, 14, 9, 14), "water")
    if sample:
        rect(d, (6, 8, 10, 12), "cream")
        if sample == "sheep":
            rect(d, (7, 9, 9, 11), "white")
            rect(d, (8, 10, 9, 11), "shade")
        elif sample == "cow":
            rect(d, (7, 9, 9, 11), "soil_dark")
            rect(d, (7, 10, 7, 11), "white")
            rect(d, (9, 9, 9, 9), "white")
        elif sample == "chicken":
            line(d, [(7, 11), (9, 9)], "white", 2)
            rect(d, (9, 9, 10, 9), "amber")
            rect(d, (8, 8, 8, 8), "copper")
        else:
            rect(d, (7, 9, 9, 11), "amber")
            line(d, [(8, 9), (8, 11)], "soil_dark")
            rect(d, (7, 8, 7, 8), "white")
            rect(d, (9, 8, 9, 8), "white")
    return im, d

def items():
    im, d = canvas()
    poly(d, [(1, 12), (3, 9), (6, 8), (8, 6), (11, 8), (13, 9), (15, 13), (12, 15), (4, 15)], "ink")
    poly(d, [(2, 12), (4, 10), (7, 9), (8, 7), (10, 9), (12, 10), (14, 13), (11, 14), (4, 14)], "soil")
    for box in [(4, 10, 5, 10), (8, 9, 9, 9), (11, 12, 12, 12), (5, 13, 6, 13)]: rect(d, box, "soil_light")
    line(d, [(4, 8), (7, 10), (11, 11)], "soil_dark")
    line(d, [(8, 8), (9, 4)], "leaf_dark")
    rect(d, (6, 4, 8, 5), "leaf")
    rect(d, (9, 2, 11, 3), "leaf_light")
    save(im, "organic_compost")

    im, d = vial("leaf_dark")
    line(d, [(7, 12), (7, 8), (8, 7)], "leaf_light")
    line(d, [(7, 10), (5, 8)], "leaf")
    line(d, [(8, 11), (10, 9)], "leaf_light")
    save(im, "grass_culture")
    im, d = vial("amber")
    rect(d, (6, 9, 9, 11), "cream")
    rect(d, (7, 8, 8, 12), "cream")
    rect(d, (7, 10, 8, 10), "white")
    save(im, "nutrient_culture")
    for species, color in [("sheep", "shade"), ("cow", "soil_dark"), ("chicken", "copper"), ("bee", "amber")]:
        im, d = vial(color, species)
        save(im, species + "_sample")

    im, d = canvas()
    rect(d, (4, 2, 11, 14), "ink")
    rect(d, (5, 4, 10, 12), "deep")
    for y in (5, 8, 11): line(d, [(5, y), (10, y)], "shade")
    for x in (5, 8, 10): line(d, [(x, 5), (x, 11)], "stone")
    rect(d, (4, 2, 11, 3), "copper")
    rect(d, (4, 13, 11, 14), "copper_dark")
    line(d, [(5, 2), (10, 2)], "copper_light")
    line(d, [(5, 13), (10, 13)], "copper")
    save(im, "water_filter")

    im, d = canvas()
    rect(d, (2, 3, 13, 12), "ink")
    rect(d, (3, 4, 12, 11), "leaf_dark")
    rect(d, (6, 5, 9, 9), "ink")
    rect(d, (7, 6, 8, 8), "stone")
    for y in (4, 7, 10):
        line(d, [(1, y), (1, y + 1)], "copper_light")
        line(d, [(14, y), (14, y + 1)], "copper")
    line(d, [(4, 5), (4, 8), (5, 8)], "copper_light")
    line(d, [(10, 6), (11, 6), (11, 10), (9, 10)], "copper")
    rect(d, (4, 10, 5, 10), "leaf_light")
    save(im, "archive_fragment")

    im, d = canvas()
    line(d, [(10, 1), (10, 4)], "ink", 2)
    rect(d, (3, 3, 12, 14), "ink")
    rect(d, (4, 4, 11, 13), "copper")
    line(d, [(4, 4), (10, 4)], "copper_light")
    rect(d, (5, 5, 10, 9), "deep")
    line(d, [(6, 8), (7, 6), (8, 7), (9, 6)], "leaf_light")
    rect(d, (5, 11, 6, 12), "ink")
    rect(d, (9, 11, 10, 12), "water_light")
    save(im, "environmental_scanner")

    im, d = canvas()
    rect(d, (2, 2, 12, 14), "ink")
    rect(d, (3, 3, 11, 12), "soil")
    rect(d, (4, 3, 10, 11), "cream")
    line(d, [(4, 13), (11, 13)], "white")
    line(d, [(3, 3), (3, 12)], "copper_light")
    line(d, [(8, 9), (7, 6)], "leaf_dark")
    rect(d, (5, 5, 7, 6), "leaf")
    rect(d, (8, 4, 9, 5), "leaf_dark")
    rect(d, (9, 12, 10, 15), "leaf")
    save(im, "field_journal")

    im, d = canvas()
    poly(d, [(5, 1), (10, 1), (10, 4), (12, 7), (13, 11), (11, 14), (4, 14), (2, 11), (3, 7), (5, 4)], "ink")
    poly(d, [(6, 2), (9, 2), (8, 5), (11, 8), (12, 11), (10, 13), (5, 13), (3, 11), (4, 8), (7, 5)], "soil_light")
    line(d, [(4, 9), (4, 11), (6, 12)], "cream")
    rect(d, (5, 4, 10, 5), "soil_dark")
    rect(d, (7, 8, 9, 10), "leaf_dark")
    rect(d, (8, 7, 10, 8), "leaf")
    line(d, [(8, 10), (7, 11)], "leaf")
    save(im, "recovery_bundle")

def casing(kind, face):
    im, d = canvas()
    rect(d, (0, 0, 15, 15), "ink")
    rect(d, (1, 1, 14, 14), "shade")
    rect(d, (2, 2, 13, 13), "stone")
    line(d, [(1, 14), (14, 14), (14, 1)], "deep")
    line(d, [(1, 1), (14, 1)], "light")
    for x, y in ((1, 1), (12, 1), (1, 12), (12, 12)):
        rect(d, (x, y, x + 2, y + 2), "copper_dark")
        rect(d, (x, y, x + 1, y + 1), "copper")
        rect(d, (x, y, x, y), "copper_light")
    if face == "side":
        rect(d, (4, 4, 11, 11), "shade")
        line(d, [(5, 5), (10, 5)], "light")
        for y in (7, 9): line(d, [(5, y), (10, y)], "deep")
        line(d, [(3, 6), (3, 10), (5, 12)], "copper_dark")
        if kind == "water_restorer": line(d, [(11, 4), (11, 11), (9, 11)], "water")
        elif kind in ("heart_of_the_world", "restoration_core"):
            line(d, [(7, 12), (9, 10), (9, 7)], "leaf_dark")
        return im
    if face == "top":
        rect(d, (4, 4, 11, 11), "deep")
        if kind == "soil_reclaimer":
            for x in (5, 7, 9): line(d, [(x, 4), (x, 11)], "light")
            line(d, [(4, 7), (11, 7)], "shade")
        elif kind == "water_restorer":
            rect(d, (5, 5, 10, 10), "water_dark")
            line(d, [(5, 5), (10, 5), (10, 7)], "water_light")
            line(d, [(6, 9), (9, 9)], "water")
        elif kind == "revival_chamber":
            rect(d, (5, 5, 10, 10), "water_dark")
            line(d, [(5, 5), (10, 5)], "water_light")
            rect(d, (7, 6, 8, 9), "leaf")
            rect(d, (6, 7, 9, 8), "leaf_light")
        else:
            poly(d, [(7, 3), (12, 7), (8, 12), (3, 8)], "copper")
            poly(d, [(7, 5), (10, 7), (8, 10), (5, 8)], "leaf_dark")
            line(d, [(7, 6), (7, 8), (8, 8)], "leaf_light")
        return im
    rect(d, (3, 4, 12, 12), "ink")
    if kind == "soil_reclaimer":
        for y in (5, 7): line(d, [(4, y), (11, y)], "light")
        rect(d, (4, 10, 10, 12), "copper_dark")
        rect(d, (5, 10, 9, 10), "copper_light")
        rect(d, (6, 11, 8, 12), "soil_dark")
    elif kind == "restoration_core":
        for x in (4, 11): line(d, [(x, 5), (x, 10)], "copper")
        poly(d, [(7, 4), (11, 8), (8, 12), (4, 8)], "leaf_dark")
        poly(d, [(7, 6), (9, 8), (8, 10), (6, 8)], "leaf_light")
        rect(d, (7, 7, 8, 8), "leaf")
    elif kind == "water_restorer":
        rect(d, (4, 5, 6, 11), "water_dark")
        for y in (6, 9, 11): line(d, [(4, y), (6, y)], "water_light")
        for x in (9, 11): line(d, [(x, 5), (x, 11)], "stone")
        line(d, [(8, 12), (11, 12)], "copper")
    elif kind == "revival_chamber":
        rect(d, (4, 5, 11, 11), "water_dark")
        line(d, [(4, 5), (10, 5)], "water_light")
        rect(d, (5, 6, 5, 7), "water_light")
        line(d, [(8, 10), (8, 7)], "leaf_light")
        rect(d, (6, 7, 7, 8), "leaf")
        rect(d, (9, 6, 10, 7), "leaf_light")
    else:
        poly(d, [(7, 4), (11, 8), (8, 12), (4, 8)], "copper")
        poly(d, [(7, 5), (10, 8), (8, 11), (5, 8)], "leaf_dark")
        line(d, [(7, 7), (8, 8), (8, 9)], "leaf_light")
        line(d, [(4, 3), (4, 4), (3, 4)], "shade")
    return im

def blocks():
    for kind in ("soil_reclaimer", "restoration_core", "water_restorer", "revival_chamber", "heart_of_the_world"):
        for face in ("front", "side", "top"):
            save(casing(kind, face), kind + "_" + face, "block")
        # Retain the old texture path for resource packs that reference it.
        save(casing(kind, "side"), kind, "block")
    for state, color in (("off", "shade"), ("stored", "amber"), ("active", "leaf_light")):
        im, d = canvas()
        rect(d, (0, 0, 15, 15), "ink")
        rect(d, (2, 2, 13, 13), color)
        line(d, [(2, 2), (13, 2)], "edge" if state != "off" else "stone")
        rect(d, (2, 12, 13, 13), "copper_dark" if state == "stored" else "leaf_dark" if state == "active" else "deep")
        save(im, "indicator_" + state, "block")

def icon():
    im = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(im)
    poly(d, [(7, 1), (24, 1), (30, 7), (30, 24), (24, 30), (7, 30), (1, 24), (1, 7)], "ink")
    poly(d, [(8, 3), (23, 3), (28, 8), (28, 23), (23, 28), (8, 28), (3, 23), (3, 8)], "copper")
    rect(d, (5, 7, 26, 23), "deep")
    rect(d, (8, 5, 23, 26), "deep")
    rect(d, (5, 21, 26, 23), "soil_dark")
    rect(d, (8, 24, 23, 26), "soil_dark")
    line(d, [(16, 21), (16, 14)], "leaf_light", 2)
    poly(d, [(15, 16), (10, 15), (8, 10), (13, 10), (16, 13)], "leaf")
    poly(d, [(17, 13), (18, 8), (24, 7), (23, 12), (19, 15)], "leaf_light")
    line(d, [(16, 22), (13, 24), (11, 24)], "soil_light")
    line(d, [(17, 22), (20, 24)], "soil_light")
    line(d, [(8, 3), (23, 3), (27, 7)], "copper_light")
    im.resize((128, 128), Image.Resampling.NEAREST).save(ASSETS / "icon.png")

def preview():
    files = sorted((ASSETS / "textures/item").glob("*.png"))
    files += sorted((ASSETS / "textures/block").glob("*_front.png"))
    sheet = Image.new("RGB", (640, ((len(files) + 3) // 4) * 124), P["deep"])
    d = ImageDraw.Draw(sheet)
    for i, path in enumerate(files):
        image = Image.open(path).convert("RGBA").resize((80, 80), Image.Resampling.NEAREST)
        x, y = i % 4 * 160, i // 4 * 124
        sheet.paste(image, (x + 38, y + 8), image)
        d.text((x + 4, y + 94), path.stem.replace("_front", ""), fill=P["cream"])
    sheet.save(ROOT / "docs/art/pixel-art-preview.png")

if __name__ == "__main__":
    items()
    blocks()
    icon()
    preview()
    print("Generated 16px items, machine faces, indicators and the ReTerra icon.")
