# Ashes: pure Java, Undertale-style pixel art drawn in code

Read this first if you are picking the work up (a later session, or another model).
Update the **Status** section at the end of every working session.

## Decision (2026-11-12)
**Pure Java that opens and runs in BlueJ**, no Godot, no Blender, no image or sound files.
The look is **like Undertale** (original art, nothing copied from it), all three parts the user asked for:
1. **Pixel look:** a 480 x 360 canvas drawn with hard pixels and blown up whole (2x, 960 x 720) with
   faint scanlines; a hand-made 5 x 7 pixel font. Quality bar (user's reference image, 2026-11-12):
   painterly pixel art lit by a warm lamp, shadows leaning purple, highlights leaning orange, warm rim
   light, dithered tone clusters instead of black outlines.
2. **Talking:** the masked characters speak in typewriter text boxes ("* ..." narration and quoted
   lines), each letter with a little square-wave voice blip at that character's pitch.
3. **Battle feel:** facing the opponent he is shown big above a text box, with four choices along the
   bottom; choosing PLAY shows the board on the table under him. The shop is the same layout with a
   dealer behind his counter and < > to turn.
Superseded the same day: a "dark storybook" look drawn in Java2D (`Ink.java`, `Figures.java`,
`Cellar.java`, `ArtPreview.java` remain but are unused; delete them once the pixel game is done).
The Godot 3D version (`godot/`, `GameServer.java`, `play3d.cmd`, `ENGINE_PLAN.md`) is kept, set aside.

Carry over: the cellar with one lamp; five opponents cycling by level, masks real people wear covering
the whole face (gas mask, plague doctor, hockey goalie mask, burlap sack, welding helmet); dealers
(Venetian bauta, fencing mask, balaclava); reactions (think, capture, hurt, check, win, lose; dealers
nod / shake); lights-out swap after a win; checkmate lunge; turning between dealers in the shop.
Don't copy Undertale's characters, its heart cursor, or its FIGHT/ACT/ITEM/MERCY labels.

## Direction change (2026-11-13): the megacity, prisoners and guards
The user gave a concept image (a huge, hazy, overcrowded city of towers under a shaft of light, cables,
neon, flying traffic) and asked for:
- **An opening on launch** (`Opening.java`, timed by the "opening" song): a bathroom where you see only
  tiles and hear someone wash; he opens the door and the camera follows him from the side across a
  skywalk through the rotting city (credits hidden on neon signs: A GAME BY MADDOX, SAMBAR BOY, and
  the ASHES title); his footsteps turn into the music; at the far door he puts on his mask, enters the
  game room, takes his place among the guards; the camera turns to you and moves in; then the menu.
  Any key or click skips it.
- **Who wears what:** the players are prisoners forced to play, in makeshift masks: opponents The Rag
  (t-shirt and swim goggles), The Tape (duct tape), The Box (cardboard, marker smile), The Bag (paper
  bag), The Pillow (pillowcase with a drawn face); you wear a paper plate (`PixelArt.PLAYER`). The guards
  standing round the table wear real masks: the walker is The White Smile (a white smiling mask with a
  curled moustache, the user's "anonymous mask", drawn as our own take), then gas mask, plague doctor,
  hockey, welding (`PixelArt.GUARDS`, standing with folded arms). Two guards stand in the dark either
  side of every table screen. The dealers (bauta, fencing, balaclava) are guards who sell.
- **Music:** the table music continues straight out of the opening (same key and tempo; its kick on the
  third beat is the walk's heartbeat). The menu, name and table screens all use it now.

## Rules
- Plain `javac *.java`, default package, flat folder, BlueJ-openable, no libraries. Tests stay
  plain `Test*.java` classes (`.\test.cmd`). `DESIGN.md` is the rulebook.
- The user's terminal is PowerShell 5.1. Don't paste code into chat; write files and summarise.

**Inspired by Undertale, not a copy of it** (the user's correction). Ashes' own look:
- a lamp-lit brick cellar behind everything (not a black void), the light falling off in dither;
- text boxes are charred wood frames with brass corner rivets, the speaker's name on a brass plate;
  narration in ash grey, spoken lines in cream inside quotes (no "*" narration prefix);
- choices are a row of playing cards (PLAY, CARDS, TALK, LEAVE; in the shop BUY, SELL, TALK, BACK);
  the chosen card rises with a lit candle on it;
- status in the top-left corner as icons: a coin (gold), a skull (level), a pawn (army / requirement).

## Architecture
- `Px.java`: the pixel kit. 480 x 360 canvas (`present` adds faint scanlines; `cellar` caches a
  lit brick wall per lamp position: per-brick tone, cracks, missing bricks, dithered falloff) (`canvas`, `pen`, `present` scales to the window with whole
  pixels, `toCanvas` maps mouse clicks back), palette, the 5 x 7 font (`text`, `centred`, `wrap` with a
  letter count for typewriter text), `box` (white-bordered), `Talk` (typewriter line with voice blips),
  `blip` (Java Sound square wave; silent if there is no audio device).
- `Shade.java`: the shading engine. `part(img, shape, ramp, round)` rasterises a Java2D shape, builds a
  dome height field from a distance transform, lights it (lamp up-left-front), maps it onto a `ramp()`
  (purple shadows to orange lights) with Bayer dithering and a warm rim. `stroke` for hoses / ropes.
  Every sprite is built from these parts plus a few hand-placed pixels (eye glints, stitches, rivets).
- `PixelArt.java`: sprites drawn in code at 1x (never scaled, so all pixels match). `person(who, t,
  reaction, p)` returns a 150 x 140 upper-body image (peaked hood or bare, robe, sleeves, hands on the
  table, mask); the eight `Person`s; `piece(g, code, x, y)` turned wooden pieces 18 x 28, cached.
- `PixelPreview.java`: `java PixelPreview` (live window: click = next screen, key = next character) or
  `java PixelPreview out.png SCREEN WHO` (1 facing him, 2 the board, 3 a dealer).
- `PixelGame.java`: **the game.** One JPanel, a `Mode` per screen (MENU, SCORES, NAME, INTRO, FACE,
  BOARD, LUNGE, SUMMARY, SWAP, SHOP, DRAFT, HAND, CONFIRM, OVER), each with a `drawX` method. Every
  clickable thing adds a `Hit` while drawn; `choices()` draws the card row (keys: left / right, Enter).
  `speechBox()` shows `say(...)` lines. Board geometry: squares 22 x 18 at (152, 156). The AI runs in a
  SwingWorker as before. `java PixelGame out.png SCREEN` saves a picture of a screen (see its `main`).
- `Lines.java`: what everyone says (greet, talk, hurt, capture, check, win, lose, draw; dealers greet,
  talk, yes, no), as {narration, speech}.
- `Music.java`: **all music and sound, synthesized in Java** (no sound files). Songs are written as note
  strings (`seq(layer, instrument, velocity, beat, "D4/1 F4+A4/2 -/1")`) for synth instruments (organ, bass,
  music box, pulse lead, reed, bell, tremolo strings, heartbeat, clock ticks, boom). Each song is rendered
  once in the background with a cellar reverb, levelled, made seamless for looping, and streamed with the
  effects through one line. Songs: menu (D minor, music box over organ), table (E minor ostinato and
  heartbeat; a second layer of clock and strings fades in while you are in check, `Music.tension`),
  shop (A minor waltz), ambient (summary), over (dirge with a bell). Stings: mate, win, lightsout.
  Effects: move, capture, check, coin, deny, card, and the typewriter blips. `PixelGame.setMode` picks
  the song per screen. `java Music music_preview` writes everything as WAVs (folder ignored by git).
- `RoguelikeChessAppSwing.java`: the window (960 x 720) holding one PixelGame. BlueJ: run its `main`.

## Status
(newest first)
- 2026-11-13 (opening): **the new direction is in, waiting for the user's verdict.** `Opening.java`
  (bathroom tiles, city in parallax layers built once: sky shaft, three tower layers with floors of lit
  windows, neon glyph signs, credit signs, traffic, a barge, god rays, dust, the street and crowd below;
  the skywalk with booth, sleepers, broken vending machine, rubbish, puddle, drone, passers-by, the game
  room door; the walking guard drawn with Shade in 12 frames plus the mask going on; the room with the
  guard ring and your back; the reverse shot of you with a zoom). Music: "opening" song (water, scrub,
  tap, towel, latch, door samples placed on beats; city bed; one footstep a beat from beat 17 to 95, kick
  from 32, the table bass and key jingle from 48, organ and music box from 64, melody from 80; the room
  from 100), `Music.playThen("opening", "table")` chains into the table loop on beat 116;
  `Music.position` drives the pictures (`PixelGame.drawOpening`). Measured: steps land within 15 ms of
  the beat; opening starts about 1 s after launch. New masks and lines for the prisoners (`Lines`).
  Snapshot any moment: `java PixelGame out.png open <beat>`.
- 2026-11-12 (polish): the music was approved by ear ("Sounds good"). Added: pieces glide between squares
  with a small arc (knights hop higher), a dust puff where a piece is taken, an ember line for sniper
  shots, the move sound landing with the piece (`startSlide`, `snapshot`); a figure sitting in the dark on
  the menu, a different opponent each time the lamp stutters (`inTheDark`); a different checkmate lunge
  per opponent (gas fog, beak stab, cracked glass, flicker-closer, welding flare, each with its sound;
  `drawLunge`, `lungeWho`); footsteps down the stairs, a chair when you sit or stand, steps up the
  stairs when you give up, and a longer lights-out where you hear the beaten one leave and the next
  one arrive; drips and creaks in the cellar now and then (`Music.steps`, `Music.later`, ambient).
  Snapshots: `java PixelGame out.png slide|lunge0..4 <seconds>|menu`.
- 2026-11-12 (music): **music and sound effects done, waiting for the user's ears.** `Music.java` (see
  Architecture). Checked by measurement only (levels about -23 dBFS RMS with peaks near 0.3, no clipping,
  seamless loops, keys confirmed by chroma analysis; the check layer doubles the rhythmic activity).
  Analysis tools installed for this: Python mido, music21, librosa, soundfile (plus numpy / scipy).
  The music is not heard by Claude; adjust per the user's feedback (tempo, instruments, loudness).
- 2026-11-12 (conversion): **the whole game is now the pixel style.** `PixelGame` + `Lines` replace
  MenuSwing, BoardViewSwing, ShopScene, IntroScene, CutScene; ScenePlayer, SceneClip, FootageLook,
  BoardArt and the storybook files were deleted (in git history if ever wanted). Flow: menu, name,
  two-line intro, facing him (PLAY / CARDS / TALK / GIVE UP), board (hand on the right, click to play;
  promotion and Sniper choices as cards; Esc gives up), his reactions and lines on captures / check,
  checkmate lunge, summary ledger, lights-out swap after a win, shop with three dealers (< > to turn,
  wares panel, mystery-pack draft), choosing the hand, game over. Tests pass; every screen was checked
  as a picture; not yet click-tested through a whole run by the user. The music files (battle.wav,
  intro.wav) are missing, as before. Ideas next: footsteps / ambience made with Java Sound, a smoother
  piece slide animation, per-opponent lunge, menu backdrop with a figure in the dark.
- 2026-11-12 (quality): **quality upgrade done, waiting for the user's verdict.** Canvas 480 x 360,
  `Shade.java` added, all eight characters, the pieces, the wall, lamp, plank table and board redrawn in
  the reference style. Preview layouts rescaled. Next if approved: the conversion listed below (window
  960 x 720 at 2x, not 3x); board squares 22 x 18 at (152, 156) as in `PixelPreview` screen 2.
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
