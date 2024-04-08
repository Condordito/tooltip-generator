import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.Dimension
import java.awt.Toolkit

@Composable
@Preview
fun App() {
    MaterialTheme {
        Row {
            val title = remember { mutableStateOf("") }
            val lore = remember { mutableStateOf("") }
            val weight = remember { mutableStateOf(1f) }
            val isRunning = remember { mutableStateOf(true) }
            val composable = ImageComposable(title, lore, isRunning)
            GradientContainer(
                Modifier.weight(weight.value).draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val d = delta.coerceIn(-20f, 20f) * 0.003
                        weight.value = (weight.value + d.toFloat()).coerceIn(1f, 2f)
                    },
                ),
            ) {
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
            GradientContainer(Modifier.weight(2f, true)) { composable() }
        }
    }
}

fun main() = application {
    Window(
        state = WindowState(
            size = DpSize(Util.WINDOW_WIDTH.dp, Util.WINDOW_HEIGHT.dp),
            position = WindowPosition(Alignment.Center),
        ),
        onCloseRequest = ::exitApplication,
        title = "Tooltip generator"
    ) {
        window.minimumSize = Dimension(
            (Util.WINDOW_WIDTH * 0.85).toInt(),
            (Util.WINDOW_HEIGHT * 0.85).toInt())
        window.maximumSize = Toolkit.getDefaultToolkit().screenSize
        App()
    }
}