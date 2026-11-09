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
2. **Hand**: pick up to **5 cards** from the stash to take into the round. Hand size can be upgraded at key levels.
3. **Play the round** under normal chess rules. Cards in hand can be played at any point, subject to each card's conditions.
4. **Result**

   | Result | Pieces | Cards played | Cards not played | Coins | Next |
   |---|---|---|---|---|---|
   | Win (checkmate AI) | survivors carry over | gone | back to stash | full payout | next level |
   | Draw | survivors carry over | gone | back to stash | small payout | replay same level |
   | Loss (checkmated) | only the King remains | gone | gone | none | replay same level |

5. **Game over**: when you cannot reach the level's requirement, even after selling everything.

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

### Current numbers (Economy.java / MoveBonuses.java, first pass, to be tuned)
- Win: 2 coins per point of your material left + 3 per point of enemy material defeated + bonuses + level base (10 + 2 × level).
- Draw: 30% of that. Loss: nothing.
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

## AI
- Difficulty comes from a per-level table (search depth, time budget, evaluation quality, blunder chance), not raw depth alone; practical search depth tops out around 6–8.
- The AI plans with plain chess rules: it does not understand the player's card effects. Its chosen move is always checked against the real rules.
- Current table (Difficulty.java): depth 1 at level 1 rising to 6 at level 14+, think time 0.5 s → 4 s, evaluation from material-only up to piece placement, capture search from level 5, and a chance of a random move (30% at level 1, 0% from level 6).

## Cards
- Cards are one-round consumables, played from the hand during a round.
- Card abilities may not capture or target a King (snipe, vault, etc.) for now.

## Leaderboard
- Ranked by the highest level beaten.

## Project constraints
- Builds with plain `javac *.java`, run with `java RoguelikeChessAppSwing`.
- Must stay openable in BlueJ: default package, flat folder, no Maven/Gradle.
- Tests are plain classes named `Test*.java` with a `main` method (no JUnit).
