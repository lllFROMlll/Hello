package com.meuagente.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AgenteDao {

    @Insert
    suspend fun salvarMensagem(mensagem: MensagemEntity): Long

    @Query("SELECT * FROM mensagens ORDER BY dataHora ASC")
    suspend fun listarMensagens(): List<MensagemEntity>

    @Query("SELECT * FROM mensagens WHERE conversaId = :conversaId ORDER BY dataHora ASC")
    suspend fun listarMensagensDaConversa(conversaId: Int): List<MensagemEntity>

    @Insert
    suspend fun criarConversa(conversa: ConversaEntity): Long

    @Query("SELECT * FROM conversas ORDER BY dataCriacao DESC")
    suspend fun listarConversas(): List<ConversaEntity>

    @Query("SELECT * FROM conversas WHERE id = :conversaId")
    suspend fun buscarConversaPorId(conversaId: Int): ConversaEntity?

    @Update
    suspend fun atualizarConversa(conversa: ConversaEntity)

    @Query("SELECT * FROM conversas ORDER BY fixada DESC, dataCriacao DESC")
    suspend fun listarConversasFixadasPrimeiro(): List<ConversaEntity>

    @Query(
        """
        SELECT c.id AS id, c.titulo AS titulo, c.fixada AS fixada, c.dataCriacao AS dataCriacao,
            (SELECT m.texto FROM mensagens m WHERE m.conversaId = c.id ORDER BY m.dataHora DESC LIMIT 1) AS ultimaMensagem,
            (SELECT MAX(m.dataHora) FROM mensagens m WHERE m.conversaId = c.id) AS ultimaAtividade
        FROM conversas c
        ORDER BY c.fixada DESC, c.dataCriacao DESC
        """
    )
    suspend fun listarCartoesDeConversa(): List<CartaoConversa>

    @Query("DELETE FROM mensagens WHERE conversaId = :conversaId")
    suspend fun apagarMensagensDaConversa(conversaId: Int)

    @Query("DELETE FROM conversas WHERE id = :conversaId")
    suspend fun apagarConversa(conversaId: Int)

    @Insert
    suspend fun salvarLembrete(lembrete: LembreteEntity): Long

    @Query("SELECT * FROM lembretes WHERE id = :id")
    suspend fun buscarLembretePorId(id: Int): LembreteEntity?

    @Query("SELECT * FROM lembretes WHERE concluido = 0 AND status = 'PENDENTE'")
    suspend fun listarLembretesPendentes(): List<LembreteEntity>

    @Query("SELECT * FROM lembretes ORDER BY dataCriacao DESC")
    suspend fun listarTodosLembretes(): List<LembreteEntity>

    @Update
    suspend fun atualizarLembrete(lembrete: LembreteEntity)

    @Query("DELETE FROM lembretes WHERE id = :id")
    suspend fun apagarLembrete(id: Int)
}
