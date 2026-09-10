package com.meuagente.app.ia

import android.util.Log
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
    private val baseUrl: String,
    override val modelos: List<String> = emptyList()
) : ProvedorIA {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $chaveApi")
                .post(corpo)

            if (nome == "OpenRouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://meuagente.app")
                requestBuilder.addHeader("X-Title", "MeuAgente")
            }
            val request = requestBuilder.build()

            try {
                val resposta = client.newCall(request).execute()
                val textoResposta = resposta.body?.string() ?: ""

                if (!resposta.isSuccessful) {
                    Log.e("ProvedorIA", "Provedor $nome com modelo '$modelo' falhou com HTTP ${resposta.code}: ${textoResposta.take(300)}")
                    throw GeminiProvider.classificarFalha(
                        resposta.code,
                        resposta.header("Retry-After"),
                        textoResposta,
                        nome
                    )
                }

                val json = JSONObject(textoResposta)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .ifBlank { throw FalhaIA.Indisponivel("resposta vazia de $nome") }
            } catch (e: java.net.SocketTimeoutException) {
                Log.w("ProvedorIA", "Provedor $nome com modelo '$modelo' estourou tempo limite de 45s: ${e.message}")
                throw FalhaIA.Indisponivel("tempo limite de 45s esgotado no modelo $modelo")
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
