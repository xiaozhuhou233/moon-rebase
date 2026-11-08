package moon.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.zIndex
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.EncodedImageFormat
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Canvas as AwtCanvas
import java.awt.event.KeyEvent as AwtKeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.animateScrollBy
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

object Material3PreviewWindow {
    private val requestedVisible = AtomicBoolean(false)
    private var frame: JFrame? = null

    @JvmStatic
    fun isVisible(): Boolean = requestedVisible.get()

    @JvmStatic
    fun main(args: Array<String>) = setVisible(true)

    @JvmStatic
    fun toggle() = setVisible(!requestedVisible.get())

    @JvmStatic
    fun setVisible(visible: Boolean) {
        requestedVisible.set(visible)
        SwingUtilities.invokeLater { if (visible) showWindow() else closeWindow() }
    }

    private fun showWindow() {
        if (frame?.isDisplayable == true) {
            frame?.isVisible = true
            frame?.toFront()
            return
        }
        val panel = androidx.compose.ui.awt.ComposePanel().apply { setContent { Material3Preview() } }
        val window = JFrame("Moon | Material 3 Components").apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            contentPane.add(panel)
            setSize(920, 820)
            minimumSize = java.awt.Dimension(680, 560)
            setLocationRelativeTo(null)
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(event: WindowEvent) { setVisible(false) }
            })
        }
        frame = window
        window.isVisible = true
    }

    private fun closeWindow() {
        frame?.dispose()
        frame = null
    }
}

/** Software-backed Compose scene used by the in-game LWJGL texture bridge. */
object Material3GameScene {
    private val keySource = AwtCanvas()
    private var scene: ImageComposeScene? = null
    private var width = 0
    private var height = 0
    private var pressedButton = -1
    private var clickGuiModel: MaterialClickGuiModel? = null
    private var moduleListScroll: ScrollState? = null
    private var moduleListScope: CoroutineScope? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var densityScale = 1f
    private var scrollJob: kotlinx.coroutines.Job? = null
    private var scrollTarget = 0f
    private var panelMode = false
    private var panelW = 1
    private var panelH = 1
    private val closeRequested = mutableStateOf(false)
    private var closeDone = false
    private val inputRevision = mutableIntStateOf(0)

    @JvmStatic fun invalidateTextInput() { inputRevision.intValue++ }

    @Composable fun closeRequest(): Boolean = closeRequested.value
    @JvmStatic fun requestClose() { closeRequested.value = true }
    @JvmStatic fun markCloseFinished() { closeDone = true }
    @JvmStatic fun isCloseFinished(): Boolean = closeDone

    /** Pixel rectangle (x, y, w, h) that the GUI panel can occupy; only this part is read back and uploaded each frame. */
    @JvmStatic
    fun panelRegion(): IntArray {
        if (panelMode) return intArrayOf((width - panelW) / 2, (height - panelH) / 2, panelW, panelH)
        val d = densityScale * (clickGuiModel?.guiScale()?.coerceIn(0.5f, 1.5f) ?: 1f)
        val w = maxOf(1, minOf(width, (905f * d).toInt() + 32))
        val h = maxOf(1, minOf(height, (680f * d).toInt() + 32))
        return intArrayOf((width - w) / 2, (height - h) / 2, w, h)
    }

    // Keep Skia's raster surface separate from Minecraft's compatibility GL context.
    // Sharing a Skia GPU surface leaves generic/client vertex arrays and cached game
    // state inconsistent; the next chunk/cloud draw can crash inside the driver.
    // ImageComposeScene owns its surface, including resize and close.
    private var frameBitmap: Bitmap? = null
    @JvmField var benchRender = 0L
    @JvmField var benchRead = 0L

