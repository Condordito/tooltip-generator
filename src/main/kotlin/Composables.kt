import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer

@Composable
fun TextComposable(text: String, textStyle: TextStyle, obfuscated: Boolean = false, isRunning: MutableState<Boolean>) {
    if (!obfuscated) return ShadowedText(text, textStyle)
    var value by remember { mutableStateOf(Util.randomString(text.length)) }
    LaunchedEffect(text) {
        while (isActive && isRunning.value) {
            value = Util.randomString(text.length)
            delay(60)
        }
    }
    ShadowedText(value, textStyle.copy(fontFamily = FontFamily.Monospace))
}

@Composable
fun ChatComponent(element: JsonElement, fontSize: TextUnit, isRunning: MutableState<Boolean>) {
    var ts = TextStyle.Default.copy(Color.White, fontSize)
    if (element.isJsonPrimitive)
        return TextComposable(element.asJsonPrimitive.asString, ts, isRunning = isRunning)
    val jsonObject = element.asJsonObject

    jsonObject["color"]?.asString
        ?.let {
            if (it.startsWith("#")) java.awt.Color.decode(it)
            else Util.COLOR_MAP[it.uppercase()]
        }?.let { ts = ts.copy(color = Color(it.red, it.green, it.blue)) }

    jsonObject["italic"]?.let { ts = ts.copy(fontStyle = FontStyle.Italic) }
    jsonObject["bold"]?.let { ts = ts.copy(fontWeight = FontWeight.Bold) }
    jsonObject["strikethrough"]?.let { ts = ts.copy(textDecoration = TextDecoration.LineThrough) }
    jsonObject["underlined"]
        ?.let { ts.textDecoration ?: TextDecoration.None }
        ?.let { ts = ts.copy(textDecoration = it + TextDecoration.Underline) }
    jsonObject["text"]?.asString?.let {
        if (it.isNotEmpty())
            TextComposable(it, ts, jsonObject["obfuscated"]?.asBoolean ?: false, isRunning)
    }
    jsonObject["extra"]?.asJsonArray?.forEach { ChatComponent(it, fontSize, isRunning) }
}

@Composable
fun TooltipLine(text: String, isRunning: MutableState<Boolean>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(55.dp)) {
        Util.convertFromLegacy(text)
            .let { MiniMessage.miniMessage().deserialize(it) }
            .let { GsonComponentSerializer.gson().serializeToTree(it) }
            .let { ChatComponent(it, 40.sp, isRunning) }
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
) {
    OutlinedTextField(
        value = value.value,
        onValueChange = {
            if (it.length > maxInputSize) return@OutlinedTextField
            value.value = it
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

@Composable
fun ImageComposable(
    title: MutableState<String>,
    lore: MutableState<String>,
    isRunning: MutableState<Boolean>,
): @Composable () -> Unit = remember {
    @Composable {
        Column(
            modifier = Modifier
                .border(4.dp, Util.LORE_BG_COLOR)
                .border(7.dp, Util.LORE_BORDER_COLOR)
                .background(Util.LORE_BG_COLOR)
                .padding(20.dp)
                .padding(end = 2.dp)
        ) {
            TooltipLine(title.value.takeIf { it.isNotBlank() } ?: Util.ITEM_TITLE, isRunning)
            Spacer(Modifier.height(16.dp))
            let {
                lore.value.takeIf { it.isNotBlank() }?.split("\n") ?: Util.ITEM_LORE
            }.forEach { TooltipLine(it, isRunning) }
        }
    }
}