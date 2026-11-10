# Ashes of the Board — Design Rules

This file is the agreed source of truth for how a run works. When code and this file disagree, this file wins (or gets updated on purpose).

## Core idea
A roguelike chess run against an AI that gets stronger every level. Cards give rule-bending tricks the AI does not understand, so they feel like exploits. The player is always White; the AI is always Black.

## Run loop
1. **Shop**
   - Your army is whatever survived the previous game; it is placed on the board for free.
   - Buy pieces until your army meets the level's **material requirement**. *Continue* is locked until it is met.
   - Buy cards into your **stash**.
   - Pieces and cards can be **sold back for half** their price.
2. **Hand**: pick up to **5 cards** from the stash to take into the round, **+1 every 3 levels** (6 at level 4, 7 at level 7, 8 at level 10, ...).
3. **Play the round** under normal chess rules. Like items in Buckshot Roulette, cards in hand are played on your turn, any number per turn, and **playing one does not use your move**. A played card stays active until the round ends.
4. **Result**

   | Result | Pieces | Cards played | Cards not played | Coins | Next |
   |---|---|---|---|---|---|
   | Win (checkmate AI) | survivors carry over | gone | back to stash | full payout | next level |
   | Draw | survivors carry over | gone | back to stash | small payout | replay same level |
   | Loss (checkmated) | only the King remains | gone | gone | none | replay same level |

5. **Game over**: when you cannot reach the level's requirement, even after selling everything. (A new run starts with 0 gold, so losing level 1 ends the run; this is intended.)

## Game end (normal chess rules, no move cap)
- Checkmate: win or loss.
- Draws: stalemate, 50-move rule, threefold repetition, insufficient material.
- Replaying a level keeps the same AI strength; the setup may change.

## Economy
- Material points: Pawn 1, Knight 3, Bishop 3, Rook 5, Queen 9 (King not counted).
- Win payout = A × (your material left on the board) + B × (enemy material defeated, including pieces destroyed by your cards) + good-move bonuses + level base. A and B to be tuned by simulation.
- Draw payout: a small fraction of the win formula.
- **Good-move bonuses**, each shown as a non-blocking "+N bonus" pop-up when the move is played:
  check, fork, capturing a more valuable piece with a cheaper one, promotion, en passant, fast mate, capture made with a card ability.

### Current numbers (Economy.java / MoveBonuses.java)
- **Win** = counted(your material left) × 1 + counted(enemy material defeated) × 1.5 + min(bonuses, 10 + 2 × level) + level base (15 + 3 × level) + interest.
  - *counted(x)*: material up to the level's requirement counts fully; anything above it counts half.
  - *Interest*: 1 coin per 10 gold banked, at most 5 per round (wins and draws). Savings are your insurance against a loss.
  - Why: a point of material costs 5 coins but earns at most 2.5 per level, so pieces pay back over a few levels instead of every level (the old formula let gold snowball past 1,000).
- **Draw**: 30% of the win formula, plus interest. **Loss**: nothing.
- Bonuses: check +2, fork +4, trade up +3, promotion +3, en passant +3, card capture +3, fast mate (within 15 of your moves) +10.
- Prices: 5 coins per point of material (Pawn 5, Knight/Bishop 15, Rook 25, Queen 45). Face-up cards cost 1.5× their base price; mystery pack 8 + 2 × level. Selling returns half (cards: half the base price).

## Army limits
- **Pawn cap** (limits buying only; you keep extra pawns if you drop under it):

  ```
  max pawns = min(8, 2 + 1 per Knight/Bishop/Rook + 2 per Queen)
  ```
  The 2 is for the King. Example: a full board (2N, 2B, 2R, 1Q) allows 2 + 6 + 2 = 10, capped at 8.
- **Material requirement** per level (draft, to be tuned by simulation):

  | Level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14+ |
  |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
  | Points | 2 | 4 | 6 | 9 | 12 | 15 | 18 | 21 | 24 | 27 | 30 | 33 | 36 | 39 |

- The AI mirrors the player's army.
- A new run starts with King + 2 Pawns.
- No limit on how many of a piece type you own (promotions would break one); only board space limits it (7 pieces besides the King on the back rank, 8 pawns).

## AI
- Difficulty comes from a per-level table (search depth, time budget, evaluation quality, blunder chance), not raw depth alone; practical search depth tops out around 6–8.
- The AI plans with plain chess rules: it does not understand the player's card effects. Its chosen move is always checked against the real rules.
- Current table (Difficulty.java): depth 1 at level 1 rising to 6 at level 14+, think time 0.5 s → 4 s, evaluation from material-only up to piece placement, capture search from level 5, and a chance of a random move (30% at level 1, 0% from level 6).

## Cards
- Cards are one-round consumables, played from the hand during a round (see Run loop). You can own several copies of a card, but each card can only be active once per round.
- **Local** cards help only the player. **Global** cards change the rules for both sides.
- Rarity: Common, Uncommon, Rare. The shop weights offers by rarity, and rares get more likely at higher levels. All cards live in CardCatalog.java (one line per card).
- Card abilities may not capture or target a King (snipe, vault, etc.) for now.
- 19 cards: the original 14 plus Pawn Surge, Pawn Retreat, Bishop Shift, King's Stride and Time Stop (rebuilt from the oldest version's powerups).

## Presentation (pre-rendered scenes)
- A cellar under one hanging lamp. The player sits at a table facing a masked opponent; three hooded dealers stand around the seat: the **card dealer** (left), the **piece dealer** (right) and the **reseller** (behind, right).
- New Game plays an intro (11 s, click to skip):
  - It fades in from black as the bathroom strip light stutters on. The camera drifts toward a fogged, cracked mirror (a shattered spot and a few long cracks; you can barely see anything in it) while the title "ASHES / of the board" fades in and out (drawn by the game, IntroScene).
  - You turn, walk down the corridor and into the room, and look at the card dealer and then the piece dealer. Every masked head turns to follow you.
  - You sit; the opponent lifts a hand toward the board; you lean over it.