    /**
     * Renders a frame and returns a direct view over a reused Skia bitmap (tightly packed BGRA, premultiplied).
     * No per-frame heap arrays are allocated; the buffer is only valid until the next call.
     */
    @JvmStatic
    fun renderRegionBuffer(w: Int, h: Int): java.nio.ByteBuffer {
        val target = checkNotNull(scene) { "Material 3 scene is not initialized" }
        val t0 = System.nanoTime()
        val image = target.render(System.nanoTime())
        benchRender += System.nanoTime() - t0
        try {
            var bitmap = frameBitmap
            if (bitmap == null || bitmap.width != w || bitmap.height != h) {
                bitmap?.close()
                bitmap = Bitmap()
                check(bitmap.setImageInfo(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)) && bitmap.allocPixels()) { "Could not allocate Compose frame" }
                frameBitmap = bitmap
            }
            val t1 = System.nanoTime()
            check(image.readPixels(bitmap, 0, 0)) { "Could not read Compose frame" }
            benchRead += System.nanoTime() - t1
            val pixmap = checkNotNull(bitmap.peekPixels()) { "Could not access Compose pixels" }
            val addr = pixmap.addr
            pixmap.close()
            return org.lwjgl.system.MemoryUtil.memByteBuffer(addr, w * h * 4)
        } finally {
            image.close()
        }
    }

    @JvmStatic
    fun renderRegionBgra(x: Int, y: Int, w: Int, h: Int): ByteArray {
        val target = checkNotNull(scene) { "Material 3 scene is not initialized" }
        val image = target.render(System.nanoTime())
        val bitmap = Bitmap()
        try {
            check(bitmap.setImageInfo(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)) && bitmap.allocPixels()) { "Could not allocate Compose frame" }
            check(image.readPixels(bitmap, if (panelMode) 0 else x, if (panelMode) 0 else y)) { "Could not read Compose frame region" }
            val pixmap = checkNotNull(bitmap.peekPixels()) { "Could not access Compose pixels" }
            try {
                val data = checkNotNull(pixmap.buffer) { "Could not access Compose pixel buffer" }
                try {
                    return data.bytes
                } finally {
                    data.close()
                }
            } finally {
                pixmap.close()
            }
        } finally {
            bitmap.close()
            image.close()
        }
    }

    /** Loads fonts, typography and Compose/Skia classes on a daemon thread so the first ClickGUI open does not stall. */
    @JvmStatic
    fun prewarm(model: MaterialClickGuiModel?) {
        Thread({
            runCatching {
                ClickGuiTypography.hashCode()
                val warm = ImageComposeScene(960, 720, Density(1f)) {
                    if (model == null) MaterialTheme(colorScheme = clickGuiScheme(0), typography = ClickGuiTypography) { Text("Moon"); Switch(checked = true, onCheckedChange = {}) }
                    else MaterialClickGui(model)
                }
                try { repeat(3) { warm.render(System.nanoTime() + it * 16_000_000L).close() } } finally { warm.close() }
            }
        }, "Moon-Compose-Prewarm").apply { isDaemon = true }.start()
    }
    @Composable fun textInputRevision(): Int = inputRevision.intValue

    @JvmStatic
    fun openClickGui(nextWidth: Int, nextHeight: Int, model: MaterialClickGuiModel) {
        scene?.close()
        scene = null
        width = 0
        height = 0
        clickGuiModel = model
        closeRequested.value = false
        closeDone = false
        resize(nextWidth, nextHeight)
    }

    @JvmStatic
    fun resize(nextWidth: Int, nextHeight: Int) {
        require(nextWidth > 0 && nextHeight > 0)
        if (scene != null && width == nextWidth && height == nextHeight) return
        scene?.close()
        width = nextWidth
        height = nextHeight
        val designScale = minOf(1f, nextWidth / 905f, nextHeight / 680f)
        densityScale = designScale
        panelMode = clickGuiModel != null
        val panelScale = designScale * (clickGuiModel?.guiScale()?.coerceIn(0.5f, 1.5f) ?: 1f)
        panelW = if (panelMode) maxOf(1, minOf(nextWidth, (905f * panelScale).toInt() + 32)) else nextWidth
        panelH = if (panelMode) maxOf(1, minOf(nextHeight, (680f * panelScale).toInt() + 32)) else nextHeight
        scene = ImageComposeScene(panelW, panelH, Density(designScale)) {
            val model = clickGuiModel
            if (model == null) Material3Preview() else MaterialClickGui(model)
        }
        pressedButton = -1
    }

    @JvmStatic
    fun renderBgra(): ByteArray {
        val target = checkNotNull(scene) { "Material 3 scene is not initialized" }
        val image = target.render(System.nanoTime())
        val bitmap = Bitmap()
        try {
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
            check(bitmap.setImageInfo(info) && bitmap.allocPixels()) { "Could not allocate Compose frame" }
            if (!image.readPixels(bitmap)) {
                val encoded = checkNotNull(image.encodeToData(EncodedImageFormat.PNG, 100)) { "Could not encode Compose frame" }
                try {
                    val raster = Image.makeFromEncoded(encoded.bytes)
                    try {
                        check(raster.readPixels(bitmap)) { "Could not copy Compose pixels from raster fallback" }
                    } finally {
                        raster.close()
                    }
                } finally {
                    encoded.close()
                }
            }
            val pixmap = checkNotNull(bitmap.peekPixels()) { "Could not access Compose pixels" }
            try {
                val data = checkNotNull(pixmap.buffer) { "Could not access Compose pixel buffer" }
                try {
                    val required = width * height * 4
                    check(data.size >= required) { "Compose pixel buffer is too small: ${data.size} < $required" }
                    return data.bytes.copyOf(required)
                } finally {
                    data.close()
                }
            } finally {
                pixmap.close()
            }
        } finally {
            bitmap.close()
            image.close()
        }
    }

    @JvmStatic
    fun pointerMove(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        scene?.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(x, y),
            buttons = pointerButtons(),
        )
    }

    @JvmStatic
    fun pointerPress(x: Float, y: Float, button: Int) {
        pointerX = x
        pointerY = y
        pressedButton = button
        scene?.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(x, y),
            buttons = pointerButtons(),
            button = pointerButton(button),
        )
    }

    @JvmStatic
    fun pointerRelease(x: Float, y: Float, button: Int) {
        pointerX = x
        pointerY = y
        scene?.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(x, y),
            buttons = PointerButtons(),
            button = pointerButton(button),
        )
        pressedButton = -1
    }

    @JvmStatic
    fun hasInvalidations(): Boolean = scene?.hasInvalidations() ?: false

    @JvmStatic
    fun registerModuleListScroll(state: ScrollState, scope: CoroutineScope) {
        moduleListScroll = state
        moduleListScope = scope
    }

    @JvmStatic
    fun resetModuleListScroll() {
        val state = moduleListScroll ?: return
        val scope = moduleListScope ?: return
        MaterialClickGuiState.scrollOffset = 0
        scope.launch { state.scrollTo(0) }
    }

    @JvmStatic
    fun scroll(delta: Int) {
        if (delta == 0) return
        val state = moduleListScroll ?: return
        val scope = moduleListScope ?: return
        val scale = densityScale * (clickGuiModel?.guiScale()?.coerceIn(0.5f, 1.5f) ?: 1f)
        val notches = if (kotlin.math.abs(delta) >= 120) delta / 120f else if (delta > 0) 1f else -1f
        val base = if (scrollJob?.isActive == true) scrollTarget else state.value.toFloat()
        scrollTarget = (base - notches * 110f * scale).coerceIn(0f, state.maxValue.toFloat())
        val to = scrollTarget.toInt()
        scrollJob?.cancel()
        scrollJob = scope.launch { state.animateScrollTo(to, androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    }

    @OptIn(InternalComposeUiApi::class)
    @JvmStatic
    fun keyEvent(character: Char, lwjglKey: Int, pressed: Boolean = true) {
        val target = scene ?: return
        val now = System.currentTimeMillis()
        val keyCode = awtKeyCode(lwjglKey)
        target.sendKeyEvent(KeyEvent(key = Key(keyCode), type = if (pressed) KeyEventType.KeyDown else KeyEventType.KeyUp))
        if (pressed && character != AwtKeyEvent.CHAR_UNDEFINED && !character.isISOControl()) {
            target.sendKeyEvent(KeyEvent(key = Key.Unknown, type = KeyEventType.Unknown, codePoint = character.code))
        }
    }

    private fun modifiers(): Int {
        return 0
    }

    private fun awtKeyCode(key: Int): Int = when (key) {
        14 -> AwtKeyEvent.VK_BACK_SPACE
        15 -> AwtKeyEvent.VK_TAB
        28 -> AwtKeyEvent.VK_ENTER
        203 -> AwtKeyEvent.VK_LEFT
        205 -> AwtKeyEvent.VK_RIGHT
        200 -> AwtKeyEvent.VK_UP
        208 -> AwtKeyEvent.VK_DOWN
        211 -> AwtKeyEvent.VK_DELETE
        199 -> AwtKeyEvent.VK_HOME
        207 -> AwtKeyEvent.VK_END
        else -> when {
            key in 2..11 -> if (key == 11) AwtKeyEvent.VK_0 else AwtKeyEvent.VK_1 + key - 2
            key in 16..25 -> "QWERTYUIOP"[key - 16].code + 32
            key in 30..38 -> "ASDFGHJKL"[key - 30].code + 32
            key in 44..50 -> "ZXCVBNM"[key - 44].code + 32
            else -> key
        }
    }

    private fun pointerButtons() = PointerButtons(
        isPrimaryPressed = pressedButton == 0,
        isSecondaryPressed = pressedButton == 1,
        isTertiaryPressed = pressedButton == 2,
    )

    private fun pointerButton(button: Int) = when (button) {
        0 -> PointerButton.Primary
        1 -> PointerButton.Secondary
        else -> PointerButton.Tertiary
    }

    @JvmStatic
    fun close() {
        scene?.close()
        scene = null
        frameBitmap?.close()
        frameBitmap = null
        clickGuiModel = null
        moduleListScroll = null
        moduleListScope = null
        width = 0
        height = 0
        pressedButton = -1
        pointerX = 0f
        pointerY = 0f
    }
}

interface MaterialClickGuiModel {
    fun categoryCount(): Int
    fun categoryName(index: Int): String
    fun moduleCount(category: Int): Int
    fun moduleName(category: Int, module: Int): String
    fun moduleSuffix(category: Int, module: Int): String
    fun moduleEnabled(category: Int, module: Int): Boolean
    fun toggleModule(category: Int, module: Int)
    fun moduleBindLabel(category: Int, module: Int): String
    fun bindModule(category: Int, module: Int, key: Int)
    fun guiScale(): Float
    fun palette(): Int
    fun setSelectedCategory(category: Int)
    fun requestedModuleSettings(): Int
    fun settingCount(category: Int, module: Int): Int
    fun setting(category: Int, module: Int, setting: Int): MaterialSettingModel
    fun focusTextSetting(setting: MaterialSettingModel)
    fun closeGui()
}

interface MaterialModuleBoundsSink {
    fun clearModuleBounds()
    fun setModuleBounds(category: Int, module: Int, left: Float, top: Float, right: Float, bottom: Float)
}

object MaterialClickGuiState {
    var searchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableIntStateOf(0)
    var selectedModuleCategory by mutableIntStateOf(-1)
    var selectedModuleIndex by mutableIntStateOf(-1)
    var scrollOffset by mutableIntStateOf(0)
    var railExpanded by mutableStateOf(false)
    var bindCategory by mutableIntStateOf(-1)
    var bindModule by mutableIntStateOf(-1)
}

interface MaterialSettingModel {
    fun kind(): String
    fun name(): String
    fun description(): String
    fun valueText(): String
    fun number(): Float
    fun minimum(): Float
    fun maximum(): Float
    fun checked(): Boolean
    fun optionCount(): Int
    fun optionName(index: Int): String
    fun optionSelected(index: Int): Boolean
    fun toggleOption(index: Int)
    fun colorArgb(): Int
    fun setNumber(value: Float)
    fun setChecked(value: Boolean)
    fun setText(value: String)
    fun setColorArgb(value: Int)
    fun advanceChoice()
    fun choiceCount(): Int
    fun choiceName(index: Int): String
    fun choiceSelected(index: Int): Boolean
    fun selectChoice(index: Int)
}

private data class MaterialModuleAddress(val category: Int, val module: Int)

private val PreviewColors = darkColorScheme(
    primary = Color(0xFFB8C7FF),
    onPrimary = Color(0xFF172957),
    secondary = Color(0xFFB8CAC4),
    tertiary = Color(0xFFE6BBDD),
    background = Color(0xFF111318),
    surface = Color(0xFF191B22),
    surfaceVariant = Color(0xFF30333B),
)

private fun loadClickGuiFont(path: String, name: String, weight: FontWeight): androidx.compose.ui.text.font.Font? = runCatching {
    Material3GameScene::class.java.getResourceAsStream(path)?.use { Font(name, it.readBytes(), weight) }
}.getOrNull()

/** Prefers Google Sans (drop GoogleSans-Regular/Medium/Bold.ttf into resources/moon/fonts), falls back to Tahoma. */
private val ClickGuiFontFamily: FontFamily by lazy {
    val google = listOfNotNull(
        loadClickGuiFont("/moon/fonts/GoogleSans-Regular.ttf", "GoogleSans-Regular", FontWeight.Normal),
        loadClickGuiFont("/moon/fonts/GoogleSans-Medium.ttf", "GoogleSans-Medium", FontWeight.Medium),
        loadClickGuiFont("/moon/fonts/GoogleSans-Bold.ttf", "GoogleSans-Bold", FontWeight.Bold),
    )
    if (google.isNotEmpty()) FontFamily(google)
    else loadClickGuiFont("/moon/fonts/Tahoma.ttf", "Tahoma", FontWeight.Normal)?.let { FontFamily(it) } ?: FontFamily.Default
}

private val ClickGuiTypography: Typography by lazy {
    val base = Typography()
    val f = ClickGuiFontFamily
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = f), displayMedium = base.displayMedium.copy(fontFamily = f),
        displaySmall = base.displaySmall.copy(fontFamily = f), headlineLarge = base.headlineLarge.copy(fontFamily = f),
        headlineMedium = base.headlineMedium.copy(fontFamily = f), headlineSmall = base.headlineSmall.copy(fontFamily = f),
        titleLarge = base.titleLarge.copy(fontFamily = f), titleMedium = base.titleMedium.copy(fontFamily = f),
        titleSmall = base.titleSmall.copy(fontFamily = f), bodyLarge = base.bodyLarge.copy(fontFamily = f),
        bodyMedium = base.bodyMedium.copy(fontFamily = f), bodySmall = base.bodySmall.copy(fontFamily = f),
        labelLarge = base.labelLarge.copy(fontFamily = f), labelMedium = base.labelMedium.copy(fontFamily = f),
        labelSmall = base.labelSmall.copy(fontFamily = f),
    )
}

