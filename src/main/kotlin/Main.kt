import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

const val ITEM_TITLE = "&fDiamond Sword"
val ITEM_LORE = listOf(
    "&fWhen in main hand:",
    "  &21.6 Attack Speed",
    "  &27 Attack Damage",
    "&8minecraft:diamond_sword",
    "&8NBT: 1 tag(s)"
)

@Composable
fun GradientContainer(bgColor: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .background(
                brush = Brush.linearGradient(colors = listOf(bgColor.copy(alpha = 0.1f), bgColor))
            ),
        color = Color.Transparent,
        contentColor = MaterialTheme.colors.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
fun CustomTextField(
    maxInputSize: Int,
    placeholder: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
    value: MutableState<String>,
    label: String,
    onValueChange: () -> Unit
) {


    OutlinedTextField(
        value = value.value,
        onValueChange = {
            if (it.length > maxInputSize) return@OutlinedTextField
            value.value = it
            onValueChange()
        },
        placeholder = { Text(placeholder) },
        maxLines = maxLines,
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp, 20.dp),
        colors = TextFieldDefaults.textFieldColors(
            textColor = Color.Black,
            focusedIndicatorColor = Color.Black,
        ),
        label = { Text(label, color = Color.Black) },
        shape = RoundedCornerShape(8.dp)
    )
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
            GradientContainer(Color.Gray, Modifier.weight(1f)) {
                CustomTextField(
                    maxInputSize = 50,
                    placeholder = ITEM_TITLE,
                    maxLines = 1,
                    label = "Title",
                    value = title,
                    onValueChange = onValueChange
                )
                CustomTextField(
                    maxInputSize = 625,
                    placeholder = ITEM_LORE.joinToString("\n"),
                    maxLines = 15,
                    modifier = Modifier.height(400.dp),
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
                                .setName(title.value)
                                .setLore(lore.value)
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
            GradientContainer(Color.Gray, Modifier.weight(1f)) {
                Image(
                    modifier = Modifier.fillMaxWidth(),
                    painter = BitmapPainter(image = image.value),
                    contentDescription = null
                )

            }
        }
    }
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