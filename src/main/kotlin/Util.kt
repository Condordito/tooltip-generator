import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.IntSize
import ir.mahozad.multiplatform.comshot.captureToImage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object Util {
    const val TEXT_SIZE = 40.0
    const val TOOLTIP_BORDER_SIZE = 10
    const val TOOLTIP_PADDING = 30
    const val WINDOW_WIDTH = 1000
    const val WINDOW_HEIGHT = 600
    const val SHADOW_OFFSET = 3f
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
        '0' to "<black>",
        '1' to "<dark_blue>",
        '2' to "<dark_green>",
        '3' to "<dark_aqua>",
        '4' to "<dark_red>",
        '5' to "<dark_purple>",
        '6' to "<gold>",
        '7' to "<gray>",
        '8' to "<dark_gray>",
        '9' to "<blue>",
        'a' to "<green>",
        'b' to "<aqua>",
        'c' to "<red>",
        'd' to "<light_purple>",
        'e' to "<yellow>",
        'f' to "<white>",
        'n' to "<u>",
        'm' to "<st>",
        'k' to "<obf>",
        'o' to "<i>",
        'l' to "<b>",
        'r' to "<reset>"
    )

    private var MAGIC_CHARS: String = "ABCDEFGHIJKLMNÑOPQRSTUVWXYZ0123456789[]-_{}<>¡!¿?#$%&/();:"
    private val LEGACY_FORMAT = "(?i)&([0-9a-fnmoklr])|&(#[0-9a-f]{6})".toRegex()
    private const val BASE_SCALE = 0.01f
    private const val SCALE_OFFSET = 200
    private const val SIZE_DIFF_THRESHOLD = 100
    private const val MAX_SCALE = 1.25f
    private const val MIN_TEXT_SIZE = 12.0

    fun saveOutput(composable: @Composable () -> Unit, isRunning: MutableState<Boolean>): Any {
        val frames = captureFrames(isRunning, composable, 2).toMutableList()
        val writer = AnimatedGIFWriter(true)
        val stream = FileOutputStream("tooltip.gif")
        val isImage = compareImages(frames[0], frames[1])
        if (isImage) return writer.write(frames[0], stream)
        frames += captureFrames(isRunning, composable, 3)
        writer.prepareForWrite(stream, -1, -1)
        frames.forEach { writer.writeFrame(stream, it, 50) }
        return writer.finishWrite(stream)
    }

    fun decreaseBrightness(
        color: androidx.compose.ui.graphics.Color,
        factor: Float,
    ): androidx.compose.ui.graphics.Color = color.copy(
        red = color.red * factor, green = color.green * factor, blue = color.blue * factor
    )

    fun convertFromLegacy(legacy: String) = LEGACY_FORMAT.replace(legacy) { result ->
        val match = result.groupValues.last { it.isNotEmpty() }
        LEGACY_COLOR_CODES[match[0]] ?: "<$match>"
    }

    fun randomString(length: Int): String = (1..length).map { MAGIC_CHARS.random() }.joinToString("")

    private fun captureFrames(
        running: MutableState<Boolean>,
        composable: @Composable () -> Unit,
        amount: Int,
    ): List<BufferedImage> {
        return List(amount) {
            running.value = false
            val image = captureToImage(composable)
            trimEmptySpace(image.toAwtImage())
        }.also { running.value = true }
    }

    private fun compareImages(buffer1: BufferedImage, buffer2: BufferedImage): Boolean {
        for (x in 0 until buffer1.width) {
            for (y in 0 until buffer1.height) {
                if (buffer1.getRGB(x, y) != buffer2.getRGB(x, y)) {
                    return false
                }
            }
        }
        return true
    }

    private fun trimEmptySpace(image: BufferedImage): BufferedImage {
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) and 0x00FFFFFF != 0) {
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        return image.getSubimage(0, 0, ++maxX, ++maxY)
    }

    fun computeScale(computed: IntSize, max: IntSize, scale: Float): Float {
        val xDiff = max.width - computed.width - SCALE_OFFSET
        val yDiff = max.height - computed.height - SCALE_OFFSET
        if (xDiff < 0 || yDiff < 0) return scale - BASE_SCALE
        if (xDiff < SIZE_DIFF_THRESHOLD || yDiff < SIZE_DIFF_THRESHOLD) return scale
        return min(scale + BASE_SCALE, MAX_SCALE)
    }

    fun resizeText(size: Size, max: IntSize, textSize: Double): Double {
        val scaleFactor = BASE_SCALE * 5
        val xDiff = max.width.toFloat() - size.width
        val yDiff = max.height.toFloat() - size.height
        if (xDiff < 0 || yDiff < 0) return max(textSize - scaleFactor, MIN_TEXT_SIZE)
        if (xDiff < SIZE_DIFF_THRESHOLD || yDiff < SIZE_DIFF_THRESHOLD) return textSize
        return min(textSize + scaleFactor, TEXT_SIZE)
    }
}
