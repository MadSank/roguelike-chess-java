"""
Backdrops for Ashes from the local generator: the game room behind the table, and the dealers' wall.
Generated at 1280 x 960 (the pixel-art LoRA draws ~8-pixel blocks, so 160 x 120 cells), shrunk onto that
grid, reduced to a 48-colour palette and shown at 3x to fill the 480 x 360 canvas.

Usage (from C:\\Ashes, ComfyUI running):  python tools/pixelgen/scenes.py [name ...]
Outputs: tools/pixelgen/out/scenes/<name>_<n>.png (480 x 360, ready) and <name>_sheet.png.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate  # noqa: E402

OUT = os.path.join(generate.OUT, "scenes")
STYLE = ("pixel art, dark horror, dystopian, warm orange light from a single hanging lamp, deep purple shadows, "
         "muted colours, highly detailed, no people, empty")

SCENES = {
    "room": (201, "the back wall of a grim underground gambling den, old stained brick and concrete wall, a single bare "
                  "lamp hanging from a cable in the middle, pipes and cables along the wall, shadows in the corners, "
                  "straight-on view of the wall"),
    "shop": (202, "the back wall of a black market stall in a cyberpunk slum, shelves of junk and boxes in shadow, two "
                  "candles on brackets, a tattered curtain, warm lamplight, straight-on view of the wall"),
}


def shrink(raw, cells=(160, 120), colours=48, scale=3):
    small = raw.convert("RGB").resize(cells, Image.NEAREST)
    small = small.quantize(colors=colours, method=Image.Quantize.MEDIANCUT).convert("RGB")
    return small.resize((cells[0] * scale, cells[1] * scale), Image.NEAREST)


def main():
    names = sys.argv[1:] or list(SCENES)
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        seed, prompt = SCENES[name]
        imgs = generate.generate(prompt, 3, seed, 1280, 960, STYLE)
        tiles = []
        for i, data in enumerate(imgs):
            raw = os.path.join(OUT, f"{name}_{i + 1}_raw.png")
            with open(raw, "wb") as f:
                f.write(data)
            ready = shrink(Image.open(raw))
            ready.save(os.path.join(OUT, f"{name}_{i + 1}.png"))
            tiles.append(ready.resize((320, 240)))
        sheet = Image.new("RGB", (320 * len(tiles), 240))
        for i, t in enumerate(tiles):
            sheet.paste(t, (320 * i, 0))
        sheet.save(os.path.join(OUT, f"{name}_sheet.png"))
        print("done", name, flush=True)


if __name__ == "__main__":
    main()
