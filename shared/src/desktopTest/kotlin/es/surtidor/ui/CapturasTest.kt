package es.surtidor.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import es.surtidor.datos.remoto.RespuestaDto
import es.surtidor.datos.remoto.aDominio
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Coordenadas
import es.surtidor.dominio.Favorita
import es.surtidor.dominio.Orden
import es.surtidor.dominio.PrecioDelDia
import es.surtidor.dominio.Provincia
import es.surtidor.dominio.ordenar
import es.surtidor.dominio.resumir
import es.surtidor.presentacion.EstadoDetalle
import es.surtidor.presentacion.EstadoHistorico
import es.surtidor.presentacion.EstadoLista
import es.surtidor.presentacion.Seleccion
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Genera las capturas del README con datos reales: `./gradlew :shared:desktopTest -Pcapturas=<carpeta>` con una
 * carpeta que tenga `actual.json` (precios de una provincia) y `hist_dd-MM-yyyy.json` (sus días anteriores), tal como
 * los da la API. Sin la propiedad, la prueba no hace nada.
 */
@OptIn(ExperimentalTestApi::class)
class CapturasTest {
    private val carpeta = System.getProperty("surtidor.capturas").orEmpty()
    private val salida = File(System.getProperty("surtidor.salidaCapturas", "build/capturas"))
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun generarCapturas() {
        if (carpeta.isBlank()) return
        salida.mkdirs()
        val actual = json.decodeFromString<RespuestaDto>(File(carpeta, "actual.json").readText()).aDominio()
        val historicos =
            File(carpeta).listFiles { f -> f.name.startsWith("hist_") }!!.map { f ->
                val (d, m, a) =
                    f.name
                        .removePrefix("hist_")
                        .removeSuffix(".json")
                        .split("-")
                        .map { it.toInt() }
                LocalDate(a, m, d) to json.decodeFromString<RespuestaDto>(f.readText()).aDominio()
            }
        val carburante = Carburante.GASOLINA_95
        // Desde la plaza Circular de Murcia, como si el usuario hubiera pulsado «Cerca de mí».
        val desde = Coordenadas(37.9917, -1.1301)
        val lista =
            EstadoLista(
                seleccion =
                    Seleccion(
                        provinciaId = "30",
                        carburante = carburante,
                        orden = Orden.PRECIO,
                        ubicacion = desde,
                    ),
                provincias = listOf(Provincia("30", "Murcia")),
                gasolineras = ordenar(actual.gasolineras, carburante, Orden.PRECIO, desde),
                resumen = resumir(actual.gasolineras.mapNotNull { it.precios[carburante] }),
                publicadosEn = actual.publicadosEn,
                cargando = false,
            )
        val elegida = lista.gasolineras.first { it.gasolinera.precios.size >= 3 }.gasolinera
        val historico =
            (
                historicos.mapNotNull { (dia, p) ->
                    p.gasolineras
                        .firstOrNull { it.id == elegida.id }
                        ?.precios
                        ?.get(
                            carburante,
                        )?.let { PrecioDelDia(dia, it) }
                } +
                    listOfNotNull(elegida.precios[carburante]?.let { PrecioDelDia(LocalDate(2026, 10, 13), it) })
            ).sortedBy { it.fecha }
        val detalle =
            EstadoDetalle(
                cargando = false,
                gasolinera = elegida,
                carburante = carburante,
                mediaProvincia = lista.resumen?.media,
                historico = EstadoHistorico.Datos(historico),
                favorita = Favorita(elegida.id, carburante, umbral = 1.6),
            )

        capturar("escritorio-lista.png", 1100, 820, 1f, oscuro = false) {
            PantallaLista(lista, AccionesLista(pedirUbicacion = {}))
        }
        capturar("escritorio-ficha.png", 1100, 820, 1f, oscuro = false) { PantallaDetalle(detalle, AccionesDetalle()) }
        capturar("movil-ficha-oscuro.png", 412, 900, 2f, oscuro = true) { PantallaDetalle(detalle, AccionesDetalle()) }
    }

    private fun capturar(
        nombre: String,
        ancho: Int,
        alto: Int,
        densidad: Float,
        oscuro: Boolean,
        contenido: @Composable () -> Unit,
    ) = runSkikoComposeUiTest(Size(ancho * densidad, alto * densidad), Density(densidad)) {
        setContent {
            TemaSurtidor(oscuro = oscuro) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { contenido() }
            }
        }
        waitForIdle()
        val imagen = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
        File(salida, nombre).writeBytes(imagen.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
