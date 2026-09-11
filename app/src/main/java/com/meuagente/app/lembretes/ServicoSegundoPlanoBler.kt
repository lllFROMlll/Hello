package com.meuagente.app.lembretes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meuagente.app.Configuracoes
import com.meuagente.app.MainActivity

/**
 * Foreground Service que mantém o processo do Blér vivo em segundo plano,
 * exibindo uma notificação permanente e discreta na barra de status.
 *
 * Impede que o Android encerre o processo do app por economia de energia,
 * garantindo confiabilidade total na entrega de lembretes e alarmes.
 */
class ServicoSegundoPlanoBler : Service() {

    companion object {
        private const val TAG = "BlerForegroundService"
        const val CANAL_ID = "bler_canal_servico_ativo"
        const val NOTIFICACAO_ID = 1001

        fun iniciar(contexto: Context) {
            if (!Configuracoes.servicoSegundoPlanoAtivo(contexto)) {
                Log.d(TAG, "Serviço em segundo plano desativado nas preferências")
                return
            }
            try {
                val intent = Intent(contexto, ServicoSegundoPlanoBler::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(contexto, intent)
                } else {
                    contexto.startService(intent)
                }
                Log.d(TAG, "Iniciando ServicoSegundoPlanoBler")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar ServicoSegundoPlanoBler", e)
            }
        }

        fun parar(contexto: Context) {
            try {
                val intent = Intent(contexto, ServicoSegundoPlanoBler::class.java)
                contexto.stopService(intent)
                Log.d(TAG, "Parando ServicoSegundoPlanoBler")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar ServicoSegundoPlanoBler", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        criarCanalNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificacao = construirNotificacao()
        try {
            startForeground(NOTIFICACAO_ID, notificacao)
            Log.d(TAG, "startForeground chamado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Falha no startForeground", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Blér em Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o Blér ativo para monitorar lembretes e tarefas com pontualidade"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(canal)
        }
    }

    private fun construirNotificacao(): Notification {
        val intentApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intentApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle("Blér Ativo")
            .setContentText("Monitorando lembretes e serviços em segundo plano")
            .setSmallIcon(com.meuagente.app.R.drawable.ic_bler_notificacao)
            .setColor(0xFF00E5FF.toInt())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