/** Full Material 3 tonal scheme (all container/surface roles filled, so Slider, Chip and tonal buttons follow the palette). */
private fun mdScheme(dark: Boolean, p: Long, onP: Long, pc: Long, onPc: Long, s: Long, sc: Long, onSc: Long, surf: Long, low: Long, cont: Long, high: Long, highest: Long, onS: Long, onSv: Long, out: Long, outV: Long): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(p), onPrimary = Color(onP), primaryContainer = Color(pc), onPrimaryContainer = Color(onPc),
        secondary = Color(s), onSecondary = Color(onP), secondaryContainer = Color(sc), onSecondaryContainer = Color(onSc),
        tertiary = Color(s), background = Color(if (dark) low else 0xFFFFFFFF), onBackground = Color(onS),
        surface = Color(surf), onSurface = Color(onS), surfaceVariant = Color(cont), onSurfaceVariant = Color(onSv),
        outline = Color(out), outlineVariant = Color(outV),
        surfaceContainerLowest = Color(if (dark) surf else 0xFFFFFFFF), surfaceContainerLow = Color(low), surfaceContainer = Color(cont),
        surfaceContainerHigh = Color(high), surfaceContainerHighest = Color(highest),
    )
}

/** Index matches ClickGuiModule.Palette ordinal: purple/blue/green light, then purple/blue/green dark. */
private fun clickGuiScheme(index: Int): ColorScheme = when (index) {
    1 -> mdScheme(false, 0xFF415F91, 0xFFFFFFFF, 0xFFD6E3FF, 0xFF001B3E, 0xFF565F71, 0xFFDAE2F9, 0xFF131C2B, 0xFFF9F9FF, 0xFFF3F3FA, 0xFFECEDF4, 0xFFE7E8EE, 0xFFE2E2E9, 0xFF191C20, 0xFF44474E, 0xFF74777F, 0xFFC4C6D0)
    2 -> mdScheme(false, 0xFF3B6939, 0xFFFFFFFF, 0xFFBCF0B4, 0xFF002204, 0xFF53634F, 0xFFD6E8CE, 0xFF111F0F, 0xFFF8FAF0, 0xFFF2F4EA, 0xFFECEFE5, 0xFFE6E9DF, 0xFFE1E3D9, 0xFF191D17, 0xFF43483F, 0xFF73796E, 0xFFC3C8BB)
    3 -> mdScheme(true, 0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF, 0xFFCCC2DC, 0xFF4A4458, 0xFFE8DEF8, 0xFF141218, 0xFF1D1B20, 0xFF211F26, 0xFF2B2930, 0xFF36343B, 0xFFE6E0E9, 0xFFCAC4D0, 0xFF938F99, 0xFF49454F)
    4 -> mdScheme(true, 0xFFAAC7FF, 0xFF0A305F, 0xFF284777, 0xFFD6E3FF, 0xFFBEC6DC, 0xFF3E4759, 0xFFDAE2F9, 0xFF111318, 0xFF191C20, 0xFF1D2024, 0xFF282A2F, 0xFF33353A, 0xFFE2E2E9, 0xFFC4C6D0, 0xFF8E9099, 0xFF44474E)
    5 -> mdScheme(true, 0xFFA1D39A, 0xFF0A390F, 0xFF235024, 0xFFBCF0B4, 0xFFBACCB3, 0xFF3B4B38, 0xFFD6E8CE, 0xFF10140F, 0xFF191D17, 0xFF1D211B, 0xFF272B25, 0xFF323630, 0xFFE1E3D9, 0xFFC3C8BB, 0xFF8D9387, 0xFF43483F)
    else -> mdScheme(false, 0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D, 0xFF625B71, 0xFFE8DEF8, 0xFF1D192B, 0xFFFEF7FF, 0xFFF7F2FA, 0xFFF3EDF7, 0xFFECE6F0, 0xFFE6E0E9, 0xFF1D1B20, 0xFF49454F, 0xFF79747E, 0xFFCAC4D0)
}

