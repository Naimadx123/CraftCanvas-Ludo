package zone.vao.craftcanvas.ludo.assets

import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.bukkit.plugin.java.JavaPlugin
import zone.vao.craftcanvas.CanvasScreenDefinition

object LudoAssets {
    val names = buildList {
        addAll(listOf("board", "empty"))
        repeat(4) { add("c$it") }
        (1..15).forEach { add("m$it") }
        (0..12).forEach { add("d1_$it") }
        (1..6).forEach { f -> (13..17).forEach { add("d${f}_$it") } }
    }
        .toSet()

    fun install(plugin: JavaPlugin): Path {
        val directory = plugin.dataFolder.toPath().resolve("art")
        Files.createDirectories(directory)
        names.forEach { name ->
            val file = directory.resolve("$name.png")
            if (!Files.exists(file)) {
                requireNotNull(plugin.getResource("art/$name.png")) { "Missing bundled image '$name'" }.use {
                    Files.copy(it, file)
                }
            }
        }
        return directory
    }

    fun register(builder: CanvasScreenDefinition.Builder, directory: Path): CanvasScreenDefinition.Builder {
        names.sorted().forEach { name ->
            val file = directory.resolve("$name.png")
            val image = requireNotNull(ImageIO.read(file.toFile())) { "Invalid image '$name.png'" }
            builder.image("craftcanvas:ludo/$name", file, image.width, image.height)
        }
        return builder
    }
}
