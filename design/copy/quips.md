# Quips

Every personality line in Dither, grouped by where it shows up. Read, mark up, suggest.

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
- **Exception (T42): verbatim game quotes.** At least half of each KO and winner list is famous
  one-liners from 8- and 16-bit era games, quoted exactly (Sam, 2026-11-14), listed under *Verbatim
  game quotes*. They keep their own punctuation and quirks (`!`, `?`, a closing full stop, `!!` after
  a space, a lower-case start); otherwise they are stored in sentence case, because every KO and
  winner line is drawn in caps. Each one's source is in the table at the bottom. Only lines checked
  word-for-word against a source; a misquote is worse than no quote.

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

#### Verbatim game quotes *(sources below)*
- Thou art dead.
- You die...
- Somebody set up us the bomb.
- Oh no!
- Unit lost.
- Too late for that.
- A valiant attempt.
- Your prayers are not heard.
- Shoot! It was so close too!
- Stay a while... stay forever!
- But nothing happened.
- Critical hit!
- I feel asleep!!
- You fight like a dairy farmer.
- Rise from your grave!
- Barf!

### Knocked out: poison (10 counters)
- Poisoned. Gross
- It was only a 1/1
- Nibbled to death
- Ten is a very low number
- Died of ten papercuts

#### Verbatim game quotes *(sources below)*
- The poison was deadly...
- It's not very effective...
- Buy medicine before you go.
- Your life force is running out
- Time passes...

### Knocked out: commander damage (21 from one commander)
- One creature. Lots of hats
- Bonk. Twenty-one
- Should have chump blocked
- Ugh, that commander again
- Was saving the removal
- Bullied by one commander
- Killed it. It came back
- Ignored it for too long

#### Verbatim game quotes *(sources below)*
- All your base are belong to us.
- It's super effective!
- Welcome to your doom!
- One-hit KO!
- Excellent move!
- Toasty!
- You spoony bard!
- Son of a submariner!
- I hunger!
- It's you !!
- Can't escape!
- What you say !!
- The truck have started to move!

### Knocked out: marked out by hand (conceded)
- Scooped with dignity
- Rage quit, politely
- Chose peace
- Has a train to catch
- Went to get snacks

#### Verbatim game quotes *(sources below)*
- Chickening out... already?
- You run THAT fast?
- Got away safely!
- Do you wish to leave the game?
- go ahead and leave.
- The enemy is running away.
- Leave at once!
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

#### Verbatim game quotes *(sources below)*
- A winner is you
- Conglaturation !!!
- You have completed a great game.
- You won!
- The legends have proven true.
- For great justice.
- Course clear!
- Yippee!
- Hurrah!
- Now go and rest our heroes !
- I've not seen such bravery
- Thou art truly brave.
- Do you expect me to applaud?
- Boy, you're rich!
- Go home and be a family man!
- How are you gentlemen !!

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
original casing (the tile draws everything in caps). Each was checked against the source named —
disassemblies and released source code first, then transcripts. Check any you doubt before shipping;
drop rather than "fix" one. The wider bench (with spares for dice, reset and history) is
`design/copy/retro-candidates.md`.

