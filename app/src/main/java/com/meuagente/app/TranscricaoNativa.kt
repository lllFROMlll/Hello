package com.meuagente.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Transcrição de voz usando o SpeechRecognizer NATIVO do Android.
 *
 * É o caminho "Nativo" (e o fallback automático do modo Automático):
 * gratuito, sem consumir cota de IA.
 *
 * Como o nativo entrega PARCIAIS ao longo da fala, ele também alimenta o
 * balão "ao vivo" da imersão de voz (texto em tempo real).
 */
class TranscricaoNativa(
    private val contexto: Context,
    private val aoParcial: (String) -> Unit = {},
    private val aoResultado: (String) -> Unit,
    private val aoErro: (String) -> Unit,
    private val aoInicio: () -> Unit = {},
    private val aoFim: () -> Unit = {}
) {
    private var reconhecedor: SpeechRecognizer? = null
    private var parciais = StringBuilder()

    fun iniciar() {
        parciais = StringBuilder()
        reconhecedor?.destroy()
        reconhecedor = null

        val novo = SpeechRecognizer.createSpeechRecognizer(contexto)
        reconhecedor = novo

        novo.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = aoInicio()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = aoFim()

            override fun onError(error: Int) {
                val mensagem = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio. Tenta de novo."
                    SpeechRecognizer.ERROR_CLIENT -> "Erro no cliente de reconhecimento."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone negada."
                    SpeechRecognizer.ERROR_NETWORK -> "Erro de rede. Verifica sua conexão."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo de rede esgotado. Tenta de novo."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender o que você falou. Tenta de novo."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecedor está ocupado. Tenta de novo."
                    SpeechRecognizer.ERROR_SERVER -> "Erro no servidor de reconhecimento."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nada. Tenta falar de novo."
                    else -> "Erro de reconhecimento de voz ($error)."
                }
                aoErro(mensagem)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val trechos = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val texto = trechos?.firstOrNull()?.trim()
                if (!texto.isNullOrBlank()) {
                    // Acumula os parciais para o balão ao vivo.
                    parciais = StringBuilder(texto)
                    aoParcial(texto)
                }
            }

            override fun onResults(results: Bundle?) {
                val resultados = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val texto = resultados?.firstOrNull()?.trim()
                if (!texto.isNullOrBlank()) {
                    aoResultado(texto)
                } else if (parciais.isNotBlank()) {
                    aoResultado(parciais.toString())
                } else {
                    aoErro("Não consegui entender o que você falou. Tenta de novo.")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        novo.startListening(intent)
    }

    fun parar() {
        reconhecedor?.stopListening()
    }

    fun destruir() {
        reconhecedor?.destroy()
        reconhecedor = null
    }
}