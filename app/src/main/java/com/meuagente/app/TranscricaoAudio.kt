package com.meuagente.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Adaptadores de áudio por provedor de IA.
 *
 * Não fixa nada no código (Lei "Nada é Fixo"): cada provedor tem seu
 * adaptador certo e, se um não suportar áudio, o app avisa em português
 * simples em vez de travar.
 *
 * Duas capacidades:
 *  - transcrever áudio → texto (provedores com API de transcrição);
 *  - enviar áudio BRUTO ao modelo multimodal (Gemini / OpenRouter complejo).
 */

const val PROMPT_TRANSCRICAO =
    "Transcreva fielmente em português o que foi falado neste áudio. " +
        "Preserve o sentido e as nuances. Responda APENAS com o texto falado, " +
        "sem aspas, sem explicações e sem prefixos."

private fun clienteHttp(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
}

/**
 * Transcreve o áudio WAV usando o provedor ativo.
 * Retorna o texto transcrito, ou uma mensagem de erro/aviso em português.
 */
suspend fun transcreverAudioComProvedor(
    audioWav: ByteArray,
    provedor: String,
    modelo: String,
    chaveApi: String
): String {
    if (chaveApi.isBlank()) {
        return "Você ainda não configurou uma chave de API para o provedor \"$provedor\". Toque no menu (☰) para adicionar uma."
    }

    return when (provedor) {
        "Gemini" -> transcreverViaGemini(audioWav, chaveApi, modelo.ifBlank { "gemini-2.5-flash" })
        "OpenAI" -> transcreverViaOpenAI(audioWav, chaveApi, modelo)
        "OpenRouter" -> transcreverViaOpenRouter(audioWav, chaveApi, modelo)
        "Anthropic" -> "A transcrição por voz com Anthropic ainda não está pronta. Escolha Gemini, OpenAI ou OpenRouter nas Configurações por enquanto."
        else -> "A transcrição por voz com o provedor \"$provedor\" ainda não tem adaptador de áudio. Escolha Gemini, OpenAI ou OpenRouter nas Configurações."
    }
}

/**
 * Envia o áudio BRUTO ao modelo multimodal e devolve a resposta em texto.
 * Destinado ao fluxo em que o usuário quer "falar com a IA" preservando tom
 * e urgência. Retorna texto de resposta ou mensagem simples de erro.
 */
suspend fun falarComAudioBruto(
    audioWav: ByteArray,
    provedor: String,
    modelo: String,
    chaveApi: String,
    perguntaContexto: String? = null
): String {
    if (chaveApi.isBlank()) {
        return "Você ainda não configurou uma chave de API para o provedor \"$provedor\"."
    }
    val instrucao = perguntaContexto
        ?: "Escute o áudio abaixo (português) e responda de forma natural, como um amigo, " +
            "reagindo ao tom, ênfase e conteúdo do que foi falado."

    return when (provedor) {
        "Gemini" -> falarAudioViaGemini(audioWav, chaveApi, modelo.ifBlank { "gemini-2.5-flash" }, instrucao)
        "OpenRouter" -> falarAudioViaOpenRouter(audioWav, chaveApi, modelo, instrucao)
        "OpenAI" -> "O envio de áudio bruto direto à IA ainda não está pronto para OpenAI neste fluxo. " +
            "Desative o \"modo IA\" ou escolha Gemini/OpenRouter, ou use a transcrição em texto."
        "Anthropic" -> "O envio de áudio bruto direto à IA ainda não está pronto para Anthropic."
        else -> "O provedor \"$provedor\" ainda não aceita áudio bruto neste fluxo."
    }
}

// ──────────────────────────────── GEMINI ────────────────────────────────
private suspend fun transcreverViaGemini(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String
): String = withContext(Dispatchers.IO) {
    try {
        val base64 = Base64.encodeToString(audioWav, Base64.NO_WRAP)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$chaveApi"

        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "audio/wav")
                        .put("data", base64)
                )
            )
            .put(JSONObject().put("text", PROMPT_TRANSCRICAO))

        val corpoJson = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put("parts", parts)
            )
        )

        val request = Request.Builder()
            .url(url)
            .post(corpoJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resposta = clienteHttp().newCall(request).execute()
        val textoResposta = resposta.body?.string() ?: ""
        if (!resposta.isSuccessful) {
            return@withContext "Erro ${resposta.code} ao transcrever com Gemini: $textoResposta"
        }

        val json = JSONObject(textoResposta)
        val candidatos = json.getJSONArray("candidates")
        val primeiro = candidatos.getJSONObject(0)
        val conteudo = primeiro.getJSONObject("content")
        val partes = conteudo.getJSONArray("parts")
        partes.getJSONObject(0).getString("text").trim()
    } catch (e: IOException) {
        "Não consegui me conectar à internet agora. Tenta de novo em instantes."
    } catch (e: Exception) {
        "Algo deu errado ao transcrever com Gemini: ${e.message}"
    }
}