@Composable
private fun MaterialClickGui(model: MaterialClickGuiModel) {
    Material3GameScene.textInputRevision()
    val listScrollState = rememberScrollState(MaterialClickGuiState.scrollOffset)
    Material3GameScene.registerModuleListScroll(listScrollState, rememberCoroutineScope())
    LaunchedEffect(listScrollState) {
        snapshotFlow { listScrollState.value }.collect { MaterialClickGuiState.scrollOffset = it }
    }
    val categoryCount = model.categoryCount()
    var selectedCategory by remember {
        mutableIntStateOf(MaterialClickGuiState.selectedCategory.coerceIn(0, maxOf(0, categoryCount - 1)))
    }
    val restoredCategory = MaterialClickGuiState.selectedModuleCategory
    val restoredModule = MaterialClickGuiState.selectedModuleIndex
    var selectedModule by remember {
        mutableIntStateOf(if (restoredCategory in 0 until categoryCount &&
            restoredModule in 0 until model.moduleCount(restoredCategory)) restoredModule else -1)
    }
    var revision by remember { mutableIntStateOf(0) }
    var choiceDialog by remember { mutableStateOf<MaterialSettingModel?>(null) }
    var colorDialog by remember { mutableStateOf<MaterialSettingModel?>(null) }
    val selected = if (categoryCount == 0) 0 else selectedCategory.coerceIn(0, categoryCount - 1)
    val settingsCategory = MaterialClickGuiState.selectedModuleCategory
        .takeIf { it in 0 until categoryCount } ?: selected
    val isSearchActive = MaterialClickGuiState.searchActive
    val searchQuery = MaterialClickGuiState.searchQuery
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    val pageAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    val closeRequested = Material3GameScene.closeRequest()
    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            appear.animateTo(0f, androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutLinearInEasing))
            Material3GameScene.markCloseFinished()
        } else appear.animateTo(1f, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val pageKey = if (selectedModule >= 0) selectedModule + 1000 * (settingsCategory + 1) else if (isSearchActive) -1 else -2 - selected
    LaunchedEffect(pageKey) {
        pageAnim.snapTo(0f)
        pageAnim.animateTo(1f, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isSearchActive, MaterialClickGuiState.selectedModuleIndex) {
        if (isSearchActive) {
            if (MaterialClickGuiState.selectedModuleIndex >= 0) {
                focusManager.clearFocus()
            } else {
                if (selectedModule >= 0) {
                    selectedModule = -1
                    Material3GameScene.resetModuleListScroll()
                }
                withFrameNanos { }
                runCatching { searchFocusRequester.requestFocus() }
            }
        }
    }
    val requestedModule = model.requestedModuleSettings()
    if (requestedModule >= 0) {
        selectedCategory = MaterialClickGuiState.selectedCategory.coerceIn(0, maxOf(0, categoryCount - 1))
        selectedModule = requestedModule
        MaterialClickGuiState.selectedModuleCategory = selectedCategory
        MaterialClickGuiState.selectedModuleIndex = requestedModule
        Material3GameScene.resetModuleListScroll()
    }
    val displayedModules = if (isSearchActive) {
        if (searchQuery.isBlank()) emptyList() else buildList {
            for (category in 0 until categoryCount) {
                for (module in 0 until model.moduleCount(category)) {
                    if (model.moduleName(category, module).contains(searchQuery.trim(), ignoreCase = true)) {
                        add(MaterialModuleAddress(category, module))
                    }
                }
            }
        }
    } else (0 until model.moduleCount(selected)).map { MaterialModuleAddress(selected, it) }
    val rippleDriver = remember { androidx.compose.animation.core.Animatable(0f) }
    var rippleRow by remember { mutableIntStateOf(-1) }
    val bindIdxNow = displayedModules.indexOfFirst { it.category == MaterialClickGuiState.bindCategory && it.module == MaterialClickGuiState.bindModule }
    LaunchedEffect(bindIdxNow) {
        if (bindIdxNow >= 0) { rippleRow = bindIdxNow; rippleDriver.animateTo(1f, androidx.compose.animation.core.spring(0.62f, 380f)) }
        else rippleDriver.animateTo(0f, androidx.compose.animation.core.spring(0.42f, 320f))
    }
    val heroImage = remember { loadMaterialImage("hero-art") }

    val railW by androidx.compose.animation.core.animateFloatAsState(
        if (MaterialClickGuiState.railExpanded) 216f else 64f, androidx.compose.animation.core.spring(0.8f, 380f), label = "rail")
    MaterialTheme(colorScheme = clickGuiScheme(model.palette()), typography = ClickGuiTypography) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val parentDensity = LocalDensity.current
            val pageDensity = Density(parentDensity.density * model.guiScale().coerceIn(0.5f, 1.5f))
            CompositionLocalProvider(LocalDensity provides pageDensity) {
            Surface(
                modifier = Modifier.requiredSize(905.dp, 680.dp).graphicsLayer { val t = appear.value; alpha = t; scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                border = BorderStroke(8.dp, MaterialTheme.colorScheme.surface),
                shadowElevation = 0.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    ChoiceDialogOverlay(choiceDialog, { choiceDialog = null }) { revision++ }
                    ColorDialogOverlay(colorDialog, { colorDialog = null }) { revision++ }
                    Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            Modifier.weight(1f).height(44.dp).then(
                                if (isSearchActive) Modifier else Modifier.clickable {
                                    MaterialClickGuiState.searchActive = true
                                    MaterialClickGuiState.selectedModuleCategory = -1
                                    MaterialClickGuiState.selectedModuleIndex = -1
                                    selectedModule = -1
                                    Material3GameScene.resetModuleListScroll()
                                }
                            ),
                            shape = RoundedCornerShape(50.dp), color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⌕", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp), fontSize = 20.sp)
                                if (isSearchActive) {
                                    BasicTextField(
                                        value = androidx.compose.ui.text.input.TextFieldValue(searchQuery, androidx.compose.ui.text.TextRange(searchQuery.length)),
                                        onValueChange = { MaterialClickGuiState.searchQuery = it.text; Material3GameScene.resetModuleListScroll() },
                                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { inner ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (searchQuery.isEmpty()) Text("Search modules", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                                inner()
                                            }
                                        },
                                    )
                                    Box(
                                        Modifier.size(28.dp).clip(CircleShape).clickable {
                                            if (searchQuery.isEmpty()) MaterialClickGuiState.searchActive = false
                                            else MaterialClickGuiState.searchQuery = ""
                                        }, contentAlignment = Alignment.Center,
                                    ) { Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp) }
                                } else Text("Search", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("★", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Surface(Modifier.size(28.dp), shape = CircleShape, color = MaterialTheme.colorScheme.outline) {
                            Box(contentAlignment = Alignment.Center) { Text("M", color = Color.White, fontSize = 14.sp) }
                        }
                    }
                    MaterialRail(model, categoryCount, selected, isSearchActive, railW, MaterialClickGuiState.railExpanded,
                        { MaterialClickGuiState.railExpanded = !MaterialClickGuiState.railExpanded },
                        {
                            MaterialClickGuiState.searchActive = true
                            MaterialClickGuiState.selectedModuleCategory = -1
                            MaterialClickGuiState.selectedModuleIndex = -1
                            selectedModule = -1
                            Material3GameScene.resetModuleListScroll()
                        },
                        { index ->
                            selectedCategory = index
                            MaterialClickGuiState.selectedCategory = index
                            MaterialClickGuiState.searchActive = false
                            MaterialClickGuiState.selectedModuleCategory = -1
                            MaterialClickGuiState.selectedModuleIndex = -1
                            model.setSelectedCategory(index)
                            selectedModule = -1
                        })
                    Column(
                        Modifier.offset(x = (8f + railW).dp, y = 92.dp).width((873f - railW).dp).height(572.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (selectedModule >= 0) {
                                        selectedModule = -1
                                        MaterialClickGuiState.selectedModuleCategory = -1
                                        MaterialClickGuiState.selectedModuleIndex = -1
                                        Material3GameScene.resetModuleListScroll()
                                    } else if (isSearchActive) {
                                        MaterialClickGuiState.searchActive = false
                                        MaterialClickGuiState.searchQuery = ""
                                        Material3GameScene.resetModuleListScroll()
                                    } else Material3GameScene.requestClose()
                                }) { Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(
                                    when {
                                        selectedModule >= 0 -> model.moduleName(settingsCategory, selectedModule)
                                        isSearchActive -> "Search"
                                        categoryCount == 0 -> "Modules"
                                        else -> model.categoryName(selected)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            Column(Modifier.fillMaxSize().graphicsLayer { alpha = pageAnim.value; translationY = (1f - pageAnim.value) * 20.dp.toPx() }.verticalScroll(listScrollState).padding(bottom = 24.dp)) {
                            if (selectedModule < 0 && !isSearchActive) Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(224.dp).clip(RoundedCornerShape(28.dp))) {
                                if (heroImage != null) Image(heroImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.18f), BlendMode.Saturation))
                                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
                                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp)) {
                                    Text(if (categoryCount == 0) "Modules" else model.categoryName(selected),
                                        color = Color.White, fontSize = 45.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Kill others on pvp server", color = Color(0xCCF5EFF7), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            if (selectedModule >= 0) {
                                repeat(model.settingCount(settingsCategory, selectedModule)) { settingIndex ->
                                    val setting = model.setting(settingsCategory, selectedModule, settingIndex)
                                    SettingEditor(setting, { revision++ }, { choiceDialog = it }, { colorDialog = it }) { model.focusTextSetting(setting) }
                                }
                            } else if (isSearchActive && searchQuery.isBlank()) {
                                Text("Type a module name", Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            } else if (isSearchActive && displayedModules.isEmpty()) {
                                Text("No matching modules", Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                            if (selectedModule < 0) (model as? MaterialModuleBoundsSink)?.clearModuleBounds()
                            if (selectedModule < 0) displayedModules.forEachIndexed { rowIndex, entry ->
                                val moduleCategory = entry.category
                                val moduleIndex = entry.module
                                val enabled = model.moduleEnabled(moduleCategory, moduleIndex)
                                val suffix = model.moduleSuffix(moduleCategory, moduleIndex)
                                val firstRow = rowIndex == 0
                                val lastRow = rowIndex == displayedModules.size - 1
                                val rowShape = RoundedCornerShape(
                                    topStart = if (firstRow) 28.dp else 4.dp, topEnd = if (firstRow) 28.dp else 4.dp,
                                    bottomStart = if (lastRow) 28.dp else 4.dp, bottomEnd = if (lastRow) 28.dp else 4.dp)
                                val rowContainer = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                val rowContent = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                val rowSecondary = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                val bindDensity = LocalDensity.current.density
                                val bindRowIndex = displayedModules.indexOfFirst { it.category == MaterialClickGuiState.bindCategory && it.module == MaterialClickGuiState.bindModule }
                                val bindingHere = bindRowIndex == rowIndex
                                Column(Modifier.fillMaxWidth().graphicsLayer { translationX = (if (rowIndex == rippleRow) -96f else -18f * Math.pow(0.55, (kotlin.math.abs(rowIndex - rippleRow) - 1).toDouble()).toFloat()) * bindDensity * rippleDriver.value }.padding(start = 24.dp, end = 24.dp, top = if (firstRow) 16.dp else 2.dp)
                                    .clip(rowShape).background(rowContainer)
                                    .clickable { model.toggleModule(moduleCategory, moduleIndex); revision++ }
                                    .defaultMinSize(minHeight = 56.dp).onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionInRoot()
                                    (model as? MaterialModuleBoundsSink)?.setModuleBounds(
                                        moduleCategory, moduleIndex, position.x, position.y,
                                        position.x + coordinates.size.width, position.y + coordinates.size.height)
                                }) {
                                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(model.moduleName(moduleCategory, moduleIndex), Modifier.weight(1f).padding(end = 16.dp),
                                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)), color = rowContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val tail = if (isSearchActive) listOf(model.categoryName(moduleCategory), suffix).filter { it.isNotBlank() }.joinToString(" · ") else suffix
                                        if (tail.isNotBlank()) Text(tail, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)), color = rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val bindLabel = model.moduleBindLabel(moduleCategory, moduleIndex)
                                        if (bindingHere) Text("BIND", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = rowContent)
                                        else if (bindLabel.isNotBlank()) Text(bindLabel, Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(rowContent.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = rowSecondary)
                                        Box(Modifier.padding(start = 4.dp).size(32.dp).clip(CircleShape).clickable {
                                            if (bindingHere) { MaterialClickGuiState.bindCategory = -1; MaterialClickGuiState.bindModule = -1 }
                                            else { MaterialClickGuiState.bindCategory = moduleCategory; MaterialClickGuiState.bindModule = moduleIndex }
                                        }, contentAlignment = Alignment.Center) {
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(rowSecondary)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                        }
                    }
                }
            }
            }
        }
    revision.hashCode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingEditor(setting: MaterialSettingModel, onChange: () -> Unit, onChoice: (MaterialSettingModel) -> Unit, onColor: (MaterialSettingModel) -> Unit, onTextFocus: () -> Unit) {
    val title = setting.name().ifBlank { setting.kind() }
    val description = setting.description()
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (setting.kind()) {
            "BOOLEAN" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val boxOn = MaterialTheme.colorScheme.primary
                val boxOff = MaterialTheme.colorScheme.outline
                val boxTick = MaterialTheme.colorScheme.onPrimary
                Canvas(Modifier.size(40.dp).clickable {
                    setting.setChecked(!setting.checked())
                    onChange()
                }) {
                    val boxSize = 20.dp.toPx()
                    val left = (size.width - boxSize) * .5f
                    val top = (size.height - boxSize) * .5f
                    val boxOffset = Offset(left, top)
                    val boxDimensions = androidx.compose.ui.geometry.Size(boxSize, boxSize)
                    val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    val checked = setting.checked()
                    drawRoundRect(
                        color = if (checked) boxOn else Color.Transparent,
                        topLeft = boxOffset,
                        size = boxDimensions,
                        cornerRadius = radius,
                    )
                    if (!checked) drawRoundRect(
                        color = boxOff,
                        topLeft = boxOffset,
                        size = boxDimensions,
                        cornerRadius = radius,
                        style = Stroke(width = 1.5.dp.toPx()),
                    ) else {
                        val p = 5.dp.toPx()
                        drawLine(boxTick, Offset(left + p, top + boxSize * .52f), Offset(left + boxSize * .43f, top + boxSize - p), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(boxTick, Offset(left + boxSize * .43f, top + boxSize - p), Offset(left + boxSize - p, top + p), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            "FLOAT", "INTEGER" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(setting.valueText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(value = setting.number(), onValueChange = { setting.setNumber(it); onChange() },
                    valueRange = setting.minimum()..setting.maximum())
                if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "STRING" -> {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = setting.valueText(), onValueChange = { setting.setText(it); onChange() },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onTextFocus() },
                    singleLine = true, label = { Text(title) })
                if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "COLOR" -> {
                Row(Modifier.fillMaxWidth().clickable { onColor(setting) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(setting.valueText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(Modifier.size(32.dp), shape = CircleShape, color = Color(setting.colorArgb()), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { }
                }
                if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "ENUM", "MODE" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { onChoice(setting) }) { Text(setting.valueText() + "  ▾") }
            }
            "MULTIPLE_CHOICE" -> {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(setting.optionCount()) { index ->
                        FilterChip(selected = setting.optionSelected(index), onClick = { setting.toggleOption(index); onChange() },
                            label = { Text(setting.optionName(index)) })
                    }
                }
                if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "GROUP" -> Text(title, Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            else -> Text("$title: ${setting.valueText()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Material3Preview() {
    MaterialTheme(colorScheme = PreviewColors) {
        var selectedTab by remember { mutableIntStateOf(0) }
        var sliderValue by remember { mutableFloatStateOf(42f) }
        var switchOn by remember { mutableStateOf(true) }
        var checked by remember { mutableStateOf(true) }
        var selectedRadio by remember { mutableIntStateOf(0) }
        var field by remember { mutableStateOf("Moon") }
        var menuExpanded by remember { mutableStateOf(false) }
        var selectedMenu by remember { mutableStateOf("Normal") }
        var alertVisible by remember { mutableStateOf(false) }
        var chipSelected by remember { mutableStateOf(true) }

        Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Material 3", style = MaterialTheme.typography.headlineMedium)
                    Text("Compose Desktop component preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    listOf("Components", "Inputs", "Layout").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
                Section("Buttons and actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text("Filled") }
                        FilledTonalButton(onClick = {}) { Text("Tonal") }
                        OutlinedButton(onClick = {}) { Text("Outlined") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElevatedButton(onClick = {}) { Text("Elevated") }
                        TextButton(onClick = { alertVisible = true }) { Text("Text action") }
                    }
                }
                Section("Selection and chips") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                        Spacer(Modifier.width(8.dp)); Text("Switch")
                        Spacer(Modifier.width(20.dp))
                        Checkbox(checked = checked, onCheckedChange = { checked = it }); Text("Checkbox")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedRadio == 0, onClick = { selectedRadio = 0 }); Text("Default")
                        RadioButton(selected = selectedRadio == 1, onClick = { selectedRadio = 1 }); Text("Alternate")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("Assist") })
                        FilterChip(selected = chipSelected, onClick = { chipSelected = !chipSelected }, label = { Text("Filter") })
                        InputChip(selected = chipSelected, onClick = { chipSelected = !chipSelected }, label = { Text("Input") })
                    }
                }
                Section("Sliders and text fields") {
                    Text("Value: ${sliderValue.toInt()}%")
                    Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 0f..100f, steps = 9)
                    TextField(value = field, onValueChange = { field = it }, label = { Text("Filled text field") }, singleLine = true)
                    OutlinedTextField(value = field, onValueChange = { field = it }, label = { Text("Outlined text field") }, singleLine = true)
                }
                Section("Menu and feedback") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mode: $selectedMenu")
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { menuExpanded = true }) { Text("Open menu") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            listOf("Normal", "Focused", "Compact").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { selectedMenu = option; menuExpanded = false })
                            }
                        }
                    }
                    Snackbar { Text("Saved to latest.json") }
                    LinearProgressIndicator(progress = { .72f }, modifier = Modifier.fillMaxWidth())
                    CircularProgressIndicator(progress = { .72f })
                }
                Section("Surfaces") {
                    Card(Modifier.fillMaxWidth()) { Surface(Modifier.padding(16.dp)) { Text("Card surface") } }
                    ElevatedCard(Modifier.fillMaxWidth()) { Surface(Modifier.padding(16.dp)) { Text("Elevated card") } }
                    OutlinedCard(Modifier.fillMaxWidth()) { Surface(Modifier.padding(16.dp)) { Text("Outlined card") } }
                }
                Section("Dialog") {
                    Button(onClick = { alertVisible = true }) { Text("Show Material dialog") }
                }
            }
        }
        if (alertVisible) AlertDialog(
            onDismissRequest = { alertVisible = false },
            title = { Text("Material dialog") },
            text = { Text("This is the Compose Material 3 dialog surface.") },
            confirmButton = { TextButton(onClick = { alertVisible = false }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { alertVisible = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChoiceDialogOverlay(setting: MaterialSettingModel?, onDismiss: () -> Unit, onPicked: () -> Unit) {
    if (setting == null) return
    val scrimSource = remember { MutableInteractionSource() }
    val cardSource = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().zIndex(10f).background(Color.Black.copy(alpha = 0.32f)).clickable(interactionSource = scrimSource, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(min = 280.dp, max = 400.dp).heightIn(max = 440.dp).clickable(interactionSource = cardSource, indication = null) { }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(setting.name(), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    repeat(setting.choiceCount()) { index ->
                        Row(Modifier.fillMaxWidth().clickable { setting.selectChoice(index); onPicked(); onDismiss() }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = setting.choiceSelected(index), onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(setting.choiceName(index), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun RailIconSlot(glyph: String, color: Color) {
    val icon = railIcon(glyph)
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(color))
        else Text(glyph, fontSize = 16.sp, color = color)
    }
}

private val railIconCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

private fun loadMaterialImage(name: String): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val stream = Material3GameScene::class.java.getResourceAsStream("/moon/gui/material/$name.png")
        ?: return@runCatching null
    stream.use {
        val decoded = Image.makeFromEncoded(it.readBytes())
        try {
            decoded.toComposeImageBitmap()
        } finally {
            decoded.close()
        }
    }
}.getOrNull()

private fun railIconFile(key: String): String? = when (key) {
    "Combat" -> "combat"
    "Player" -> "player"
    "Exploit" -> "movement"
    "Visual" -> "visual"
    "Search" -> "search"
    else -> null
}

private fun railIcon(key: String): androidx.compose.ui.graphics.ImageBitmap? = railIconCache.getOrPut(key) {
    val file = railIconFile(key) ?: return@getOrPut null
    loadMaterialImage(file)
}

@Composable
private fun RailItem(label: String, glyph: String, active: Boolean, railW: Float, p: Float, onClick: () -> Unit) {
    val pillW = lerp(56f, railW - 16f, p)
    val pillH = lerp(32f, 56f, p)
    val pillX = (railW - pillW) / 2f
    val tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.width(railW.dp).height(lerp(44f, 56f, p).dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        Box(Modifier.offset(x = pillX.dp).size(pillW.dp, pillH.dp).clip(RoundedCornerShape((pillH / 2f).dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
        Box(Modifier.offset(x = (pillX + 16f).dp, y = lerp(4f, 16f, p).dp)) { RailIconSlot(glyph, tint) }
        if (p > 0.6f) Text(label, Modifier.offset(x = lerp(0f, pillX + 52f, p).dp, y = lerp(38f, 18f, p).dp).width(lerp(railW, pillW - 60f, p).dp),
            color = tint, fontSize = lerp(10f, 14f, p).sp, lineHeight = lerp(12f, 18f, p).sp,
            textAlign = if (p < 0.5f) TextAlign.Center else TextAlign.Start, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MaterialRail(model: MaterialClickGuiModel, categoryCount: Int, selected: Int, searchActive: Boolean, railW: Float, expanded: Boolean,
                         onToggle: () -> Unit, onSearch: () -> Unit, onCategory: (Int) -> Unit) {
    val p = ((railW - 64f) / 152f).coerceIn(0f, 1f)
    Column(Modifier.offset(x = 8.dp, y = 68.dp).width(railW.dp).fillMaxHeight().padding(top = 4.dp)) {
        Box(Modifier.padding(start = 8.dp).size(48.dp).clip(CircleShape).clickable { onToggle() }, contentAlignment = Alignment.Center) {
            Text(if (expanded) "☰‹" else "☰", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        val fabW = lerp(56f, railW - 16f, p)
        val fabTint = if (searchActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        Row(Modifier.padding(start = ((railW - fabW) / 2f).dp).width(fabW.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (searchActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onSearch() }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RailIconSlot("Search", fabTint)
            if (p > 0.05f) Text("Search", Modifier.padding(start = 12.dp).graphicsLayer { alpha = p }, style = MaterialTheme.typography.labelLarge, color = fabTint, maxLines = 1)
        }
        Spacer(Modifier.height(20.dp))
        repeat(categoryCount) { index ->
            val name = model.categoryName(index)
            RailItem(name, if (railIconFile(name) != null) name else name.take(1), !searchActive && selected == index, railW, p) { onCategory(index) }
        }
    }
}

@Composable
private fun ColorDialogOverlay(setting: MaterialSettingModel?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    if (setting == null) return
    val start = remember(setting) { val c = java.awt.Color(setting.colorArgb(), true); java.awt.Color.RGBtoHSB(c.red, c.green, c.blue, null) }
    var hue by remember(setting) { mutableFloatStateOf(start[0]) }
    var sat by remember(setting) { mutableFloatStateOf(start[1]) }
    var bri by remember(setting) { mutableFloatStateOf(start[2]) }
    val alphaBits = setting.colorArgb() and 0xFF000000.toInt()
    fun push() { setting.setColorArgb(alphaBits or (java.awt.Color.HSBtoRGB(hue, sat, bri) and 0xFFFFFF)); onChanged() }
    val scrimSource = remember { MutableInteractionSource() }
    val cardSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val presets = listOf(scheme.primary, scheme.primaryContainer, scheme.secondary, scheme.secondaryContainer, Color(0xFFFF0000), Color(0xFFFF9800), Color(0xFFFFEB3B), Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFF9C27B0))
    Box(Modifier.fillMaxSize().zIndex(10f).background(Color.Black.copy(alpha = 0.32f)).clickable(interactionSource = scrimSource, indication = null) { onDismiss() }, contentAlignment = Alignment.Center) {
        Surface(Modifier.width(360.dp).clickable(interactionSource = cardSource, indication = null) { }, shape = RoundedCornerShape(28.dp), color = scheme.surface, tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(setting.name(), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { o -> sat = (o.x / size.width).coerceIn(0f, 1f); bri = 1f - (o.y / size.height).coerceIn(0f, 1f); push() } }
                    .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); sat = (change.position.x / size.width).coerceIn(0f, 1f); bri = 1f - (change.position.y / size.height).coerceIn(0f, 1f); push() } }) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color(java.awt.Color.HSBtoRGB(hue, 1f, 1f)))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val c = Offset(sat * size.width, (1f - bri) * size.height)
                        drawCircle(Color.White, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        drawCircle(Color(java.awt.Color.HSBtoRGB(hue, sat, bri)), 6.dp.toPx(), c)
                    }
                }
                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) { detectTapGestures { o -> hue = (o.x / size.width).coerceIn(0f, 1f); push() } }
                    .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); hue = (change.position.x / size.width).coerceIn(0f, 1f); push() } }) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(List(7) { Color(java.awt.Color.HSBtoRGB(it / 6f, 1f, 1f)) }))
                        drawCircle(Color.White, 9.dp.toPx(), Offset(hue * size.width, size.height / 2f), style = Stroke(2.dp.toPx()))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { c ->
                        Box(Modifier.size(24.dp).clip(CircleShape).background(c).clickable {
                            val argb = c.toArgb(); val hs = java.awt.Color.RGBtoHSB((argb shr 16) and 255, (argb shr 8) and 255, argb and 255, null)
                            hue = hs[0]; sat = hs[1]; bri = hs[2]; push()
                        })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(32.dp), shape = CircleShape, color = Color(setting.colorArgb()), border = BorderStroke(1.dp, scheme.outline)) { }
                    Text(setting.valueText(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
