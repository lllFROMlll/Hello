package com.meuagente.app

import androidx.room.Entity
import androidx.room.PrimaryKey

// Tipos de repetição possíveis para lembretes agendados
enum class Repeticao {
    NENHUMA, DIARIA, SEMANAL, MENSAL
}

@Entity(tableName = "lembretes")
data class LembreteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Comum aos dois tipos de lembrete
    val descricao: String,
    val pessoa: String?,
    val dataCriacao: Long,
    val concluido: Boolean = false,

    // Só para lembrete AGENDADO (com data/hora marcada)
    // Se dataHoraAgendada for null, é um lembrete "aberto" (sem data)
    val dataHoraAgendada: Long? = null,
    val repeticao: Repeticao = Repeticao.NENHUMA
)
