import java.awt.Dimension
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.IndexColorModel
import java.awt.image.Raster
import java.io.IOException
import java.io.OutputStream
import java.util.*

class AnimatedGIFWriter @JvmOverloads constructor(private val isApplyDither: Boolean = false) {
    // Fields
    private var codeLen = 0
    private var codeIndex = 0
    private var clearCode = 0
    private var endOfImage = 0
    private var bufIndex = 0
    private var empty_bits = 0x08

    private var bitsPerPixel = 0x08

    private val bytes_buf = ByteArray(256)
    private lateinit var colorPalette: IntArray

    /**
     * A child is made up of a parent(or prefix) code plus a suffix color
     * and siblings are strings with a common parent(or prefix) and different
     * suffix colors
     */
    var child: IntArray = IntArray(4097)

    var siblings: IntArray = IntArray(4097)
    var suffix: IntArray = IntArray(4097)

    private var logicalScreenWidth = 0
    private var logicalScreenHeight = 0

    private var animated = false
    private var loopCount = 0
    private var firstFrame = true

    // Write as a single frame GIF
    @Throws(Exception::class)
    fun write(img: BufferedImage?, os: OutputStream) {
        if (img == null) throw NullPointerException("Input image is null")
        val imageWidth = img.width
        val imageHeight = img.height
        write(getRGB(img), imageWidth, imageHeight, os)
    }

    @Throws(Exception::class)
    private fun encode(pixels: ByteArray, os: OutputStream) {
        // Define local variables
        var parent = 0
        var son = 0
        var brother = 0
        var color = 0
        var index = 0
        val dimension = pixels.size

        // Write out the length of the root
        os.write((if ((bitsPerPixel == 1)) 2 else bitsPerPixel).also { bitsPerPixel = it })
        // Initialize the encoder
        init_encoder(bitsPerPixel)
        // Tell the decoder to initialize the string table
        send_code_to_buffer(clearCode, os)
        // Get the first color and assign it to parent
        parent = (pixels[index++].toInt() and 0xff)

        while (index < dimension) {
            color = (pixels[index++].toInt() and 0xff)
            son = child[parent]

            if (son > 0) {
                if (suffix[son] == color) {
                    parent = son
                } else {
                    brother = son
                    while (true) {
                        if (siblings[brother] > 0) {
                            brother = siblings[brother]
                            if (suffix[brother] == color) {
                                parent = brother
                                break
                            }
                        } else {
                            siblings[brother] = codeIndex
                            suffix[codeIndex] = color
                            send_code_to_buffer(parent, os)
                            parent = color
                            codeIndex++
                            // Check code length
                            if (codeIndex > ((1 shl codeLen))) {
                                if (codeLen == 12) {
                                    send_code_to_buffer(clearCode, os)
                                    init_encoder(bitsPerPixel)
                                } else codeLen++
                            }
                            break
                        }
                    }
                }
            } else {
                child[parent] = codeIndex
                suffix[codeIndex] = color
                send_code_to_buffer(parent, os)
                parent = color
                codeIndex++
                // Check code length
                if (codeIndex > ((1 shl codeLen))) {
                    if (codeLen == 12) {
                        send_code_to_buffer(clearCode, os)
                        init_encoder(bitsPerPixel)
                    } else codeLen++
                }
            }
        }
        // Send the last color code to the buffer
        send_code_to_buffer(parent, os)
        // Send the endOfImage code to the buffer
        send_code_to_buffer(endOfImage, os)
        // Flush the last code buffer
        flush_buf(os, bufIndex + 1)
    }

    /**
     * This is intended to be called after writing all the frames if we write
     * an animated GIF frame by frame.
     *
     * @param os OutputStream for the animated GIF
     * @throws Exception
     */
    @Throws(Exception::class)
    fun finishWrite(os: OutputStream) {
        os.write(IMAGE_TRAILER.toInt())
        os.close()
    }

    @Throws(Exception::class)
    private fun flush_buf(os: OutputStream, len: Int) {
        os.write(len)
        os.write(bytes_buf, 0, len)
        // Clear the bytes buffer
        bufIndex = 0
        Arrays.fill(bytes_buf, 0, 0xff, 0x00.toByte())
    }

    private fun init_encoder(bitsPerPixel: Int) {
        clearCode = 1 shl bitsPerPixel
        endOfImage = clearCode + 1
        codeLen = bitsPerPixel + 1
        codeIndex = endOfImage + 1
        // Reset arrays
        Arrays.fill(child, 0)
        Arrays.fill(siblings, 0)
        Arrays.fill(suffix, 0)
    }

    /**
     * This is intended to be called first when writing an animated GIF
     * frame by frame.
     *
     * @param os OutputStream for the animated GIF
     * @param logicalScreenWidth width of the logical screen. If it is less than
     * or equal zero, it will be determined from the first frame
     * @param logicalScreenHeight height of the logical screen. If it is less than
     * or equal zero, it will be determined from the first frame
     * @throws Exception
     */
    @Throws(Exception::class)
    fun prepareForWrite(os: OutputStream, logicalScreenWidth: Int, logicalScreenHeight: Int) {
        // Header first
        writeHeader(os, true)
        this.logicalScreenWidth = logicalScreenWidth
        this.logicalScreenHeight = logicalScreenHeight
        // We are going to write animated GIF, so enable animated flag
        animated = true
    }

    // Translate codes into bytes
    @Throws(Exception::class)
    private fun send_code_to_buffer(code: Int, os: OutputStream) {
        var code = code
        var temp = codeLen
        // Shift the code to the left of the last byte in bytes_buf
        bytes_buf[bufIndex] =
            (bytes_buf[bufIndex].toInt() or ((code and MASK[empty_bits]) shl (8 - empty_bits))).toByte()
        code = code shr empty_bits
        temp -= empty_bits
        // If the code is longer than the empty_bits
        while (temp > 0) {
            if (++bufIndex >= 0xff) {
                flush_buf(os, 0xff)
            }
            bytes_buf[bufIndex] = (bytes_buf[bufIndex].toInt() or (code and 0xff)).toByte()
            code = code shr 8
            temp -= 8
        }
        empty_bits = -temp
    }

    fun setLoopCount(loopCount: Int) {
        this.loopCount = loopCount
    }

    @Throws(Exception::class)
    private fun write(pixels: IntArray, imageWidth: Int, imageHeight: Int, os: OutputStream) {
        // Write GIF header
        writeHeader(os, true)
        // Set logical screen size
        logicalScreenWidth = imageWidth
        logicalScreenHeight = imageHeight
        firstFrame = true
        // We only need to write one frame, so disable animated flag
        animated = false
        // Write the image frame
        writeFrame(pixels, imageWidth, imageHeight, 0, 0, 0, os)
        // Make a clean end up of the image
        os.write(IMAGE_TRAILER.toInt())
        os.close()
    }

    @Throws(Exception::class)
    private fun writeComment(os: OutputStream, comment: String) {
        os.write(EXTENSION_INTRODUCER.toInt())
        os.write(COMMENT_EXTENSION_LABEL.toInt())
        val commentBytes = comment.toByteArray()
        val numBlocks = commentBytes.size / 0xff
        val leftOver = commentBytes.size % 0xff
        var offset = 0
        if (numBlocks > 0) {
            for (i in 0 until numBlocks) {
                os.write(0xff)
                os.write(commentBytes, offset, 0xff)
                offset += 0xff
            }
        }
        if (leftOver > 0) {
            os.write(leftOver)
            os.write(commentBytes, offset, leftOver)
        }
        os.write(0)
    }
    @JvmOverloads
    @Throws(Exception::class)
    fun writeFrame(os: OutputStream, frame: BufferedImage, delay: Int = 100) {
        // Retrieve image dimension
        var imageWidth = frame.width
        var imageHeight = frame.height
        // Determine the logical screen dimension
        if (firstFrame) {
            if (logicalScreenWidth <= 0) logicalScreenWidth = imageWidth
            if (logicalScreenHeight <= 0) logicalScreenHeight = imageHeight
        }
        if (imageWidth > logicalScreenWidth) imageWidth = logicalScreenWidth
        if (imageHeight > logicalScreenHeight) imageHeight = logicalScreenHeight
        writeFrame(getRGB(frame.getSubimage(0, 0, imageWidth, imageHeight)), imageWidth, imageHeight, 0, 0, delay, os)
    }

