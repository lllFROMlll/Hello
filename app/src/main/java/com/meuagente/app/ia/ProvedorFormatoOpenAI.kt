package com.meuagente.app.ia

import com.meuagente.app.MensagemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Provider genérico do formato OpenAI (messages + Bearer). Serve para
 * OpenRouter e OpenAI — muda apenas a URL base e o nome. Um provedor
 * futuro (9Router) com o mesmo formato entra aqui de graça.
 */
class ProvedorFormatoOpenAI(
    override val nome: String,
    private val chaveApi: String,
    private val baseUrl: String
) : ProvedorIA {

    override val modelos: List<String> = emptyList()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun perguntar(modelo: String, historico: List<MensagemEntity>, instrucao: String): String {
        return withContext(Dispatchers.IO) {
            val mensagens = JSONArray()
            mensagens.put(JSONObject().put("role", "system").put("content", instrucao))
            for (msg in historico) {
                val papel = if (msg.autor == "você") "user" else "assistant"
                mensagens.put(JSONObject().put("role", papel).put("content", msg.texto))
            }

            val corpoJson = JSONObject()
                .put("model", modelo)
                .put("messages", mensagens)

            val corpo = corpoJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $chaveApi")
                .post(corpo)
                .build()

            try {
                val resposta = client.newCall(request).execute()
                val textoResposta = resposta.body?.string() ?: ""

                if (!resposta.isSuccessful) {
                    throw GeminiProvider.classificarFalha(
                        resposta.code,
                        resposta.header("Retry-After"),
                        textoResposta
                    )
                }

                val json = JSONObject(textoResposta)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .ifBlank { throw FalhaIA.Indisponivel("resposta vazia de $nome") }
            } catch (e: IOException) {
                throw FalhaIA.SemRede
            } catch (e: FalhaIA) {
                throw e
            } catch (e: Exception) {
                throw FalhaIA.Indisponivel(e.message ?: "erro de processamento")
            }
        }
    }
}
