package com.meuagente.app

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Classificador local e barato da pergunta: "sensível ao tempo"
 * (notícias, placares, cotações, "hoje", "agora"...) ou "atemporal"
 * (definições, conceitos, história). Sem chamada de IA — apenas
 * gatilhos de palavras em português. Ambiguidade → atemporal
 * (economiza a cota de confirmação).
 */
object ClassificadorPergunta {

    private val GATILHOS_TEMPO = Regex(
        "(?i)\\b(hoje|agora|ontem|amanh[ãa]|atual(mente)?|recente(?:mente)?|últim[oa]s?|esta semana|esse mês|este mês|este ano|esse ano|" +
            "placar|jogo|jogos|partida|campeonato|notíci[as]a?|manchete|" +
            "cotação|cotações|preço|preços|dólar|euro|bitcoin|cripto|bolsa|" +
            "clima|tempo|previsão|temperatura|ao vivo|venceu|ganhou|perdeu|está jogando)\\b"
    )

    fun sensivelAoTempo(pergunta: String): Boolean =
        GATILHOS_TEMPO.containsMatchIn(pergunta)
}

/**
 * Camada final de confirmação seletiva: UMA chamada ao Google via
 * Gemini Grounding, apenas para perguntas sensíveis ao tempo, para
 * validar o dado central da resposta candidata.
 *
 * - Google confirma → retorna null (mantém a resposta candidata)
 * - Google contradiz → retorna a resposta corrigida
 * - Qualquer falha (erro/cota) → retorna null (mantém a candidata,
 *   sem travar o fluxo nem expor erro técnico)
 */
object ConfirmacaoGoogle {

    private const val MODELO = "gemini-2.0-flash"

    fun confirmar(chaveGemini: String, respostaCandidata: String, pergunta: String): String? {
        if (chaveGemini.isBlank()) return null

        val prompt =
            "Pergunta do usuário: \"$pergunta\"\n" +
                "Resposta candidata do assistente: \"${respostaCandidata.take(900)}\"\n\n" +
                "Pesquise no Google e verifique se o dado central (fato atual, placar, " +
                "número, data ou evento) dessa resposta está correto e atualizado. " +
                "Responda EXATAMENTE em um destes formatos:\n" +
                "CONFIRMADO: o dado está correto\n" +
                "CORRIGIDO: <resposta correta e atualizada, completa, em português, citando a fonte>"

        val corpo = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject()))
            )
            .toString()

        val resposta: String = Jsoup.connect(
            "https://generativelanguage.googleapis.com/v1beta/models/$MODELO:generateContent?key=$chaveGemini"
        )
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(25000)
            .header("Content-Type", "application/json")
            .requestBody(corpo)
            .execute()
            .body()

        val json = JSONObject(resposta)
        val texto = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.let { partes ->
                (0 until partes.length()).joinToString("") { i ->
                    partes.optJSONObject(i)?.optString("text").orEmpty()
                }
            }?.trim().orEmpty()

        if (texto.isBlank()) return null

        return when {
            texto.startsWith("CONFIRMADO", ignoreCase = true) -> null
            texto.startsWith("CORRIGIDO", ignoreCase = true) ->
                texto.substringAfter(":").trim().ifBlank { null }
            else -> null
        }
    }
}