    @Throws(Exception::class)
    private fun writeFrame(
        pixels: IntArray,
        imageWidth: Int,
        imageHeight: Int,
        imageLeftPosition: Int,
        imageTopPosition: Int,
        delay: Int,
        disposalMethod: Int,
        userInputFlag: Int,
        os: OutputStream
    ) {
        // Reset empty_bits
        empty_bits = 0x08

        var transparent_color = -1
        var colorInfo: IntArray

        // Reduce colors, if the color depth is less than 8 bits, reduce colors
        // to the actual bits needed, otherwise reduce to 8 bits.
        val newPixels = ByteArray(imageWidth * imageHeight)
        colorPalette = IntArray(256)

        colorInfo = checkColorDepth(pixels, newPixels, colorPalette)

        if (colorInfo[0] > 0x08) {
            bitsPerPixel = 8
            colorInfo = if (isApplyDither) reduceColorsDiffusionDither(
                pixels,
                imageWidth,
                imageHeight,
                bitsPerPixel,
                newPixels,
                colorPalette
            )
            else reduceColors(pixels, bitsPerPixel, newPixels, colorPalette)
        }

        bitsPerPixel = colorInfo[0]

        transparent_color = colorInfo[1]

        val num_of_color = 1 shl bitsPerPixel

        if (firstFrame) {
            // Logical screen descriptor
            var flags = 0x88.toByte() //0b10001000 (having sorted global color map) - To be updated
            var bgcolor: Byte = 0x00 // To be set
            val aspectRatio: Byte = 0x00
            val colorResolution = 0x07
            // Set GIF logical screen descriptor parameters
            flags = (flags.toInt() or ((colorResolution shl 4) or (bitsPerPixel - 1))).toByte()
            if (transparent_color >= 0) bgcolor = transparent_color.toByte()
            // Write logical screen descriptor
            writeLSD(
                os,
                logicalScreenWidth.toShort(),
                logicalScreenHeight.toShort(),
                flags.toShort(),
                bgcolor,
                aspectRatio
            )
            // Write the global colorPalette
            writePalette(os, num_of_color)
            writeComment(os, "Created by ICAFE - https://github.com/dragon66/icafe")
            if (animated) // Write Netscape extension block
                writeNetscapeApplicationBlock(os, loopCount)
        }

        // Output the graphic control block
        writeGraphicControlBlock(os, delay, transparent_color, disposalMethod, userInputFlag)
        // Output image descriptor
        if (firstFrame) {
            writeImageDescriptor(os, imageWidth, imageHeight, imageLeftPosition, imageTopPosition, -1)
            firstFrame = false
        } else {
            writeImageDescriptor(os, imageWidth, imageHeight, imageLeftPosition, imageTopPosition, bitsPerPixel - 1)
            // Write local colorPalette
            writePalette(os, num_of_color)
        }
        // LZW encode the image
        encode(newPixels, os)
        /** Write out a zero length data sub-block  */
        os.write(0x00)
    }

    @Throws(Exception::class)
    private fun writeFrame(
        pixels: IntArray,
        imageWidth: Int,
        imageHeight: Int,
        imageLeftPosition: Int,
        imageTopPosition: Int,
        delay: Int,
        os: OutputStream
    ) {
        writeFrame(
            pixels,
            imageWidth,
            imageHeight,
            imageLeftPosition,
            imageTopPosition,
            delay,
            GIFFrame.DISPOSAL_RESTORE_TO_BACKGROUND,
            GIFFrame.USER_INPUT_NONE,
            os
        )
    }

    // Unit of delay is supposed to be in millisecond
    @Throws(Exception::class)
    private fun writeGraphicControlBlock(
        os: OutputStream,
        delay: Int,
        transparent_color: Int,
        disposalMethod: Int,
        userInputFlag: Int
    ) {
        // Scale delay
        var delay = delay
        delay = Math.round(delay / 10.0f)

        val buf = ByteArray(8)
        buf[0] = EXTENSION_INTRODUCER // Extension introducer
        buf[1] = GRAPHIC_CONTROL_LABEL // Graphic control label
        buf[2] = 0x04 // Block size
        // Add disposalMethod and userInputFlag
        buf[3] = (buf[3]
            .toInt() or (((disposalMethod and 0x07) shl 2) or ((userInputFlag and 0x01) shl 1))).toByte()
        buf[4] = (delay and 0xff).toByte() // Delay time
        buf[5] = ((delay shr 8) and 0xff).toByte()
        buf[6] = transparent_color.toByte()
        buf[7] = 0x00

        if (transparent_color >= 0) // Add transparency indicator
            buf[3] = (buf[3].toInt() or 0x01).toByte()

        os.write(buf, 0, 8)
    }

    @Throws(IOException::class)
    private fun writeHeader(os: OutputStream, newFormat: Boolean) {
        // 6 bytes: GIF signature (always "GIF") plus GIF version ("87a" or "89a")
        if (newFormat) os.write("GIF89a".toByteArray())
        else os.write("GIF87a".toByteArray())
    }

    @Throws(Exception::class)
    private fun writeImageDescriptor(
        os: OutputStream,
        imageWidth: Int,
        imageHeight: Int,
        imageLeftPosition: Int,
        imageTopPosition: Int,
        colorTableSize: Int
    ) {
        val imageDescriptor = ByteArray(10)
        imageDescriptor[0] = IMAGE_SEPARATOR // Image separator ","
        imageDescriptor[1] = (imageLeftPosition and 0xff).toByte() // Image left position
        imageDescriptor[2] = ((imageLeftPosition shr 8) and 0xff).toByte()
        imageDescriptor[3] = (imageTopPosition and 0xff).toByte() // Image top position
        imageDescriptor[4] = ((imageTopPosition shr 8) and 0xff).toByte()
        imageDescriptor[5] = (imageWidth and 0xff).toByte()
        imageDescriptor[6] = ((imageWidth shr 8) and 0xff).toByte()
        imageDescriptor[7] = (imageHeight and 0xff).toByte()
        imageDescriptor[8] = ((imageHeight shr 8) and 0xff).toByte()
        imageDescriptor[9] = 0x20.toByte() //0b00100000 - Packed fields

        if (colorTableSize >= 0) // Local color table will follow
            imageDescriptor[9] = (imageDescriptor[9].toInt() or (1 shl 7 or colorTableSize)).toByte()

        os.write(imageDescriptor, 0, 10)
    }

    // Write logical screen descriptor
    @Throws(IOException::class)
    private fun writeLSD(
        os: OutputStream,
        screen_width: Short,
        screen_height: Short,
        flags: Short,
        bgcolor: Byte,
        aspectRatio: Byte
    ) {
        val descriptor = ByteArray(7)
        // Screen_width
        descriptor[0] = (screen_width.toInt() and 0xff).toByte()
        descriptor[1] = ((screen_width.toInt() shr 8) and 0xff).toByte()
        // Screen_height
        descriptor[2] = (screen_height.toInt() and 0xff).toByte()
        descriptor[3] = ((screen_height.toInt() shr 8) and 0xff).toByte()
        // Global flags
        descriptor[4] = (flags.toInt() and 0xff).toByte()
        // Background color
        descriptor[5] = bgcolor
        // AspectRatio
        descriptor[6] = aspectRatio

        os.write(descriptor)
    }

