package com.meuagente.app.ia

import com.meuagente.app.MensagemEntity

/**
 * Tipos de falha classificados da chamada de IA. A cascata usa essa
 * classificação para decidir entre castigar (429), pular (401),
 * simplesmente tentar o próximo (timeout/5xx) ou interromper tudo
 * (sem internet).
 */
sealed class FalhaIA(mensagem: String) : Exception(mensagem) {

    /** Cota/limite estourado — o modelo entra "de castigo" por um tempo. */
    class CotaEstourada(val retryAfterSegundos: Long? = null) :
        FalhaIA("cota estourada")

    /** Chave inválida — pulado na cascata, sem castigo (erro de configuração). */
    object ChaveInvalida : FalhaIA("chave inválida")

    /** Timeout ou erro 5xx — falha do momento, sem castigo. */
    class Indisponivel(val detalhe: String = "") : FalhaIA("indisponível: $detalhe")

    /** Sem internet — interrompe a cascata inteira. */
    object SemRede : FalhaIA("sem conexão com a internet")
}

/** Nenhum provedor ligado com chave e modelos configurados. */
object NenhumProvedorConfigurado : Exception("nenhum provedor configurado")

/** Todos os provedores/modelos da cascata falharam. */
class TodasFalharam(val detalhes: String = "todos os provedores falharam") : Exception(detalhes)

/**
 * Interface comum de provedores de IA (mesmo princípio do ProvedorBusca
 * da camada de busca). Implementações: GeminiProvider, ProvedorFormatoOpenAI
 * (OpenRouter/OpenAI) — 9Router e IA Local entram no futuro pela mesma
 * interface, sem tocar na cascata.
 */
interface ProvedorIA {
    val nome: String

    /** Modelos configurados, na ordem de tentativa definida pelo usuário. */
    val modelos: List<String>

    suspend fun perguntar(modelo: String, historico: List<MensagemEntity>, instrucao: String): String
}
