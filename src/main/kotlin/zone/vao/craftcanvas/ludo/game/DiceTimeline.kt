package zone.vao.craftcanvas.ludo.game

object DiceTimeline {
    const val FRAMES = 18
    const val INTERVAL = 2

    val motionFrames = (0 until FRAMES).flatMap { if (it < 12) listOf(it) else listOf(it, it) }
    val ticks: Long = motionFrames.size * INTERVAL.toLong()

    val autoplayFrames = motionFrames
}