    @Throws(Exception::class)
    private fun writeNetscapeApplicationBlock(os: OutputStream, loopCounts: Int) {
        val buf = ByteArray(19)
        buf[0] = EXTENSION_INTRODUCER // Extension introducer
        buf[1] = APPLICATION_EXTENSION_LABEL // Application extension label
        buf[2] = 0x0b // Block size
        buf[3] = 'N'.code.toByte() // Application Identifier (8 bytes)
        buf[4] = 'E'.code.toByte()
        buf[5] = 'T'.code.toByte()
        buf[6] = 'S'.code.toByte()
        buf[7] = 'C'.code.toByte()
        buf[8] = 'A'.code.toByte()
        buf[9] = 'P'.code.toByte()
        buf[10] = 'E'.code.toByte()
        buf[11] = '2'.code.toByte() // Application Authentication Code (3 bytes)
        buf[12] = '.'.code.toByte()
        buf[13] = '0'.code.toByte()
        buf[14] = 0x03
        buf[15] = 0x01
        buf[16] = (loopCounts and 0xff).toByte() // Loop counts
        buf[17] = ((loopCounts shr 8) and 0xff).toByte()
        buf[18] = 0x00 // Block terminator

        os.write(buf)
    }

    @Throws(Exception::class)
    private fun writePalette(os: OutputStream, num_of_color: Int) {
        var index = 0
        val colors = ByteArray(num_of_color * 3)

        for (i in 0 until num_of_color) {
            colors[index++] = ((colorPalette[i] shr 16) and 0xff).toByte()
            colors[index++] = ((colorPalette[i] shr 8) and 0xff).toByte()
            colors[index++] = (colorPalette[i] and 0xff).toByte()
        }

        os.write(colors, 0, num_of_color * 3)
    }

    class GIFFrame @JvmOverloads constructor(
        frame: BufferedImage?,
        leftPosition: Int = 0,
        topPosition: Int = 0,
        delay: Int = 0,
        disposalMethod: Int = DISPOSAL_UNSPECIFIED,
        userInputFlag: Int = USER_INPUT_NONE,
        transparencyFlag: Int = TRANSPARENCY_INDEX_NONE,
        transparentColor: Int = TRANSPARENCY_COLOR_NONE
    ) {
        // Frame parameters
        var frame: BufferedImage? = null
        var leftPosition: Int = 0
        var topPosition: Int = 0
        var frameWidth: Int = 0
        var frameHeight: Int = 0
        var delay: Int = 0
        var disposalMethod: Int = DISPOSAL_UNSPECIFIED
        var userInputFlag: Int = USER_INPUT_NONE
        var transparencyFlag: Int = TRANSPARENCY_INDEX_NONE

        // The transparent color value in RRGGBB format.
        // The highest order byte has no effect.
        var transparentColor: Int = TRANSPARENCY_COLOR_NONE // Default no transparent color

        init {
            var delay = delay
            requireNotNull(frame) { "Null input image" }
            require(!(disposalMethod < DISPOSAL_UNSPECIFIED || disposalMethod > DISPOSAL_TO_BE_DEFINED)) { "Invalid disposal method: $disposalMethod" }
            require(!(userInputFlag < USER_INPUT_NONE || userInputFlag > USER_INPUT_EXPECTED)) { "Invalid user input flag: $userInputFlag" }
            require(!(transparencyFlag < TRANSPARENCY_INDEX_NONE || transparencyFlag > TRANSPARENCY_INDEX_SET)) { "Invalid transparency flag: $transparencyFlag" }
            require(!(leftPosition < 0 || topPosition < 0)) { "Negative coordinates for frame top-left position" }
            if (delay < 0) delay = 0
            this.frame = frame
            this.leftPosition = leftPosition
            this.topPosition = topPosition
            this.delay = delay
            this.disposalMethod = disposalMethod
            this.userInputFlag = userInputFlag
            this.transparencyFlag = transparencyFlag
            this.frameWidth = frame.width
            this.frameHeight = frame.height
            this.transparentColor = transparentColor
        }

        companion object {
            const val DISPOSAL_UNSPECIFIED: Int = 0
            const val DISPOSAL_LEAVE_AS_IS: Int = 1
            const val DISPOSAL_RESTORE_TO_BACKGROUND: Int = 2
            const val DISPOSAL_RESTORE_TO_PREVIOUS: Int = 3

            // Values between 4-7 inclusive
            const val DISPOSAL_TO_BE_DEFINED: Int = 7

            const val USER_INPUT_NONE: Int = 0
            const val USER_INPUT_EXPECTED: Int = 1

            const val TRANSPARENCY_INDEX_NONE: Int = 0
            const val TRANSPARENCY_INDEX_SET: Int = 1

            const val TRANSPARENCY_COLOR_NONE: Int = -1
        }
    }

    /**
     * Java port of
     * C Implementation of Wu's Color Quantizer (v. 2)
     * (see Graphics Gems vol. II, pp. 126-133)
     * Author:	Xiaolin Wu
     * Dept. of Computer Science
     * Univ. of Western Ontario
     * London, Ontario N6A 5B7
     * wu@csd.uwo.ca
     *
     * Algorithm: Greedy orthogonal bipartition of RGB space for variance
     * minimization aided by inclusion-exclusion tricks.
     * For speed no nearest neighbor search is done. Slightly
     * better performance can be expected by more sophisticated
     * but more expensive versions.
     *
     * The author thanks Tom Lane at Tom_Lane@G.GP.CS.CMU.EDU for much of
     * additional documentation and a cure to a previous bug.
     *
     * Free to distribute, comments and suggestions are appreciated.
     */
    private class WuQuant(private val pixels: IntArray, /*color look-up table size*/private var lut_size: Int) {
        private class Box {
            var r0: Int = 0 /* min value, exclusive */
            var r1: Int = 0 /* max value, inclusive */
            var g0: Int = 0
            var g1: Int = 0
            var b0: Int = 0
            var b1: Int = 0
            var vol: Int = 0
        }

        private val size = pixels.size /*image size*/
        private lateinit var qadd: IntArray
        private var transparent_color = -1 // Transparent color

        private val m2 = Array(QUANT_SIZE) { Array(QUANT_SIZE) { FloatArray(QUANT_SIZE) } }
        private val wt = Array(QUANT_SIZE) { Array(QUANT_SIZE) { LongArray(QUANT_SIZE) } }
        private val mr = Array(QUANT_SIZE) { Array(QUANT_SIZE) { LongArray(QUANT_SIZE) } }
        private val mg = Array(QUANT_SIZE) { Array(QUANT_SIZE) { LongArray(QUANT_SIZE) } }
        private val mb = Array(QUANT_SIZE) { Array(QUANT_SIZE) { LongArray(QUANT_SIZE) } }

        fun quantize(newPixels: ByteArray, lut: IntArray, colorInfo: IntArray): Int {
            val cube = arrayOfNulls<Box>(MAXCOLOR)
            var lut_r: Int
            var lut_g: Int
            var lut_b: Int
            val tag = IntArray(QUANT_SIZE * QUANT_SIZE * QUANT_SIZE)

            var next: Int
            var k: Int
            var weight: Long
            val vv = FloatArray(MAXCOLOR)
            var temp: Float

            Hist3d(wt, mr, mg, mb, m2)
            M3d(wt, mr, mg, mb, m2)

            var i = 0
            while (i < MAXCOLOR) {
                cube[i] = Box()
                i++
            }

            cube[0]!!.b0 = 0
            cube[0]!!.g0 = cube[0]!!.b0
            cube[0]!!.r0 = cube[0]!!.g0
            cube[0]!!.b1 = QUANT_SIZE - 1
            cube[0]!!.g1 = cube[0]!!.b1
            cube[0]!!.r1 = cube[0]!!.g1
            next = 0

            if (transparent_color >= 0) lut_size--

            i = 1
            while (i < lut_size) {
                if (Cut(cube[next], cube[i])) {
                    /* volume test ensures we won't try to cut one-cell box */
                    vv[next] = if ((cube[next]!!.vol > 1)) Var(cube[next]) else 0.0f
                    vv[i] = if ((cube[i]!!.vol > 1)) Var(cube[i]) else 0.0f
                } else {
                    vv[next] = 0.0f /* don't try to split this box again */
                    i-- /* didn't create box i */
                }
                next = 0
                temp = vv[0]
                k = 1
                while (k <= i) {
                    if (vv[k] > temp) {
                        temp = vv[k]
                        next = k
                    }
                    ++k
                }
                if (temp <= 0.0f) {
                    k = i + 1
                    break
                }
                ++i
            }

            k = 0
            while (k < lut_size) {
                Mark(cube[k], k, tag)
                weight = Vol(cube[k], wt)
                if (weight > 0) {
                    lut_r = (Vol(cube[k], mr) / weight).toInt()
                    lut_g = (Vol(cube[k], mg) / weight).toInt()
                    lut_b = (Vol(cube[k], mb) / weight).toInt()
                    lut[k] = (255 shl 24) or (lut_r shl 16) or (lut_g shl 8) or lut_b
                } else {
                    lut[k] = 0
                }
                ++k
            }

            i = 0
            while (i < size) {
                if ((pixels[i] ushr 24) < 0x80) newPixels[i] = lut_size.toByte()
                else newPixels[i] = tag[qadd[i]].toByte()
                ++i
            }

            var bitsPerPixel = 0
            while ((1 shl bitsPerPixel) < lut_size) bitsPerPixel++
            colorInfo[0] = bitsPerPixel
            colorInfo[1] = -1

            if (transparent_color >= 0) {
                lut[lut_size] = transparent_color // Set the transparent color
                colorInfo[1] = lut_size
            }

            return lut_size
        }

