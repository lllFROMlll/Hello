package com.meuagente.app.lembretes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.meuagente.app.AgenteDatabase
import com.meuagente.app.LembreteEntity
import com.meuagente.app.Repeticao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GerenciadorLembretes {

    /**
     * Agenda o lembrete no AlarmManager com precisão de milissegundos,
     * garantindo o disparo mesmo com o celular em modo de economia Doze.
     */
    fun agendar(contexto: Context, lembrete: LembreteEntity) {
        val horario = lembrete.dataHoraAgendada ?: return
        if (horario <= System.currentTimeMillis()) return

        val alarmManager = contexto.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(contexto, ReceptorAlarmeLembrete::class.java).apply {
            action = "com.meuagente.app.DISPARAR_LEMBRETE"
            putExtra("lembrete_id", lembrete.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            contexto,
            lembrete.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("BlerAlarme", "GerenciadorLembretes.agendar: ID=${lembrete.id} ('${lembrete.descricao}') para $horario (em ${(horario - System.currentTimeMillis()) / 1000}s)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        horario,
                        pendingIntent
                    )
                } else {
                    // Fallback para alarme flexível se a permissão de alarme exato não foi concedida
                    Log.w("BlerAlarme", "Permissão SCHEDULE_EXACT_ALARM ausente. Usando alarme flexível.")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        horario,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    horario,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    horario,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("BlerAlarme", "SecurityException ao agendar alarme exato", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, horario, pendingIntent)
        }
    }

    /**
     * Verifica se o app possui autorização do sistema para disparar alarmes no segundo exato.
     */
    fun podeAgendarAlarmesExatos(contexto: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = contexto.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    /**
     * Abre a tela nativa do Android para que o usuário autorize alarmes e lembretes exatos com 1 toque.
     */
    fun abrirConfiguracaoAlarmesExatos(contexto: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${contexto.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${contexto.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    contexto.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Agenda um lembrete teste para exatamente 10 segundos no futuro,
     * permitindo verificar o toque, vibração, notificação e tela cheia sem esperar.
     */
    fun agendarTeste10Segundos(contexto: Context, aoAgendar: ((Int) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AgenteDatabase.obter(contexto)
            val horarioTeste = System.currentTimeMillis() + 10_000L // 10s
            val lembreteTeste = LembreteEntity(
                descricao = "Teste do Alarme (10s)",
                pessoa = null,
                dataCriacao = System.currentTimeMillis(),
                concluido = false,
                dataHoraAgendada = horarioTeste,
                repeticao = Repeticao.NENHUMA,
                contexto = "Disparo de teste do Blér para validação da tela cheia neon, som e botões de ação.",
                status = "PENDENTE"
            )
            val id = db.agenteDao().salvarLembrete(lembreteTeste).toInt()
            val lembreteComId = lembreteTeste.copy(id = id)
            agendar(contexto, lembreteComId)
            withContext(Dispatchers.Main) {
                aoAgendar?.invoke(id)
            }
        }
    }

    /**
     * Cancela o agendamento de um lembrete no sistema operacional.
     */
    fun cancelar(contexto: Context, lembreteId: Int) {
        val alarmManager = contexto.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(contexto, ReceptorAlarmeLembrete::class.java).apply {
            action = "com.meuagente.app.DISPARAR_LEMBRETE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            contexto,
            lembreteId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Adia um lembrete existente para um novo horário em milissegundos.
     * Atualiza o registro no banco Room (sem duplicar) e reagenda no AlarmManager.
     */
    fun adiar(
        contexto: Context,
        lembreteId: Int,
        novoHorarioMillis: Long,
        aoConcluir: (() -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AgenteDatabase.obter(contexto)
            val lembrete = db.agenteDao().buscarLembretePorId(lembreteId)
            if (lembrete != null) {
                val atualizado = lembrete.copy(
                    dataHoraAgendada = novoHorarioMillis,
                    status = "PENDENTE",
                    concluido = false
                )
                db.agenteDao().atualizarLembrete(atualizado)
                agendar(contexto, atualizado)

                // Atualiza também na Google Agenda se estiver ativada
                SincronizadorGoogleAgenda.sincronizarLembrete(
                    contexto = contexto,
                    titulo = atualizado.descricao,
                    descricaoContexto = atualizado.contexto,
                    dataHoraMillis = novoHorarioMillis,
                    eventoIdExistente = atualizado.googleEventId
                )
            }
            NotificadorLembrete.cancelarNotificacao(contexto, lembreteId)
            aoConcluir?.invoke()
        }
    }

    /**
     * Marca o lembrete como concluído no banco e limpa a notificação.
     */
    fun marcarConcluido(
        contexto: Context,
        lembreteId: Int,
        aoConcluir: (() -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AgenteDatabase.obter(contexto)
            val lembrete = db.agenteDao().buscarLembretePorId(lembreteId)
            if (lembrete != null) {
                val atualizado = lembrete.copy(status = "CONCLUIDO", concluido = true)
                db.agenteDao().atualizarLembrete(atualizado)
            }
            cancelar(contexto, lembreteId)
            NotificadorLembrete.cancelarNotificacao(contexto, lembreteId)
            aoConcluir?.invoke()
        }
    }

    /**
     * Chamado após o boot do celular para restaurar todos os lembretes pendentes futuros.
     */
    fun reagendarTodosPendentes(contexto: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AgenteDatabase.obter(contexto)
            val pendentes = db.agenteDao().listarLembretesPendentes()
            val agora = System.currentTimeMillis()
            pendentes.forEach { lembrete ->
                if ((lembrete.dataHoraAgendada ?: 0L) > agora) {
                    agendar(contexto, lembrete)
                }
            }
        }
    }
}
