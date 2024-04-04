import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDecoration.Companion.LineThrough
import androidx.compose.ui.text.style.TextDecoration.Companion.Underline
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.google.gson.JsonElement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun decreaseBrightness(color: Color, factor: Float): Color = color.copy(
    red = (color.red * (1 - factor)).coerceIn(0f, 1f),
    green = (color.green * (1 - factor)).coerceIn(0f, 1f),
    blue = (color.blue * (1 - factor)).coerceIn(0f, 1f)
)

@Composable
fun ShadowedText(text: String, style: TextStyle) {
    Box {
        Text(
            text = text, modifier = Modifier.padding(top = 1.5.dp, start = 1.5.dp),
            style = style.copy(color = decreaseBrightness(style.color, .5f))
        )
        Text(text = text, style = style)
    }

}
@Composable
fun TextComposable(text: String, textStyle: TextStyle, obfuscated: Boolean = false) {
    if (!obfuscated) return ShadowedText(text, textStyle)
    var value by remember { mutableStateOf(Util.getRandomString(Minecraft.MAGIC_CHARS, text.length)) }
    LaunchedEffect(text) {
        while (isActive) {
            value = Util.getRandomString(Minecraft.MAGIC_CHARS, text.length)
            delay(60)
        }
    }
    ShadowedText(value, textStyle.copy(fontFamily = FontFamily.Monospace))
}


@Composable
@Preview
fun App() {
    MaterialTheme {
        val title = remember { mutableStateOf("") }
        val lore = remember { mutableStateOf("") }
        val image = remember { mutableStateOf(getDefaultImage()) }

        var debounceJob by remember { mutableStateOf<Job?>(null) }
        val coroutineScope = rememberCoroutineScope()

        val onValueChange = {
            debounceJob?.cancel()
            debounceJob = coroutineScope.launch {
                delay(1000)
                var nextTitle = title.value
                var nextLore = lore.value
                if (title.value.isEmpty() && lore.value.isEmpty()) {
                    nextTitle = ITEM_TITLE
                    nextLore = ITEM_LORE.joinToString("\n")
                }
                val imageBuffer = TooltipBuilder()
                    .setColorCode("&")
                    .setName(nextTitle)
                    .setLore(nextLore)
                    .asBufferedImage()
                image.value = loadImageBitmap(ByteArrayOutputStream().use {
                    ImageIO.write(imageBuffer, "png", it)
                    ByteArrayInputStream(it.toByteArray())
                })
            }
        }

        Row {
            GradientContainer(Modifier.weight(1f)) {
                CustomTextField(
                    maxInputSize = 40,
                    placeholder = ITEM_TITLE,
                    maxLines = 1,
                    label = "Title",
                    value = title,
                    onValueChange = onValueChange
                )
                CustomTextField(
                    maxInputSize = 620,
                    placeholder = ITEM_LORE.joinToString("\n"),
                    maxLines = 15,
                    modifier = Modifier.height(350.dp),
                    value = lore,
                    label = "Description",
                    onValueChange = onValueChange,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            TooltipBuilder()
                                .setColorCode("&")
                                .setName(title.value.ifBlank { ITEM_TITLE })
                                .setLore(lore.value.ifBlank { ITEM_LORE.joinToString("\n") })
                                .save("tooltip.png")
                        },
                        modifier = Modifier.width(175.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.DarkGray,
                            contentColor = Color.White
                        )
                    ) { Text("Save image") }
                }
            }
            GradientContainer(Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .border(4.dp, LORE_BG_COLOR)
                        .border(7.dp, LORE_BORDER_COLOR)
                        .background(LORE_BG_COLOR)
                        .padding(20.dp)
                        .padding(end = 2.dp)
                ) {
                    TooltipLine(title.value.takeIf { it.isNotBlank() } ?: ITEM_TITLE)
                    Spacer(Modifier.height(16.dp))
                    let {
                        lore.value.takeIf { it.isNotBlank() }?.split("\n") ?: ITEM_LORE
                    }.forEach { TooltipLine(it) }
                }
            }
        }
    }
}

@Composable
fun ChatComponent(element: JsonElement, fontSize: TextUnit) {
    var ts = TextStyle.Default.copy(Color.White, fontSize)
    if (element.isJsonPrimitive) return TextComposable(element.asJsonPrimitive.asString, ts)
    val jsonObject = element.asJsonObject

    jsonObject["color"]?.asString
        ?.let {
            if (it.startsWith("#")) java.awt.Color.decode(it)
            else Minecraft.Color.valueOf(it.uppercase()).color
        }?.let { ts = ts.copy(color = Color(it.red, it.green, it.blue)) }

    jsonObject["italic"]?.let { ts = ts.copy(fontStyle = FontStyle.Italic) }
    jsonObject["bold"]?.let { ts = ts.copy(fontWeight = FontWeight.Bold) }
    jsonObject["strikethrough"]?.let { ts = ts.copy(textDecoration = LineThrough) }
    jsonObject["underlined"]
        ?.let { ts.textDecoration ?: TextDecoration.None }
        ?.let { ts = ts.copy(textDecoration = it + Underline) }
    jsonObject["text"]?.asString?.let {
        if (it.isNotEmpty())
            TextComposable(it, ts, jsonObject["obfuscated"]?.asBoolean ?: false)
    }
    jsonObject["extra"]?.asJsonArray?.forEach { ChatComponent(it, fontSize) }
}

fun getDefaultImage(): ImageBitmap {
    val bufferedImage = TooltipBuilder()
        .setColorCode("&")
        .setName(ITEM_TITLE)
        .setLore(ITEM_LORE.toMutableList())
        .asBufferedImage()
    return loadImageBitmap(ByteArrayOutputStream().use {
        ImageIO.write(bufferedImage, "png", it)
        ByteArrayInputStream(it.toByteArray())
    })
}

fun main() = application {
    Window(
        state = WindowState(
            size = DpSize(1000.dp, 600.dp),
            position = WindowPosition(Alignment.Center)
        ),
        onCloseRequest = ::exitApplication,
        title = "Tooltip generator",
        resizable = false
    ) { App() }
}