package com.meuagente.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Sons 100% sintetizados por CÓDIGO (sem arquivos de mídia, sem pasta res/raw).
 *
 * Respeita o interruptor global de efeitos sonoros das Configurações:
 * se o usuário desligar, nada toca.
 *
 * Sons disponíveis:
 *  - POP_CLIQUE: "pop" seco para cliques/botões em geral.
 *  - APRESENTAR: "subida" suave (sweep) quando o microfone se apresenta.
 *  - DESPEDIR:   "descida" suave quando o globo encerra a gravação.
 */
object FxSons {

    private const val TAXA = 44100

    fun clique(contexto: Context) {
        tocarSeAtivo(contexto) { pop(0.06f, 900f, 1400f) }
    }

    fun apresentar(contexto: Context) {
        tocarSeAtivo(contexto) { sweep(0.30f, 500f, 1600f) }
    }

    fun despedir(contexto: Context) {
        tocarSeAtivo(contexto) { sweep(0.30f, 1600f, 400f) }
    }

    // Constrói um pequeno "pop": seno curto com decaimento exponencial.
    private fun pop(duracaoSeg: Float, freqInicial: Float, freqFinal: Float): ShortArray {
        val n = (TAXA * duracaoSeg).toInt()
        val out = ShortArray(n)
        var freq = freqInicial
        val passo = (freqFinal - freqInicial) / n
        for (i in 0 until n) {
            val t = i.toFloat() / TAXA
            val envelope = Math.exp(-t * 90.0).toFloat()
            out[i] = (30000 * envelope * sin(2 * PI * freq * t)).toInt().toShort()
            freq += passo
        }
        return out
    }

    // Sweep (varrer frequência) para apresentar/despedir com envelope suave.
    private fun sweep(duracaoSeg: Float, freqInicial: Float, freqFinal: Float): ShortArray {
        val n = (TAXA * duracaoSeg).toInt()
        val out = ShortArray(n)
        var freq = freqInicial
        val passo = (freqFinal - freqInicial) / n
        for (i in 0 until n) {
            val t = i.toFloat() / TAXA
            // Envelope de ataque/decimento para não estalar.
            val f = (t / duracaoSeg).coerceIn(0f, 1f)
            val envelope = (sin(PI * f)).toFloat()
            out[i] = (25000 * envelope * sin(2 * PI * freq * t)).toInt().toShort()
            freq += passo
        }
        return out
    }

    // Toca um sintetizador em thread separada, se os sons estiverem ativos.
    private fun tocarSeAtivo(contexto: Context, gerar: () -> ShortArray) {
        if (!Configuracoes.obterSonsAtivos(contexto)) return
        thread {
            var track: AudioTrack? = null
            try {
                val amostras = gerar()
                val tamanho = amostras.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(TAXA)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(tamanho * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(amostras, 0, amostras.size)
                track.play()
                // Mantém a thread viva até o som terminar.
                Thread.sleep((amostras.size.toLong() * 1000 / TAXA) + 30)
            } catch (_: Exception) {
                // Sem áudio não é fatal: apenas não toca.
            } finally {
                try {
                    track?.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}