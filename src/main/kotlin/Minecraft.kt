import java.awt.Color

object Minecraft {
    const val CODE: Char = '§'

    var MAGIC_CHARS: String = "abcdefghijklmnñopqrstuvwxyzABCDEFGHIJKLMNÑOPQRSTUVWXYZ0123456789[]-_{}<>¡!¿?#$%&/();:"

    enum class Color(val code: String, val color: java.awt.Color) {
        BLACK("0", Color(0, 0, 0)),
        DARK_BLUE("1", Color(0, 0, 170)),
        DARK_GREEN("2", Color(0, 170, 0)),
        DARK_AQUA("3", Color(0, 170, 170)),
        DARK_RED("4", Color(170, 0, 0)),
        PURPLE("5", Color(170, 0, 170)),
        GOLD("6", Color(255, 170, 0)),
        GRAY("7", Color(170, 170, 170)),
        DARK_GRAY("8", Color(85, 85, 85)),
        BLUE("9", Color(85, 85, 255)),
        LIME("a", Color(85, 255, 85)),
        AQUA("b", Color(85, 255, 255)),
        RED("c", Color(255, 85, 85)),
        LIGHT_PURPLE("d", Color(255, 85, 255)),
        YELLOW("e", Color(255, 255, 85)),
        WHITE("f", Color(255, 255, 255))
    }

    enum class Format(val code: String) {
        MAGIC("k"),
        BOLD("l"),
        STRIKETHROUGH("m"),
        UNDERLINE("n"),
        ITALIC("o"),
        RESET("r")
    }
}