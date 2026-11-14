package zone.vao.craftcanvas.ludo.ui

import java.nio.file.Path
import zone.vao.craftcanvas.*
import zone.vao.craftcanvas.ludo.assets.LudoAssets
import zone.vao.craftcanvas.ludo.canvas.LudoActions
import zone.vao.craftcanvas.ludo.config.Messages
import zone.vao.craftcanvas.ludo.game.*

class LudoScreens(private val art: Path, private val m: Messages) {
    private fun asset(name: String) = CanvasAssets.nativeImage("craftcanvas:ludo/$name")

    private fun text(id: String, key: String, x: Int, y: Int, w: Int = 280, h: Int = 12) =
        CanvasComponentDefinition.text(id)
            .position(x, y)
            .size(w, h)
            .layer(20)
            .text(m.text(key))
            .color("#e9edcf")
            .overflow(CanvasOverflow.TRUNCATE)

    private fun button(
        id: String,
        key: String,
        x: Int,
        y: Int,
        w: Int = 136,
        h: Int = 18,
        op: String = id,
    ): CanvasComponentDefinition.Builder =
        CanvasComponentDefinition.button(id)
            .position(x, y)
            .size(w, h)
            .layer(30)
            .text(m.text(key))
            .centered()
            .asset(CanvasAssets.nativeImage("craftcanvas:button"))
            .tooltip(m.text(key))
            .hover(false)
            .debounce(2)
            .onClick(CanvasActions.callback(LudoActions.ID, mapOf("op" to op)))

    private fun consoleAction(op: String) =
        mapOf(
            "tooltip" to listOf(m.text(when (op) {
                "setup" -> "solo"
                "solo" -> "solo-start"
                "joinCode" -> "join"
                else -> op
            })),
            "actions" to
                mapOf(
                    "click" to
                        listOf(CanvasActions.callback(LudoActions.ID, mapOf("op" to op)).fields)
                )
        )

    private fun base(id: String, key: String): CanvasScreenDefinition.Builder =
        CanvasScreenDefinition.builder("ludo:$id")
            .extendsScreen("frame")
            .title(m.text(key))
            .field("permission", "ludo.play")
            .field("book", mapOf("update" to "resend"))
            .field("session", mapOf("timeout-ticks" to 12000))
            .component(text("heading", "title", 104, 60, 296))
            .component("console_home", consoleAction("menu"))
            .component("console_back", consoleAction("menu"))
            .component("console_a", consoleAction("resume"))
            .component(text("caption", key, 112, 84))
            .variable("version", CanvasVariableType.STRING, "-1")
            .variable("room", CanvasVariableType.STRING, "")

