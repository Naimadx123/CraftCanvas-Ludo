package zone.vao.craftcanvas.ludo.game

fun interface Cancel {
    fun cancel()
}

interface GameClock : AutoCloseable {
    fun after(ticks: Long, task: () -> Unit): Cancel

    override fun close() {}
}
