package com.meuagente.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mensagens")
data class MensagemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val autor: String,
    val texto: String,
    val dataHora: Long
)