private suspend fun falarAudioViaGemini(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String,
    instrucao: String
): String = withContext(Dispatchers.IO) {
    try {
        val base64 = Base64.encodeToString(audioWav, Base64.NO_WRAP)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$chaveApi"

        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", base64)
                )
            )
            .put(JSONObject().put("text", instrucao))

        val corpoJson = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        )

        val request = Request.Builder()
            .url(url)
            .post(corpoJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val resposta = clienteHttp().newCall(request).execute()
        val textoResposta = resposta.body?.string() ?: ""
        if (!resposta.isSuccessful) {
            return@withContext "Erro ${resposta.code} no modo áudio com Gemini: $textoResposta"
        }

        val json = JSONObject(textoResposta)
        val candidatos = json.getJSONArray("candidates")
        val conteudo = candidatos.getJSONObject(0).getJSONObject("content")
        val partes = conteudo.getJSONArray("parts")
        partes.getJSONObject(0).getString("text")
    } catch (e: IOException) {
        "Não consegui me conectar à internet agora. Tenta de novo em instantes."
    } catch (e: Exception) {
        "Algo deu errado no modo áudio com Gemini: ${e.message}"
    }
}

// ──────────────────────────────── OPENAI ────────────────────────────────
private suspend fun transcreverViaOpenAI(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String
): String = withContext(Dispatchers.IO) {
    try {
        val modeloAudio = when {
            modelo.contains("whisper", ignoreCase = true) -> modelo
            else -> "whisper-1"
        }

        val corpo = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", modeloAudio)
            .addFormDataPart("language", "pt")
            .addFormDataPart("file", "audio.wav", audioWav.toRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $chaveApi")
            .post(corpo)
            .build()

        val resposta = clienteHttp().newCall(request).execute()
        val textoResposta = resposta.body?.string() ?: ""
        if (!resposta.isSuccessful) {
            return@withContext "Erro ${resposta.code} ao transcrever com OpenAI: $textoResposta"
        }

        JSONObject(textoResposta).optString("text", textoResposta).trim()
    } catch (e: IOException) {
        "Não consegui me conectar à internet agora. Tenta de novo em instantes."
    } catch (e: Exception) {
        "Algo deu errado ao transcrever com OpenAI: ${e.message}"
    }
}

// ──────────────────────────────── OPENROUTER ────────────────────────────
private suspend fun transcreverViaOpenRouter(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String
): String = withContext(Dispatchers.IO) {
    try {
        val modeloAudio = when {
            modelo.contains("whisper", ignoreCase = true) -> modelo
            else -> "openai/whisper-large-v3"
        }

        val corpo = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", modeloAudio)
            .addFormDataPart("language", "pt")
            .addFormDataPart("file", "audio.wav", audioWav.toRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $chaveApi")
            .addHeader("HTTP-Referer", "https://github.com/IIIFROMIII/Hello")
            .addHeader("X-Title", "Bler")
            .post(corpo)
            .build()

        val resposta = clienteHttp().newCall(request).execute()
        val textoResposta = resposta.body?.string() ?: ""
        if (!resposta.isSuccessful) {
            if (modelo.contains("gemini", ignoreCase = true) || modelo.contains("google/", ignoreCase = true)) {
                return@withContext transcreverOpenRouterMultimodal(audioWav, chaveApi, modelo, PROMPT_TRANSCRICAO)
            }
            return@withContext "Erro ${resposta.code} ao transcrever com OpenRouter: $textoResposta"
        }

        val texto = try {
            JSONObject(textoResposta).optString("text", "")
        } catch (_: Exception) {
            ""
        }
        if (texto.isNotBlank()) texto.trim() else textoResposta.trim()
    } catch (e: IOException) {
        "Não consegui me conectar à internet agora. Tenta de novo em instantes."
    } catch (e: Exception) {
        "Algo deu errado ao transcrever com OpenRouter: ${e.message}"
    }
}

private suspend fun falarAudioViaOpenRouter(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String,
    instrucao: String
): String {
    return transcreverOpenRouterMultimodal(audioWav, chaveApi, modelo, instrucao)
}

/**
 * Gemini via chat do OpenRouter, como fallback multimdial.
 */
private suspend fun transcreverOpenRouterMultimodal(
    audioWav: ByteArray,
    chaveApi: String,
    modelo: String,
    instrucao: String
): String = withContext(Dispatchers.IO) {
    try {
        val base64 = Base64.encodeToString(audioWav, Base64.NO_WRAP)
        val dataUrl = "data:audio/wav;base64,$base64"

        val contentAlt = JSONArray()
            .put(
                JSONObject()
                    .put("type", "file")
                    .put(
                        "file",
                        JSONObject().put("filename", "audio.wav").put("file_data", dataUrl)
                    )
            )
            .put(JSONObject().put("type", "text").put("text", instrucao))

        fun montarPedido(partes: JSONArray): Request {
            val corpoJson = JSONObject()
                .put("model", modelo)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put("content", partes)
                    )
                )
            return Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer $chaveApi")
                .addHeader("HTTP-Referer", "https://github.com/IIIFROMIII/Hello")
                .addHeader("X-Title", "Bler")
                .post(corpoJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
        }

        val resposta = clienteHttp().newCall(montarPedido(contentAlt)).execute()
        val textoResposta = resposta.body?.string() ?: ""
        if (!resposta.isSuccessful) {
            return@withContext "Erro ${resposta.code} ao transcrever com OpenRouter: $textoResposta"
        }

        val json = JSONObject(textoResposta)
        val escolhas = json.getJSONArray("choices")
        val mensagem = escolhas.getJSONObject(0).getJSONObject("message")
        mensagem.getString("content").trim()
    } catch (e: Exception) {
        "Algo deu errado ao transcrever com OpenRouter: ${e.message}"
    }
}