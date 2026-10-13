package es.surtidor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.surtidor.dominio.Carburante
import es.surtidor.presentacion.EstadoDetalle
import es.surtidor.presentacion.EstadoHistorico

data class AccionesDetalle(
    val elegirCarburante: (Carburante) -> Unit = {},
    val alternarFavorita: () -> Unit = {},
    val fijarUmbral: (Double?) -> Unit = {},
    val reintentarHistorico: () -> Unit = {},
)

@Composable
fun PantallaDetalle(
    estado: EstadoDetalle,
    acciones: AccionesDetalle,
    modifier: Modifier = Modifier,
) {
    val g = estado.gasolinera
    if (g == null) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            if (estado.cargando) CircularProgressIndicator() else TextoSuave("No se encuentra esta gasolinera.")
        }
        return
    }
    val enlaces = LocalUriHandler.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(g.rotulo, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(g.direccion, style = MaterialTheme.typography.bodyLarge)
            val lugar = listOf(g.codigoPostal, g.localidad.ifEmpty { g.municipio }).filter { it.isNotBlank() }
            TextoSuave(lugar.joinToString(" "))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            g.coordenadas?.let { c ->
                FilledTonalButton(
                    onClick = { enlaces.openUri(enlaceComoLlegar(c.latitud, c.longitud)) },
                ) {
                    Icon(Iconos.ComoLlegar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cómo llegar")
                }
            }
            OutlinedButton(onClick = acciones.alternarFavorita) {
                Icon(if (estado.favorita != null) Iconos.Estrella else Iconos.EstrellaVacia, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (estado.favorita != null) "Quitar de favoritas" else "Guardar en favoritas")
            }
        }

        Tarjeta("Precios") {
            Carburante.entries.filter { it in g.precios }.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.nombre, modifier = Modifier.weight(1f))
                    Text(
                        precio(g.precios.getValue(c)),
                        style = MaterialTheme.typography.titleMedium.merge(estiloPrecio),
                    )
                }
            }
            if (g.precios.isEmpty()) TextoSuave("Esta gasolinera no publica precios ahora mismo.")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Horario: ${g.horario.ifBlank { "no publicado" }}", style = MaterialTheme.typography.bodyMedium)
        }

        Tarjeta("Evolución en los últimos 30 días") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Carburante.entries.filter { it in g.precios }.forEach { c ->
                    FilterChip(
                        selected = c == estado.carburante,
                        onClick = { acciones.elegirCarburante(c) },
                        label = { Text(c.corto) },
                    )
                }
            }
            estado.precio?.let { actual ->
                val media = estado.mediaProvincia
                if (media != null) {
                    TextoSuave(
                        "${diferenciaEnCentimos(
                            actual - media,
                        )} respecto a la media de la provincia (${precio(media)}).",
                    )
                }
            }
            when (val h = estado.historico) {
                EstadoHistorico.Cargando -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(Modifier.width(20.dp))
                        Spacer(Modifier.width(12.dp))
                        TextoSuave("Descargando el histórico del Ministerio…")
                    }
                }

                EstadoHistorico.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextoSuave("No se ha podido cargar el histórico.")
                        TextButton(onClick = acciones.reintentarHistorico) { Text("Reintentar") }
                    }
                }

                is EstadoHistorico.Datos -> {
                    GraficaDePrecios(h.precios, Modifier.padding(top = 8.dp))
                }
            }
        }

        if (estado.favorita != null) {
            TarjetaDeAviso(estado, acciones.fijarUmbral)
        }

        TextoSuave(
            "Datos: Ministerio para la Transformación Digital y de la Función Pública (Geoportal de carburantes).",
        )
    }
}

/** Indicaciones hasta la gasolinera en Google Maps (abre la app de mapas en el móvil o el navegador). */
fun enlaceComoLlegar(
    latitud: Double,
    longitud: Double,
): String = "https://www.google.com/maps/dir/?api=1&destination=$latitud,$longitud"

@Composable
private fun TarjetaDeAviso(
    estado: EstadoDetalle,
    fijar: (Double?) -> Unit,
) {
    val favorita = estado.favorita ?: return
    var texto by remember(favorita.umbral) { mutableStateOf(favorita.umbral?.let { decimales(it, 3) } ?: "") }
    val valor = texto.replace(',', '.').toDoubleOrNull()
    Tarjeta("Avisarme si baja de") {
        TextoSuave("Precio de ${favorita.carburante.nombre.lowercase()} en euros. Se comprueba cada pocas horas.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(6) },
                label = { Text("Euros por litro") },
                singleLine = true,
                isError = texto.isNotEmpty() && (valor == null || valor <= 0.0),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { fijar(valor) }, enabled = texto.isEmpty() || (valor != null && valor > 0)) {
                Text(if (texto.isEmpty()) "Sin aviso" else "Guardar")
            }
        }
    }
}

@Composable
internal fun Tarjeta(
    titulo: String,
    contenido: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            contenido()
        }
    }
}
