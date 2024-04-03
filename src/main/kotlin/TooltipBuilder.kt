import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class TooltipBuilder {
    private var font = Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE)
    private var textShadow = true
    private var betterMagic = false


    private var code = Minecraft.CODE.toString()

    private var name = ""
    private var lore: MutableList<String> = ArrayList()

    fun setFont(font: Font): TooltipBuilder {
        this.font = font
        return this
    }

    fun setTextShadow(textShadow: Boolean): TooltipBuilder {
        this.textShadow = textShadow
        return this
    }

    fun setBetterMagic(betterMagic: Boolean): TooltipBuilder {
        this.betterMagic = betterMagic
        return this
    }

    fun setName(name: String): TooltipBuilder {
        this.name = name
        return this
    }

    fun setLore(lore: MutableList<String>): TooltipBuilder {
        this.lore = lore
        return this
    }

    fun setLore(lore: String): TooltipBuilder {
        this.lore = lore.split("\n").toMutableList()
        return this
    }

    fun addLore(line: String): TooltipBuilder {
        lore.add(line)
        return this
    }

    fun setLore(index: Int, line: String): TooltipBuilder {
        lore[index] = line
        return this
    }

    fun removeLore(line: String): TooltipBuilder {
        lore.remove(line)
        return this
    }

    fun removeLore(index: Int): TooltipBuilder {
        lore.removeAt(index)
        return this
    }

    fun setColorCode(code: String): TooltipBuilder {
        this.code = code
        return this
    }

    fun asBufferedImage(): BufferedImage {
        var maxTextWidth = getTextWidth(font, name)
        var maxTextHeight = getTextHeight(
            font,
            name
        ) + (if (lore.size > 0) NAME_LORE_DIVIDER_SIZE else 0) + TEXT_OFFSET + LINE_OFFSET + BACKGROUND_OFFSET + TEXT_SHADOW_OFFSET

        for (loreLine in lore) {
            val number = getTextWidth(font, loreLine)
            if (number > maxTextWidth) maxTextWidth = number
            maxTextHeight += getTextHeight(font, loreLine)
        }

        val width = maxTextWidth.toInt() + TEXT_OFFSET * 2 //600;
        val height = if (lore.size == 0) (1.5 * getTextHeight(font, name)).toInt() else maxTextHeight.toInt() //300;

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics2D = bufferedImage.createGraphics()
        graphics2D.color = Color(0, 0, 0)
        graphics2D.fillRect(0, 0, width, height)

        graphics2D.color = Color(22, 8, 24)
        graphics2D.fillRect(
            BACKGROUND_OFFSET,
            BACKGROUND_OFFSET,
            width - BACKGROUND_OFFSET * 2,
            height - BACKGROUND_OFFSET * 2
        )

        graphics2D.color = Color(46, 6, 95)

        //LEFT TOP > LEFT BOTTOM
        graphics2D.fillRect(LINE_OFFSET, LINE_OFFSET, LINE_SIZE, height - LINE_OFFSET * 2)
        //LEFT TOP > RIGHT TOP
        graphics2D.fillRect(LINE_OFFSET, LINE_OFFSET, width - LINE_OFFSET * 2, LINE_SIZE)

        //LEFT BOTTOM > RIGHT BOTTOM
        graphics2D.fillRect(LINE_OFFSET, height - LINE_OFFSET - LINE_SIZE, width - LINE_OFFSET - LINE_OFFSET, LINE_SIZE)
        //RIGHT TOP > RIGHT BOTTOM
        graphics2D.fillRect(width - LINE_OFFSET - LINE_SIZE, LINE_OFFSET, LINE_SIZE, height - LINE_OFFSET * 2)

        graphics2D.color = Color(255, 255, 255)


        val textCurrentX = LINE_OFFSET + BACKGROUND_OFFSET + TEXT_OFFSET
        var textCurrentY = (LINE_OFFSET + BACKGROUND_OFFSET + getTextHeight(font, name) * 0.70).toInt()

        graphics2D.font = font
        drawColoredText(graphics2D, name, textCurrentX, textCurrentY)

        textCurrentY = (textCurrentY + (getTextHeight(font, name) + NAME_LORE_DIVIDER_SIZE)).toInt()

        var i = 1
        for (line in lore) {
            drawColoredText(graphics2D, line, textCurrentX, textCurrentY)
            textCurrentY = (textCurrentY + getTextHeight(font, line)).toInt()

            i++
        }

        graphics2D.color = Color(0, 255, 0)

        return bufferedImage
    }

    fun save(outputDirectory: String) {
        var outputDirectory = outputDirectory
        try {
            outputDirectory = outputDirectory.replace("\\\\".toRegex(), "/")

            val fileName = outputDirectory.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val ext = fileName[fileName.size - 1].split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val extension = ext[ext.size - 1]

            ImageIO.write(asBufferedImage(), extension, File(outputDirectory))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTextWidth(font: Font, text: String): Double {
        return font.getStringBounds(text, FontRenderContext(font.transform, false, false)).bounds.getWidth()
    }

    private fun getTextHeight(font: Font, text: String): Double {
        return font.getStringBounds(text, FontRenderContext(font.transform, false, false)).bounds.getHeight()
    }

    private fun drawColoredText(graphics2D: Graphics2D, text: String, x: Int, y: Int) {
        var x = x
        var color = Minecraft.Color.WHITE.color

        for (tm in text.split(code.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            var textMatched = tm;
            val colorCode = if (textMatched.length > 0) textMatched[0].toString() else ""

            if (textMatched.length > 0) textMatched = textMatched.substring(1)


            for (mccolor in Minecraft.Color.entries.toTypedArray()) {
                if (mccolor.code == colorCode) {
                    color = mccolor.color
                    break
                }
            }

            val formats: MutableList<Minecraft.Format> = ArrayList()

            for (format in Minecraft.Format.entries.toTypedArray()) {
                if (format.code == colorCode) {
                    formats.add(format)
                    break
                }
            }

            var font = this.font

            if (formats.contains(Minecraft.Format.BOLD)) formats.add(Minecraft.Format.BOLD)

            if (formats.contains(Minecraft.Format.ITALIC)) formats.add(Minecraft.Format.ITALIC)

            if (formats.contains(Minecraft.Format.RESET)) color = Minecraft.Color.WHITE.color

            if (formats.contains(Minecraft.Format.MAGIC)) {
                textMatched = if (!betterMagic) {
                    Util.getRandomString(Minecraft.MAGIC_CHARS, textMatched.length)
                } else {
                    Util.getRandomString("█", textMatched.length)
                }
            }

            if (formats.contains(Minecraft.Format.BOLD) && formats.contains(Minecraft.Format.ITALIC)) {
                font = font.deriveFont(Font.BOLD + Font.ITALIC)
            } else if (formats.contains(Minecraft.Format.BOLD)) {
                font = font.deriveFont(Font.BOLD)
            } else if (formats.contains(Minecraft.Format.ITALIC)) {
                font = font.deriveFont(Font.ITALIC)
            }

            graphics2D.font = font

            if (textShadow) {
                val shadowColor = Color(
                    (color.red * SHADOW_COLOR).toInt(),
                    (color.green * SHADOW_COLOR).toInt(),
                    (color.blue * SHADOW_COLOR).toInt()
                )
                graphics2D.color = shadowColor
                graphics2D.drawString(textMatched, x + TEXT_SHADOW_OFFSET, y + TEXT_SHADOW_OFFSET)
            }

            graphics2D.color = color
            graphics2D.drawString(textMatched, x, y)

            if (formats.contains(Minecraft.Format.UNDERLINE)) {
                val width = getTextWidth(font, textMatched).toInt()
                val height = getTextHeight(font, textMatched).toInt()

                val lineSize =
                    if (formats.contains(Minecraft.Format.BOLD)) UNDERLINE_STRIKETHROUGH_LINE_HEIGH_BOLD else UNDERLINE_STRIKETHROUGH_LINE_HEIGH
                graphics2D.fillRect(x, (y + (height * 0.1)).toInt(), width, lineSize)
            }

            if (formats.contains(Minecraft.Format.STRIKETHROUGH)) {
                val width = getTextWidth(font, textMatched).toInt()
                val height = getTextHeight(font, textMatched).toInt()

                val lineSize =
                    if (formats.contains(Minecraft.Format.BOLD)) UNDERLINE_STRIKETHROUGH_LINE_HEIGH_BOLD else UNDERLINE_STRIKETHROUGH_LINE_HEIGH
                graphics2D.fillRect(x, (y - (height * 0.25)).toInt(), width, lineSize)
            }

            x = (x + getTextWidth(graphics2D.font, textMatched)).toInt()
        }
    }

    companion object {
        private const val FONT_SIZE = 50
        private const val SHADOW_COLOR = 0.2
        private const val TEXT_OFFSET = (FONT_SIZE * 0.2).toInt()

        private const val LINE_OFFSET = (FONT_SIZE * 0.2).toInt()
        private const val BACKGROUND_OFFSET = (FONT_SIZE * 0.08).toInt()
        private const val TEXT_SHADOW_OFFSET = (FONT_SIZE * 0.08).toInt()

        private const val LINE_SIZE = (FONT_SIZE * 0.10).toInt()

        private const val NAME_LORE_DIVIDER_SIZE = FONT_SIZE / 2

        private const val UNDERLINE_STRIKETHROUGH_LINE_HEIGH = (FONT_SIZE * 0.05).toInt()
        private const val UNDERLINE_STRIKETHROUGH_LINE_HEIGH_BOLD = UNDERLINE_STRIKETHROUGH_LINE_HEIGH * 2
    }
}