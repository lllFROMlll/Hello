package com.meuagente.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AgenteDao {

    @Insert
    suspend fun salvarMensagem(mensagem: MensagemEntity)

    @Query("SELECT * FROM mensagens ORDER BY dataHora ASC")
    suspend fun listarMensagens(): List<MensagemEntity>

    @Query("SELECT * FROM mensagens WHERE conversaId = :conversaId ORDER BY dataHora ASC")
    suspend fun listarMensagensDaConversa(conversaId: Int): List<MensagemEntity>

    @Insert
    suspend fun criarConversa(conversa: ConversaEntity): Long

    @Query("SELECT * FROM conversas ORDER BY dataCriacao DESC")
    suspend fun listarConversas(): List<ConversaEntity>

    @Insert
    suspend fun salvarLembrete(lembrete: LembreteEntity)

    @Query("SELECT * FROM lembretes WHERE concluido = 0")
    suspend fun listarLembretesPendentes(): List<LembreteEntity>

    @Query("SELECT * FROM lembretes ORDER BY dataCriacao DESC")
    suspend fun listarTodosLembretes(): List<LembreteEntity>

    @Update
    suspend fun atualizarLembrete(lembrete: LembreteEntity)

    @Query("DELETE FROM lembretes WHERE id = :id")
    suspend fun apagarLembrete(id: Int)
}
