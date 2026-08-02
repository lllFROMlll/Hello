package com.meuagente.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// Isso é o "bibliotecário": ele sabe onde cada ficha fica guardada
// e como buscar, salvar ou atualizar elas.
@Dao
interface AgenteDao {

    @Insert
    suspend fun salvarMensagem(mensagem: MensagemEntity)

    @Query("SELECT * FROM mensagens ORDER BY dataHora ASC")
    suspend fun listarMensagens(): List<MensagemEntity>

    @Insert
    suspend fun salvarLembrete(lembrete: LembreteEntity)

    @Query("SELECT * FROM lembretes WHERE concluido = 0")
    suspend fun listarLembretesPendentes(): List<LembreteEntity>

    @Update
    suspend fun atualizarLembrete(lembrete: LembreteEntity)
}
