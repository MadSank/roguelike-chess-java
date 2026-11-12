# Ashes: pure Java, Undertale-style pixel art drawn in code

Read this first if you are picking the work up (a later session, or another model).
Update the **Status** section at the end of every working session.

## Decision (2026-11-12)
**Pure Java that opens and runs in BlueJ**, no Godot, no Blender, no image or sound files.
The look is **like Undertale** (original art, nothing copied from it), all three parts the user asked for:
1. **Pixel look:** a 320 x 240 canvas drawn with hard pixels and blown up whole; black backgrounds;
   white-bordered text boxes; a hand-made 5 x 7 pixel font.
2. **Talking:** the masked characters speak in typewriter text boxes ("* ..." narration and quoted
   lines), each letter with a little square-wave voice blip at that character's pitch.
3. **Battle feel:** facing the opponent he is shown big above a text box, with four choices along the
   bottom (PLAY, CARDS, TALK, YIELD) and a small pawn as the cursor; choosing PLAY shows the board in a
   box under him. The shop is the same layout with a dealer behind his counter and < > to turn.
Superseded the same day: a "dark storybook" look drawn in Java2D (`Ink.java`, `Figures.java`,
`Cellar.java`, `ArtPreview.java` remain but are unused; delete them once the pixel game is done).
The Godot 3D version (`godot/`, `GameServer.java`, `play3d.cmd`, `ENGINE_PLAN.md`) is kept, set aside.

Carry over: the cellar with one lamp; five opponents cycling by level, masks real people wear covering
the whole face (gas mask, plague doctor, hockey goalie mask, burlap sack, welding helmet); dealers
(Venetian bauta, fencing mask, balaclava); reactions (think, capture, hurt, check, win, lose; dealers
nod / shake); lights-out swap after a win; checkmate lunge; turning between dealers in the shop.
Don't copy Undertale's characters, its heart cursor, or its FIGHT/ACT/ITEM/MERCY labels.

## Rules
- Plain `javac *.java`, default package, flat folder, BlueJ-openable, no libraries. Tests stay
  plain `Test*.java` classes (`.\test.cmd`). `DESIGN.md` is the rulebook.
- The user's terminal is PowerShell 5.1. Don't paste code into chat; write files and summarise.

## Architecture
- `Px.java`: the pixel kit. 320 x 240 canvas (`canvas`, `pen`, `present` scales to the window with whole
  pixels, `toCanvas` maps mouse clicks back), palette, the 5 x 7 font (`text`, `centred`, `wrap` with a
  letter count for typewriter text), `box` (white-bordered), `Talk` (typewriter line with voice blips),
  `blip` (Java Sound square wave; silent if there is no audio device).
- `PixelArt.java`: sprites drawn in code. `person(who, t, reaction, p)` returns an 80 x 72 upper-body
  image (blow it up 2x for the big view); the eight `Person`s; `piece(g, code, x, y)` 10-pixel pieces.
- `PixelPreview.java`: `java PixelPreview` (live window: click = next screen, key = next character) or
  `java PixelPreview out.png SCREEN WHO` (1 facing him, 2 the board, 3 a dealer).
- To do: the real screens use these (see Status).

## Status
(newest first)
- 2026-11-12 (pixel): **pixel prototype done, waiting for the user's verdict.** All eight characters,
  the facing-him screen, the board screen and a dealer screen render (`pixel_preview.png`, ignored).
  Nothing in the game uses them yet. Next if approved: rebuild the game's screens on a single pixel
  panel: menu (title + name entry in a text box), facing-him / board (PLAY shows the board, CARDS the
  hand, TALK a line from him, YIELD gives up the round's hopes = abandon run with a confirm), summary as
  dialogue, shop (turn between dealers, buy / sell in a menu box), lights-out swap, checkmate lunge, a
  short intro. Window 960 x 720 (3x). Dialogue lines per character in a new `Lines.java`. Then retire
  ScenePlayer, SceneClip, FootageLook, BoardArt, IntroScene/CutScene clip playback and the storybook files.
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
