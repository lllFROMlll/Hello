package com.meuagente.app.ia

import android.content.Context
import com.meuagente.app.Configuracoes
import com.meuagente.app.MensagemEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Núcleo da cascata de IA. Sem estado entre perguntas — exceto o mapa
 * de castigos por 429 (global, em memória, com prazo de liberação).
 *
 * Estratégia "esgotar o provedor": tenta todos os modelos do primeiro
 * provedor da ordem antes de passar para o próximo. Cada nova pergunta
 * recomeça do topo, pulando apenas modelos ainda em castigo (429) e o
 * modelo excluído por degeneração na tentativa atual (anti-loop).
 *
 * Silêncio para o usuário: erros são lançados como FalhaIA; quem chama
 * decide a mensagem amigável — nunca o erro técnico cru.
 */
object CascataIA {

    data class Resposta(val texto: String, val provedor: String, val modelo: String)

    private val castigos = ConcurrentHashMap<String, Long>()

    /** Padrão quando a resposta não traz header Retry-After. */
    private const val CASTIGO_PADRAO_SEGUNDOS = 24L * 3600

    private val PROVEDORES_CONHECIDOS = listOf("Gemini", "OpenRouter")

    private fun castigoAtivo(chave: String): Boolean {
        val liberacao = castigos[chave] ?: return false
        return liberacao > System.currentTimeMillis()
    }

    private fun castigar(provedor: String, modelo: String, retryAfterSegundos: Long?) {
        val segundos = retryAfterSegundos?.takeIf { it > 0 } ?: CASTIGO_PADRAO_SEGUNDOS
        castigos["$provedor:$modelo"] = System.currentTimeMillis() + segundos * 1000
    }

    private fun perguntaComplexa(pergunta: String): Boolean {
        if (pergunta.length > 200) return true
        return Regex(
            "(?i)(explique|explica|analise|analiza|compare|comparar|escreva|escreve|crie|criar|" +
                "código|codigo|passo a passo|resumo detalhado|detalhadamente|projeto|plano)"
        ).containsMatchIn(pergunta)
    }

    private fun modeloLeve(nome: String): Boolean =
        Regex("(?i)(lite|mini|flash|nano|small|[0-9]+b)").containsMatchIn(nome)

    private fun ordenarPorComplexidade(modelos: List<String>, complexa: Boolean): List<String> {
        val (leves, fortes) = modelos.partition { modeloLeve(it) }
        return if (complexa) fortes + leves else leves + fortes
    }

    suspend fun perguntar(
        contexto: Context,
        historico: List<MensagemEntity>,
        instrucao: String,
        excluirModelo: String? = null
    ): Resposta {
        val automatico = Configuracoes.obterAutoCascata(contexto)
        val ultimaPergunta = historico.lastOrNull { it.autor == "você" }?.texto.orEmpty()
        val complexa = perguntaComplexa(ultimaPergunta)
        val modeloLegado = Configuracoes.obterModeloAtual(contexto)
        val provedorLegado = Configuracoes.obterProvedorAtual(contexto)

        data class Entrada(val provedor: String, val provedorIA: ProvedorIA, val prioridade: Int)

        val entradas = mutableListOf<Entrada>()
        for (provedor in PROVEDORES_CONHECIDOS) {
            if (!Configuracoes.obterProvedorAtivoNaCascata(contexto, provedor, padrao = provedor == "Gemini")) continue

            val chave = Configuracoes.obterChaveDoProvedor(contexto, provedor)
            if (chave.isBlank()) continue

            val legado = modeloLegado.takeIf { provedor == provedorLegado && it.isNotBlank() }.orEmpty()
            val modelos = Configuracoes.obterModelosProvedor(contexto, provedor, legado)
            if (modelos.isEmpty()) continue

            val provedorIA: ProvedorIA = when (provedor) {
                "Gemini" -> GeminiProvider(chave)
                "OpenRouter" -> ProvedorFormatoOpenAI(
                    "OpenRouter", chave, "https://openrouter.ai/api/v1"
                )
                "OpenAI" -> ProvedorFormatoOpenAI(
                    "OpenAI", chave, "https://api.openai.com/v1"
                )
                else -> continue
            }
            entradas.add(Entrada(provedor, provedorIA, Configuracoes.obterPrioridadeProvedor(contexto, provedor)))
        }

        if (entradas.isEmpty()) throw NenhumProvedorConfigurado

        val ordenados = if (automatico) entradas else entradas.sortedBy { it.prioridade }

        var tentouAlgo = false
        for (entrada in ordenados) {
            val modelos = if (automatico) {
                ordenarPorComplexidade(entrada.provedorIA.modelos, complexa)
            } else {
                entrada.provedorIA.modelos
            }

            for (modelo in modelos) {
                val chaveCastigo = "${entrada.provedor}:$modelo"
                if (castigoAtivo(chaveCastigo)) continue
                if (excluirModelo != null && chaveCastigo == excluirModelo) continue

                tentouAlgo = true
                try {
                    val texto = entrada.provedorIA.perguntar(modelo, historico, instrucao)
                    return Resposta(texto, entrada.provedor, modelo)
                } catch (e: FalhaIA.CotaEstourada) {
                    castigar(entrada.provedor, modelo, e.retryAfterSegundos)
                } catch (e: FalhaIA.ChaveInvalida) {
                    // Erro de configuração: pula sem castigo
                } catch (e: FalhaIA.Indisponivel) {
                    // Falha do momento: pula sem castigo
                } catch (e: FalhaIA.SemRede) {
                    throw e
                }
            }
        }

        throw if (tentouAlgo) TodasFalharam else NenhumProvedorConfigurado
    }
}
