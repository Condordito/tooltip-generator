import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import ir.mahozad.multiplatform.comshot.captureToImage
import java.awt.Toolkit
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

@Composable
fun ShadowedText(text: String, style: TextStyle) {
    Box {
        Text(
            text = text, modifier = Modifier.padding(top = 1.5.dp, start = 1.5.dp),
            style = style.copy(color = Util.decreaseBrightness(style.color, .5f))
        )
        Text(text = text, style = style)
    }

}

@Composable
@Preview
fun App() {
    MaterialTheme {
        val title = remember { mutableStateOf("") }
        val lore = remember { mutableStateOf("") }
        val isRunning = remember { mutableStateOf(true) }
        val composable = ImageComposable(title, lore, isRunning)

        Row {
            GradientContainer(Modifier.weight(1f)) {
                CustomTextField(
                    maxInputSize = 40,
                    placeholder = Util.ITEM_TITLE,
                    maxLines = 1,
                    label = "Title",
                    value = title
                )
                CustomTextField(
                    maxInputSize = 620,
                    placeholder = Util.ITEM_LORE.joinToString("\n"),
                    maxLines = 15,
                    modifier = Modifier.height(350.dp),
                    value = lore,
                    label = "Description"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            isRunning.value = false
                            val screenshot = captureToImage(composable).toAwtImage()
                            try {
                                File("tooltip.png").outputStream().use { out ->
                                    ImageIO.write(screenshot, "png", out)
                                }
                                isRunning.value = true
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }

                        },
                        modifier = Modifier.width(175.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.DarkGray,
                            contentColor = Color.White
                        )
                    ) { Text("Save image") }
                }
            }
            GradientContainer(Modifier.weight(1f)) { composable() }
        }
    }
}

fun main() = application {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    Window(
        state = WindowState(
            size = DpSize(
                (screenSize.width * Util.WINDOW_RATIO).dp,
                (screenSize.height * Util.WINDOW_RATIO * 0.8).dp
            ),
            position = WindowPosition(Alignment.Center)
        ),
        onCloseRequest = ::exitApplication,
        title = "Tooltip generator",
        resizable = false
    ) { App() }
}