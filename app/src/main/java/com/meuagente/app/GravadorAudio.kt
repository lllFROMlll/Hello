package com.meuagente.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Captura o áudio bruto do microfone (PCM 16-bit mono → WAV) em segundo plano.
 *
 * É a base do comando de voz avançado:
 * - Calcula a INTENSIDADE em tempo real (para a onda/balao neon reagirem à voz);
 * - Aplica VAD (detecção de fala): após o usuário falar, uma pausa
 *   silenciosa encerra a gravação sozinha.
 * - Entrega o áudio WAV pronto para enviar à IA ou ao nativo.
 */
class GravadorAudio(
    private val aoAtualizarIntensidade: (Float) -> Unit,
    private val aoFinalizar: (ByteArray) -> Unit,
    private val aoErro: (String) -> Unit,
    private val silencioParaPararMs: Long = 1500L
) {
    @Volatile
    private var gravando = false

    @Volatile
    private var thread: Thread? = null

    fun estaGravando(): Boolean = gravando

    fun iniciar() {
        if (gravando) return
        gravando = true

        thread = Thread {
            var gravador: AudioRecord? = null
            try {
                val taxa = TAXA_AMOSTRAGEM
                val canal = AudioFormat.CHANNEL_IN_MONO
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                val tamanhoMin = AudioRecord.getMinBufferSize(taxa, canal, encoding)
                if (tamanhoMin <= 0) {
                    aoErro("Não foi possível abrir o microfone neste aparelho.")
                    gravando = false
                    return@Thread
                }

                val tamanhoBuffer = tamanhoMin * 2
                gravador = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    taxa, canal, encoding, tamanhoBuffer
                )

                if (gravador.state != AudioRecord.STATE_INITIALIZED) {
                    aoErro("O microfone não ficou pronto. Tente de novo.")
                    gravando = false
                    return@Thread
                }

                val pcm = ByteArrayOutputStream()
                val buffer = ShortArray(tamanhoBuffer / 2)
                var jaFalou = false
                var silencioAcumuladoMs = 0L
                val janelaMs = ((buffer.size.toDouble() / taxa) * 1000.0).toLong().coerceAtLeast(20L)

                gravador.startRecording()

                while (gravando) {
                    val lidos = gravador.read(buffer, 0, buffer.size)
                    if (lidos <= 0) continue

                    for (i in 0 until lidos) {
                        val amostra = buffer[i]
                        pcm.write(amostra.toInt() and 0xFF)
                        pcm.write((amostra.toInt() shr 8) and 0xFF)
                    }

                    val intensidade = calcularIntensidade(buffer, lidos)
                    aoAtualizarIntensidade(intensidade)

                    if (intensidade >= LIMIAR_FALA) {
                        jaFalou = true
                        silencioAcumuladoMs = 0L
                    } else if (jaFalou) {
                        silencioAcumuladoMs += janelaMs
                        if (silencioAcumuladoMs >= silencioParaPararMs) {
                            gravando = false
                            break
                        }
                    }
                }

                try {
                    gravador.stop()
                } catch (_: Exception) {
                }

                val pcmBytes = pcm.toByteArray()
                if (pcmBytes.isEmpty() || !jaFalou) {
                    aoErro("Não captamos fala clara. Toque no balão e fale de novo.")
                } else {
                    aoFinalizar(pcmParaWav(pcmBytes, taxa))
                }
            } catch (e: SecurityException) {
                aoErro("Sem permissão de microfone. Ative nas configurações do celular.")
            } catch (e: Exception) {
                aoErro("Falha ao gravar o áudio: ${e.message}")
            } finally {
                try {
                    gravador?.release()
                } catch (_: Exception) {
                }
                gravando = false
                aoAtualizarIntensidade(0f)
            }
        }.also { it.start() }
    }

    fun parar() {
        gravando = false
    }

    companion object {
        const val TAXA_AMOSTRAGEM = 16_000
        private const val LIMIAR_FALA = 0.035f

        private fun calcularIntensidade(amostras: ShortArray, quantidade: Int): Float {
            if (quantidade <= 0) return 0f
            var soma = 0.0
            for (i in 0 until quantidade) {
                val v = amostras[i].toDouble() / Short.MAX_VALUE
                soma += v * v
            }
            val rms = sqrt(soma / quantidade).toFloat()
            return (rms * 4f).coerceIn(0f, 1f)
        }

        fun pcmParaWav(pcm: ByteArray, taxa: Int): ByteArray {
            val canais = 1
            val bits = 16
            val byteRate = taxa * canais * bits / 8
            val totalDataLen = pcm.size + 36
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(canais.toShort())
            header.putInt(taxa)
            header.putInt(byteRate)
            header.putShort((canais * bits / 8).toShort())
            header.putShort(bits.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcm.size)

            val saida = ByteArrayOutputStream(44 + pcm.size)
            saida.write(header.array())
            saida.write(pcm)
            return saida.toByteArray()
        }
    }
}