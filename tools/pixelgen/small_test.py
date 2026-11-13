"""
Checks whether the generator can make the game's small things: a chess piece (drawn 18 x 28 on the board)
and a card picture (36 x 34 in the card's window). Generates each, then shrinks it to its real size and
puts it next to the code-drawn one, 6x enlarged, in tools/pixelgen/out/small_test.png.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate  # noqa: E402

OUT = generate.OUT
STYLE = "pixel art, single object centred, plain flat dark grey background, warm light from the upper left, highly detailed"

TESTS = {
    "knight": (501, "a carved ivory chess knight piece, a horse head, standing upright, side view", (18, 28)),
    "bishop": (502, "a carved ivory chess bishop piece with a mitre and a slit, standing upright", (18, 28)),
    "blood_moon": (503, "a huge red moon over black hills at night, card illustration", (36, 34)),
    "minefield": (504, "a spiked naval mine on a chessboard under a grinning skull, card illustration", (36, 34)),
}


def main():
    tiles = []
    for name, (seed, prompt, size) in TESTS.items():
        data = generate.generate(prompt, 1, seed, 1024, 1024, STYLE)[0]
        raw_path = os.path.join(OUT, f"small_{name}_raw.png")
        with open(raw_path, "wb") as f:
            f.write(data)
        clean = generate.clean(Image.open(raw_path))
        small = clean.copy()
        small.thumbnail(size, Image.LANCZOS)
        small = small.quantize(colors=24).convert("RGBA") if small.mode != "RGBA" else small
        canvas = Image.new("RGBA", size, (30, 26, 30, 255))
        canvas.alpha_composite(small, ((size[0] - small.width) // 2, size[1] - small.height))
        tiles.append(canvas.resize((size[0] * 6, size[1] * 6), Image.NEAREST))
        print("done", name, flush=True)
    sheet = Image.new("RGBA", (sum(t.width for t in tiles) + 20 * len(tiles), max(t.height for t in tiles)), (20, 16, 20, 255))
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 20
    sheet.save(os.path.join(OUT, "small_test.png"))


if __name__ == "__main__":
    main()
