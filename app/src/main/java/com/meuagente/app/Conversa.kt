package com.meuagente.app

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cada conversa é uma "aba" separada de chat. As mensagens ficam
// vinculadas a uma conversa através do campo conversaId.
@Entity(tableName = "conversas")
data class ConversaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val dataCriacao: Long,
    val fixada: Boolean = false
)

// Cartão de conversa para a gaveta lateral, com prévia da última mensagem.
data class CartaoConversa(
    val id: Int,
    val titulo: String,
    val fixada: Boolean,
    val dataCriacao: Long,
    val ultimaMensagem: String?,
    val ultimaAtividade: Long?
)
