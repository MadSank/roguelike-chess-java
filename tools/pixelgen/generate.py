"""
Free, local pixel-art generation for Ashes, on your own GPU.

Talks to a ComfyUI server running on this PC (http://127.0.0.1:8188) with Stable Diffusion XL and the
pixel-art-xl LoRA, then cleans each picture up for the game:
  1. shrink it 8x onto a true pixel grid (the LoRA draws "pixels" about 8 screen pixels wide),
  2. cut out the flat background colour around the figure (transparent PNG),
  3. limit the colours to a small palette, like hand-made pixel art.

Usage (from C:\\Ashes):
  python tools/pixelgen/generate.py "<what to draw>" <name> [count] [seed]
Outputs go to tools/pixelgen/out/: <name>_<n>_raw.png (as generated) and <name>_<n>.png (game-ready).

Needs only the Python standard library plus Pillow. Licences: SDXL base 1.0 is CreativeML OpenRAIL++-M,
pixel-art-xl is CreativeML OpenRAIL-M; both allow use in a game, with their use-based restrictions.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

SERVER = "http://127.0.0.1:8188"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")

STYLE = ("pixel art, front view, waist-up portrait, dark horror, dystopian, warm orange lamplight from the upper left, "
         "deep purple shadows, muted colours, highly detailed, simple plain flat dark grey background")
NEGATIVE = ("blurry, photo, photograph, 3d render, text, watermark, signature, frame, border, extra arms, extra hands, "
            "deformed, cropped head, cartoon, chibi, cute, anime, multiple people, gradient background, scenery, circle, vignette, hands in pockets, green")


def workflow(prompt, seed, count, width=1024, height=1024, style=None):
    return {
        "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "sd_xl_base_1.0.safetensors"}},
        "10": {"class_type": "LoraLoader", "inputs": {"model": ["4", 0], "clip": ["4", 1], "lora_name": "pixel-art-xl.safetensors",
                                                      "strength_model": 1.0, "strength_clip": 1.0}},
        "6": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt + ", " + (STYLE if style is None else style), "clip": ["10", 1]}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"text": NEGATIVE, "clip": ["10", 1]}},
        "5": {"class_type": "EmptyLatentImage", "inputs": {"width": width, "height": height, "batch_size": count}},
        "3": {"class_type": "KSampler", "inputs": {"seed": seed, "steps": 30, "cfg": 6.0, "sampler_name": "dpmpp_2m",
                                                   "scheduler": "karras", "denoise": 1.0, "model": ["10", 0],
                                                   "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["3", 0], "vae": ["4", 2]}},
        "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "ashes", "images": ["8", 0]}},
    }


def post(path, data):
    req = urllib.request.Request(SERVER + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())


def get(path):
    with urllib.request.urlopen(SERVER + path) as r:
        return r.read()


def generate(prompt, count, seed, width=1024, height=1024, style=None):
    pid = post("/prompt", {"prompt": workflow(prompt, seed, count, width, height, style)})["prompt_id"]
    while True:
        hist = json.loads(get("/history/" + pid))
        if pid in hist and hist[pid].get("outputs"):
            break
        time.sleep(1.5)
    images = []
    for im in hist[pid]["outputs"]["9"]["images"]:
        q = urllib.parse.urlencode({"filename": im["filename"], "subfolder": im["subfolder"], "type": im["type"]})
        images.append(get("/view?" + q))
    return images


def clean(raw, grid=8, colours=32, tol=60):
    """Shrink to the pixel grid, flood away the backdrop from the edges, cut the palette down.
    First tries the backdrop's single main colour; if that leaves almost everything (a shaded or framed
    backdrop), tries again with all of the border's main tones at a tighter tolerance."""
    w, h = raw.size
    small = raw.convert("RGB").resize((w // grid, h // grid), Image.NEAREST)
    W, H = small.size
    px = small.load()
    border = [px[x, y] for x in range(W) for y in (0, 1, H - 2, H - 1)] + [px[x, y] for y in range(H) for x in (0, 1, W - 2, W - 1)]

    def flood(tones, tol):
        gone = [[False] * H for _ in range(W)]
        stack = [(x, y) for x in range(W) for y in (0, H - 1)] + [(x, y) for y in range(H) for x in (0, W - 1)]
        while stack:
            x, y = stack.pop()
            if x < 0 or y < 0 or x >= W or y >= H or gone[x][y]:
                continue
            c = px[x, y]
            if not any(abs(c[0] - t[0]) + abs(c[1] - t[1]) + abs(c[2] - t[2]) < tol for t in tones):
                continue
            gone[x][y] = True
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        return gone

    main_tone = max(set(border), key=border.count)
    gone = flood([main_tone], tol)
    kept = sum(1 for x in range(W) for y in range(H) if not gone[x][y]) / (W * H)
    if kept > 0.85:
        groups = {}
        for c in border:
            groups.setdefault((c[0] // 24, c[1] // 24, c[2] // 24), []).append(c)
        tones = [tuple(sum(v[i] for v in g) // len(g) for i in range(3)) for g in groups.values() if len(g) > len(border) * 0.1]
        gone = flood(tones, 45)
    specks = [(x, y) for x in range(W) for y in range(H) if not gone[x][y] and
              sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= x + dx < W and 0 <= y + dy < H and gone[x + dx][y + dy]) >= 4]
    for x, y in specks:                              # lone leftover pixels only, decided all at once
        gone[x][y] = True
    quant = small.quantize(colors=colours, method=Image.Quantize.MEDIANCUT).convert("RGB")
    out = Image.new("RGBA", small.size)
    for y in range(H):
        for x in range(W):
            out.putpixel((x, y), (0, 0, 0, 0) if gone[x][y] else quant.getpixel((x, y)) + (255,))
    bbox = out.getbbox()
    return out.crop(bbox) if bbox else out


def upload(path):
    """Sends a local image to ComfyUI's input folder; returns the name it is stored under."""
    boundary = "----ashes" + str(int(time.time() * 1000))
    crlf = "\r\n"
    with open(path, "rb") as f:
        data = f.read()
    head = ("--" + boundary + crlf + 'Content-Disposition: form-data; name="image"; filename="' + os.path.basename(path) + '"'
            + crlf + "Content-Type: image/png" + crlf + crlf)
    tail = (crlf + "--" + boundary + crlf + 'Content-Disposition: form-data; name="overwrite"' + crlf + crlf + "true"
            + crlf + "--" + boundary + "--" + crlf)
    body = head.encode() + data + tail.encode()
    req = urllib.request.Request(SERVER + "/upload/image", data=body, headers={"Content-Type": "multipart/form-data; boundary=" + boundary})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["name"]


def restyle(prompt, image_path, count, seed, denoise=0.6):
    """Image-to-image: repaints an existing picture (e.g. a code-drawn sprite, enlarged) in the generator's style,
    keeping its composition."""
    name = upload(image_path)
    wf = workflow(prompt, seed, 1)
    wf["11"] = {"class_type": "LoadImage", "inputs": {"image": name}}
    wf["12"] = {"class_type": "VAEEncode", "inputs": {"pixels": ["11", 0], "vae": ["4", 2]}}
    wf["13"] = {"class_type": "RepeatLatentBatch", "inputs": {"samples": ["12", 0], "amount": count}}
    wf["3"]["inputs"]["latent_image"] = ["13", 0]
    wf["3"]["inputs"]["denoise"] = denoise
    del wf["5"]
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    while True:
        hist = json.loads(get("/history/" + pid))
        if pid in hist and hist[pid].get("outputs"):
            break
        time.sleep(1.5)
    out = []
    for im in hist[pid]["outputs"]["9"]["images"]:
        q = urllib.parse.urlencode({"filename": im["filename"], "subfolder": im["subfolder"], "type": im["type"]})
        out.append(get("/view?" + q))
    return out


def main():
    prompt, name = sys.argv[1], sys.argv[2]
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 2
    seed = int(sys.argv[4]) if len(sys.argv) > 4 else 1234
    os.makedirs(OUT, exist_ok=True)
    t0 = time.time()
    for i, data in enumerate(generate(prompt, count, seed)):
        raw_path = os.path.join(OUT, f"{name}_{i + 1}_raw.png")
        with open(raw_path, "wb") as f:
            f.write(data)
        clean(Image.open(raw_path)).save(os.path.join(OUT, f"{name}_{i + 1}.png"))
    print(f"{count} image(s) in {time.time() - t0:.0f} s -> {OUT}")


if __name__ == "__main__":
    main()
