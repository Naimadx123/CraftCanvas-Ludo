package zone.vao.craftcanvas.ludo.config

import org.bukkit.configuration.file.FileConfiguration

data class LudoSettings(
    val animationEnabled: Boolean,
    val botDelayTicks: Long,
    val disconnectGraceTicks: Long,
    val maxRooms: Int,
) {
    companion object {
        fun load(config: FileConfiguration) = LudoSettings(
            animationEnabled = config.getBoolean("animation-enabled", true),
            botDelayTicks = config.getLong("bot-delay-ticks", 14).coerceIn(1, 200),
            disconnectGraceTicks = config.getLong("disconnect-grace-seconds", 90).coerceIn(1, 3600) * 20,
            maxRooms = config.getInt("max-rooms", 100).coerceIn(1, 10000),
        )
    }
}
