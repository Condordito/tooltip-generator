import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.Toolkit

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
                    placeholder = Util.ITEM_TITLE,
                    label = "Title", value = title,
                    singleLine = true
                )
                CustomTextField(
                    placeholder = Util.ITEM_LORE.joinToString("\n"),
                    modifier = Modifier.weight(3f),
                    label = "Description", value = lore
                )
                SaveButton(composable, isRunning)
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
            position = WindowPosition(Alignment.Center),
        ),
        onCloseRequest = ::exitApplication,
        title = "Tooltip generator",
    ) { App() }
}