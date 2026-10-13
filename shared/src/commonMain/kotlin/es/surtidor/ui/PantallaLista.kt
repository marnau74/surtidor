package es.surtidor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.GasolineraEnLista
import es.surtidor.dominio.Orden
import es.surtidor.dominio.Resumen
import es.surtidor.presentacion.EstadoLista
import kotlin.time.Clock

/** Acciones de la lista: la pantalla no conoce el ViewModel, así se prueba con un estado cualquiera. */
data class AccionesLista(
    val elegirProvincia: (String) -> Unit = {},
    val elegirCarburante: (Carburante) -> Unit = {},
    val ordenarPor: (Orden) -> Unit = {},
    val buscar: (String) -> Unit = {},
    val refrescar: () -> Unit = {},
    val pedirUbicacion: (() -> Unit)? = null,
    val abrir: (Long) -> Unit = {},
    val descartarError: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLista(
    estado: EstadoLista,
    acciones: AccionesLista,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = estado.cargando,
        onRefresh = acciones.refrescar,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("lista"),
        ) {
            item(key = "cabecera") { Cabecera(estado, acciones) }
            if (estado.error != null) {
                item(key = "error") { AvisoDeError(estado.error, estado.publicadosEn != null, acciones) }
            }
            when {
                estado.vacio && estado.error == null -> {
                    item(key = "vacio") { TextoSuave("Aún no hay datos de esta provincia.") }
                }

                !estado.cargando && estado.publicadosEn != null && estado.gasolineras.isEmpty() -> {
                    item(key = "ninguna") {
                        TextoSuave(
                            if (estado.seleccion.texto.isBlank()) {
                                "Ninguna gasolinera de la provincia vende ${estado.seleccion.carburante.nombre.lowercase()}."
                            } else {
                                "Ninguna gasolinera coincide con «${estado.seleccion.texto}»."
                            },
                        )
                    }
                }
            }
            items(estado.gasolineras, key = { it.gasolinera.id }) { fila ->
                FilaGasolinera(fila, estado.resumen, onClick = { acciones.abrir(fila.gasolinera.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecera(
    estado: EstadoLista,
    acciones: AccionesLista,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gasolineras más baratas",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = acciones.refrescar, enabled = !estado.cargando) {
                Icon(Iconos.Actualizar, contentDescription = "Actualizar precios")
            }
        }
        SelectorDeProvincia(estado, acciones.elegirProvincia)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Carburante.entries.forEach { c ->
                FilterChip(
                    selected = c == estado.seleccion.carburante,
                    onClick = { acciones.elegirCarburante(c) },
                    label = { Text(c.nombre) },
                )
            }
        }
        var texto by remember(estado.seleccion.provinciaId) { mutableStateOf(estado.seleccion.texto) }
        OutlinedTextField(
            value = texto,
            onValueChange = {
                texto = it
                acciones.buscar(it)
            },
            label = { Text("Municipio, calle, rótulo o código postal") },
            leadingIcon = { Icon(Iconos.Buscar, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().testTag("buscar"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = estado.seleccion.orden == Orden.PRECIO,
                onClick = { acciones.ordenarPor(Orden.PRECIO) },
                label = { Text("Más baratas") },
            )
            if (estado.seleccion.ubicacion != null) {
                FilterChip(
                    selected = estado.seleccion.orden == Orden.DISTANCIA,
                    onClick = { acciones.ordenarPor(Orden.DISTANCIA) },
                    label = { Text("Más cerca") },
                )
            }
            acciones.pedirUbicacion?.let { pedir ->
                TextButton(onClick = pedir) {
                    Icon(Iconos.Ubicacion, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (estado.seleccion.ubicacion == null) "Cerca de mí" else "Actualizar ubicación")
                }
            }
        }
        if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
        estado.publicadosEn?.let { publicados ->
            val resumen = estado.resumen
            TextoSuave(
                buildString {
                    append("Datos del Ministerio de ${momento(publicados, Clock.System.now())}")
                    if (resumen != null) {
                        append(" · ${resumen.gasolineras} gasolineras · media ${precio(resumen.media)}")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeProvincia(
    estado: EstadoLista,
    elegir: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = estado.provincia?.nombre ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Provincia") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .testTag("provincia"),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            estado.provincias.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.nombre) },
                    onClick = {
                        abierto = false
                        elegir(p.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AvisoDeError(
    mensaje: String,
    hayDatos: Boolean,
    acciones: AccionesLista,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(mensaje, color = MaterialTheme.colorScheme.onErrorContainer)
            if (hayDatos) {
                Text(
                    "Se muestran los últimos precios guardados.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                TextButton(onClick = acciones.refrescar) { Text("Reintentar") }
                TextButton(onClick = acciones.descartarError) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun FilaGasolinera(
    fila: GasolineraEnLista,
    resumen: Resumen?,
    onClick: () -> Unit,
) {
    val g = fila.gasolinera
    val diferencia = resumen?.let { fila.precio - it.media }
    val descripcion =
        buildString {
            append("${g.rotulo}, ${g.direccion}, ${g.municipio}. ${precioHablado(fila.precio)}")
            diferencia?.let { append(", ${diferenciaEnCentimos(it)} respecto a la media") }
            fila.distanciaKm?.let { append(", a ${distancia(it)}") }
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = descripcion },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    g.rotulo,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    g.direccion,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        g.municipio,
                        fila.distanciaKm?.let { distancia(it) },
                        "24 h".takeIf { g.abierta24Horas },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 96.dp)) {
                Text(precio(fila.precio), style = MaterialTheme.typography.titleLarge.merge(estiloPrecio))
                if (diferencia != null) {
                    Text(
                        diferenciaEnCentimos(diferencia),
                        style = MaterialTheme.typography.labelMedium.merge(estiloPrecio),
                        color =
                            if (diferencia <
                                0
                            ) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TextoSuave(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