    fun definitions(animationEnabled: Boolean = true): List<CanvasScreenDefinition> {
        val menu =
            base("menu", "menu")
                .component("console_a", consoleAction("setup"))
                .component(text("intro", "intro", 112, 104))
                .component(button("newSolo", "solo", 112, 124, 280, 22, "setup"))
                .component(button("newMulti", "create", 112, 154, 280, 22, "create"))
                .component(button("join", "join", 112, 184, 136, 22, "joinScreen"))
                .component(button("resume", "resume", 256, 184, 136, 22))
                .component(button("hide", "hide", 112, 216, 280, 18))
                .build()
        val setup =
            base("setup", "solo")
                .component("console_a", consoleAction("solo"))
                .variable("bots", CanvasVariableType.INTEGER, 1)
                .component(text("botLabel", "solo-bots-title", 112, 108).centered())
                .component(button("soloStart", "solo-start", 112, 172, 280, 24, "solo"))
                .component(button("menu", "back", 112, 212, 280, 18))
        for (count in 1..3) {
            val label = "solo-bots-$count"
            setup.component(
                button("bots$count", label, 112 + (count - 1) * 96, 130, 88, 26)
                    .onClick(CanvasActions.setVariable("bots", count))
                    .state(
                        CanvasComponentAppearance.SELECTED,
                        CanvasAppearanceDefinition.builder()
                            .text("[${m.text(label)}]")
                            .color("#f7dc78")
                            .whenCondition(CanvasConditions.variableEquals("bots", count)),
                    )
            )
        }
        val join =
            base("join", "rooms")
                .variable("code", CanvasVariableType.STRING, "")
                .component("console_a", consoleAction("joinCode"))
                .component(
                    CanvasComponentDefinition.textInput("code", "code")
                        .position(112, 106)
                        .size(170, 20)
                        .layer(30)
                        .asset(CanvasAssets.nativeImage("craftcanvas:button"))
                        .input(6, CanvasInputMode.DIALOG, m.text("code-prompt"))
                        .hover(false)
                )
                .component(button("joinCode", "join", 290, 106, 102, 20))
                .component(text("roomLabel", "available", 112, 134))
                .component(button("refreshRooms", "refresh", 112, 224, 280, 18))
        repeat(4) { i ->
            join
                .variable("room$i", CanvasVariableType.STRING, "")
                .component(
                    button("room$i", "room-row", 112, 150 + i * 18, 280, 16, "room$i")
                        .text("{room$i}")
                )
        }
        val lobby =
            base("lobby", "lobby")
                .component(text("caption", "room-title", 112, 84))
                .component("console_a", consoleAction("start"))
                .component(text("roomHelp", "lobby-help", 112, 204, 280, 18))
                .component(button("ready", "ready", 288, 104, 104, 18))
                .component(button("colour", "colour", 288, 128, 104, 18))
                .component(button("addBot", "add-bot", 288, 152, 104, 18))
                .component(button("removeBot", "remove-bot", 288, 176, 104, 18))
                .component(button("start", "start", 112, 228, 136, 16))
                .component(button("leave", "leave", 256, 228, 136, 16))
        repeat(4) { i ->
            lobby
                .variable("seat$i", CanvasVariableType.STRING, "")
                .component(
                    text("seat$i", "empty-seat", 112, 108 + i * 22, 170, 18).text("{seat$i}")
                )
        }
        val board =
            base("board", "board")
                .variable("status", CanvasVariableType.STRING, "")
                .component(
                    "console_a",
                    consoleAction("roll") +
                        mapOf(
                            "conditions" to
                                mapOf(
                                    "enabled" to
                                        CanvasConditions.variableEquals("canRoll", true).fields
                                )
                        ),
                )
                .variable("phase", CanvasVariableType.STRING, "")
                .variable("dieFace", CanvasVariableType.STRING, "0")
                .variable("result", CanvasVariableType.STRING, "-")
                .variable("canRoll", CanvasVariableType.BOOLEAN, false)
                .component(text("caption", "board", 112, 84).text("{status}"))
                .component(
                    CanvasComponentDefinition.image("boardArt")
                        .position(108, 104)
                        .size(120, 120)
                        .layer(15)
                        .asset(asset("board"))
                )
                .component(text("result", "result", 240, 104, 152).text(m.text("die-result")))
                .component(
                    button("roll", "roll", 240, 122, 152, 20)
                        .hover(true)
                        .enabledWhen(CanvasConditions.variableEquals("canRoll", true))
                )
                .component(
                    text("waiting", "waiting", 240, 198, 152, 26)
                        .text("{hint}")
                        .overflow(CanvasOverflow.WRAP)
                )
                .variable("hint", CanvasVariableType.STRING, "")
                .component(text("roomCode", "room-title", 112, 232, 160))
                .component(button("hide", "hide", 280, 230, 112, 16))
        repeat(4) { i ->
            board
                .variable("legal$i", CanvasVariableType.BOOLEAN, false)
                .component(
                    button("pawn$i", "pawn", 240 + (i % 2) * 78, 150 + (i / 2) * 24, 74, 18)
                        .text(m.text("pawn", "n" to i + 1))
                        .tooltip(m.text("pawn", "n" to i + 1))
                        .enabledWhen(CanvasConditions.variableEquals("legal$i", true))
                )
        }

        val pawnOptions = buildMap {
            put("empty", asset("empty"))
            (1..15).forEach { put("m$it", asset("m$it")) }
        }
        val colours = buildMap {
            put("empty", asset("empty"))
            repeat(4) { put("c$it", asset("c$it")) }
        }
        BoardGeometry.pawnCells.forEach { cell ->
            val occupied = CanvasConditions.variableEquals("colour_${cell.id}", "empty").not()
            board
                .variable("colour_${cell.id}", CanvasVariableType.STRING, "empty")
                .component(
                    CanvasComponentDefinition.image("colour_${cell.id}")
                        .position(108 + cell.x, 104 + cell.y)
                        .size(cell.size, cell.size)
                        .layer(20)
                        .assetSelect("colour_${cell.id}", colours, "empty")
                        .visibleWhen(occupied)
                )
            if (cell.size == 8)
                board
                    .variable(cell.id, CanvasVariableType.STRING, "empty")
                    .component(
                        CanvasComponentDefinition.image("cell_${cell.id}")
                            .position(108 + cell.x, 104 + cell.y)
                            .size(cell.size, cell.size)
                            .layer(21)
                            .assetSelect(cell.id, pawnOptions, "empty")
                            .visibleWhen(occupied)
                    )
        }
        for (face in 1..6) {
            board.component(
                CanvasComponentDefinition.image("die$face")
                    .position(104, 148)
                    .size(80, 32)
                    .layer(40)
                    .visibleWhen(CanvasConditions.variableEquals("dieFace", face.toString()))
                    .asset(asset("d${face}_17"))
            )
        }
        val rollScreens =
            if (animationEnabled)
                (1..6).map { face ->
                    CanvasScreenDefinition.builder("ludo:rolling$face")
                        .extendsScreen("ludo:board")
                        .component(
                            CanvasComponentDefinition.image("rollingDie")
                                .position(104, 148)
                                .size(80, 32)
                                .layer(41)
                                .autoplay(
                                    DiceTimeline.autoplayFrames.map { i ->
                                        asset("d${if(i<13)1 else face}_$i")
                                    },
                                    2,
                                    loop = false,
                                )
                        )
                        .build()
                }
            else emptyList()
        val result =
            base("result", "finished")
                .variable("winner", CanvasVariableType.STRING, "")
                .component("console_a", consoleAction("rematch"))
                .component(text("winner", "winner", 112, 116, 280, 24))
                .component(button("rematch", "rematch", 112, 158, 280, 24))
                .component(button("leave", "leave", 112, 198, 136, 22))
                .component(button("hide", "hide", 256, 198, 136, 22))

        return listOf(
            LudoAssets.register(menu.toBuilder(), art).build(),
            setup.build(),
            join.build(),
            lobby.build(),
            board.build(),
            result.build(),
        ) + rollScreens
    }
}
