"""
Downloads the Poly Haven assets the scenes use (all CC0: https://polyhaven.com/license)
into tools/blender/assets/ (ignored by git).

Run with Blender's bundled Python (no extra installs needed):
    "C:\\Program Files\\Blender Foundation\\Blender 5.2\\5.2\\python\\bin\\python.exe" -I tools\\blender\\fetch_assets.py

Files already downloaded are skipped, so it is safe to re-run after adding assets below.
"""
import json
import os
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")
API = "https://api.polyhaven.com/files/"
HEADERS = {"User-Agent": "AshesGame-asset-fetch"}

# Textures: id -> resolution. Maps fetched: colour, OpenGL normal, roughness.
TEXTURES = {
    "plaster_brick_01": "2k",
    "concrete_floor_worn_001": "2k",
    "brown_leather": "1k",
    "long_white_tiles": "1k",
    "dirty_tiles": "1k",
    # Fabrics for the realistic characters' robes, hoods and gloves.
    "caban": "2k",
    "hessian_230": "2k",
    "poly_wool_herringbone": "2k",
    "rough_linen": "2k",
    "knitted_fleece": "2k",
    "velour_velvet": "2k",
    "fabric_leather_02": "1k",
}
# Models: id -> resolution (glTF with its textures).
MODELS = {
    "round_wooden_table_01": "1k",
    "WoodenChair_01": "1k",
    "chess_set": "1k",
    "hanging_industrial_lamp": "1k",
    "wooden_crate_01": "1k",
    "Barrel_01": "1k",
    "wine_bottles_01": "1k",
    "brass_candleholders": "1k",
    "ornate_mirror_01": "1k",
    "mounted_fluorescent_lights": "1k",
    "barrel_stove": "1k",
    "wooden_bookshelf_worn": "1k",
    "old_military_crate": "1k",
}
TEXTURE_MAPS = {"Diffuse": "diff", "nor_gl": "nor_gl", "Rough": "rough"}


def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS)) as r:
        return json.loads(r.read().decode("utf-8"))


def download(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS)) as r, open(path, "wb") as f:
        f.write(r.read())
    return True


def fetch_texture(asset_id, res):
    files = get_json(API + asset_id)
    for api_name, short in TEXTURE_MAPS.items():
        entry = files.get(api_name, {}).get(res, {}).get("jpg")
        if not entry:
            print("  missing", api_name, "for", asset_id)
            continue
        path = os.path.join(ASSETS, "textures", asset_id, "%s_%s.jpg" % (asset_id, short))
        if download(entry["url"], path):
            print("  got", os.path.relpath(path, HERE))


def fetch_model(asset_id, res):
    files = get_json(API + asset_id)
    gltf = files.get("gltf", {}).get(res, {}).get("gltf")
    if not gltf:
        print("  no glTF at", res, "for", asset_id)
        return
    folder = os.path.join(ASSETS, "models", asset_id)
    if download(gltf["url"], os.path.join(folder, os.path.basename(gltf["url"]))):
        print("  got", asset_id)
    for rel, inc in gltf.get("include", {}).items():
        download(inc["url"], os.path.join(folder, rel))


def main():
    for asset_id, res in TEXTURES.items():
        print("texture", asset_id)
        fetch_texture(asset_id, res)
    for asset_id, res in MODELS.items():
        print("model", asset_id)
        fetch_model(asset_id, res)
    print("DONE", ASSETS)


main()