| Line | As shown | Game (year, platform) | Verified against | Where |
|---|---|---|---|---|
| Thou art dead. | Thou art dead. | Dragon Warrior (1989, NES) | DW disassembly (nmikstas), Bank02.asm | KO: life |
| You die... | You die... | NetHack (1987, PC) | NetHack source, end.c | KO: life |
| Somebody set up us the bomb. | SOMEBODY SET UP US THE BOMB. | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | KO: life |
| Oh no! | (spoken) | Lemmings (1991, Amiga/PC) | Retro Gamer #39; MobyGames trivia | KO: life |
| Unit lost. | (spoken) | Command & Conquer (1995, DOS) | PCGamesN on the EVA lines | KO: life |
| Too late for that. | Too late for that. | Zork I (1980, PC) | Zork I source (historicalsource), gverbs.zil | KO: life |
| A valiant attempt. | A valiant attempt. | Zork I (1980, PC) | Zork I source, gverbs.zil | KO: life |
| Your prayers are not heard. | Your prayers are not heard. | Zork I (1980, PC) | Zork I source, 1actions.zil | KO: life |
| Shoot! It was so close too! | Shoot! It was so close too! | Pokemon Red / Blue (1998 US, Game Boy) | pokered disassembly (pret), text_6.asm | KO: life |
| Stay a while... stay forever! | (spoken) | Impossible Mission (1984, C64) | Wikipedia: Impossible Mission | KO: life |
| But nothing happened. | But nothing happened. | Dragon Warrior (1989, NES) | DW disassembly, Bank02.asm | KO: life |
| Critical hit! | Critical hit! | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: life |
| I feel asleep!! | I FEEL ASLEEP!! | Metal Gear (1988, NES) | Wikipedia: Metal Gear (video game) (case per NES all-caps text) | KO: life |
| You fight like a dairy farmer. | You fight like a dairy farmer. | The Secret of Monkey Island (1990, PC) | AGS wiki: Insult Swordfighting; Wikiquote | KO: life |
| Rise from your grave! | (spoken) | Altered Beast (1988 arcade / 1989 MD) | VGMRips sound-test track list; Wikipedia | KO: life |
| Barf! | BARF! | River City Ransom (1990, NES) | Legends of Localization: River City Ransom's BARF! | KO: life |
| The poison was deadly... | The poison was deadly... | NetHack (1987, PC) | NetHack source, attrib.c | KO: poison |
| It's not very effective... | It's not very effective... | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: poison |
| Buy medicine before you go. | BUY MEDICINE BEFORE YOU GO. | The Legend of Zelda (1987, NES) | Legends of Localization: Zelda text comparison | KO: poison |
| Your life force is running out | (spoken) | Gauntlet (1985, arcade) | Wikipedia: Gauntlet (1985 video game) | KO: poison |
| Time passes... | Time passes... | Zork I (1980, PC) | Zork I source, gverbs.zil | KO: poison |
| All your base are belong to us. | ALL YOUR BASE ARE BELONG TO US. | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | KO: commander damage |
| It's super effective! | It's super effective! | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: commander damage |
| Welcome to your doom! | (spoken) | Altered Beast (1988 arcade / 1989 MD) | VGMRips track list | KO: commander damage |
| One-hit KO! | One-hit KO! | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: commander damage |
| Excellent move! | Excellent move! | Dragon Warrior (1989, NES) | DW disassembly (critical hit) | KO: commander damage |
| Toasty! | (spoken) | Mortal Kombat II (1993, arcade) | Mortal Kombat Wiki; PC Gamer | KO: commander damage |
| You spoony bard! | You spoony bard! | Final Fantasy II US (IV) (1991, SNES) | Legends of Localization: You Spoony Bard | KO: commander damage |
| Son of a submariner! | Son of a submariner! | Final Fantasy III US (VI) (1994, SNES) | Wikipedia: Ted Woolsey | KO: commander damage |
| I hunger! | (spoken) | Sinistar (1982, arcade) | Wikipedia: Sinistar | KO: commander damage |
| It's you !! | It's you !! | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | KO: commander damage |
| Can't escape! | Can't escape! | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: commander damage |
| What you say !! | WHAT YOU SAY !! | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | KO: commander damage |
| The truck have started to move! | THE TRUCK HAVE STARTED TO MOVE! | Metal Gear (1988, NES) | Wikipedia: Metal Gear (own sentence after "Uh-oh!") | KO: commander damage |
| Chickening out... already? | Chickening out... already? | Wolfenstein 3D (1992, DOS) | Wolf3D source (id Software), WL_MENU.C | KO: conceded |
| You run THAT fast? | You run THAT fast? | The Secret of Monkey Island (1990, PC) | AGS wiki: Insult Swordfighting | KO: conceded |
| Got away safely! | Got away safely! | Pokemon Red / Blue (Game Boy) | pokered, text_2.asm | KO: conceded |
| Do you wish to leave the game? | Do you wish to leave the game? | Zork I (1980, PC) | Zork I source, gverbs.zil | KO: conceded |
| go ahead and leave. | go ahead and leave. | Doom (1993, DOS) | Doom source (id Software), dstrings.c | KO: conceded |
| The enemy is running away. | The enemy is running away. | Dragon Warrior (1989, NES) | DW disassembly | KO: conceded |
| Leave at once! | Leave at once! | Dragon Warrior (1989, NES) | DW disassembly | KO: conceded |
| It's a secret to everybody. | IT'S A SECRET TO EVERYBODY. | The Legend of Zelda (1987, NES) | Legends of Localization; Wikiquote | KO: conceded |
| A winner is you | A WINNER IS YOU | Pro Wrestling (1987, NES, US release) | Wikipedia; The Cutting Room Floor notes later revisions | Winner |
| Conglaturation !!! | CONGLATURATION !!! | Ghostbusters (1988, NES) | Legends of Localization (?p=9672) | Winner |
| You have completed a great game. | YOU HAVE COMPLETED A GREAT GAME. | Ghostbusters (1988, NES) | Legends of Localization (?p=9672) | Winner |
| You won! | YOU WON! | EarthBound (1995, SNES) | Legends of Localization: EarthBound battles | Winner |
| The legends have proven true. | The legends have proven true. | Dragon Warrior (1989, NES) | DW disassembly | Winner |
| For great justice. | FOR GREAT JUSTICE. | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | Winner |
| Course clear! | COURSE CLEAR! | Super Mario Bros. 3 (1990, NES) | Mario Wiki: Goal | Winner |
| Yippee! | (spoken) | Lemmings (1991, Amiga/PC) | Retro Gamer #39 | Winner |
| Hurrah! | Hurrah! | Dragon Warrior (1989, NES) | DW disassembly | Winner |
| Now go and rest our heroes ! | NOW GO AND REST OUR HEROES ! | Ghostbusters (1988, NES) | Legends of Localization (?p=9672) | Winner |
| I've not seen such bravery | (spoken) | Gauntlet (1985, arcade) | Wikipedia: Gauntlet | Winner |
| Thou art truly brave. | Thou art truly brave. | Dragon Warrior (1989, NES) | DW disassembly | Winner |
| Do you expect me to applaud? | Do you expect me to applaud? | Zork I (1980, PC) | Zork I source, gverbs.zil | Winner |
| Boy, you're rich! | BOY, YOU'RE RICH! | The Legend of Zelda (1987, NES) | Legends of Localization: Zelda text comparison | Winner |
| Go home and be a family man! | Go home and be a family man! | Street Fighter II (1991, arcade) | Legends of Localization (?p=944) (original casing unconfirmed) | Winner |
| How are you gentlemen !! | HOW ARE YOU GENTLEMEN !! | Zero Wing (1991, Mega Drive) | Wikiquote: Zero Wing | Winner |