        fun quantize(lut: IntArray, colorInfo: IntArray): Int {
            val cube = arrayOfNulls<Box>(MAXCOLOR)
            var lut_r: Int
            var lut_g: Int
            var lut_b: Int

            var next: Int
            var k: Int
            var weight: Long
            val vv = FloatArray(MAXCOLOR)
            var temp: Float

            Hist3d(wt, mr, mg, mb, m2)
            M3d(wt, mr, mg, mb, m2)

            var i = 0
            while (i < MAXCOLOR) {
                cube[i] = Box()
                i++
            }

            cube[0]!!.b0 = 0
            cube[0]!!.g0 = cube[0]!!.b0
            cube[0]!!.r0 = cube[0]!!.g0
            cube[0]!!.b1 = QUANT_SIZE - 1
            cube[0]!!.g1 = cube[0]!!.b1
            cube[0]!!.r1 = cube[0]!!.g1
            next = 0

            if (transparent_color >= 0) lut_size--

            i = 1
            while (i < lut_size) {
                if (Cut(cube[next], cube[i])) {
                    /* volume test ensures we won't try to cut one-cell box */
                    vv[next] = if ((cube[next]!!.vol > 1)) Var(cube[next]) else 0.0f
                    vv[i] = if ((cube[i]!!.vol > 1)) Var(cube[i]) else 0.0f
                } else {
                    vv[next] = 0.0f /* don't try to split this box again */
                    i-- /* didn't create box i */
                }
                next = 0
                temp = vv[0]
                k = 1
                while (k <= i) {
                    if (vv[k] > temp) {
                        temp = vv[k]
                        next = k
                    }
                    ++k
                }
                if (temp <= 0.0f) {
                    k = i + 1
                    break
                }
                ++i
            }

            k = 0
            while (k < lut_size) {
                weight = Vol(cube[k], wt)
                if (weight > 0) {
                    lut_r = (Vol(cube[k], mr) / weight).toInt()
                    lut_g = (Vol(cube[k], mg) / weight).toInt()
                    lut_b = (Vol(cube[k], mb) / weight).toInt()
                    lut[k] = (255 shl 24) or (lut_r shl 16) or (lut_g shl 8) or lut_b
                } else {
                    lut[k] = 0
                }
                ++k
            }

            var bitsPerPixel = 0
            while ((1 shl bitsPerPixel) < lut_size) bitsPerPixel++
            colorInfo[0] = bitsPerPixel
            colorInfo[1] = -1

            if (transparent_color >= 0) {
                lut[lut_size] = transparent_color // Set the transparent color
                colorInfo[1] = lut_size
            }

            return lut_size
        }

        /* Histogram is in elements 1..HISTSIZE along each axis,
         * element 0 is for base or marginal value
         * NB: these must start out 0!
         */
        private fun Hist3d(
            vwt: Array<Array<LongArray>>,
            vmr: Array<Array<LongArray>>,
            vmg: Array<Array<LongArray>>,
            vmb: Array<Array<LongArray>>,
            m2: Array<Array<FloatArray>>
        ) {
            /* build 3-D color histogram of counts, r/g/b, c^2 */
            var r: Int
            var g: Int
            var b: Int
            var inr: Int
            var ing: Int
            var inb: Int
            val table = IntArray(256)

            var i = 0
            while (i < 256) {
                table[i] = i * i
                ++i
            }

            qadd = IntArray(size)

            i = 0
            while (i < size) {
                val rgb = pixels[i]
                if ((rgb ushr 24) < 0x80) { // Transparent
                    if (transparent_color < 0) // Find the transparent color
                        transparent_color = rgb
                }
                r = ((rgb shr 16) and 0xff)
                g = ((rgb shr 8) and 0xff)
                b = (rgb and 0xff)
                inr = (r shr 3) + 1
                ing = (g shr 3) + 1
                inb = (b shr 3) + 1
                qadd[i] = (inr shl 10) + (inr shl 6) + inr + (ing shl 5) + ing + inb
                /*[inr][ing][inb]*/
                ++vwt[inr][ing][inb]
                vmr[inr][ing][inb] += r.toLong()
                vmg[inr][ing][inb] += g.toLong()
                vmb[inr][ing][inb] += b.toLong()
                m2[inr][ing][inb] += (table[r] + table[g] + table[b]).toFloat()
                ++i
            }
        }

        /* At conclusion of the histogram step, we can interpret
         *   wt[r][g][b] = sum over voxel of P(c)
         *   mr[r][g][b] = sum over voxel of r*P(c)  ,  similarly for mg, mb
         *   m2[r][g][b] = sum over voxel of c^2*P(c)
         * Actually each of these should be divided by 'size' to give the usual
         * interpretation of P() as ranging from 0 to 1, but we needn't do that here.
         */
        /* We now convert histogram into moments so that we can rapidly calculate
         * the sums of the above quantities over any desired box.
         */
        private fun M3d(
            vwt: Array<Array<LongArray>>,
            vmr: Array<Array<LongArray>>,
            vmg: Array<Array<LongArray>>,
            vmb: Array<Array<LongArray>>,
            m2: Array<Array<FloatArray>>
        ) {
            /* compute cumulative moments. */
            var i: Int
            var g: Int
            var b: Int
            var line: Int
            var line_r: Int
            var line_g: Int
            var line_b: Int
            val area = IntArray(QUANT_SIZE)
            val area_r = IntArray(QUANT_SIZE)
            val area_g = IntArray(QUANT_SIZE)
            val area_b = IntArray(QUANT_SIZE)
            var line2: Float
            val area2 = FloatArray(QUANT_SIZE)

            var r = 1
            while (r < QUANT_SIZE) {
                i = 0
                while (i < QUANT_SIZE) {
                    area_b[i] = 0
                    area_g[i] = area_b[i]
                    area_r[i] = area_g[i]
                    area[i] = area_r[i]
                    area2[i] = area[i].toFloat()
                    ++i
                }
                g = 1
                while (g < QUANT_SIZE) {
                    line_b = 0
                    line_g = line_b
                    line_r = line_g
                    line = line_r
                    line2 = line.toFloat()
                    b = 1
                    while (b < QUANT_SIZE) {
                        line += vwt[r][g][b].toInt()
                        line_r += vmr[r][g][b].toInt()
                        line_g += vmg[r][g][b].toInt()
                        line_b += vmb[r][g][b].toInt()
                        line2 += m2[r][g][b]

                        area[b] += line
                        area_r[b] += line_r
                        area_g[b] += line_g
                        area_b[b] += line_b
                        area2[b] += line2

                        vwt[r][g][b] = vwt[r - 1][g][b] + area[b]
                        vmr[r][g][b] = vmr[r - 1][g][b] + area_r[b]
                        vmg[r][g][b] = vmg[r - 1][g][b] + area_g[b]
                        vmb[r][g][b] = vmb[r - 1][g][b] + area_b[b]
                        m2[r][g][b] = m2[r - 1][g][b] + area2[b]
                        ++b
                    }
                    ++g
                }
                ++r
            }
        }

