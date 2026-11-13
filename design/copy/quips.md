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
- **Exception (T42): verbatim game quotes.** The KO and winner lists end with a few famous one-liners
  from 8- and 16-bit era games, quoted exactly (Sam, 2026-11-14), listed under *Verbatim game quotes*.
  They keep their own punctuation (`!`, a closing full stop, odd spacing); the case here is sentence
  case because every KO and winner line is drawn in caps. Each one's source is in the table at the
  bottom. Only lines we are sure of word-for-word; a misquote is worse than no quote.

**Size (T42):** KO and winner lines replace the life total and are drawn as big as they fit — up to
7 characters on one line at the largest size, two lines of 9 at the next, two lines of 12 below
that; anything longer drops to the small size. Short is better.

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
- Oof
- Folded like a lawn chair
- Should have mulliganed
- Durdled too long
- Got got
- Trusted a handshake deal
- Poked the wrong bear
- Deck was 'about a 7'
- Spectator mode unlocked
- Thoroughly cooked
- Lost the table vote
- Oops. Anyway
- Game over
- Press start to cry

#### Verbatim game quotes *(sources below)*
- Somebody set up us the bomb.
- Critical hit!
- But nothing happened!
- You fight like a dairy farmer.
- Rise from your grave!
- Toasty!
- Barf!

### Knocked out: poison (10 counters)
- Poisoned. Gross
- It was only a 1/1
- Nibbled to death
- Full health, still dead
- One proliferate too many
- Wasn't counting, clearly
- Ten is a very low number
- Death by tally marks
- Doesn't feel so good
- Died of ten papercuts
- Forgot the antidote

#### Verbatim game quotes *(sources below)*
- It's not very effective...

### Knocked out: commander damage (21 from one commander)
- One creature. Lots of hats
- Bonk. Twenty-one
- Should have chump blocked
- Ugh, that commander again
- Was saving the removal
- Bullied by one commander
- Killed it. It came back
- Ignored it for too long
- Lost to the final boss

#### Verbatim game quotes *(sources below)*
- It's super effective!
- Welcome to your doom!
- Fatality
- Can't escape!
- What you say !!

### Knocked out: marked out by hand (conceded)
- Scooped with dignity
- Rage quit, politely
- Chose peace
- Has a train to catch
- Went to get snacks
- Bailed. Fair enough
- Insert coin to rejoin

#### Verbatim game quotes *(sources below)*
- Got away safely!
- It's a secret to everybody.

### Starting player *(under name)*
- Goes first. No pressure
- Don't blow it
- Opens. Make it a good land
- First in, first targeted
- Off you go then
- Goes first. Everyone noticed
- Clock's ticking
- Chosen at random. Honestly

### d20: natural 1
- Yikes
- Blame the die
- Nailed it
- Somebody breathed on it
- Ooh, unlucky
- Retire this die

### d20: natural 20
- Show-off
- Keep this die
- Okay, calm down
- Don't touch it again
- Peaked early
- Nobody saw that coming

### Any other roll
- Fair and square
- Thrilling stuff
- No take-backs
- Could have been worse
- Sure, that'll do
- Make of that what you will

### Coin: heads
- Heads you win, probably
- Heads, obviously
- The coin smiles on you
- Heads. Don't gloat

### Coin: tails
- Tails. Sorry, not sorry
- Tails, as foretold
- The coin is unmoved
- Tails, sadly

### Reset / new game: prompt
- Start over? Everyone back to full life
- Reset the game? Sore losers welcome
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
- Go on, do something
- Nothing has happened. Yet
- Waiting on you lot

### Winner *(drawn large on their tile, T42)*
- Last one standing
- Never in doubt
- Survived the politics
- Told you so
- Wins. Rematch?
- Too easy
- High score. Enter name
- Level up

#### Verbatim game quotes *(sources below)*
- All your base are belong to us.
- For great justice.
- How are you gentlemen !!
- A winner is you
- Conglaturation !!!
- You have completed a great game.
- Flawless victory
- Course clear!

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

## Sources (verbatim game quotes)

Exact on-screen text (or the spoken line, for announcer voices) as it appeared; "As shown" is the
original casing. Check any you doubt before shipping; drop rather than "fix" one.

| Line | As shown | Game (year, platform) | Where |
|---|---|---|---|
| Somebody set up us the bomb. | SOMEBODY SET UP US THE BOMB. | Zero Wing (1991, Mega Drive) | KO: life |
| Critical hit! | Critical hit! | Pokemon Red / Blue (1996-98, Game Boy) | KO: life |
| But nothing happened! | But nothing happened! | Pokemon Red / Blue (Game Boy) | KO: life |
| You fight like a dairy farmer. | You fight like a dairy farmer. | The Secret of Monkey Island (1990, PC) | KO: life |
| Rise from your grave! | (spoken) | Altered Beast (1988, arcade / Mega Drive) | KO: life |
| Toasty! | (spoken) | Mortal Kombat II (1993, arcade) | KO: life |
| Barf! | BARF! | River City Ransom (1989, NES) | KO: life |
| It's not very effective... | It's not very effective... | Pokemon Red / Blue (Game Boy) | KO: poison |
| It's super effective! | It's super effective! | Pokemon Red / Blue (Game Boy) | KO: commander damage |
| Welcome to your doom! | (spoken) | Altered Beast (1988, arcade / Mega Drive) | KO: commander damage |
| Fatality | FATALITY (and spoken) | Mortal Kombat (1992, arcade) | KO: commander damage |
| Can't escape! | Can't escape! | Pokemon Red / Blue (Game Boy) | KO: commander damage |
| What you say !! | WHAT YOU SAY !! | Zero Wing (1991, Mega Drive) | KO: commander damage |
| Got away safely! | Got away safely! | Pokemon Red / Blue (Game Boy) | KO: conceded |
| It's a secret to everybody. | IT'S A SECRET TO EVERYBODY. | The Legend of Zelda (1986, NES) | KO: conceded |
| All your base are belong to us. | ALL YOUR BASE ARE BELONG TO US. | Zero Wing (1991, Mega Drive) | Winner |
| For great justice. | FOR GREAT JUSTICE. | Zero Wing (1991, Mega Drive) | Winner |
| How are you gentlemen !! | HOW ARE YOU GENTLEMEN !! | Zero Wing (1991, Mega Drive) | Winner |
| A winner is you | A WINNER IS YOU | Pro Wrestling (1986, NES) | Winner |
| Conglaturation !!! | CONGLATURATION !!! | Ghostbusters (1988, NES) | Winner |
| You have completed a great game. | YOU HAVE COMPLETED A GREAT GAME. | Ghostbusters (1988, NES) | Winner |
| Flawless victory | (spoken) | Mortal Kombat (1992, arcade) | Winner |
| Course clear! | COURSE CLEAR! | Super Mario World (1990, SNES) | Winner |
