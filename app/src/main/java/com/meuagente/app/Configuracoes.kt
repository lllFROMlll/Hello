package com.meuagente.app

import android.content.Context

// Isso guarda a chave de API do usuário, salva no próprio celular,
// de forma privada (só o nosso app consegue ler).
object Configuracoes {
    private const val ARQUIVO = "config_agente"
    private const val CHAVE_API = "chave_api"

    fun salvarChave(contexto: Context, chave: String) {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        prefs.edit().putString(CHAVE_API, chave).apply()
    }

    fun obterChave(contexto: Context): String {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        return prefs.getString(CHAVE_API, "") ?: ""
    }
}
