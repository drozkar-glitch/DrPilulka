package cz.mares.drpilulka

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow

// Vstupni bod pro Web / WasmJS verzi hry Dr. Pilulka
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Dr. Pilulka") {
        App()
    }
}
