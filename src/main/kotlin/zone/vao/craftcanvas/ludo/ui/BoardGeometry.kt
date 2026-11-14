package zone.vao.craftcanvas.ludo.ui

import zone.vao.craftcanvas.ludo.game.Colour
import zone.vao.craftcanvas.ludo.game.Game
import zone.vao.craftcanvas.ludo.game.Rules

data class Cell(val id: String, val x: Int, val y: Int)

data class PawnCell(val id: String, val x: Int, val y: Int, val size: Int)

object BoardGeometry {
    private fun rotate(x: Int, y: Int, c: Int): Pair<Int, Int> {
        var a = x
        var b = y
        repeat(c) {
            val old = a
            a = 14 - b
            b = old
        }
        return a to b
    }

    val ring: List<Pair<Int, Int>> = buildList {
        (0..5).forEach { add(it to 6) }
        (5 downTo 0).forEach { add(6 to it) }
        add(7 to 0)
        add(8 to 0)
        (1..5).forEach { add(8 to it) }
        (9..14).forEach { add(it to 6) }
        add(14 to 7)
        add(14 to 8)
        (13 downTo 9).forEach { add(it to 8) }
        (9..14).forEach { add(8 to it) }
        add(7 to 14)
        add(6 to 14)
        (13 downTo 9).forEach { add(6 to it) }
        (5 downTo 0).forEach { add(it to 8) }
        add(0 to 7)
    }
    val cells = buildList {
        ring.forEachIndexed { i, (x, y) -> add(Cell("r$i", x, y)) }
        Colour.entries.forEach { c ->
            repeat(6) { i ->
                val (x, y) = rotate(i + 1, 7, c.ordinal)
                add(Cell("h${c.ordinal}_$i", x, y))
            }
            listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3).forEachIndexed { i, (a, b) ->
                val (x, y) = rotate(a, b, c.ordinal)
                add(Cell("b${c.ordinal}_$i", x, y))
            }
        }
    }

    val pawnCells = cells.flatMap { cell ->
        val x = cell.x * 8
        val y = cell.y * 8
        if (cell.id.startsWith("r") && Rules.isSafe(cell.id.substring(1).toInt())) {
            Colour.entries.map { colour ->
                val index = colour.ordinal
                PawnCell("${cell.id}_$index", x + index % 2 * 4, y + index / 2 * 4, 4)
            }
        } else listOf(PawnCell(cell.id, x, y, 8))
    }

    fun field(colour: Colour, pawn: Int, progress: Int): String =
        when {
            progress < 0 -> "b${colour.ordinal}_$pawn"
            progress < 52 -> "r${Rules.absolute(colour,progress)}"
            else -> "h${colour.ordinal}_${progress-52}"
        }

    fun occupants(g: Game): Map<String, String> {
        val masks = mutableMapOf<String, Pair<Int, Int>>()
        g.seats.forEach { seat ->
            seat.pawns.forEachIndexed { pawn, p ->
                val square = field(seat.colour, pawn, p)
                val field =
                    if (p in 0 until Rules.RING && Rules.isSafe(Rules.absolute(seat.colour, p)))
                        "${square}_${seat.colour.ordinal}"
                    else square
                val old = masks[field]?.second ?: 0
                masks[field] = seat.colour.ordinal to (old or (1 shl pawn))
            }
        }
        return pawnCells.associate {
            it.id to (masks[it.id]?.let { (c, m) -> "p${c}_$m" } ?: "empty")
        }
    }
}