        private fun Vol(cube: Box?, mmt: Array<Array<LongArray>>): Long {
            /* Compute sum over a box of any given statistic */
            return ((((mmt[cube!!.r1][cube.g1][cube.b1]
                    - mmt[cube.r1][cube.g1][cube.b0]
                    - mmt[cube.r1][cube.g0][cube.b1])
                    + mmt[cube.r1][cube.g0][cube.b0]
                    - mmt[cube.r0][cube.g1][cube.b1]
                    ) + mmt[cube.r0][cube.g1][cube.b0]
                    + mmt[cube.r0][cube.g0][cube.b1])
                    - mmt[cube.r0][cube.g0][cube.b0])
        }

        /* The next two routines allow a slightly more efficient calculation
         * of Vol() for a proposed subbox of a given box.  The sum of Top()
         * and Bottom() is the Vol() of a subbox split in the given direction
         * and with the specified new upper bound.
         */
        private fun Bottom(cube: Box?, dir: Int, mmt: Array<Array<LongArray>>): Long {
            /* Compute part of Vol(cube, mmt) that doesn't depend on r1, g1, or b1 */
            /* (depending on dir) */
            return when (dir) {
                RED -> ((-mmt[cube!!.r0][cube.g1][cube.b1]
                        + mmt[cube.r0][cube.g1][cube.b0]
                        + mmt[cube.r0][cube.g0][cube.b1])
                        - mmt[cube.r0][cube.g0][cube.b0])

                GREEN -> ((-mmt[cube!!.r1][cube.g0][cube.b1]
                        + mmt[cube.r1][cube.g0][cube.b0]
                        + mmt[cube.r0][cube.g0][cube.b1])
                        - mmt[cube.r0][cube.g0][cube.b0])

                BLUE -> ((-mmt[cube!!.r1][cube.g1][cube.b0]
                        + mmt[cube.r1][cube.g0][cube.b0]
                        + mmt[cube.r0][cube.g1][cube.b0])
                        - mmt[cube.r0][cube.g0][cube.b0])

                else -> 0
            }
        }

        private fun Top(cube: Box?, dir: Int, pos: Int, mmt: Array<Array<LongArray>>): Long {
            /* Compute remainder of Vol(cube, mmt), substituting pos for */
            /* r1, g1, or b1 (depending on dir) */
            return when (dir) {
                RED -> ((mmt[pos][cube!!.g1][cube.b1]
                        - mmt[pos][cube.g1][cube.b0]
                        - mmt[pos][cube.g0][cube.b1])
                        + mmt[pos][cube.g0][cube.b0])

                GREEN -> ((mmt[cube!!.r1][pos][cube.b1]
                        - mmt[cube.r1][pos][cube.b0]
                        - mmt[cube.r0][pos][cube.b1])
                        + mmt[cube.r0][pos][cube.b0])

                BLUE -> ((mmt[cube!!.r1][cube.g1][pos]
                        - mmt[cube.r1][cube.g0][pos]
                        - mmt[cube.r0][cube.g1][pos])
                        + mmt[cube.r0][cube.g0][pos])

                else -> 0
            }
        }

        private fun Var(cube: Box?): Float {
            /* Compute the weighted variance of a box */
            /* NB: as with the raw statistics, this is really the variance * size */
            val dr = Vol(cube, mr).toFloat()
            val dg = Vol(cube, mg).toFloat()
            val db = Vol(cube, mb).toFloat()
            val xx = ((((m2[cube!!.r1][cube.g1][cube.b1]
                    - m2[cube.r1][cube.g1][cube.b0]
                    - m2[cube.r1][cube.g0][cube.b1])
                    + m2[cube.r1][cube.g0][cube.b0]
                    - m2[cube.r0][cube.g1][cube.b1]
                    ) + m2[cube.r0][cube.g1][cube.b0]
                    + m2[cube.r0][cube.g0][cube.b1])
                    - m2[cube.r0][cube.g0][cube.b0])
            return xx - (dr * dr + dg * dg + db * db) / Vol(cube, wt)
        }

        /* We want to minimize the sum of the variances of two subboxes.
         * The sum(c^2) terms can be ignored since their sum over both subboxes
         * is the same (the sum for the whole box) no matter where we split.
         * The remaining terms have a minus sign in the variance formula,
         * so we drop the minus sign and MAXIMIZE the sum of the two terms.
         */
        private fun Maximize(
            cube: Box?, dir: Int, first: Int, last: Int, cut: IntArray,
            whole_r: Long, whole_g: Long, whole_b: Long, whole_w: Long
        ): Float {
            var half_r: Long
            var half_g: Long
            var half_b: Long
            var half_w: Long
            var temp: Float
            var max: Float

            val base_r = Bottom(cube, dir, mr)
            val base_g = Bottom(cube, dir, mg)
            val base_b = Bottom(cube, dir, mb)
            val base_w = Bottom(cube, dir, wt)

            max = 0.0f
            cut[0] = -1

            var i = first
            while (i < last) {
                half_r = base_r + Top(cube, dir, i, mr)
                half_g = base_g + Top(cube, dir, i, mg)
                half_b = base_b + Top(cube, dir, i, mb)
                half_w = base_w + Top(cube, dir, i, wt)
                /* now half_x is sum over lower half of box, if split at i */
                if (half_w == 0L) {
                    ++i
                    /* subbox could be empty of pixels! */
                    continue  /* never split into an empty box */
                }
                temp = (half_r * half_r + half_g * half_g + half_b * half_b) / half_w.toFloat()
                half_r = whole_r - half_r
                half_g = whole_g - half_g
                half_b = whole_b - half_b
                half_w = whole_w - half_w
                if (half_w == 0L) {
                    ++i
                    /* subbox could be empty of pixels! */
                    continue  /* never split into an empty box */
                }
                temp += (half_r * half_r + half_g * half_g + half_b * half_b) / half_w.toFloat()

                if (temp > max) {
                    max = temp
                    cut[0] = i
                }
                ++i
            }

            return max
        }

        private fun Cut(set1: Box?, set2: Box?): Boolean {
            val dir: Int
            val cutr = IntArray(1)
            val cutg = IntArray(1)
            val cutb = IntArray(1)
            val maxr: Float
            val maxg: Float
            val maxb: Float

            val whole_r = Vol(set1, mr)
            val whole_g = Vol(set1, mg)
            val whole_b = Vol(set1, mb)
            val whole_w = Vol(set1, wt)

            maxr = Maximize(
                set1, RED, set1!!.r0 + 1, set1.r1, cutr,
                whole_r, whole_g, whole_b, whole_w
            )
            maxg = Maximize(
                set1, GREEN, set1.g0 + 1, set1.g1, cutg,
                whole_r, whole_g, whole_b, whole_w
            )
            maxb = Maximize(
                set1, BLUE, set1.b0 + 1, set1.b1, cutb,
                whole_r, whole_g, whole_b, whole_w
            )

            if (maxr >= maxg && maxr >= maxb) {
                dir = RED
                if (cutr[0] < 0) return false /* can't split the box */
            } else if (maxg >= maxr && maxg >= maxb) dir = GREEN
            else dir = BLUE

            set2!!.r1 = set1.r1
            set2.g1 = set1.g1
            set2.b1 = set1.b1

            when (dir) {
                RED -> {
                    run {
                        set1.r1 = cutr[0]
                        set2.r0 = set1.r1
                    }
                    set2.g0 = set1.g0
                    set2.b0 = set1.b0
                }

                GREEN -> {
                    run {
                        set1.g1 = cutg[0]
                        set2.g0 = set1.g1
                    }
                    set2.r0 = set1.r0
                    set2.b0 = set1.b0
                }

                BLUE -> {
                    run {
                        set1.b1 = cutb[0]
                        set2.b0 = set1.b1
                    }
                    set2.r0 = set1.r0
                    set2.g0 = set1.g0
                }
            }
            set1.vol = (set1.r1 - set1.r0) * (set1.g1 - set1.g0) * (set1.b1 - set1.b0)
            set2.vol = (set2.r1 - set2.r0) * (set2.g1 - set2.g0) * (set2.b1 - set2.b0)

            return true
        }

