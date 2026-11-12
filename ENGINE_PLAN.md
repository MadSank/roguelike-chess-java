# Ashes in a real 3D engine: plan and handover

Read this first if you are picking the work up (a later session, or another model).
Update the **Status** section at the end of every working session.

## Why
On 2026-11-11 the pre-rendered look (Blender clips played by Swing) was rejected: the characters
read as props, the code-built masks looked comic, and the interface (Swing panels, Windows pop-ups)
looked plain. Decision: **move everything visual to a real-time 3D engine, and rebuild the whole
interface there.** Reference for the target feel: the horror game *Amenti* (dark, first person,
camera-footage look) and *Buckshot Roulette* (one table, one lamp, fixed seat).

Rejected, do not re-propose: more Blender pre-render polish; masks sculpted in code.

## Architecture
```
godot/                 Godot 4.7 project (GDScript, text scenes) : everything you see
  scripts/rules.gd       starts the Java server, sends commands, parses the JSON state
  ...
GameServer.java        headless Java process: one command per line in, one JSON line out
RunController, ChessEngine, AIPlayer, ShopManager, cards...   the rules, unchanged
tools/blender/         asset preparation only (export_godot.py: room and pieces; export_people.py:
                       characters; both write glTF into godot/assets/)
tools/godot/           the Godot binary (local, ignored by git)
```
- **The rules stay in Java.** They have 192 passing checks (`.\test.cmd`) and stay BlueJ-openable.
  Never re-implement a rule in GDScript: ask the server. `DESIGN.md` is still the rulebook.
