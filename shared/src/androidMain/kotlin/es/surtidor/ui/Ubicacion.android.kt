package es.surtidor.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import es.surtidor.dominio.Coordenadas

/**
 * Ubicación aproximada con el `LocationManager` del sistema (sin Google Play Services: funciona en cualquier
 * Android). El permiso se pide al pulsar «Cerca de mí», nunca al arrancar.
 */
@Composable
actual fun recordarPeticionDeUbicacion(alObtener: (Coordenadas?) -> Unit): (() -> Unit)? {
    val contexto = LocalContext.current
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
            if (concedidos.values.any { it }) leerUbicacion(contexto, alObtener) else alObtener(null)
        }
    return remember(contexto) {
        {
            if (tienePermiso(contexto)) {
                leerUbicacion(contexto, alObtener)
            } else {
                lanzador.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }
}

private fun tienePermiso(contexto: Context) =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // Solo se llama con el permiso concedido.
private fun leerUbicacion(
    contexto: Context,
    alObtener: (Coordenadas?) -> Unit,
) {
    val gestor = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val proveedor =
        listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { gestor.isProviderEnabled(it) }
    if (proveedor == null) {
        alObtener(null)
        return
    }
    val entregar: (Location?) -> Unit = { l -> alObtener(l?.let { Coordenadas(it.latitude, it.longitude) }) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        gestor.getCurrentLocation(proveedor, CancellationSignal(), ContextCompat.getMainExecutor(contexto), entregar)
    } else {
        entregar(gestor.getLastKnownLocation(proveedor))
    }
}
