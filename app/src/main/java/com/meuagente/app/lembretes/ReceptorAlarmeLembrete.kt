package com.meuagente.app.lembretes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.meuagente.app.AgenteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver disparado pelo AlarmManager no segundo exato do lembrete,
 * ou pelos botões de ação rápida da notificação ("Concluir" e "Adiar 10m").
 */
class ReceptorAlarmeLembrete : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val lembreteId = intent.getIntExtra(NotificadorLembrete.EXTRA_LEMBRETE_ID, -1)
        Log.d("BlerAlarme", "ReceptorAlarmeLembrete.onReceive: action=${intent.action}, lembreteId=$lembreteId")

        if (lembreteId == -1) return

        when (intent.action) {
            NotificadorLembrete.ACAO_CONCLUIR -> {
                GerenciadorLembretes.marcarConcluido(context, lembreteId)
            }
            NotificadorLembrete.ACAO_ADIAR_10M -> {
                val dezMinutosDepois = System.currentTimeMillis() + (10 * 60 * 1000)
                GerenciadorLembretes.adiar(context, lembreteId, dezMinutosDepois)
            }
            else -> {
                // Disparo oficial do alarme: adquire WakeLock temporário para garantir que a CPU não durma
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Bler:AlarmeWakeLock"
                )
                try {
                    wakeLock?.acquire(30_000L) // 30s de limite
                } catch (e: Exception) {
                    Log.w("BlerAlarme", "Falha ao adquirir WakeLock", e)
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AgenteDatabase.obter(context)
                        val lembrete = db.agenteDao().buscarLembretePorId(lembreteId)
                        Log.d("BlerAlarme", "Buscando lembrete no Room: encontrado=${lembrete != null}, concluido=${lembrete?.concluido}")

                        if (lembrete != null && !lembrete.concluido) {
                            // Atualiza status para DISPARADO
                            val atualizado = lembrete.copy(status = "DISPARADO")
                            db.agenteDao().atualizarLembrete(atualizado)

                            // Tenta iniciar a TelaLembreteActivity diretamente como backup
                            try {
                                val directIntent = Intent(context, TelaLembreteActivity::class.java).apply {
                                    putExtra(NotificadorLembrete.EXTRA_LEMBRETE_ID, lembrete.id)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(directIntent)
                                Log.d("BlerAlarme", "Intent direto para TelaLembreteActivity enviado")
                            } catch (e: Exception) {
                                Log.d("BlerAlarme", "Lançamento direto de activity restrito pelo Android (esperado em background): ${e.message}")
                            }

                            // Dispara notificação com tela cheia (Full-Screen Intent)
                            NotificadorLembrete.dispararLembrete(context, atualizado)
                        }
                    } catch (e: Exception) {
                        Log.e("BlerAlarme", "Erro no processamento do alarme", e)
                    } finally {
                        try {
                            if (wakeLock?.isHeld == true) {
                                wakeLock.release()
                            }
                        } catch (_: Exception) {}
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
