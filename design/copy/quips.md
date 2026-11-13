# Quips

Every personality line in Dither Tracker, grouped by where it shows up. Read, mark up, suggest.

**Source of truth:** `core/src/main/kotlin/au/com/josephmark/dithertracker/core/flavour/QuipTables.kt`.
This file mirrors it. To change a line, edit it in `QuipTables.kt` *and* here, then run
`./gradlew :core:test` — `QuipsDocTest` fails if the two disagree, and `FlavourTest` fails if a
line breaks the rules below. (Not a coder? Edit this file and ask for the Kotlin to be updated.)

**Rules** (enforced by tests):
- Lines and captions: at most 32 characters (aim for 24). Reset prompts: at most two lines of 32.
  Button labels and player names: at most 10.
- Characters the `ui` face can draw: letters, digits, space and `+ - / : ; . , ? ( ) [ ] % # & ' " < > = _ | *`.
- Sentence case, no full stop at the end, no exclamation marks. No duplicates anywhere.
- Original lines only: no card names, card text or other apps' quips. Kid-safe.

**Voice:** the friend across the Commander table who enjoys your misfortune a little. A smidge
combative — "uh oh", "whoops", "beefed it" — short, blunt interjections mixed with the dry,
shared-experience lines (politics, durdling, "I'm not the threat"). Knock-outs react to the moment,
winners get gloated for, dice and coins needle. Teasing, never mean, crude or personal; kid-safe.
Reset prompts and buttons stay plain about what they do.

Captions marked *(under name)* are drawn beneath the player's name, which the UI shows separately.
Lines are picked at random, never the same line twice in a row. With quips off, the **Plain**
strings at the bottom are used instead.

## Lines

### Knocked out: life (0 or less)
- Beefed it
- Kept a one-lander
- Had an answer. In the deck
- Oof
- Board wipe was one turn late
- Folded like a lawn chair
- Should have mulliganed
- Big talk, small life total
- Durdled too long
- Got got
- Trusted a handshake deal
- Poked the wrong bear
- Deck was 'about a 7'
- Spectator mode unlocked
- Asked what it does. Too late
- Kind of had that coming
- Was one turn from winning
- Thoroughly cooked
- Monarch for one whole turn
- Down you go
- Turned out to be the threat
- That plan aged badly
- Tapped out at the worst time
- Lost the table vote
- Paid life for everything
- Oops. Anyway

### Knocked out: poison (10 counters)
- Thought 9 was a safe number
- Poisoned. Gross
- It was only a 1/1
- Nibbled to death
- Laughed at the infect deck
- Full health, still dead
- One proliferate too many
- Wasn't counting, clearly
- Ten is a very low number
- Death by tally marks
- Doesn't feel so good
- Died of ten papercuts

### Knocked out: commander damage (21 from one commander)
- One creature. Lots of hats
- Bonk. Twenty-one
- Counted to 21 the hard way
- Should have chump blocked
- Ugh, that commander again
- Was saving the removal
- Bullied by one commander
- Killed it. It came back
- Ignored it for too long
- Commander tax was too low
- Couldn't stop it. Didn't try
- Unblockable, as it turns out

### Knocked out: marked out by hand (conceded)
- Scooped with dignity
- Rage quit, politely
- Declined to watch the combo
- Chose peace
- Has a train to catch
- Read the room. Left it
- Went to get snacks
- Bailed. Fair enough
- Sure, 'had to go'

### Starting player *(under name)*
- Goes first. No pressure
- Goes first. Everyone noticed
- Gets the first land drop
- Opens. Make it a good land
- Kicks things off
- Sets the pace
- Has the floor
- Chosen at random. Honestly

### d20: natural 1
- Not ideal
- Blame the die
- Retire this die
- Somebody breathed on it
- At least it's memorable

### d20: natural 20
- Keep this die
- That's the good die
- Don't touch it again
- Nobody saw that coming
- Remember this moment

### Any other roll
- Fair and square
- No take-backs
- Rolled in plain sight
- Could have been worse
- Make of that what you will

### Coin: heads
- Heads you win, probably
- Landed face up
- The coin smiles on you

### Coin: tails
- Tails, as foretold
- Tails never fails, they say
- The coin is unmoved

### Reset / new game: prompt
- Start over? Everyone back to full life
- Reset the table? No hard feelings
- Clear the board and start fresh?
- Reset everything? Grudges not included
- Start a new game? This one gets wiped
- New game? Shuffle up, we go again

### Reset / new game: buttons (confirm / cancel)
Any pair can follow any prompt, so the confirm label must read as "reset" on its own.
- Start over / Keep going
- New game / Not yet
- Reset / Hold on
- Reset it / Leave it

### Empty history
- Nothing yet. Suspiciously quiet
- Nothing has happened. Yet
- Nobody has done anything yet
- All quiet on the battlefield

### Winner *(under name)*
- Last one standing
- Outlasted the table
- Survived the politics
- Was the threat all along
- Gracious in victory, hopefully
- Wins. Rematch?
- Wins. Salt levels rising
- Picks the snacks next time

### Commander damage: the targeted player *(drawn large on their tile, at most 16 characters; heckling a little)*
- Uh oh
- Whoops
- It's you
- Big mistake
- Here it comes
- This'll sting
- Not your day
- Should've ducked
- Hold still
- Called it
- Brace yourself

### Default player names (by seat; the first set is the default)
- Players: Player 1, Player 2, Player 3, Player 4, Player 5, Player 6
- Colours: White, Blue, Black, Red, Green, Colourless
- Archetypes: Aggro, Control, Combo, Midrange, Stax, Group Hug
- Turn: Untap, Upkeep, Draw, Main, Combat, End Step
- Usual cast: The Threat, Durdler, Kingmaker, Topdecker, Politician, Rule Zero

## Plain (quips off)

### Knocked out: life
- Knocked out

### Knocked out: poison
- Knocked out: poison

### Knocked out: commander damage
- Knocked out: commander damage

### Knocked out: marked out by hand
- Marked out

### Starting player
- Goes first

### d20: natural 1
- Natural 1

### d20: natural 20
- Natural 20

### Any other roll
- Rolled

### Coin: heads
- Heads

### Coin: tails
- Tails

### Reset / new game: prompt
- Reset game?

### Reset / new game: buttons
- Yes / No

### Empty history
- No history yet

### Winner
- Wins

### Commander damage: the targeted player
- Target

### Default player names
- Players: Player 1, Player 2, Player 3, Player 4, Player 5, Player 6
