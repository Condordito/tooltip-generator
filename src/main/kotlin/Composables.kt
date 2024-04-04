import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val height = remember { (Util.TEXT_SIZE + 10).dp }
    val textSize = remember { Util.TEXT_SIZE.sp }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(height)) {
        Util.convertFromLegacy(text)
            .let { MiniMessage.miniMessage().deserialize(it) }
            .let { GsonComponentSerializer.gson().serializeToTree(it) }
            .let { ChatComponent(it, textSize, isRunning) }
    }
}

@Composable
fun CustomTextField(
    placeholder: String,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
    value: MutableState<String>,
    label: String,
) {
    OutlinedTextField(
        value = value.value,
        onValueChange = { value.value = it },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
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
        ) {
            TooltipLine(title.value.takeIf { it.isNotBlank() } ?: Util.ITEM_TITLE, isRunning)
            Spacer(Modifier.height(16.dp))
            let {
                lore.value.takeIf { it.isNotBlank() }?.split("\n") ?: Util.ITEM_LORE
            }.forEach { TooltipLine(it, isRunning) }
        }
    }
}

@Composable
fun SaveButton(composable: @Composable () -> Unit, isRunning: MutableState<Boolean>) {
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (isPressed) return@Button
            isPressed = true
            scope.launch { Util.saveOutput(composable, isRunning) }
            scope.launch { delay(2000); isPressed = false }
        },
        modifier = Modifier.width(175.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isPressed) Color(0xfff44336) else Color.DarkGray,
            contentColor = Color.White
        )
    ) { Text("Save output") }
}