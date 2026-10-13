package es.surtidor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import es.surtidor.Muestras
import es.surtidor.datos.remoto.RespuestaDto
import es.surtidor.datos.remoto.aDominio
import es.surtidor.dominio.Carburante
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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PantallasTest {
    private val publicados = JSON.decodeFromString<RespuestaDto>(Muestras.ACTUAL_MURCIA).aDominio()

    private fun estadoLista(carburante: Carburante = Carburante.GASOLEO_A) =
        EstadoLista(
            seleccion = Seleccion(provinciaId = "30", carburante = carburante, orden = Orden.PRECIO),
            provincias = listOf(Provincia("30", "Murcia")),
            gasolineras = ordenar(publicados.gasolineras, carburante, Orden.PRECIO),
            resumen = resumir(publicados.gasolineras.mapNotNull { it.precios[carburante] }),
            publicadosEn = publicados.publicadosEn,
            cargando = false,
        )

    @Test
    fun laListaEnsenaLasGasolinerasConSuPrecioYAbreLaFicha() =
        runComposeUiTest {
            var abierta: Long? = null
            setContent { TemaSurtidor { PantallaLista(estadoLista(), AccionesLista(abrir = { abierta = it })) } }

            onNodeWithText("Murcia").assertIsDisplayed()
            onNodeWithText("GLOBALTANK").assertIsDisplayed()
            onNodeWithText("1,829 €").assertIsDisplayed()
            onNode(hasText("Datos del Ministerio", substring = true)).assertIsDisplayed()
            // La gasolinera de gas natural no vende gasóleo: no sale.
            onNode(hasText("NATURGY", substring = true)).assertDoesNotExist()

            onNode(hasContentDescription("GLOBALTANK", substring = true)).performClick()
            assertEquals(2202L, abierta)
        }

    @Test
    fun buscarYElegirCarburanteAvisanAlViewModel() =
        runComposeUiTest {
            var buscado = ""
            var elegido: Carburante? = null
            setContent {
                TemaSurtidor {
                    PantallaLista(
                        estadoLista(),
                        AccionesLista(buscar = { buscado = it }, elegirCarburante = {
                            elegido =
                                it
                        }),
                    )
                }
            }
            onNodeWithTag("buscar").performTextInput("aguilas")
            assertEquals("aguilas", buscado)
            onNodeWithText("Autogás (GLP)").performClick()
            assertEquals(Carburante.GLP, elegido)
        }

    @Test
    fun sinResultadosYConErrorLoExplica() =
        runComposeUiTest {
            var reintentos = 0
            val estado =
                estadoLista().copy(
                    gasolineras = emptyList(),
                    seleccion = Seleccion("30", texto = "zzz"),
                    error = "Sin conexión",
                )
            setContent { TemaSurtidor { PantallaLista(estado, AccionesLista(refrescar = { reintentos++ })) } }
            onNodeWithText("Ninguna gasolinera coincide con «zzz».").assertIsDisplayed()
            onNodeWithText("Sin conexión").assertIsDisplayed()
            onNodeWithText("Se muestran los últimos precios guardados.").assertIsDisplayed()
            onNodeWithText("Reintentar").performClick()
            assertEquals(1, reintentos)
        }

    @Test
    fun laFichaEnsenaPreciosHistoricoYAviso() =
        runComposeUiTest {
            val g = publicados.gasolineras.first { it.id == 4149L }
            val historico =
                listOf(
                    PrecioDelDia(LocalDate(2026, 10, 11), 1.929),
                    PrecioDelDia(LocalDate(2026, 10, 12), 1.919),
                    PrecioDelDia(LocalDate(2026, 10, 13), 1.909),
                )
            var umbral: Double? = null
            setContent {
                TemaSurtidor {
                    PantallaDetalle(
                        EstadoDetalle(
                            cargando = false,
                            gasolinera = g,
                            carburante = Carburante.GASOLEO_A,
                            mediaProvincia = 1.889,
                            historico = EstadoHistorico.Datos(historico),
                            favorita = Favorita(g.id, Carburante.GASOLEO_A, null),
                        ),
                        AccionesDetalle(fijarUmbral = { umbral = it }),
                    )
                }
            }
            onNodeWithText("E.S. MONTEPINAR").assertIsDisplayed()
            onNodeWithText("Gasóleo A").assertIsDisplayed()
            onNode(hasText("+2,0 cts respecto a la media de la provincia", substring = true)).assertExists()
            onNode(hasContentDescription(resumenDeLaGrafica(historico))).assertExists()
            onNodeWithText("Quitar de favoritas").assertExists()
            onNodeWithText("Euros por litro").performTextInput("1,85")
            onNodeWithText("Guardar").performClick()
            assertEquals(1.85, umbral)
        }

    @Test
    fun elResumenDeLaGraficaCuentaLaEvolucion() {
        val texto =
            resumenDeLaGrafica(
                listOf(PrecioDelDia(LocalDate(2026, 10, 1), 1.95), PrecioDelDia(LocalDate(2026, 10, 13), 1.909)),
            )
        assertEquals(
            "Del 1 oct al 13 oct: de 1,950 euros a 1,909 euros. Mínimo, 1,909 euros el 13 oct; máximo, 1,950 euros el 1 oct.",
            texto,
        )
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
