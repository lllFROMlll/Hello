package com.meuagente.app

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cada conversa é uma "aba" separada de chat. As mensagens ficam
// vinculadas a uma conversa através do campo conversaId.
@Entity(tableName = "conversas")
data class ConversaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val dataCriacao: Long
)
