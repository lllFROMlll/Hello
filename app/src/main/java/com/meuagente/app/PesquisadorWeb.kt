package com.meuagente.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pesquisa real da internet com arquitetura desacoplada: uma interface
 * comum [ProvedorBusca] e uma cascata configurável. Se um provedor
 * falhar ou vier vazio, o próximo assume — o fluxo nunca quebra.
 *
 * Ordem (configurada em buscar()):
 * 1. Tavily (API oficial, conteúdo já limpo para IA) — se houver chave
 * 2. Brave Search (API oficial, índice próprio) — se houver chave
 * 3. Scraping como último recurso (sem chave): DuckDuckGo → Bing →
 *    Mojeek → SearXNG → Wikipedia
 *
 * Resultados são combinados e deduplicados por URL; as páginas mais
 * relevantes são abertas para extrair o texto principal.
 */
object PesquisadorWeb {

    private const val AGENTE =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private const val MAX_RESULTADOS_BRUTOS = 8
    private const val MAX_PAGINAS_ABERTAS = 3
    private const val MAX_CARACTERES_PAGINA = 3500

    data class ResultadoWeb(
        val titulo: String,
        val url: String,
        val resumo: String,
        val conteudo: String = ""
    )

    interface ProvedorBusca {
        val nome: String
        fun buscar(termos: String): List<ResultadoWeb>
    }

    private fun normalizar(url: String): String =
        url.substringBefore("#").trimEnd('/').lowercase()

    fun buscar(termos: String, chaveTavily: String = "", chaveBrave: String = ""): String {
        val provedores = buildList {
            if (chaveTavily.isNotBlank()) add(TavilyBusca(chaveTavily))
            if (chaveBrave.isNotBlank()) add(BraveBusca(chaveBrave))
            add(ScrapingBusca("DuckDuckGo") { buscarDuckDuckGo(it) })
            add(ScrapingBusca("Bing") { buscarBing(it) })
            add(ScrapingBusca("Mojeek") { buscarMojeek(it) })
            add(SearxBusca())
            add(WikipediaBusca())
        }

        val coletados = mutableListOf<ResultadoWeb>()
        val vistas = mutableSetOf<String>()

        for (provedor in provedores) {
            runCatching { provedor.buscar(termos) }
                .getOrDefault(emptyList())
                .forEach { resultado ->
                    val chave = normalizar(resultado.url)
                    if (chave.startsWith("http") && vistas.add(chave)) {
                        coletados.add(resultado)
                    }
                }
            if (coletados.size >= MAX_RESULTADOS_BRUTOS) break
        }

        if (coletados.isEmpty()) {
            return "A busca \"$termos\" não retornou resultados utilizáveis."
        }

        val blocos = StringBuilder()
        var numero = 1

        for (resultado in coletados.take(MAX_PAGINAS_ABERTAS)) {
            blocos.append("FONTE $numero: ${resultado.titulo}\nURL: ${resultado.url}\n")
            val conteudo = if (resultado.conteudo.length >= 400) {
                resultado.conteudo.take(MAX_CARACTERES_PAGINA)
            } else {
                (resultado.resumo + "\n" + runCatching {
                    val pagina: Document = Jsoup.connect(resultado.url)
                        .userAgent(AGENTE)
                        .timeout(15000)
                        .followRedirects(true)
                        .get()
                    pagina.body().text()
                }.getOrDefault("")).take(MAX_CARACTERES_PAGINA)
            }
            if (conteudo.isNotBlank()) {
                blocos.append("Conteúdo: $conteudo\n")
            }
            blocos.append("\n")
            numero++
        }
        return blocos.toString().trim()
    }

    // ══════════════ PROVEDORES COM API OFICIAL ══════════════

    private class TavilyBusca(private val chave: String) : ProvedorBusca {
        override val nome = "Tavily"

        override fun buscar(termos: String): List<ResultadoWeb> {
            val corpo = org.json.JSONObject()
                .put("query", termos)
                .put("max_results", 5)
                .put("search_depth", "basic")
                .toString()

            val resposta: String = Jsoup.connect("https://api.tavily.com/search")
                .ignoreContentType(true)
                .userAgent(AGENTE)
                .timeout(15000)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $chave")
                .requestBody(corpo)
                .execute()
                .body()

            val json = org.json.JSONObject(resposta)
            val array = json.optJSONArray("results") ?: return emptyList()

            return (0 until array.length()).mapNotNull { i ->
                val item = array.getJSONObject(i)
                val url = item.optString("url")
                val titulo = item.optString("title").trim()
                if (!url.startsWith("http") || titulo.isBlank()) null
                else ResultadoWeb(titulo, url, item.optString("content").trim())
            }
        }
    }

    private class BraveBusca(private val chave: String) : ProvedorBusca {
        override val nome = "Brave"

        override fun buscar(termos: String): List<ResultadoWeb> {
            val resposta: String = Jsoup.connect(
                "https://api.search.brave.com/res/v1/web/search?q=" + URLEncoder.encode(termos, "UTF-8")
            )
                .ignoreContentType(true)
                .userAgent(AGENTE)
                .timeout(15000)
                .header("Accept", "application/json")
                .header("X-Subscription-Token", chave)
                .execute()
                .body()

            val json = org.json.JSONObject(resposta)
            val array = json.optJSONObject("web")?.optJSONArray("results") ?: return emptyList()

            return (0 until array.length()).mapNotNull { i ->
                val item = array.getJSONObject(i)
                val url = item.optString("url")
                val titulo = item.optString("title").trim()
                if (!url.startsWith("http") || titulo.isBlank()) null
                else ResultadoWeb(titulo, url, item.optString("description").trim())
            }
        }
    }

