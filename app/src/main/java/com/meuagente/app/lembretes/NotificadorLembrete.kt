package com.meuagente.app.lembretes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.meuagente.app.LembreteEntity

object NotificadorLembrete {

    const val CANAL_ID = "canal_lembretes_bler_v1"
    const val ACAO_CONCLUIR = "com.meuagente.app.ACAO_CONCLUIR_LEMBRETE"
    const val ACAO_ADIAR_10M = "com.meuagente.app.ACAO_ADIAR_10M_LEMBRETE"
    const val EXTRA_LEMBRETE_ID = "lembrete_id"

    fun criarCanalSeNecessario(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val somPadrao = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val canal = NotificationChannel(
                CANAL_ID,
                "Lembretes e Alarmes do Blér",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações e tela de prioridade máxima para os seus lembretes agendados"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(somPadrao, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val manager = contexto.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(canal)
        }
    }

    /**
     * Dispara a notificação com Full-Screen Intent para acordar a tela e abrir a TelaLembreteActivity,
     * ou exibir o banner heads-up com som alto se o usuário já estiver com a tela desbloqueada.
     */
    fun dispararLembrete(contexto: Context, lembrete: LembreteEntity) {
        criarCanalSeNecessario(contexto)

        // Intent de tela cheia (abre TelaLembreteActivity sobre a tela de bloqueio)
        val fullScreenIntent = Intent(contexto, TelaLembreteActivity::class.java).apply {
            putExtra(EXTRA_LEMBRETE_ID, lembrete.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            contexto,
            lembrete.id,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação rápida 1: Concluir
        val intentConcluir = Intent(contexto, ReceptorAlarmeLembrete::class.java).apply {
            action = ACAO_CONCLUIR
            putExtra(EXTRA_LEMBRETE_ID, lembrete.id)
        }
        val pendingConcluir = PendingIntent.getBroadcast(
            contexto,
            lembrete.id * 10 + 1,
            intentConcluir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Ação rápida 2: Adiar 10m
        val intentAdiar = Intent(contexto, ReceptorAlarmeLembrete::class.java).apply {
            action = ACAO_ADIAR_10M
            putExtra(EXTRA_LEMBRETE_ID, lembrete.id)
        }
        val pendingAdiar = PendingIntent.getBroadcast(
            contexto,
            lembrete.id * 10 + 2,
            intentAdiar,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val somAlarme = com.meuagente.app.Configuracoes.obterUriSomLembrete(contexto)

        val textoApoio = if (!lembrete.contexto.isNullOrBlank()) {
            lembrete.contexto
        } else {
            "Toque para abrir ou adiar o lembrete"
        }

        val builder = NotificationCompat.Builder(contexto, CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Lembrete: ${lembrete.descricao}")
            .setContentText(textoApoio)
            .setStyle(NotificationCompat.BigTextStyle().bigText(textoApoio))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFF00E5FF.toInt()) // NeonAzul
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(somAlarme)
            .setContentIntent(fullScreenPendingIntent)

        if (com.meuagente.app.Configuracoes.usarTelaCheiaLembrete(contexto)) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        builder.addAction(android.R.drawable.ic_menu_agenda, "Adiar 10m", pendingAdiar)
            .addAction(android.R.drawable.checkbox_on_background, "Concluir", pendingConcluir)

        android.util.Log.d("BlerAlarme", "NotificadorLembrete: Disparando notify ID=${lembrete.id} (temPermissaoNotificacao: ${temPermissaoNotificacao(contexto)})")

        try {
            val manager = NotificationManagerCompat.from(contexto)
            manager.notify(lembrete.id, builder.build())
            android.util.Log.d("BlerAlarme", "NotificadorLembrete: notify() executado com sucesso para ID=${lembrete.id}")
        } catch (e: SecurityException) {
            android.util.Log.e("BlerAlarme", "SecurityException ao emitir notificação", e)
        }
    }

    /**
     * Verifica se o app tem autorização para emitir notificações no sistema.
     * No Android 13+ (API 33+), checa a permissão de runtime POST_NOTIFICATIONS.
     */
    fun temPermissaoNotificacao(contexto: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                contexto,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(contexto).areNotificationsEnabled()
        }
    }

    /**
     * Abre a tela nativa do Android para que o usuário ative as notificações do app.
     */
    fun abrirConfiguracaoNotificacoes(contexto: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, contexto.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${contexto.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            contexto.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun cancelarNotificacao(contexto: Context, lembreteId: Int) {
        try {
            val manager = NotificationManagerCompat.from(contexto)
            manager.cancel(lembreteId)
        } catch (_: Exception) {
        }
    }
}
