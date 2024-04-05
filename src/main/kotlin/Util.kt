import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import ir.mahozad.multiplatform.comshot.captureToImage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.imageio.ImageIO


object Util {
    const val TEXT_SIZE = 30
    const val WINDOW_RATIO = 0.7
    const val ITEM_TITLE = "&fDiamond Sword"
    val ITEM_LORE = listOf(
        "&fWhen in main hand:",
        "  &21.6 Attack Speed",
        "  &27 Attack Damage",
        "&8minecraft:diamond_sword",
        "&8NBT: 1 tag(s)"
    )
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

    private var MAGIC_CHARS: String = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ0123456789[]-_{}<>¡!¿?#$%&/();:"
    private val LEGACY_FORMAT = "(?i)&([0-9a-fnmoklr])|&(#[0-9a-f]{6})".toRegex()

    @Suppress("UNCHECKED_CAST")
    fun saveOutput(composable: @Composable () -> Unit, isRunning: MutableState<Boolean>) {
        var frames = List<Any>(5) {
            isRunning.value = false
            captureToImage(composable)
        }.also { isRunning.value = true }
        val equalFrames = equalFrames(frames as List<ImageBitmap>)
        frames = frames.map { trimEmptySpace(it.toAwtImage()) }
        if (equalFrames) {
            File("tooltip.png").outputStream().use {
                ImageIO.write(frames[0], "png", it)
            }
        } else {
            val writer = AnimatedGIFWriter(true)
            val stream = FileOutputStream("animated.gif")
            writer.prepareForWrite(stream, -1, -1)
            frames.forEach { writer.writeFrame(stream, it, 50) }
            writer.finishWrite(stream);
        }
    }

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

    private fun equalFrames(frames: List<ImageBitmap>) : Boolean {
        val first = frames[0].toPixelMap().buffer
        val second = frames[1].toPixelMap().buffer
        return first contentEquals second
    }

    private fun trimEmptySpace(image: BufferedImage): BufferedImage {
        // Find bounding box of non-transparent pixels
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) and 0x00FFFFFF != 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        // Create trimmed image
        val trimmedWidth = maxX - minX + 1
        val trimmedHeight = maxY - minY + 1

        return image.getSubimage(minX, minY, trimmedWidth, trimmedHeight)
    }
}
