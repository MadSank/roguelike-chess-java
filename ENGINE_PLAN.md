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
tools/blender/         asset preparation only (export_godot.py writes glTF into godot/assets/)
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
- Masks: to be replaced by **museum 3D scans of real masks** (public domain: Smithsonian Open
  Access, other CC0 scans). The user must approve each download source.
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
- 2026-11-11: Godot 4.7.2 installed in `tools/godot`. `GameServer.java` written and smoke-tested
  (new / move / ai / state answer with valid JSON). Godot project not started yet.