        private fun Mark(cube: Box?, label: Int, tag: IntArray) {
            var g: Int
            var b: Int

            var r = cube!!.r0 + 1
            while (r <= cube.r1) {
                g = cube.g0 + 1
                while (g <= cube.g1) {
                    b = cube.b0 + 1
                    while (b <= cube.b1) {
                        tag[(r shl 10) + (r shl 6) + r + (g shl 5) + g + b] = label
                        ++b
                    }
                    ++g
                }
                ++r
            }
        }

        companion object {
            private const val MAXCOLOR = 256
            private const val RED = 2
            private const val GREEN = 1
            private const val BLUE = 0

            private const val QUANT_SIZE = 33 // quant size
        }
    }

    /**
     * A hash table using primitive integer keys.
     *
     * Based on
     * QuadraticProbingHashTable.java
     *
     *
     * Probing table implementation of hash tables.
     * Note that all "matching" is based on the equals method.
     *
     * @author Mark Allen Weiss
     */
    private class IntHashtable<E>(size: Int) {
        /** The array of HashEntry.  */
        private var array: Array<HashEntry<E>?> // The array of HashEntry
        private var currentSize = 0 // The number of occupied cells

        /**
         * Construct the hash table.
         * @param size the approximate initial size.
         */
        init {
            array = arrayOfNulls<HashEntry<E>?>(size)
            makeEmpty()
        }

        /**
         * Insert into the hash table. If the item is
         * already present, do nothing.
         * @param key the item to insert.
         */
        fun put(key: Int, value: E) {
            // Insert key as active
            val currentPos = locate(key)
            if (isActive(currentPos)) return

            array[currentPos] = HashEntry(key, value, true)

            // Rehash
            if (++currentSize > array.size / 2) rehash()
        }

        /**
         * Expand the hash table.
         */
        private fun rehash() {
            val oldArray = array

            // Create a new double-sized, empty table
            array = arrayOfNulls<HashEntry<E>?>(nextPrime(2 * oldArray.size))
            currentSize = 0

            // Copy table over
            for (i in oldArray.indices) if (oldArray[i] != null && oldArray[i]!!.isActive) put(
                oldArray[i]!!.key,
                oldArray[i]!!.value
            )

            return
        }

        /**
         * Method that performs quadratic probing resolution.
         * @param key the item to search for.
         * @return the index of the item.
         */
        private fun locate(key: Int): Int {
            var collisionNum = 0

            // And with the largest positive integer
            var currentPos = (key and 0x7FFFFFFF) % array.size

            while (array[currentPos] != null &&
                array[currentPos]!!.key != key
            ) {
                currentPos += 2 * ++collisionNum - 1 // Compute ith probe
                if (currentPos >= array.size) // Implement the mod
                    currentPos -= array.size
            }
            return currentPos
        }

        /**
         * Find an item in the hash table.
         * @param key the item to search for.
         * @return the value of the matching item.
         */
        fun get(key: Int): E? {
            val currentPos = locate(key)
            return if (isActive(currentPos)) array[currentPos]!!.value else null
        }

        /**
         * Return true if currentPos exists and is active.
         * @param currentPos the result of a call to findPos.
         * @return true if currentPos is active.
         */
        private fun isActive(currentPos: Int): Boolean {
            return array[currentPos] != null && array[currentPos]!!.isActive
        }

        /**
         * Make the hash table logically empty.
         */
        fun makeEmpty() {
            currentSize = 0
            for (i in array.indices) array[i] = null
        }

        // The basic entry stored in ProbingHashTable
        private class HashEntry<V>(
// the key
            var key: Int, // the value
            var value: V, // false if deleted
            var isActive: Boolean
        ) {
            @Suppress("unused")
            constructor(k: Int, `val`: V) : this(k, `val`, true)
        }

        companion object {
            /**
             * Internal method to find a prime number at least as large as n.
             * @param n the starting number (must be positive).
             * @return a prime number larger than or equal to n.
             */
            private fun nextPrime(n: Int): Int {
                var n = n
                if (n % 2 == 0) n++

                while (!isPrime(n)) {
                    n += 2
                }

                return n
            }

            /**
             * Internal method to test if a number is prime.
             * Not an efficient algorithm.
             * @param n the number to test.
             * @return the result of the test.
             */
            private fun isPrime(n: Int): Boolean {
                if (n == 2 || n == 3) return true

                if (n == 1 || n % 2 == 0) return false

                var i = 3
                while (i * i <= n) {
                    if (n % i == 0) return false
                    i += 2
                }

                return true
            }
        }
    }

    private class InverseColorMap @JvmOverloads constructor(
// Number of bits used in color quantization.
        private val bitsReserved: Int = 5
    ) {
        private val bitsDiscarded = 8 - bitsReserved // Number of discarded bits
        private val maxColorVal = 1 shl bitsReserved // Maximum value for each quantized color
        private val invMapLen = maxColorVal * maxColorVal * maxColorVal // Length of the inverse color map

        // The inverse color map itself
        private val invColorMap = ByteArray(invMapLen)

        // Fetch the forward color map index for this RGB
        fun getNearestColorIndex(red: Int, green: Int, blue: Int): Int {
            return invColorMap[(((red shr bitsDiscarded) shl (bitsReserved shl 1))) or
                    ((green shr bitsDiscarded) shl bitsReserved) or
                    (blue shr bitsDiscarded)].toInt() and 0xff
        }

        /**
         * Create an inverse color map using the input forward RGB map.
         */
        fun createInverseMap(no_of_colors: Int, colorPalette: IntArray) {
            var red: Int
            var green: Int
            var blue: Int
            var r: Int
            var g: Int
            var b: Int
            var rdist: Int
            var gdist: Int
            var bdist: Int
            var dist: Int
            var rinc: Int
            var ginc: Int
            var binc: Int

            val x = (1 shl bitsDiscarded) // Step size for each color
            val xsqr = (1 shl (bitsDiscarded + bitsDiscarded))
            val txsqr = xsqr + xsqr
            var buf_index: Int

            val dist_buf = IntArray(invMapLen)

            // Initialize the distance buffer array with the largest integer value
            run {
                var i = invMapLen
                while (--i >= 0) {
                    dist_buf[i] = 0x7FFFFFFF
                }
            }
            // Now loop through all the colors in the color map
            for (i in 0 until no_of_colors) {
                red = ((colorPalette[i] shr 16) and 0xff)
                green = ((colorPalette[i] shr 8) and 0xff)
                blue = (colorPalette[i] and 0xff)
                /**
                 * We start from the origin (0,0,0) of the quantized colors, calculate
                 * the distance between the cell center of the quantized colors and
                 * the current color map entry as follows:
                 * (rcenter * x + x/2) - red, where rcenter is the center of the
                 * Quantized red color map entry which is 0 since we start from 0.
                 */
                rdist = (x shr 1) - red // Red distance
                gdist = (x shr 1) - green // Green distance
                bdist = (x shr 1) - blue // Blue distance
                dist = rdist * rdist + gdist * gdist + bdist * bdist //The modular
                // The distance increment with each step value x
                rinc = txsqr - (red shl (bitsDiscarded + 1))
                ginc = txsqr - (green shl (bitsDiscarded + 1))
                binc = txsqr - (blue shl (bitsDiscarded + 1))

                buf_index = 0
                // Loop through quantized RGB space
                r = 0
                rdist = dist
                while (r < maxColorVal) {
                    g = 0
                    gdist = rdist
                    while (g < maxColorVal) {
                        b = 0
                        bdist = gdist
                        while (b < maxColorVal) {
                            if (bdist < dist_buf[buf_index]) {
                                dist_buf[buf_index] = bdist
                                invColorMap[buf_index] = i.toByte()
                            }
                            bdist += binc
                            binc += txsqr
                            buf_index++
                            b++
                        }
                        gdist += ginc
                        ginc += txsqr
                        g++
                    }
                    rdist += rinc
                    rinc += txsqr
                    r++
                }
            }
        }
    }

