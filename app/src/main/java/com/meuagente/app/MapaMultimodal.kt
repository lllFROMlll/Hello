package com.meuagente.app

import android.content.Context

// Mapa de quais modelos aceitam áudio (multimodais), usando:
// - uma lista SUGERIDA interna (pré-definição, nunca fechada);
// - sobrescritas MANUAIS que o usuário faz por modelo nas Configurações.
// O modo Automático usa esta informação para decidir o caminho do áudio.
// Respeita a Lei "Nada é Fixo": nada é obrigatório, tudo editável.
object MapaMultimodal {
    private const val PREFS = "mapa_multimodal"
    private const val PREFIXO = "aceita_audio_"

    // Pré-definição interna apenas como sugestão — pode ser sobrescrita.
    private val SUGERIDOS_MULTIMODAL = setOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-pro",
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemini-3.1-pro",
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4.1",
        "gpt-4.1-mini",
        "claude-sonnet-4-6",
        "claude-opus-4-8",
        "claude-haiku-4-5-20251001"
    )

    // Modelos comuns que NÃO aceitam áudio (só texto) — também sugerido.
    private val SUGERIDOS_APENAS_TEXTO = setOf(
        "openrouter/auto",
        "meta-llama/llama-3.3-70b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "google/gemma-2-9b-it:free",
        "openai/gpt-oss-120b:free"
    )

    // Sobrescrita manual: true=aceita, false=não aceita, null=sem marcação.
    fun sobrescrita(contexto: Context, modelo: String): Boolean? {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chave = PREFIXO + modelo.trim().lowercase()
        if (!prefs.contains(chave)) return null
        return prefs.getBoolean(chave, false)
    }

    fun marcarAceitaAudio(contexto: Context, modelo: String, aceita: Boolean) {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFIXO + modelo.trim().lowercase(), aceita).apply()
    }

    // Regra de decisão do Automático:
    // 1. Sobrescrita manual manda (se houver).
    // 2. Senão, usa a sugestão interna se conhecer o modelo.
    // 3. Modelo desconhecido → assume que aceita e tenta a IA (o fallback corrige).
    fun ehMultimodal(contexto: Context, modelo: String): Boolean {
        val nome = modelo.trim().lowercase()
        val manual = sobrescrita(contexto, nome)
        if (manual != null) return manual
        if (nome in SUGERIDOS_MULTIMODAL) return true
        if (nome in SUGERIDOS_APENAS_TEXTO) return false
        return true
    }
}