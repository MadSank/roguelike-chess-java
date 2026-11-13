"""
Generates the whole cast of Ashes with the local generator (see generate.py), a few tries per person,
all from one shared description of the style so they look like one set.

Usage (from C:\\Ashes, with ComfyUI running):
  python tools/pixelgen/cast.py [name ...]      # no names = everyone
Outputs: tools/pixelgen/out/cast/<file>_<n>_raw.png and <file>_<n>.png (cleaned), plus <file>_sheet.png
showing the tries side by side. Pick one and copy it to assets/people/<file>.png.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate  # noqa: E402

OUT = os.path.join(generate.OUT, "cast")

PLAIN = "isolated on a plain flat dark grey background, no furniture"
SEATED = "upper body portrait, hands folded in front of him, front view, symmetrical, " + PLAIN
STANDING = "upper body portrait, (arms crossed over his chest:1.4), front view, symmetrical, " + PLAIN
COUNTER = "upper body portrait, hands clasped in front of him, front view, symmetrical, " + PLAIN
PRISONER = "a thin desperate prisoner in ragged dirty clothes, "
GUARD = "a menacing prison guard in a long dark coat and leather gloves, "
NO_FACE = ", no face visible, "
# Things the generator kept adding: bare faces, skulls, and a whole room around the person.
generate.NEGATIVE += (", table, desk, furniture, shelves, window, room, interior, bowl, cup, paper, laptop, "
                      "visible face, human face, bare face, nose, lips, skull, bald head, armor, full body, legs, cute, robot, cartoon")

# file name (as PixelArt looks it up) -> (seed, prompt)
CAST = {
    "rag_old": (301, PRISONER + "(his head and face completely wrapped in an old faded red t-shirt:1.5), (blue swimming goggles "
                            "over the cloth:1.3), knot at the side" + NO_FACE + "olive worn jacket, " + SEATED),
    "tape": (302, PRISONER + "(his head and face completely wrapped in grey duct tape like a mummy:1.5), only a thin dark gap "
                             "at the eyes" + NO_FACE + "dark hoodie, " + SEATED),
    "box_old": (303, PRISONER + "(a brown cardboard box worn over his whole head:1.5), two ragged eye holes, a crooked smile drawn "
                            "in black marker on the cardboard" + NO_FACE + "grey stained shirt, " + SEATED),
    "bag": (304, PRISONER + "(a brown paper grocery bag worn over his whole head:1.5), two torn eye holes" + NO_FACE
                 + "slate blue jacket, " + SEATED),
    "pillow_old": (305, PRISONER + "(a dirty white pillowcase pulled over his whole head:1.5), tied at the neck with string, a big "
                               "grin drawn on it in black marker" + NO_FACE + "dark red shirt, " + SEATED),
    "rag": (401, PRISONER + "(face completely hidden by an old red cloth wrapped round and round his head:1.6), (blue swimming "
                            "goggles over the cloth:1.3), (no skin visible on the head:1.4), olive worn jacket, " + SEATED),
    "box": (403, PRISONER + "grim and realistic, (a plain brown cardboard box worn over his head:1.6), two small ragged eye "
                            "holes cut in it, a crooked smile scrawled in black marker, (no face:1.4), torn grey shirt, " + SEATED),
    "pillow": (405, PRISONER + "(face completely hidden under a dirty white cloth pillowcase:1.6), (no face:1.5), a crude grin "
                               "drawn on the cloth in black marker, string tied at the neck, dark red shirt, " + SEATED),
    "you": (306, "a prisoner, (a white paper plate worn as a mask over his face:1.5), two small eye holes, tied with string, "
                 "a brown hooded cloak with the hood up" + NO_FACE + SEATED),
    "white_smile": (307, GUARD + "(a white guy fawkes mask:1.4) with a thin smile, curled black moustache and thin beard, "
                                 "dark suit and coat, " + STANDING),
    "goalie_guard": (310, GUARD + "(an old cracked off-white hockey goalie mask covering his face:1.5), rows of breathing holes, "
                                  "dark hood up" + NO_FACE + STANDING),
    "welder_guard": (311, GUARD + "(a dark steel welding helmet covering his whole head:1.5), a small rectangular green glass "
                                  "window" + NO_FACE + STANDING),
    "card_dealer": (312, "a mysterious card dealer in a dark brown hooded robe, (a plain white venetian bauta mask covering "
                         "the whole face:1.4)" + NO_FACE + COUNTER),
    "piece_dealer": (313, "a chess piece dealer in a tan leather apron over a dark shirt, (a black steel mesh fencing mask "
                          "covering the whole face:1.4)" + NO_FACE + COUNTER),
    "reseller": (314, "a shady reseller in a purple hooded coat, (a black knitted balaclava covering the face, only the eyes "
                      "showing:1.4)" + COUNTER),
}


def sheet(file, n):
    tiles = [Image.open(os.path.join(OUT, f"{file}_{i + 1}_raw.png")).resize((256, 256)) for i in range(n)]
    s = Image.new("RGB", (256 * n, 256))
    for i, t in enumerate(tiles):
        s.paste(t, (256 * i, 0))
    s.save(os.path.join(OUT, f"{file}_sheet.png"))


def main():
    names = sys.argv[1:] or list(CAST)
    os.makedirs(OUT, exist_ok=True)
    for file in names:
        seed, prompt = CAST[file]
        images = generate.generate(prompt, 4, seed)
        for i, data in enumerate(images):
            raw = os.path.join(OUT, f"{file}_{i + 1}_raw.png")
            with open(raw, "wb") as f:
                f.write(data)
            generate.clean(Image.open(raw)).save(os.path.join(OUT, f"{file}_{i + 1}.png"))
        sheet(file, len(images))
        print("done", file, flush=True)


if __name__ == "__main__":
    main()