- **Protocol** (see the comment at the top of `GameServer.java`): commands `new NAME`, `state`,
  `move N` (index into `legal`), `ai`, `play N`, `finish`, `buy TYPE`, `sell TYPE`, `buycard N`,
  `sellcard N`, `openpack`, `draft N`, `sit N,N,..`, `abandon`, `menu`, `quit`. Every answer is the
  whole state: `phase` (MENU / PLAYING / SHOP / GAME_OVER), `board` (8x8 of "", "wP", "bK"...; row 0
  is Black's back rank), `legal`, `hand`, `shop`, `summary`, `notes`, `ok` / `error`.
  `ai` blocks while the AI thinks (up to 4 s): call it from a thread.
- The old Swing game (`RoguelikeChessAppSwing`, `.\run.cmd`) still works and is the fallback until
  the Godot version covers the whole run. Then the Swing presentation classes can be retired.

## Running things
- Godot binary: `tools\godot\Godot_v4.7.2-stable_win64_console.exe` (download: github.com/godotengine/godot
  releases, `Godot_v4.7.2-stable_win64.exe.zip`).
- Play: `.\play3d.cmd` (compiles the Java into `out\`, then opens the Godot project).
- Import assets after changing them: `<godot> --headless --path godot --import`.
- Screenshot for checking work without a person: `<godot> --path godot -- --shot out.png [--state NAME]`
  (handled in `scripts/main.gd`; it renders a few frames, saves the picture, quits).
- Blender (5.2) asset export: `blender -b --factory-startup -P tools\blender\export_godot.py`.
  Blender Python lives at `...\Blender 5.2\5.2\python\bin\python.exe` (run it with `-I`).
- The user's terminal is PowerShell 5.1 (no `&&`). Don't paste code into chat: write files, summarise.

## Assets (all free / CC0, all local, none in git)
- Poly Haven models and textures in `tools/blender/assets/` (`fetch_assets.py`): the round table,
  the throne chair, chess set, lamp, candles, crates, stove, brick and concrete.
- People: MPFB 2 (MakeHuman) bodies with skin, eyes, hair (`realistic.py`, `make_characters.py`,
  `tools/blender/assets/characters.blend`). Known bug there: opponents 2 and 5 use MPFB age 0.3,
  which is a child; young adults are 0.5.
- Masks: full-face only, of kinds real people wear (see Status; `worn_masks.py`, `fit_masks.py`).
- Character quality is the weak point: an engine improves light, camera and motion, not the models.
  Keep figures in shadow; light hands and masks. If the user finds or buys better characters, use them.

## Look
Darkness first: one hanging lamp over the table, almost everything else black; volumetric fog;
warm lamp against cold fill; film grain, chromatic aberration, vignette (one post-process shader).
First person from the player's seat, with mouse look limited to a cone; lean in over the board to play.

## Milestones
1. **Playable room** : room, table, lamp, 3D board and pieces driven by the Java rules (click to
   move, opponent replies), one seated opponent, in-engine HUD (gold, level, hand of cards, notes),
   round summary. No shop yet (after a round, the old flow is skipped with a simple continue).
2. **Whole run** : menu, name entry, the three dealers as places you turn to, shop interface,
   hand picking, game over, leaderboard. Parity with the Swing game; then retire Swing presentation.
3. **Horror pass** : scanned masks, opponent reactions (think / capture / check / win / lose),
   opponent swap in the dark, checkmate lunge, intro walk, sound.
4. **Polish** : settings (resolution, look intensity), export to a standalone .exe that bundles
   the Java classes, performance pass.

## Status
(newest first)
- 2026-11-12 (dealers): **the three dealers are in Godot, masked, standing round the seat.**
  - Masks (`worn_masks.py`): card dealer a Venetian bauta, piece dealer a fencing mask, reseller a
    balaclava. `fit_masks.py` now covers all eight characters; `characters.blend` is fitted.
  - `export_people.py` exports the dealers too (idle + yes / no; no hand IK for standing figures):
    `card_dealer.glb`, `piece_dealer.glb`, `reseller.glb`.
  - `main.gd`: `DEALERS` places them (card left, piece right, reseller behind right), each with a small
    light; Left / Right arrows turn your head between card / table / piece / resell when not in a round;
    a shop command turns you to that dealer, who nods (yes) or shakes his head (no).
  - Checked: one screenshot facing each dealer; `--auto 1` still runs clean. Not checked: the nod /
    shake in motion; the shop panel still covers the middle of the screen, so the dealer is mostly
    hidden behind it. **Next for milestone 2: split the shop panel into one small panel per dealer,
    shown beside him when you face him** (replace `Hud.show_shop` with per-view panels).
- 2026-11-12 (masks, final direction): the user wants **masks real people wear** (gas mask, plague
  doctor, Anonymous-style, etc.), full-face. The marble / bull / lion scans were replaced.
  - Opponent 1: the scanned Poly Haven gas mask. Opponents 2-5: `tools/blender/worn_masks.py` builds a
    plague doctor's leather hood with beak, goggles and hat; a scuffed hockey goalie mask; a burlap
    sack tied with rope; a welder's helmet. Leather and burlap use the Poly Haven photo textures;
    painted surfaces get a generated scuffed texture. `fit_masks.py` `MASK_FOR` maps who wears what.
  - Whole-head masks (gas, plague, sack) remove the wearer's hair and get a whole-head black cowl.
  - The dealers still wear the enlarged theatre mask.
  - Seen: close-up previews (front and side) and each opponent seated in Godot. Not seen in motion.
  - Not built: the Guy Fawkes ("Anonymous") mask. That exact design is owned by Warner Bros (V for
    Vendetta); offer a generic smiling moustached carnival mask instead if the user asks again.
  More real-world mask ideas if wanted: Venetian bauta or volto, fencing mask, balaclava, clown
  mask, kendo men, WW1 splatter mask.
- 2026-11-12 (later): **masks replaced.** The user wants full-face masks only ("we can't make the human
  face look real": no part of a face may show). Skull scans were tried and rejected (they leave the face
  visible); don't re-propose them (`fit_skulls.py` and `assets/characters_skulls.blend` are leftovers).
  - `tools/blender/fit_masks.py` edits `characters.blend` in place: opponents 1-4 wear real scanned
    objects from Poly Haven (old gas mask, marble bust face, bronze bull head, bronze lion head; added
    to `fetch_assets.py`), opponent 5 and the three dealers keep the theatre mask, enlarged; everyone
    gets a matte black cowl under the mask so eye holes and rim gaps show only black.
    `assets/characters_theatre_masks.blend` is the library before fitting. After `make_characters.py`
    rebuilds anyone, run `fit_masks.py` again, then `export_people.py`.
  - `export_people.py` now takes the texture actually wired to Base Color / Normal (scanned objects
    keep their photo textures) and marks scanned metal as metallic.
  - All five re-exported and seen seated in Godot (one screenshot each from the seat). Not checked:
    close up in Godot, in motion, or whether the cowl ever shows through a mask while he moves.
  Ideas not done: a fifth scanned mask for opponent 5 (Poly Haven `gothic_statue` face, or a second
  marble variant), scanned masks for the dealers.
- 2026-11-12: **all five opponents are in Godot with reactions, and a whole run plays inside the engine.**
  - `export_people.py` exports idle + think / capture / hurt / check / win / lose (one NLA track each);
    `opponent1..5.glb` exist (20-26 MB each). Opponents 2 and 5 were rebuilt as adults (age 0.5).
    `make_characters.py -- name,name` now rebuilds only those and keeps the rest of the library.
  - `main.gd`: seats `state.opponent` at the start of each round; he loops "think" while the AI works,
    plays hurt / capture / check after moves and win / lose before the summary, then settles back.
  - `--auto N` (in `main.gd`) plays random moves and clicks through summary and shop: 3 rounds ran clean
    (menu -> play -> summary -> shop -> sit -> play, and game over -> menu -> new run).
  - Checked by screenshot: each opponent seated; opponent 1 in the "lose" pose. **Not watched in motion
    by a person yet**; reaction timing and blending are untested by eye.
  Next: hand picking, promotion and snipe choices (item 3); card overlays (item 4); then milestone 2
  (dealers as places, leaderboard) and milestone 3 (scanned masks, swap in the dark, lunge, intro, sound).
  Masks are still the code-built theatre masks, which the user dislikes: replacing them needs the user
  to approve a scan source.
- 2026-11-11 (evening): **opponent 1 sits in the chair in Godot**, skinned, with a looping idle
  (`tools/blender/export_people.py -- opponent1` -> `godot/assets/opponent1.glb`, 25 MB;
  `main.gd` `_seat_opponent(n)` loads `opponentN.glb` if it exists). Seen in one screenshot from the
  seat; **the idle animation has not been watched in motion**, and only opponent 1 is exported.
  `TestServer.java` (13 checks, in `test.cmd`: 205 checks total) drives a whole run through the server.
  Summary and shop panels checked with sample data (`--shot x.png --panel summary|shop|none`).
  Done from the list below: items 1 (server side) and 2 (opponent 1). Still open: a full round played
  inside Godot; exporting opponents 2-5 (fix ages first) and seating `state.opponent`; reactions as
  animations (bake `realistic.react` actions in `export_people.py`, play them from `main.gd`); items 3-5.
- 2026-11-11 (later): **first playable 3D build works.** `.\play3d.cmd` opens the Godot project: the lit
  cellar (room.glb), the 3D board and pieces (pieces.glb), click-to-move through the Java rules with the
  AI replying, the new menu, HUD (level, material, gold, hand of cards, notes), round summary and a basic
  shop panel (buy / sell pieces and cards, mystery pack, sit down). Post-process shader (fringing, grain,
  scanlines, vignette, glitch) is on.
  Files: `godot/project.godot`, `main.tscn`, `scripts/{rules,main,board,hud}.gd`, `shaders/post.gdshader`,
  `tools/blender/export_godot.py`, `GameServer.java`, `play3d.cmd`.
  Checked by screenshot only (menu, and one move with the AI's reply); **not yet played through a whole
  round, summary or shop by hand or by script.**
  **Next, in order:**
  1. Play a full round by script (extend `--demo` in `main.gd`) to exercise summary -> shop -> sit; fix what breaks.
  2. The opponent in the chair: export one person from `characters.blend` as a skinned glTF with an idle
     animation. Steps that are needed (none done): delete MPFB helper geometry and apply shape keys on the
     body; make the settled pose the rest pose; give the cloth skin weights from the body (Data Transfer)
     instead of Surface Deform; replace procedural materials with plain image-textured ones (skin: the
     MPFB diffuse image; fabrics: pre-tint the Poly Haven texture into a PNG; mask: bake or use a scan);
     bake the idle (IK included) by sampling. Fix the age bug (0.3 -> 0.5) when rebuilding.
  3. Hand picking (now: the first cards of the stash are taken automatically), promotion choice (now:
     always a queen) and snipe-versus-capture choice (now: always the snipe).
  4. Card overlays on the board (mines, stunned pieces) : the state JSON does not report them yet.
  5. Then milestone 2 (dealers as places you turn to, leaderboard screen) and 3 (masks from museum scans,
     reactions, swap, lunge, intro, sound).
  Known rough edges: `room.glb` is 47 MB (2k textures; fine locally); the board model is turned in
  `main.gd` so a1 is dark (set `rot_z=0` in `export_godot.py` and drop that line at the next export);
  mouse look is not implemented (right click toggles sitting back / leaning in).
- 2026-11-11: Godot 4.7.2 installed in `tools/godot`. `GameServer.java` written and smoke-tested
  (new / move / ai / state answer with valid JSON).
