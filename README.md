# Ashes: Roguelike Chess

**Ashes** is a roguelike chess engine built in Java with Swing. It combines the deep tactical gameplay of traditional chess with the unpredictability and progression of roguelike deckbuilders. 

Survive waves of AI opponents, earn gold, and purchase new pieces and power-up cards to build your ultimate army!

---

## 📸 Screenshots

*(Add screenshots or GIFs of the gameplay here!)*
- `![Menu](placeholder)`
- `![Gameplay](placeholder)`
- `![Shop](placeholder)`

---

## ✨ Features

- **Roguelike Progression**: Play multiple rounds against increasingly difficult AI opponents. Earn gold based on your performance.
- **Dynamic Shop System**: Spend gold between rounds to recruit new pieces (Pawns, Knights, Bishops, Rooks, Queens) for your starting army.
- **Card Modifiers**: Purchase game-changing cards that alter rules and grant powerful abilities (e.g., Heavy Armor, Sniper Bishops, Chrono Shift).
- **Chess960 Inspired**: Your purchased pieces are placed dynamically on the back rank to protect pawns, creating a unique tactical puzzle every round.
- **Custom AI Engine**: Features a minimax-based AI that evaluates piece values, mobility, center control, and card modifiers.

---

## 🎮 Gameplay & Rules

1. **The Goal**: Checkmate the AI King to win the round and proceed to the shop. If your King is checkmated, the run is over.
2. **Earnings**: Gold is earned by surviving with pieces, executing special moves, capturing enemy pieces, and finishing rounds quickly.
3. **The Shop**: Between rounds, use your gold to buy new pieces or draft powerful modifier cards.
4. **Setup**: At the start of each round, your purchased pieces are deployed randomly onto the back rank to protect your pawns. The AI's pieces will mirror your deployment. 
5. **No Castling**: Due to the chaotic nature of random back-rank piece generation, traditional castling is disabled in Ashes.

### Controls
- **Left Click**: Select a piece to see its legal moves, and click a highlighted square to move it.

---

## 🏗️ Architecture & Technologies

- **Language**: Java 17+
- **GUI Framework**: Java Swing & AWT
- **Core Systems**: 
  - `ChessEngine`: Validates moves, manages game state history, and handles move generation.
  - `AIPlayer`: Implements a minimax algorithm with alpha-beta pruning for opponent decision-making.
  - `ShopManager`: Handles the economy, piece limits, and card drafting logic.
  - `CardManager`: Resolves complex card interactions and hooks directly into the engine's move validation and capture logic.

---

## 🚀 Build & Run Instructions

To compile and run Ashes from the command line:

1. Clone the repository.
2. Navigate to the project directory:
   ```bash
   cd Ashes
   ```
3. Compile the Java files:
   ```bash
   javac *.java
   ```
4. Run the main class:
   ```bash
   java RoguelikeChessAppSwing
   ```
