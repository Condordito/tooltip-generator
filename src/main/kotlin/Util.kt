

object Util {

    private val LEGACY_FORMAT = "(?i)&([0-9a-fnmoklr])|&(#[0-9a-f]{6})".toRegex()

    private val LEGACY_COLOR_CODES = mapOf(
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

    fun getRandomString(chars: String, length: Int): String {
        val stringBuilder = StringBuilder()

        for (i in 0 until length) {
            stringBuilder.append(chars[(chars.length * Math.random()).toInt()])
        }

        return stringBuilder.toString()
    }

    fun convert(legacy: String) = LEGACY_FORMAT.replace(legacy) { result ->
        val match = result.groupValues.last { it.isNotEmpty() }
        LEGACY_COLOR_CODES[match[0]] ?: "<$match>"
    }
}