- The shop is the three dealers: the camera turns between them (◀ ▶ or arrow keys) like Buckshot Roulette's fixed views; "Sit down" turns back to the table.
- Everything is rendered in Blender:
  - `tools/blender/fetch_assets.py` downloads the Poly Haven (CC0) textures and models into `tools/blender/assets/` (local only).
  - `tools/blender/make_characters.py` builds the eight characters once (about 20 minutes) into `tools/blender/assets/characters.blend` (local only). It needs the MPFB 2 add-on: `blender --online-mode -c extension install mpfb --enable`.
  - `tools/blender/build_scenes.py` builds the realistic cellar (brick, concrete, the round table and chess set, the hanging lamp, candles, crates, a barrel stove, a tiled bathroom with a mirror), loads the characters, and renders every clip at 1280×720 with Cycles and motion blur (several hours; `--draft` renders a fast EEVEE preview, `--only` picks clips). Shared helpers live in `scene_kit.py`; the theatre masks in `masks.py`.
  - `tools/blender/build_board.py` renders the board you play on: the real chess set on the table from one fixed, tilted orthographic camera (about 20 s). Output in `resources/board/`: the empty board, one sprite per piece (with its contact shadow) and `board.properties` (square positions and sprite anchors). Because the camera is orthographic and only tilted, every square is the same rectangle, so the game draws the live position by placing sprites on squares, back rank first; highlights, mines and stuns are drawn flat on the squares under the pieces (BoardArt + BoardViewSwing).
  - `tools/blender/piece_sprites.py` renders the old low-poly piece images, used only if the realistic board hasn't been rendered.
- Everyone is masked, opponents included (decided: it avoids facial animation). Masks are smiling or sad theatre masks, each with its own cracks and break.
- Characters (`tools/blender/realistic.py`): real human bodies from MPFB 2 (MakeHuman, CC0) with a full skeleton and fingers, in a long under-robe with bell sleeves, an open-front cloak and a deep hood. The clothes are simulated as cloth: each character is dressed standing with arms down, moves into its pose (seated at the table, hands clasped, or a hand held out) and the cloth falls and folds around it; the settled cloth is then bound to the body so it follows every later movement. Fabrics are Poly Haven textures (wool, herringbone, hessian, linen, velvet, fleece) with uneven fading, dust at the hem and fine fibre bumps; gloves are black leather.
- **Opponent roster**: five different masked opponents (build, mask, fabrics), one per level, cycling (`RunController.opponentFor`). **After you win**, the lamp stutters and dies on the beaten, slumped opponent; when it stutters back on, the next one is sitting in the chair (`swap_out_<n>` then `swap_in_<n>`, played by CutScene before the shop; click to skip).
- Motion: the idle is one slow 4-second breath (96 frames) with slower and quicker drifts of the head and weight layered on it, so it never reads as a loop. In reactions the head follows the chest a beat late. Seated opponents keep their hands planted on the table (IK). (`characters.py` is the older procedural figure; only its list of reaction moves is still used.)
- The dealer views have idle loops (`card_idle`, `piece_idle`, `resell_idle`) that the game plays whenever the camera rests on a dealer.
- **Reactions**. Each is a 2-second clip: the idle plus a move that starts and ends at rest. The game cross-fades into a reaction and back into the loop at the matching point of the breath.
  - Dealers: `<view>_yes` (two nods and a small bow) when a buy or sale goes through; `<view>_no` (a slow head shake) when it's refused or you click something you can't afford.
  - Opponent: a live close-up of the current opponent sits above your hand during play (`opponent<n>_idle`, rendered at half resolution). He bows his head and tilts it while the AI thinks (`think`, a full-length loop), leans in after taking your piece (`capture`), flinches when you take one of his (`hurt`), leans in and cocks his head when he puts you in check (`check`), sits back with a laugh when he mates you (`win`) and slumps when you mate him (`lose`). The round summary waits about 1.7 s so the win or lose reaction can be seen.
- The game streams frames from disk (SceneClip: small cache plus a background decoder), scales HD frames smoothly and adds fine grain, a light vignette and lamp flicker (ScenePlayer). Low-resolution frames still get the chunky look.
- **Smooth playback**: clips are rendered at 24 frames per second with motion blur, but the game redraws about 60 times per second. Playback follows the real clock, each redraw blends the two rendered frames around that instant, and every cut between clips is a short cross-fade (ScenePlayer).
- If the rendered files are missing, the game falls back to its plain look.
- **Rendered files stay local** (ignored by git): run the Blender script to make them. A fresh clone has no art until it is rendered.

### Direction: realistic world, hidden faces (decided)
- Move from PS1 placeholders to a realistic look: real textures and models (Poly Haven, CC0), physically based lighting from the single lamp, haze, depth of field, light film grain (no pixelation).
- **Shopkeepers are masked men.** The **opponent is masked for now**; an unmasked opponent whose face shows emotion is a *maybe* for later (it brings facial animation and uncanny-valley risk).
- The chessboard stays drawn by the game: a realistic board and pieces rendered from one fixed overhead angle so the live position sits inside the scene.

## Leaderboard
- Ranked by the highest level beaten.

## Project constraints
- Builds with plain `javac *.java`, run with `java RoguelikeChessAppSwing`.
- Must stay openable in BlueJ: default package, flat folder, no Maven/Gradle.
- Tests are plain classes named `Test*.java` with a `main` method (no JUnit).
