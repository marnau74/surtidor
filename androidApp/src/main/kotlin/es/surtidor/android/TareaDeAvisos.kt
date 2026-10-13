package es.surtidor.android

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.surtidor.datos.ServicioDeAvisos
import es.surtidor.dominio.Aviso
import es.surtidor.ui.precio
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Comprueba las favoritas en segundo plano y pone una notificación por cada precio que ha bajado de su umbral. */
class TareaDeAvisos(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros),
    KoinComponent {
    private val avisos: ServicioDeAvisos by inject()

    override suspend fun doWork(): Result =
        try {
            avisos.comprobar().forEach { notificar(it) }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }

    private fun notificar(aviso: Aviso) {
        val permitido =
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!permitido) return
        val abrir =
            PendingIntent.getActivity(
                applicationContext,
                aviso.gasolinera.id.toInt(),
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notificacion =
            NotificationCompat
                .Builder(applicationContext, SurtidorApp.CANAL_AVISOS)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle("${aviso.carburante.nombre} a ${precio(aviso.precio)}")
                .setContentText(
                    "${aviso.gasolinera.rotulo}, ${aviso.gasolinera.municipio}: ha bajado de ${precio(aviso.umbral)}",
                ).setContentIntent(abrir)
                .setAutoCancel(true)
                .build()
        // Un identificador por gasolinera: un aviso nuevo de la misma sustituye al anterior.
        NotificationManagerCompat.from(applicationContext).notify(aviso.gasolinera.id.toInt(), notificacion)
    }
}
