package com.meuagente.app.ia

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ModeloOpenRouter(
    val id: String,
    val nome: String,
    val ehGratuito: Boolean
)

object RepositorioModelosOpenRouter {

    private const val TAG = "ModelosOpenRouter"
    private const val URL_MODELOS = "https://openrouter.ai/api/v1/models"
    private const val ARQUIVO_CACHE = "cache_modelos_openrouter.json"
    private const val PREFS = "prefs_modelos_openrouter"
    private const val CHAVE_ULTIMA_BUSCA = "ultima_busca_ms"
    private const val VALIDADE_CACHE_MS = 24L * 3600 * 1000 // 24 horas

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Modelos padrão de contingência caso esteja offline na primeira abertura
    private val MODELOS_PADRAO = listOf(
        ModeloOpenRouter("openrouter/auto", "Auto (melhor modelo para o prompt)", false),
        ModeloOpenRouter("meta-llama/llama-3.3-70b-instruct:free", "Meta: Llama 3.3 70B Instruct (free)", true),
        ModeloOpenRouter("deepseek/deepseek-chat", "DeepSeek Chat", false),
        ModeloOpenRouter("qwen/qwen-2.5-72b-instruct:free", "Qwen 2.5 72B Instruct (free)", true),
        ModeloOpenRouter("openai/gpt-oss-120b:free", "OpenAI: GPT-OSS 120B (free)", true),
        ModeloOpenRouter("google/gemini-2.5-flash", "Google: Gemini 2.5 Flash", false)
    )

    fun precisaAtualizar(contexto: Context): Boolean {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultimaBusca = prefs.getLong(CHAVE_ULTIMA_BUSCA, 0L)
        val agora = System.currentTimeMillis()
        return (agora - ultimaBusca) > VALIDADE_CACHE_MS || !arquivoCacheExiste(contexto)
    }

    private fun arquivoCacheExiste(contexto: Context): Boolean {
        val arquivo = File(contexto.filesDir, ARQUIVO_CACHE)
        return arquivo.exists() && arquivo.length() > 0
    }

    /**
     * Lê do cache local imediatamente (sem bloquear a interface com chamada de rede).
     */
    fun lerCacheLocal(contexto: Context): List<ModeloOpenRouter> {
        return try {
            val arquivo = File(contexto.filesDir, ARQUIVO_CACHE)
            if (!arquivo.exists()) return MODELOS_PADRAO
            val texto = arquivo.readText()
            deserializar(texto)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler cache local de modelos OpenRouter", e)
            MODELOS_PADRAO
        }
    }

    /**
     * Busca os modelos direto da API pública do OpenRouter e salva no cache local.
     * Retorna a lista atualizada ou a lista em cache caso falhe.
     */
    suspend fun buscarAoVivo(contexto: Context): List<ModeloOpenRouter> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Consultando API de modelos do OpenRouter em $URL_MODELOS...")
            val request = Request.Builder().url(URL_MODELOS).get().build()
            val response = client.newCall(request).execute()
            val corpo = response.body?.string().orEmpty()

            if (!response.isSuccessful || corpo.isBlank()) {
                Log.w(TAG, "Resposta com erro da API do OpenRouter: HTTP ${response.code}")
                return@withContext lerCacheLocal(contexto)
            }

            val json = JSONObject(corpo)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val lista = mutableListOf<ModeloOpenRouter>()

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                val nome = item.optString("name", id).trim()
                val pricing = item.optJSONObject("pricing")
                val promptPrice = pricing?.optString("prompt", "-1") ?: "-1"
                val completionPrice = pricing?.optString("completion", "-1") ?: "-1"

                val ehGratis = id.endsWith(":free") || (promptPrice == "0" && completionPrice == "0")
                lista.add(ModeloOpenRouter(id = id, nome = nome, ehGratuito = ehGratis))
            }

            if (lista.isNotEmpty()) {
                // Ordenação: modelos gratuitos no topo, depois alfabético por ID
                lista.sortWith(compareByDescending<ModeloOpenRouter> { it.ehGratuito }.thenBy { it.id })

                salvarEmCache(contexto, lista)
                Log.i(TAG, "Sucesso: ${lista.size} modelos carregados do OpenRouter.")
                lista
            } else {
                lerCacheLocal(contexto)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha de rede ao buscar modelos do OpenRouter: ${e.message}")
            lerCacheLocal(contexto)
        }
    }

    private fun salvarEmCache(contexto: Context, lista: List<ModeloOpenRouter>) {
        try {
            val jsonArray = JSONArray()
            for (m in lista) {
                val obj = JSONObject()
                obj.put("id", m.id)
                obj.put("nome", m.nome)
                obj.put("gratis", m.ehGratuito)
                jsonArray.put(obj)
            }
            val arquivo = File(contexto.filesDir, ARQUIVO_CACHE)
            arquivo.writeText(jsonArray.toString())

            val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong(CHAVE_ULTIMA_BUSCA, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar arquivo de cache de modelos", e)
        }
    }

    private fun deserializar(jsonStr: String): List<ModeloOpenRouter> {
        val array = JSONArray(jsonStr)
        val lista = mutableListOf<ModeloOpenRouter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            lista.add(
                ModeloOpenRouter(
                    id = obj.getString("id"),
                    nome = obj.optString("nome", obj.getString("id")),
                    ehGratuito = obj.optBoolean("gratis", false)
                )
            )
        }
        return if (lista.isNotEmpty()) lista else MODELOS_PADRAO
    }
}
