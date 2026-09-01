package com.meuagente.app

import android.content.Context

/**
 * Roteador do comando de voz. Decide o CAMINHO do áudio com base no
 * modo escolhido (Automático / IA / Nativo) e aplica o fallback
 * automático para o nativo quando a IA não aceitar áudio.
 *
 * Centraliza a lógica para que a UI fique fina.
 */
object GerenciadorDeVoz {

    /**
     * Decide se o áudio deve ir para a IA ou para o nativo.
     *
     * Retorna:
     *  - true  → caminho IA (áudio/modelo ativo);
     *  - false → caminho Nativo (SpeechRecognizer local).
     *
     * @param caminhoIaFalhou é preenchido pela UI quando uma tentativa de IA
     *   falhou por modelo não-multimodal; se o modo for Automático, então cai
     *   para o nativo e a UI informa o usuário em linguagem simples.
     */
    fun caminhoUsarIa(
        contexto: Context,
        modeloAtivo: String,
        caminhoIaFalhou: Boolean = false
    ): Boolean {
        if (caminhoIaFalhou) {
            // A IA não aceitou o áudio. Se Automático, caímos para nativo.
            return false
        }
        return when (ControladorModoVoz.atual(contexto)) {
            ModoVoz.NATIVO -> false
            ModoVoz.IA_AUDIO -> true
            ModoVoz.AUTOMATICO -> MapaMultimodal.ehMultimodal(contexto, modeloAtivo)
        }
    }

    /**
     * Apenas informação amigável de qual caminho está ativo agora,
     * para exibir na imersão (ex.: "escutando via IA").
     */
    fun nomeCaminhoAtivo(caminhoIa: Boolean): String =
        if (caminhoIa) "IA" else "Nativo"

    /**
     * Mesagem clara quando o Automático caiu do modelo IA para o nativo,
     * para o usuário saber por quê (em português simples).
     */
    fun mensagemFallback(modelo: String): String =
        "O modelo \"$modelo\" que você escolheu não aceita áudio. " +
            "Para não interromper, gravei usando o microfone nativo do aparelho. " +
            "Você pode trocar o modelo ou forçar o modo IA nas configurações."
}