import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

@Composable
fun TooltipLine(text: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(25.dp)) {
        Util.convert(text)
            .let { MiniMessage.miniMessage().deserialize(it) }
            .let { GsonComponentSerializer.gson().serializeToTree(it) }
            .let { ChatComponent(it, 20.sp) }
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
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp),
        colors = TextFieldDefaults.textFieldColors(
            textColor = Color.Black,
            focusedIndicatorColor = Color.Black,
        ),
        label = { Text(label, color = Color.Black) },
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
fun GradientContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Gray.copy(alpha = 0.1f), Color.Gray)
                )
            ),
        color = Color.Transparent,
        contentColor = MaterialTheme.colors.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = null,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            content()
        }
    }
}