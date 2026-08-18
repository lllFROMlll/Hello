package com.meuagente.app

import androidx.room.TypeConverter

// "Tradutor" que ensina o Room a guardar e ler o enum Repeticao
// no banco de dados (ele salva como texto: "NENHUMA", "DIARIA", etc).
class Converters {
    @TypeConverter
    fun deRepeticaoParaTexto(repeticao: Repeticao): String = repeticao.name

    @TypeConverter
    fun deTextoParaRepeticao(texto: String): Repeticao = Repeticao.valueOf(texto)
}
