package au.com.josephmark.dithertracker.core

import kotlin.random.Random

enum class CoinFace { HEADS, TAILS }

/** Random table tools. Pure: all randomness comes from the [Random] passed in. */
object TableTools {
    /** A uniformly random non-KO'd player (any player if everyone is KO'd). */
    fun pickStartingPlayer(state: GameState, random: Random): PlayerId {
        val alive = state.players.filterNot(state::isKo)
        return (alive.ifEmpty { state.players }).random(random).id
    }

    /** 1..[sides] inclusive. */
    fun rollDie(sides: Int, random: Random): Int {
        require(sides >= 2) { "a die needs at least 2 sides, got $sides" }
        return random.nextInt(1, sides + 1)
    }

    fun rollDice(count: Int, sides: Int, random: Random): List<Int> {
        require(count >= 1) { "count must be >= 1, got $count" }
        return List(count) { rollDie(sides, random) }
    }

    fun flipCoin(random: Random): CoinFace = if (random.nextBoolean()) CoinFace.HEADS else CoinFace.TAILS
}
