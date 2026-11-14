package zone.vao.craftcanvas.ludo.game

enum class Colour {
    RED,
    GREEN,
    YELLOW,
    BLUE,
}

object Rules {
    const val BASE = -1
    const val RING = 52
    const val FINISH = 57

    fun absolute(colour: Colour, progress: Int): Int {
        require(progress in 0 until RING)
        return (colour.ordinal * 13 + progress) % RING
    }

    fun isSafe(square: Int): Boolean = square in 0 until RING && square % 13 == 0

    fun destination(progress: Int, die: Int): Int? {
        require(progress in BASE..FINISH && die in 1..6)
        return when {
            progress == BASE -> if (die == 6) 0 else null
            progress == FINISH || progress + die > FINISH -> null
            else -> progress + die
        }
    }

    fun legal(pawns: List<Int>, die: Int): List<Int> =
        pawns.indices.filter { destination(pawns[it], die) != null }

    fun move(seats: List<Seat>, current: Int, pawn: Int, die: Int): List<Seat> {
        require(pawn in legal(seats[current].pawns, die))
        val actor = seats[current]
        val target = destination(actor.pawns[pawn], die)!!
        val canCapture = target < RING && !isSafe(absolute(actor.colour, target))
        return seats.mapIndexed { index, seat ->
            if (index == current)
                seat.copy(pawns = seat.pawns.mapIndexed { i, p -> if (i == pawn) target else p })
            else if (canCapture)
                seat.copy(
                    pawns =
                        seat.pawns.map { p ->
                            if (
                                p in 0 until RING &&
                                    absolute(seat.colour, p) == absolute(actor.colour, target)
                            )
                                BASE
                            else p
                        }
                )
            else seat
        }
    }

    fun won(seat: Seat) = seat.pawns.all { it == FINISH }
}

data class Seat(
    val id: java.util.UUID,
    val name: String,
    val colour: Colour,
    val bot: Boolean,
    val ready: Boolean = bot,
    val online: Boolean = true,
    val pawns: List<Int> = List(4) { Rules.BASE },
)

object Bots {
    fun choose(seats: List<Seat>, current: Int, die: Int): Int? {
        val actor = seats[current]
        return Rules.legal(actor.pawns, die).maxByOrNull { pawn ->
            val target = Rules.destination(actor.pawns[pawn], die)!!
            val moved = Rules.move(seats, current, pawn, die)
            val captures =
                seats.indices
                    .filter { it != current }
                    .sumOf { i ->
                        seats[i].pawns.count { it >= 0 } - moved[i].pawns.count { it >= 0 }
                    }
            (if (Rules.won(moved[current])) 1_000_000 else 0) +
                (if (target == Rules.FINISH) 100_000 else 0) +
                captures * 10_000 +
                (if (actor.pawns[pawn] == Rules.BASE) 1_000 else 0) +
                target
        }
    }
}
