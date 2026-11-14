package zone.vao.craftcanvas.ludo.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class Messages(private val get: (String) -> String) {
    fun text(key: String, vararg args: Pair<String, Any>): String =
        args.fold(get(key)) { text, (name, value) -> text.replace("%$name%", value.toString()) }

    companion object {
        fun load(plugin: JavaPlugin): Messages {
            val file = plugin.dataFolder.resolve("messages.yml")
            if (!file.exists()) plugin.saveResource("messages.yml", false)
            val configured = YamlConfiguration.loadConfiguration(file)
            val defaults = requireNotNull(plugin.getResource("messages.yml")).reader(Charsets.UTF_8).use {
                YamlConfiguration.loadConfiguration(it)
            }
            return Messages { key -> configured.getString(key) ?: defaults.getString(key) ?: key }
        }
    }
}
