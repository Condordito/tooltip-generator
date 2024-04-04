import java.awt.Color


object Util {

    const val WINDOW_RATIO = 0.9
    const val ITEM_TITLE = "&fDiamond Sword"
    val ITEM_LORE = listOf(
        "&fWhen in main hand:",
        "  &21.6 Attack Speed",
        "  &27 Attack Damage",
        "&8minecraft:diamond_sword",
        "&8NBT: 1 tag(s)"
    )
    var MAGIC_CHARS: String = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ0123456789[]-_{}<>¡!¿?#$%&/();:"
    val LORE_BG_COLOR = androidx.compose.ui.graphics.Color.hsl(290f, .6f, 0.1f)
    val LORE_BORDER_COLOR = androidx.compose.ui.graphics.Color.hsl(270f, .9f, 0.3f)

    val COLOR_MAP: Map<String, Color> = mapOf(
        "BLACK" to Color(0, 0, 0),
        "DARK_BLUE" to Color(0, 0, 170),
        "DARK_GREEN" to Color(0, 170, 0),
        "DARK_AQUA" to Color(0, 170, 170),
        "DARK_RED" to Color(170, 0, 0),
        "DARK_PURPLE" to Color(170, 0, 170),
        "GOLD" to Color(255, 170, 0),
        "GRAY" to Color(170, 170, 170),
        "DARK_GRAY" to Color(85, 85, 85),
        "BLUE" to Color(85, 85, 255),
        "GREEN" to Color(85, 255, 85),
        "AQUA" to Color(85, 255, 255),
        "RED" to Color(255, 85, 85),
        "LIGHT_PURPLE" to Color(255, 85, 255),
        "YELLOW" to Color(255, 255, 85),
        "WHITE" to Color(255, 255, 255)
    )

    private val LEGACY_FORMAT = "(?i)&([0-9a-fnmoklr])|&(#[0-9a-f]{6})".toRegex()

    private val LEGACY_COLOR_CODES: Map<Char, String> = mapOf(
        '0' to "<black>", '1' to "<dark_blue>",
        '2' to "<dark_green>", '3' to "<dark_aqua>",
        '4' to "<dark_red>", '5' to "<dark_purple>",
        '6' to "<gold>", '7' to "<gray>",
        '8' to "<dark_gray>", '9' to "<blue>",
        'a' to "<green>", 'b' to "<aqua>",
        'c' to "<red>", 'd' to "<light_purple>",
        'e' to "<yellow>", 'f' to "<white>",
        'n' to "<u>", 'm' to "<st>",
        'k' to "<obf>", 'o' to "<i>",
        'l' to "<b>", 'r' to "<reset>"
    )

    fun decreaseBrightness(
        color: androidx.compose.ui.graphics.Color,
        factor: Float,
    ): androidx.compose.ui.graphics.Color = color.copy(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor
    )

    fun convertFromLegacy(legacy: String) = LEGACY_FORMAT.replace(legacy) { result ->
        val match = result.groupValues.last { it.isNotEmpty() }
        LEGACY_COLOR_CODES[match[0]] ?: "<$match>"
    }

    fun randomString(length: Int): String =
        (1..length).map { MAGIC_CHARS.random() }.joinToString("")
}
