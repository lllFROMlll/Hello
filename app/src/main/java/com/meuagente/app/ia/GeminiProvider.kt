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
 * Provider do Gemini (Google AI Studio). Migra a lógica do antigo
 * chamarGemini, agora lançando FalhaIA classificada em vez de devolver
 * strings "Erro ...".
 */
class GeminiProvider(
    private val chaveApi: String,
    override val modelos: List<String> = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite")
) : ProvedorIA {

    override val nome = "Gemini"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun perguntar(modelo: String, historico: List<MensagemEntity>, instrucao: String): String {
        return withContext(Dispatchers.IO) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$chaveApi"

            val contents = JSONArray()
            for (msg in historico) {
                val papel = if (msg.autor == "você") "user" else "model"
                val item = JSONObject()
                    .put("role", papel)
                    .put("parts", JSONArray().put(JSONObject().put("text", msg.texto)))
                contents.put(item)
            }

            val corpoJson = JSONObject()
                .put("contents", contents)
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instrucao)))
                )

            val corpo = corpoJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(corpo).build()

            try {
                val resposta = client.newCall(request).execute()
                val textoResposta = resposta.body?.string() ?: ""

                if (!resposta.isSuccessful) {
                    Log.e("ProvedorIA", "Gemini com modelo '$modelo' falhou com HTTP ${resposta.code}: ${textoResposta.take(300)}")
                    throw classificarFalha(resposta.code, resposta.header("Retry-After"), textoResposta)
                }

                val json = JSONObject(textoResposta)
                val partes = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                (0 until partes.length()).joinToString("") { i ->
                    partes.getJSONObject(i).optString("text")
                }.ifBlank { throw FalhaIA.Indisponivel("resposta vazia do Gemini") }
            } catch (e: IOException) {
                throw FalhaIA.SemRede
            } catch (e: FalhaIA) {
                throw e
            } catch (e: Exception) {
                throw FalhaIA.Indisponivel(e.message ?: "erro de processamento")
            }
        }
    }

    companion object {
        fun classificarFalha(codigo: Int, retryAfter: String?, corpo: String, nomeProvedor: String = ""): FalhaIA =
            when {
                codigo == 429 -> {
                    val retrySegundos = retryAfter?.trim()?.toLongOrNull()
                    if (retrySegundos != null && retrySegundos > 0) {
                        FalhaIA.CotaEstourada(retrySegundos)
                    } else {
                        val regex = Regex("""(?i)(?:wait|retry|reset)(?:\s+in)?\s+(\d+)\s*(?:s|seg|second|sec)""").find(corpo)
                        val segundosCorpo = regex?.groupValues?.get(1)?.toLongOrNull()
                        if (segundosCorpo != null && segundosCorpo > 0) {
                            FalhaIA.CotaEstourada(segundosCorpo)
                        } else {
                            val ehDiario = corpo.contains("day", ignoreCase = true) ||
                                           corpo.contains("daily", ignoreCase = true) ||
                                           nomeProvedor == "Gemini"
                            val padrao = if (ehDiario) 24L * 3600 else 60L
                            FalhaIA.CotaEstourada(padrao)
                        }
                    }
                }
                codigo == 401 || codigo == 403 -> FalhaIA.ChaveInvalida
                codigo >= 500 -> FalhaIA.Indisponivel("HTTP $codigo")
                else -> FalhaIA.Indisponivel("HTTP $codigo: ${corpo.take(200)}")
            }
    }
}
