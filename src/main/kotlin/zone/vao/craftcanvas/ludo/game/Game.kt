package zone.vao.craftcanvas.ludo.game

import java.util.UUID

fun interface Dice {
    fun roll(): Int
}

enum class Phase {
    LOBBY,
    WAIT_ROLL,
    ROLLING,
    WAIT_MOVE,
    FINISHED,
}

data class Roll(val id: UUID, val version: Long, val value: Int)

data class Game(
    val code: String,
    val host: UUID,
    val seats: List<Seat>,
    val phase: Phase = Phase.LOBBY,
    val version: Long = 0,
    val current: Int = 0,
    val roll: Roll? = null,
    val winner: UUID? = null,
    val winnerName: String? = null,
) {
    val active
        get() = seats[current]

    fun legal() =
        if (phase == Phase.WAIT_MOVE) Rules.legal(active.pawns, roll!!.value) else emptyList()
}

class GameError(val key: String) : IllegalStateException(key)
