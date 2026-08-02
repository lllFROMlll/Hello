package com.meuagente.app

import androidx.room.Entity
import androidx.room.PrimaryKey

// Isso é a "ficha" de cada lembrete/pendência.
// descricao: o que precisa ser feito ("entregar a blusa ao Fabrício")
// pessoa: quem está envolvido (opcional, "Vitor")
// dataCriacao: quando foi pedido
// concluido: false = ainda pendente, true = já resolvido
@Entity(tableName = "lembretes")
data class LembreteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val descricao: String,
    val pessoa: String?,
    val dataCriacao: Long,
    val concluido: Boolean = false
)
