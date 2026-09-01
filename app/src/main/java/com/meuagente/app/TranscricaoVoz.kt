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
 * Isso desacopla a transcrição do "cérebro" (modelo de IA escolhido nas
 * configurações). O áudio é transcrito localmente (gratuito, sem API),
 * e só o TEXTO resultante é enviado para o cérebro — como se o usuário
 * tivesse digitado. Assim, qualquer chave de API funciona sem erro,
 * porque ela nunca recebe áudio, só texto.
 */
class TranscricaoVoz(
    private val contexto: Context,
    private val aoResultado: (String) -> Unit,
    private val aoErro: (String) -> Unit,
    private val aoInicio: () -> Unit = {},
    private val aoFim: () -> Unit = {}
) {
    private var reconhecedor: SpeechRecognizer? = null

    fun iniciar() {
        // Se já existe um reconhecedor, destrói antes de criar outro
        reconhecedor?.destroy()
        reconhecedor = null

        val novoReconhecedor = SpeechRecognizer.createSpeechRecognizer(contexto)
        reconhecedor = novoReconhecedor

        novoReconhecedor.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                aoInicio()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                aoFim()
            }

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

            override fun onResults(results: Bundle?) {
                val resultados = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val texto = resultados?.firstOrNull()?.trim()
                if (!texto.isNullOrBlank()) {
                    aoResultado(texto)
                } else {
                    aoErro("Não consegui entender o que você falou. Tenta de novo.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        novoReconhecedor.startListening(intent)
    }

    fun parar() {
        reconhecedor?.stopListening()
    }

    fun destruir() {
        reconhecedor?.destroy()
        reconhecedor = null
    }
}
