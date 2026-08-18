package com.meuagente.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// Isso é o "bibliotecário": ele sabe onde cada ficha fica guardada
// e como buscar, salvar, atualizar ou apagar elas.
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

    // Lista tudo, inclusive o que já foi concluído — usado quando o
    // usuário pergunta "o que você guardou pra mim?"
    @Query("SELECT * FROM lembretes ORDER BY dataCriacao DESC")
    suspend fun listarTodosLembretes(): List<LembreteEntity>

    @Update
    suspend fun atualizarLembrete(lembrete: LembreteEntity)

    // Apaga de vez — usado quando o usuário pede pra esquecer algo
    // de verdade, e não só marcar como concluído.
    @Query("DELETE FROM lembretes WHERE id = :id")
    suspend fun apagarLembrete(id: Int)
}
