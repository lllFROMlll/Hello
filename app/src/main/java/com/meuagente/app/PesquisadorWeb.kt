package com.meuagente.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pesquisador real da internet usado pelo comando [BUSCAR: termos].
 *
 * Cascata de motores SEM chave de API (na ordem): DuckDuckGo → Bing →
 * Mojeek → SearXNG (lista de instâncias) → Wikipedia. Os resultados são
 * combinados e deduplicados por URL; se um motor cair ou bloquear, o
 * próximo assume. Depois, as páginas mais relevantes são abertas e o
 * texto principal é extraído para a IA responder com informação atual.
 *
 * Limites honestos: páginas atrás de login ou que bloqueiam robôs não
 * são legíveis — nesses casos usa-se apenas o resumo da busca.
 */
object PesquisadorWeb {

    private const val AGENTE =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private const val MAX_RESULTADOS_BRUTOS = 8
    private const val MAX_PAGINAS_ABERTAS = 3
    private const val MAX_CARACTERES_PAGINA = 3500

    private val instanciasSearx = listOf(
        "https://searx.be",
        "https://searx.tiekoetter.com",
        "https://search.bus-hit.me"
    )

    fun buscar(termos: String): String {
        val resultados = coletarResultados(termos)
        if (resultados.isEmpty()) {
            return "A busca \"$termos\" não retornou resultados utilizáveis."
        }

        val blocos = StringBuilder()
        var numero = 1

        for (resultado in resultados.take(MAX_PAGINAS_ABERTAS)) {
            blocos.append("FONTE $numero: ${resultado.titulo}\nURL: ${resultado.url}\n")
            blocos.append("Resumo: ${resultado.resumo}\n")
            val conteudo = runCatching {
                val pagina: Document = Jsoup.connect(resultado.url)
                    .userAgent(AGENTE)
                    .timeout(15000)
                    .followRedirects(true)
                    .get()
                pagina.body().text().take(MAX_CARACTERES_PAGINA)
            }.getOrNull()
            if (!conteudo.isNullOrBlank()) {
                blocos.append("Conteúdo da página: $conteudo\n")
            }
            blocos.append("\n")
            numero++
        }
        return blocos.toString().trim()
    }

    private data class ResultadoBusca(val titulo: String, val url: String, val resumo: String)

    private fun normalizar(url: String): String =
        url.substringBefore("#").trimEnd('/').lowercase()

    private fun coletarResultados(termos: String): List<ResultadoBusca> {
        val coletados = mutableListOf<ResultadoBusca>()
        val vistas = mutableSetOf<String>()

        for (obter in listOf(
            ::buscarDuckDuckGo,
            ::buscarBing,
            ::buscarMojeek,
            ::buscarSearx,
            ::buscarWikipedia
        )) {
            runCatching { obter(termos) }
                .getOrDefault(emptyList())
                .forEach { resultado ->
                    val chave = normalizar(resultado.url)
                    if (chave.startsWith("http") && vistas.add(chave)) {
                        coletados.add(resultado)
                    }
                }
            if (coletados.size >= MAX_RESULTADOS_BRUTOS) break
        }
        return coletados
    }

    private fun buscarDuckDuckGo(termos: String): List<ResultadoBusca> {
        val documento: Document = Jsoup.connect("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoBusca>()
        for (elemento in documento.select("div.result, div.web-result")) {
            val link = elemento.selectFirst("a.result__a") ?: continue
            val titulo = link.text().trim()
            var destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank()) continue

            // DuckDuckGo usa redirecionador /l/?uddg=URL_REAL — extrair a URL real
            if (destino.contains("uddg=")) {
                runCatching {
                    val parametro = destino.substringAfter("uddg=").substringBefore("&")
                    destino = URLDecoder.decode(parametro, "UTF-8")
                }
            }
            if (!destino.startsWith("http")) continue

            val resumo = elemento.selectFirst("a.result__snippet, .result__snippet")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoBusca(titulo, destino, resumo))
        }
        return resultados
    }

    private fun buscarBing(termos: String): List<ResultadoBusca> {
        val documento: Document = Jsoup.connect("https://www.bing.com/search?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoBusca>()
        for (elemento in documento.select("li.b_algo")) {
            val link = elemento.selectFirst("h2 a") ?: continue
            val titulo = link.text().trim()
            val destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

            val resumo = elemento.selectFirst(".b_caption p, p")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoBusca(titulo, destino, resumo))
        }
        return resultados
    }

    private fun buscarMojeek(termos: String): List<ResultadoBusca> {
        val documento: Document = Jsoup.connect("https://www.mojeek.com/search?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoBusca>()
        for (elemento in documento.select("ul.results-standard li, li.result")) {
            val link = elemento.selectFirst("a.title, h2 a") ?: continue
            val titulo = link.text().trim()
            val destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

            val resumo = elemento.selectFirst("p.s, p")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoBusca(titulo, destino, resumo))
        }
        return resultados
    }

    private fun buscarSearx(termos: String): List<ResultadoBusca> {
        val consulta = URLEncoder.encode(termos, "UTF-8")

        // 1ª tentativa: modo JSON das instâncias públicas
        for (instancia in instanciasSearx) {
            val viaJson = runCatching {
                val corpo = Jsoup.connect("$instancia/search?q=$consulta&format=json")
                    .ignoreContentType(true)
                    .userAgent(AGENTE)
                    .timeout(12000)
                    .execute()
                    .body()
                val array = org.json.JSONObject(corpo).optJSONArray("results") ?: return@runCatching emptyList()
                (0 until array.length()).mapNotNull { i ->
                    val item = array.getJSONObject(i)
                    val url = item.optString("url")
                    val titulo = item.optString("title").trim()
                    if (!url.startsWith("http") || titulo.isBlank()) null
                    else ResultadoBusca(titulo, url, item.optString("content").trim())
                }
            }.getOrDefault(emptyList())
            if (viaJson.isNotEmpty()) return viaJson
        }

        // 2ª tentativa: HTML das instâncias
        for (instancia in instanciasSearx) {
            val viaHtml = runCatching {
                val documento: Document = Jsoup.connect("$instancia/search?q=$consulta")
                    .userAgent(AGENTE)
                    .timeout(12000)
                    .get()
                val resultados = mutableListOf<ResultadoBusca>()
                for (elemento in documento.select("article.result")) {
                    val link = elemento.selectFirst("h3 a") ?: continue
                    val titulo = link.text().trim()
                    val destino = link.absUrl("href").ifBlank { link.attr("href") }
                    if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue
                    val resumo = elemento.selectFirst("p.content")
                        ?.text()?.trim().orEmpty()
                    resultados.add(ResultadoBusca(titulo, destino, resumo))
                }
                resultados
            }.getOrDefault(emptyList())
            if (viaHtml.isNotEmpty()) return viaHtml
        }
        return emptyList()
    }

    private fun buscarWikipedia(termos: String): List<ResultadoBusca> {
        val documento: Document = Jsoup.connect(
            "https://pt.wikipedia.org/w/index.php?search=" + URLEncoder.encode(termos, "UTF-8")
        )
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoBusca>()
        for (elemento in documento.select("ul.mw-search-results li.mw-search-result")) {
            val link = elemento.selectFirst("a") ?: continue
            val titulo = link.text().trim()
            val destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

            val resumo = elemento.selectFirst(".searchresult")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoBusca(titulo, destino, resumo))
        }
        return resultados
    }
}
