# Ashes: pure Java, storybook art drawn in code

Read this first if you are picking the work up (a later session, or another model).
Update the **Status** section at the end of every working session.

## Decision (2026-11-12)
The user chose to go back to **pure Java that opens and runs in BlueJ** (no Godot, no Blender at run
time, no image files the art depends on), with a **cartoon look**:
- **Art drawn in Java code** (Java2D shapes at run time), not pre-rendered pictures.
- Style: **dark storybook**, a creepy picture book: thick ink outlines that wobble slightly, muted
  palette (charcoal, bone, dried red, lamp ochre, cold blue-grey shadow), hatching for shade, one lamp.
  Think Don't Starve / Tim Burton, not realism.
- The Godot 3D version (`godot/`, `GameServer.java`, `play3d.cmd`, `ENGINE_PLAN.md`) is **kept but set
  aside**: nothing in the Java game depends on it. The Blender tools (`tools/blender/`) and the
  pre-rendered clips (`resources/scenes/`, `resources/board/`) are no longer used by the game.

Carry over from the 3D work (keep the ideas, redraw them): the cellar with one lamp; the opponent
seated across the board, five of them cycling by level; the three dealers around your seat (card
dealer left, piece dealer right, reseller behind right); masks real people wear, covering the whole
face: gas mask, plague doctor (beak, goggles, hat), hockey goalie mask, burlap sack, welding helmet;
dealers: Venetian bauta, fencing mask, balaclava; reactions (think, capture, hurt, check, win, lose;
dealers nod / shake); the lights-out swap after a win; the checkmate lunge; the shop as turning
between dealers with one panel beside each.

## Rules
- Plain `javac *.java`, default package, flat folder, BlueJ-openable, no libraries. Tests stay
  plain `Test*.java` classes (`.\test.cmd`). `DESIGN.md` is the rulebook.
- The user's terminal is PowerShell 5.1. Don't paste code into chat; write files and summarise.

## Architecture (planned)
- `Ink.java`: the drawing kit. Palette, wobbly ink outlines (hand-drawn look, lines "boil" a few times
  a second), hatching, soft lamp light, paper grain.
- `Figures.java`: the people: a hooded robed body, hands, and the eight masks; poses and live animation
  (breathing, sway, head tilt, reactions) as plain functions of time.
- `Cellar.java`: the room and the board seen from the seat; pieces drawn as ink silhouettes.
- The existing Swing screens (`BoardViewSwing`, `ShopScene`, `MenuSwing`, `IntroScene`, `CutScene`)
  switch from playing clips (`ScenePlayer`, `BoardArt`) to calling these.

## Status
(newest first)
- 2026-11-12: **look prototype done, waiting for the user's verdict.** `Ink.java` (palette, wobbly
  "boiling" ink lines, hatching, glow, darkness, paper grain), `Figures.java` (seated opponent and
  standing dealer, hooded robes, hands, all eight masks, breathing / sway / tilt and the reactions
  think, capture, hurt, check, win, lose, yes, no, lunge), `Cellar.java` (brick wall, his chair, lamp
  and light cone, round table, board in true perspective with `cellAt()` for clicks, pieces as ink
  silhouettes), `ArtPreview.java` (`java ArtPreview` opens a live window, click to cycle the eight
  characters; `java ArtPreview out.png N` saves a picture). Sheet: `storybook_preview.png` (ignored).
  Nothing in the game uses these yet; `ScenePlayer` / `BoardArt` still drive the screens.
  Next if approved: replace the clip-based backdrops in BoardViewSwing (board + portrait), ShopScene
  (dealer views), MenuSwing, IntroScene and CutScene with these drawings; then retire ScenePlayer,
  SceneClip, FootageLook and BoardArt from the game path.
- 2026-11-12: decision made; plan written.
