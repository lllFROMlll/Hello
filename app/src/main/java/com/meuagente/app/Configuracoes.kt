package com.meuagente.app

import android.content.Context

// Guarda as configurações do usuário: uma chave de API por provedor
// (Gemini, OpenRouter, OpenAI, etc — sem lista fechada, qualquer nome
// de provedor pode ser usado), qual provedor está ativo agora, e qual
// modelo. Tudo salvo localmente no celular, nunca fixo no código.
object Configuracoes {
    private const val ARQUIVO = "config_agente"
    private const val CHAVE_PROVEDOR_ATUAL = "provedor_atual"
    private const val CHAVE_MODELO_ATUAL = "modelo_atual"
    private const val PREFIXO_CHAVE_API = "chave_api_"

    // Salva a chave de API de um provedor específico. Cada provedor
    // guarda sua própria chave separadamente — dá pra ter várias
    // chaves salvas ao mesmo tempo (Gemini, OpenRouter, etc).
    fun salvarChaveDoProvedor(contexto: Context, provedor: String, chave: String) {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        prefs.edit().putString(PREFIXO_CHAVE_API + provedor, chave).apply()
    }

    fun obterChaveDoProvedor(contexto: Context, provedor: String): String {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        return prefs.getString(PREFIXO_CHAVE_API + provedor, "") ?: ""
    }

    // Qual provedor está ativo agora (ex: "Gemini", "OpenRouter").
    fun salvarProvedorAtual(contexto: Context, provedor: String) {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        prefs.edit().putString(CHAVE_PROVEDOR_ATUAL, provedor).apply()
    }

    fun obterProvedorAtual(contexto: Context): String {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        return prefs.getString(CHAVE_PROVEDOR_ATUAL, "Gemini") ?: "Gemini"
    }

    // Qual modelo está sendo usado. Texto livre — qualquer nome de
    // modelo funciona, mesmo um que ainda não tenha botão pronto na tela.
    fun salvarModeloAtual(contexto: Context, modelo: String) {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        prefs.edit().putString(CHAVE_MODELO_ATUAL, modelo).apply()
    }

    fun obterModeloAtual(contexto: Context): String {
        val prefs = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
        return prefs.getString(CHAVE_MODELO_ATUAL, "") ?: ""
    }

    // Atalho: pega a chave do provedor que está ativo agora — é isso
    // que o resto do app vai usar, sem precisar saber qual provedor é.
    fun obterChaveAtual(contexto: Context): String {
        return obterChaveDoProvedor(contexto, obterProvedorAtual(contexto))
    }
}