    // ══════════════ PROVEDORES DE SCRAPING (último recurso) ══════════════

    private class ScrapingBusca(
        override val nome: String,
        private val funcao: (String) -> List<ResultadoWeb>
    ) : ProvedorBusca {
        override fun buscar(termos: String): List<ResultadoWeb> = funcao(termos)
    }

    private fun buscarDuckDuckGo(termos: String): List<ResultadoWeb> {
        val documento: Document = Jsoup.connect("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoWeb>()
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
            resultados.add(ResultadoWeb(titulo, destino, resumo))
        }
        return resultados
    }

    private fun buscarBing(termos: String): List<ResultadoWeb> {
        val documento: Document = Jsoup.connect("https://www.bing.com/search?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoWeb>()
        for (elemento in documento.select("li.b_algo")) {
            val link = elemento.selectFirst("h2 a") ?: continue
            val titulo = link.text().trim()
            val destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

            val resumo = elemento.selectFirst(".b_caption p, p")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoWeb(titulo, destino, resumo))
        }
        return resultados
    }

    private fun buscarMojeek(termos: String): List<ResultadoWeb> {
        val documento: Document = Jsoup.connect("https://www.mojeek.com/search?q=" + URLEncoder.encode(termos, "UTF-8"))
            .userAgent(AGENTE)
            .timeout(15000)
            .get()

        val resultados = mutableListOf<ResultadoWeb>()
        for (elemento in documento.select("ul.results-standard li, li.result")) {
            val link = elemento.selectFirst("a.title, h2 a") ?: continue
            val titulo = link.text().trim()
            val destino = link.absUrl("href").ifBlank { link.attr("href") }
            if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

            val resumo = elemento.selectFirst("p.s, p")
                ?.text()?.trim().orEmpty()
            resultados.add(ResultadoWeb(titulo, destino, resumo))
        }
        return resultados
    }

    private class SearxBusca : ProvedorBusca {
        override val nome = "SearXNG"

        private val instancias = listOf(
            "https://searx.be",
            "https://searx.tiekoetter.com",
            "https://search.bus-hit.me"
        )

        override fun buscar(termos: String): List<ResultadoWeb> {
            val consulta = URLEncoder.encode(termos, "UTF-8")

            for (instancia in instancias) {
                val viaJson = runCatching {
                    val corpo = Jsoup.connect("$instancia/search?q=$consulta&format=json")
                        .ignoreContentType(true)
                        .userAgent(AGENTE)
                        .timeout(12000)
                        .execute()
                        .body()
                    val array = org.json.JSONObject(corpo).optJSONArray("results")
                        ?: return@runCatching emptyList()
                    (0 until array.length()).mapNotNull { i ->
                        val item = array.getJSONObject(i)
                        val url = item.optString("url")
                        val titulo = item.optString("title").trim()
                        if (!url.startsWith("http") || titulo.isBlank()) null
                        else ResultadoWeb(titulo, url, item.optString("content").trim())
                    }
                }.getOrDefault(emptyList())
                if (viaJson.isNotEmpty()) return viaJson

                val viaHtml = runCatching {
                    val documento: Document = Jsoup.connect("$instancia/search?q=$consulta")
                        .userAgent(AGENTE)
                        .timeout(12000)
                        .get()
                    val resultados = mutableListOf<ResultadoWeb>()
                    for (elemento in documento.select("article.result")) {
                        val link = elemento.selectFirst("h3 a") ?: continue
                        val titulo = link.text().trim()
                        val destino = link.absUrl("href").ifBlank { link.attr("href") }
                        if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue
                        val resumo = elemento.selectFirst("p.content")
                            ?.text()?.trim().orEmpty()
                        resultados.add(ResultadoWeb(titulo, destino, resumo))
                    }
                    resultados
                }.getOrDefault(emptyList())
                if (viaHtml.isNotEmpty()) return viaHtml
            }
            return emptyList()
        }
    }

    private class WikipediaBusca : ProvedorBusca {
        override val nome = "Wikipedia"

        override fun buscar(termos: String): List<ResultadoWeb> {
            val documento: Document = Jsoup.connect(
                "https://pt.wikipedia.org/w/index.php?search=" + URLEncoder.encode(termos, "UTF-8")
            )
                .userAgent(AGENTE)
                .timeout(15000)
                .get()

            val resultados = mutableListOf<ResultadoWeb>()
            for (elemento in documento.select("ul.mw-search-results li.mw-search-result")) {
                val link = elemento.selectFirst("a") ?: continue
                val titulo = link.text().trim()
                val destino = link.absUrl("href").ifBlank { link.attr("href") }
                if (destino.isBlank() || titulo.isBlank() || !destino.startsWith("http")) continue

                val resumo = elemento.selectFirst(".searchresult")
                    ?.text()?.trim().orEmpty()
                resultados.add(ResultadoWeb(titulo, destino, resumo))
            }
            return resultados
        }
    }
}
