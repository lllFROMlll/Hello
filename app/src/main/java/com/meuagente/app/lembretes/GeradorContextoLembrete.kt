package com.meuagente.app.lembretes

import android.content.Context
import com.meuagente.app.AgenteDatabase
import com.meuagente.app.LembreteEntity
import com.meuagente.app.MensagemEntity
import com.meuagente.app.ia.CascataIA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeradorContextoLembrete {

    /**
     * Consulta a cascata de IA para identificar informações de apoio ou contexto
     * nas conversas recentes relacionadas a este lembrete (ex: sintomas mencionados antes de "ligar pro médico").
     */
    suspend fun buscarResumoContextual(
        contexto: Context,
        lembrete: LembreteEntity
    ): String? = withContext(Dispatchers.IO) {
        try {
            val db = AgenteDatabase.obter(contexto)
            val mensagens = if (lembrete.conversaOrigemId != null) {
                db.agenteDao().listarMensagensDaConversa(lembrete.conversaOrigemId)
            } else {
                db.agenteDao().listarMensagens().takeLast(30)
            }

            if (mensagens.isEmpty()) {
                return@withContext lembrete.contexto
            }

            val instrucao = """
                Você é o assistente Blér. O lembrete "${lembrete.descricao}" acabou de disparar.
                Analise o histórico recente da conversa e verifique se há contexto útil relacionado (como motivos, sintomas, detalhes de pessoas, compromissos ou observações feitas pelo usuário).
                Se encontrar contexto relevante, resuma em no máximo 2 frases curtas e diretas em português, começando amigavelmente (ex: "Contexto: você mencionou que...").
                Se NÃO houver nenhuma menção relevante no histórico, responda apenas: NENHUM
            """.trimIndent()

            val resposta = CascataIA.perguntar(
                contexto = contexto,
                historico = mensagens.takeLast(20),
                instrucao = instrucao
            )

            val texto = resposta.texto.trim()
            if (texto.isNotBlank() && !texto.contains("NENHUM", ignoreCase = true)) {
                texto
            } else {
                lembrete.contexto
            }
        } catch (_: Exception) {
            lembrete.contexto
        }
    }
}
