package com.meuagente.app

import android.content.Context

// Modo de escolha do caminho do áudio no comando de voz.
// NUNCA fica fixo: o usuário escolhe livremente (Lei "Nada é Fixo").
enum class ModoVoz {
    // O app decide: se o modelo ativo aceita áudio, usa a IA; se a IA
    // falhar por não-multimodal, cai automaticamente para o nativo.
    AUTOMATICO,

    // Força o envio do áudio/provedor ativo à IA multimoldal escolhida.
    IA_AUDIO,

    // Usa o reconhecimento de voz nativo do aparelho (sem consumir cota de IA).
    NATIVO
}

// Ponto único de leitura/escrita do modo de voz, usado tanto pelo
// botão do chat quanto pelas Configurações (mesma fonte de verdade).
object ControladorModoVoz {

    fun atual(contexto: Context): ModoVoz {
        return runCatching {
            ModoVoz.valueOf(Configuracoes.obterModoVoz(contexto))
        }.getOrDefault(ModoVoz.AUTOMATICO)
    }

    fun salvar(contexto: Context, modo: ModoVoz) {
        Configuracoes.salvarModoVoz(contexto, modo.name)
    }

    fun alternar(contexto: Context): ModoVoz {
        val proximo = quandoProximo(atual(contexto))
        salvar(contexto, proximo)
        return proximo
    }

    // Rótulo curto para exibir no botão de alternância do chat.
    fun rotulo(modo: ModoVoz): String = when (modo) {
        ModoVoz.AUTOMATICO -> "Auto"
        ModoVoz.IA_AUDIO -> "IA"
        ModoVoz.NATIVO -> "Nativo"
    }

    // Descrição em linguagem simples (para Configurações).
    fun descricao(modo: ModoVoz): String = when (modo) {
        ModoVoz.AUTOMATICO ->
            "O app escolhe: envia o áudio à IA se o modelo aceitar; se não, " +
                "usa o microfone nativo do aparelho automaticamente."
        ModoVoz.IA_AUDIO ->
            "Envia o áudio ao modelo de IA selecionado nas configurações. " +
                "Requer um modelo que aceite áudio (multimodal)."
        ModoVoz.NATIVO ->
            "Usa o reconhecimento de voz do próprio aparelho. Gratuito e rápido, " +
                "sem gastar cota de IA."
    }

    private fun quandoProximo(atual: ModoVoz): ModoVoz = when (atual) {
        ModoVoz.AUTOMATICO -> ModoVoz.IA_AUDIO
        ModoVoz.IA_AUDIO -> ModoVoz.NATIVO
        ModoVoz.NATIVO -> ModoVoz.AUTOMATICO
    }

    // Ícone (asset) correspondente ao modo, para o botão do chat.
    fun icone(modo: ModoVoz): Int = when (modo) {
        ModoVoz.AUTOMATICO -> R.drawable.ic_modo_auto
        ModoVoz.IA_AUDIO -> R.drawable.ic_modo_ia
        ModoVoz.NATIVO -> R.drawable.ic_modo_nativo
    }
}