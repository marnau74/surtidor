package es.surtidor.escritorio

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import es.surtidor.App
import es.surtidor.moduloComun
import es.surtidor.moduloDePlataforma
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(moduloComun, moduloDePlataforma) }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Surtidor · precios de los carburantes",
            state = rememberWindowState(size = DpSize(1100.dp, 820.dp)),
        ) {
            App()
        }
    }
}