    companion object {
        // Define constants
        const val IMAGE_SEPARATOR: Byte = 0x2c // ","
        const val IMAGE_TRAILER: Byte = 0x3b // ";"
        const val EXTENSION_INTRODUCER: Byte = 0x21 // "!"
        const val GRAPHIC_CONTROL_LABEL: Byte = 0xf9.toByte()
        const val APPLICATION_EXTENSION_LABEL: Byte = 0xff.toByte()
        const val COMMENT_EXTENSION_LABEL: Byte = 0xfe.toByte()
        const val TEXT_EXTENSION_LABEL: Byte = 0x01

        private val MASK = intArrayOf(0x00, 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, 0xff)

        private fun getLogicalScreenSize(images: Array<BufferedImage>): Dimension {
            // Determine the logical screen dimension assuming all the frames have the same
            // left and top coordinates (0, 0)
            var logicalScreenWidth = 0
            var logicalScreenHeight = 0

            for (image in images) {
                if (image.width > logicalScreenWidth) logicalScreenWidth = image.width
                if (image.height > logicalScreenHeight) logicalScreenHeight = image.height
            }

            return Dimension(logicalScreenWidth, logicalScreenHeight)
        }

        private fun getLogicalScreenSize(frames: Array<GIFFrame>): Dimension {
            // Determine the logical screen dimension given all the frames with different
            // left and top coordinates.
            var logicalScreenWidth = 0
            var logicalScreenHeight = 0

            for (frame in frames) {
                val frameRightPosition = frame.frameWidth + frame.leftPosition
                val frameBottomPosition = frame.frameHeight + frame.topPosition
                if (frameRightPosition > logicalScreenWidth) logicalScreenWidth = frameRightPosition
                if (frameBottomPosition > logicalScreenHeight) logicalScreenHeight = frameBottomPosition
            }

            return Dimension(logicalScreenWidth, logicalScreenHeight)
        }

        /**
         * Retrieves RGB values from Raster. This seems to be the fastest way to get RGB values.
         *
         * TYPE_INT_RGB        = 1
         * TYPE_INT_ARGB       = 2
         * TYPE_INT_ARGB_PRE   = 3
         * TYPE_INT_BGR        = 4
         * TYPE_3BYTE_BGR      = 5
         * TYPE_4BYTE_ABGR     = 6
         * TYPE_4BYTE_ABGR_PRE = 7
         * TYPE_BYTE_GRAY      = 10
         * TYPE_BYTE_BINARY    = 12
         * TYPE_BYTE_INDEXED   = 13
         * TYPE_USHORT_GRAY    = 11
         * TYPE_USHORT_565_RGB = 8
         * TYPE_USHORT_555_RGB = 9
         * TYPE_CUSTOM         = 0.
         *
         * @param image input BufferedImage
         * @return integer array - default RGB color space representation
         */
        private fun getRGB(image: BufferedImage?): IntArray {
            // Get the BufferedImageType
            val type = image!!.type
            // Grab the internal data array
            val raster: Raster = image.raster
            val `object` = raster.getDataElements(0, 0, image.width, image.height, null)
            // Determine the data transfer type
            val transferType = raster.transferType
            // Get image dimension
            val imageWidth = image.width
            val imageHeight = image.height
            val imageSize = imageWidth * imageHeight
            var rgbs = IntArray(imageSize)
            when (transferType) {
                DataBuffer.TYPE_INT -> {
                    rgbs = `object` as IntArray
                    if (type == BufferedImage.TYPE_INT_ARGB_PRE) {
                        var i = 0
                        while (i < imageSize) {
                            val alpha = 255.0f / ((rgbs[i] shr 24) and 0xff)
                            val red = (((rgbs[i] shr 16) and 0xff) * alpha).toInt().toByte()
                            val green = (((rgbs[i] shr 8) and 0xff) * alpha).toInt().toByte()
                            val blue = ((rgbs[i] and 0xff) * alpha).toInt().toByte()
                            rgbs[i] =
                                (rgbs[i] and -0x1000000) or ((red.toInt() and 0xff) shl 16) or ((green.toInt() and 0xff) shl 8) or (blue.toInt() and 0xff)
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_INT_BGR) { // Convert BGR to RGB
                        var i = 0
                        while (i < rgbs.size) {
                            val blue = (rgbs[i] shr 16) and 0xff
                            val green = (rgbs[i] shr 8) and 0xff
                            val red = rgbs[i] and 0xff
                            rgbs[i] = -0x1000000 or (red shl 16) or (green shl 8) or blue
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_INT_RGB) {
                        var i = 0
                        while (i < rgbs.size) {
                            rgbs[i] = -0x1000000 or rgbs[i]
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_INT_ARGB) {
                        // Do nothing
                    } else {
                        return image.getRGB(0, 0, imageWidth, imageHeight, rgbs, 0, imageWidth)
                    }
                    return rgbs
                }

                DataBuffer.TYPE_BYTE -> {
                    val bpixels = `object` as ByteArray
                    // BufferedImage.getRGB() seems a bit faster in this case for small images.
                    if (type == BufferedImage.TYPE_BYTE_INDEXED || type == BufferedImage.TYPE_BYTE_BINARY) {
                        val indexModel = image.colorModel as IndexColorModel
                        val mapSize = indexModel.mapSize
                        val reds = ByteArray(mapSize)
                        val greens = ByteArray(mapSize)
                        val blues = ByteArray(mapSize)
                        val alphas = ByteArray(mapSize)
                        val palette = IntArray(mapSize)
                        indexModel.getReds(reds)
                        indexModel.getGreens(greens)
                        indexModel.getBlues(blues)
                        indexModel.getAlphas(alphas)
                        run {
                            var i = 0
                            while (i < mapSize) {
                                palette[i] =
                                    (alphas[i].toInt() and 0xff) shl 24 or ((reds[i].toInt() and 0xff) shl 16) or ((greens[i].toInt() and 0xff) shl 8) or (blues[i].toInt() and 0xff)
                                i++
                            }
                        }
                        var i = 0
                        while (i < imageSize) {
                            rgbs[i] = palette[bpixels[i].toInt() and 0xff]
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_4BYTE_ABGR) {
                        var i = 0
                        var index = 0
                        while (i < imageSize) {
                            rgbs[i] =
                                (((bpixels[index++].toInt() and 0xff) shl 16) or ((bpixels[index++].toInt() and 0xff) shl 8) or (bpixels[index++].toInt() and 0xff) or ((bpixels[index++].toInt() and 0xff) shl 24))
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_3BYTE_BGR) {
                        var i = 0
                        var index = 0
                        while (i < imageSize) {
                            rgbs[i] =
                                ((-0x1000000) or ((bpixels[index++].toInt() and 0xff) shl 16) or ((bpixels[index++].toInt() and 0xff) shl 8) or (bpixels[index++].toInt() and 0xff))
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_4BYTE_ABGR_PRE) {
                        var i = 0
                        var index = 0
                        while (i < imageSize) {
                            val alpha = 255.0f * (bpixels[index + 3].toInt() and 0xff)
                            val blue = ((bpixels[index + 2].toInt() and 0xff) * alpha).toInt().toByte()
                            val green = ((bpixels[index + 1].toInt() and 0xff) * alpha).toInt().toByte()
                            val red = ((bpixels[index].toInt() and 0xff) * alpha).toInt().toByte()
                            rgbs[i] =
                                (bpixels[index + 3].toInt() and -0x1000000) or ((red.toInt() and 0xff) shl 16) or ((green.toInt() and 0xff) shl 8) or (blue.toInt() and 0xff)
                            i++
                            index += 4
                        }
                    } else if (type == BufferedImage.TYPE_BYTE_GRAY) {
                        var i = 0
                        while (i < imageSize) {
                            rgbs[i] =
                                (-0x1000000) or ((bpixels[i].toInt() and 0xff) shl 16) or ((bpixels[i].toInt() and 0xff) shl 8) or (bpixels[i].toInt() and 0xff)
                            i++
                        }
                    } else {
                        return image.getRGB(0, 0, imageWidth, imageHeight, rgbs, 0, imageWidth)
                    }
                    return rgbs
                }

                DataBuffer.TYPE_USHORT -> {
                    val spixels = `object` as ShortArray
                    if (type == BufferedImage.TYPE_USHORT_GRAY) {
                        var i = 0
                        while (i < imageSize) {
                            val gray = ((spixels[i].toInt() shr 8) and 0xff)
                            rgbs[i] = (-0x1000000) or (gray shl 16) or (gray shl 8) or gray
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_USHORT_565_RGB) {
                        var i = 0
                        while (i < imageSize) {
                            val red = ((spixels[i].toInt() shr 11) and 0x1f)
                            val green = ((spixels[i].toInt() shr 5) and 0x3f)
                            val blue = (spixels[i].toInt() and 0x1f)
                            rgbs[i] = (-0x1000000) or (red shl 19) or (green shl 10) or (blue shl 3)
                            i++
                        }
                    } else if (type == BufferedImage.TYPE_USHORT_555_RGB) {
                        var i = 0
                        while (i < imageSize) {
                            val red = ((spixels[i].toInt() ushr 10) and 0x1f)
                            val green = ((spixels[i].toInt() ushr 5) and 0x1f)
                            val blue = (spixels[i].toInt() and 0x1f)
                            rgbs[i] = (-0x1000000) or (red shl 19) or (green shl 11) or (blue shl 3)
                            i++
                        }
                    } else {
                        return image.getRGB(0, 0, imageWidth, imageHeight, rgbs, 0, imageWidth)
                    }
                    return rgbs
                }

                else -> return image.getRGB(0, 0, imageWidth, imageHeight, rgbs, 0, imageWidth)
            }
        }

        private fun checkColorDepth(rgbTriplets: IntArray, newPixels: ByteArray, colorPalette: IntArray): IntArray {
            var index = 0
            var temp = 0
            var bitsPerPixel = 1
            var transparent_index = -1 // Transparent color index
            var transparent_color = -1 // Transparent color
            val colorInfo = IntArray(2) // Return value

            val rgbHash = IntHashtable<Int>(1023)

            for (i in rgbTriplets.indices) {
                temp = (rgbTriplets[i] and 0x00ffffff)

                if ((rgbTriplets[i] ushr 24) < 0x80) { // Transparent
                    if (transparent_index < 0) {
                        transparent_index = index
                        transparent_color = temp // Remember transparent color
                    }
                    temp = Int.MAX_VALUE
                }

                val entry = rgbHash.get(temp)

                if (entry != null) {
                    newPixels[i] = entry.toByte()
                } else {
                    if (index > 0xff) { // More than 256 colors, have to reduce
                        // Colors before saving as an indexed color image
                        colorInfo[0] = 24
                        return colorInfo
                    }
                    rgbHash.put(temp, index)
                    newPixels[i] = index.toByte()
                    colorPalette[index++] = ((0xff shl 24) or temp)
                }
            }
            if (transparent_index >= 0) // This line could be used to set a different background color
                colorPalette[transparent_index] = transparent_color
            // Return the actual bits per pixel and the transparent color index if any
            while ((1 shl bitsPerPixel) < index) bitsPerPixel++

            colorInfo[0] = bitsPerPixel
            colorInfo[1] = transparent_index

            return colorInfo
        }

        private fun reduceColorsDiffusionDither(
            rgbTriplets: IntArray,
            width: Int,
            height: Int,
            colorDepth: Int,
            newPixels: ByteArray,
            colorPalette: IntArray
        ): IntArray {
            require(!(colorDepth > 8 || colorDepth < 1)) { "Invalid color depth $colorDepth" }
            val colorInfo = IntArray(2)
            var colors = 0
            colors = WuQuant(rgbTriplets, 1 shl colorDepth).quantize(colorPalette, colorInfo)
            // Call Floyd-Steinberg dither
            dither_FloydSteinberg(rgbTriplets, width, height, newPixels, colors, colorPalette, colorInfo[1])

            // Return the actual bits per pixel and the transparent color index if any
            return colorInfo
        }

        // Color quantization
        private fun reduceColors(
            rgbTriplets: IntArray,
            colorDepth: Int,
            newPixels: ByteArray,
            colorPalette: IntArray
        ): IntArray {
            val colorInfo = IntArray(2)
            WuQuant(rgbTriplets, 1 shl colorDepth).quantize(newPixels, colorPalette, colorInfo)

            return colorInfo
        }

        private fun dither_FloydSteinberg(
            rgbTriplet: IntArray, width: Int, height: Int, newPixels: ByteArray, no_of_color: Int,
            colorPalette: IntArray, transparent_index: Int
        ) {
            var index = 0
            var index1 = 0
            var err1: Int
            var err2: Int
            var err3: Int
            var red: Int
            var green: Int
            var blue: Int
            // Define error arrays
            // Errors for the current line
            var tempErr: IntArray
            var thisErrR = IntArray(width + 2)
            var thisErrG = IntArray(width + 2)
            var thisErrB = IntArray(width + 2)
            // Errors for the following line
            var nextErrR = IntArray(width + 2)
            var nextErrG = IntArray(width + 2)
            var nextErrB = IntArray(width + 2)

            val invMap = InverseColorMap()
            invMap.createInverseMap(no_of_color, colorPalette)

            for (row in 0 until height) {
                var col = 0
                while (col < width) {
                    // Transparent, no dither
                    if ((rgbTriplet[index1] ushr 24) < 0x80) {
                        newPixels[index1] = transparent_index.toByte()
                        index1++
                        col++
                        continue
                    }

                    red = ((rgbTriplet[index1] and 0xff0000) ushr 16) + thisErrR[col + 1]
                    if (red > 255) red = 255
                    else if (red < 0) red = 0

                    green = ((rgbTriplet[index1] and 0x00ff00) ushr 8) + thisErrG[col + 1]
                    if (green > 255) green = 255
                    else if (green < 0) green = 0

                    blue = (rgbTriplet[index1] and 0x0000ff) + thisErrB[col + 1]
                    if (blue > 255) blue = 255
                    else if (blue < 0) blue = 0

                    // Find the nearest color index
                    index = invMap.getNearestColorIndex(red, green, blue)
                    newPixels[index1] = index.toByte() // The colorPalette index for this pixel

                    // Find errors for different channels
                    err1 = red - ((colorPalette[index] shr 16) and 0xff) // Red channel
                    err2 = green - ((colorPalette[index] shr 8) and 0xff) // Green channel
                    err3 = blue - (colorPalette[index] and 0xff) // Blue channel
                    // Diffuse error
                    // Red
                    thisErrR[col + 2] += ((err1 * 7) / 16)
                    nextErrR[col] += ((err1 * 3) / 16)
                    nextErrR[col + 1] += ((err1 * 5) / 16)
                    nextErrR[col + 2] += ((err1) / 16)
                    // Green
                    thisErrG[col + 2] += ((err2 * 7) / 16)
                    nextErrG[col] += ((err2 * 3) / 16)
                    nextErrG[col + 1] += ((err2 * 5) / 16)
                    nextErrG[col + 2] += ((err2) / 16)
                    // Blue
                    thisErrB[col + 2] += ((err3 * 7) / 16)
                    nextErrB[col] += ((err3 * 3) / 16)
                    nextErrB[col + 1] += ((err3 * 5) / 16)
                    nextErrB[col + 2] += ((err3) / 16)
                    index1++
                    col++
                }
                // We have finished one row, switch the error arrays
                tempErr = thisErrR
                thisErrR = nextErrR
                nextErrR = tempErr

                tempErr = thisErrG
                thisErrG = nextErrG
                nextErrG = tempErr

                tempErr = thisErrB
                thisErrB = nextErrB
                nextErrB = tempErr

                // Clear the error arrays
                Arrays.fill(nextErrR, 0)
                Arrays.fill(nextErrG, 0)
                Arrays.fill(nextErrB, 0)
            }
        }
    }
}