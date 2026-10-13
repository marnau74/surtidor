package es.surtidor.ui

import androidx.compose.runtime.Composable
import es.surtidor.dominio.Coordenadas

/** En escritorio no hay ubicación fiable: se busca por municipio o código postal. */
@Composable
actual fun recordarPeticionDeUbicacion(alObtener: (Coordenadas?) -> Unit): (() -> Unit)? = null
