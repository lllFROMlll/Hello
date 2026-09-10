package com.meuagente.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.meuagente.app.ia.CascataIA
import com.meuagente.app.ia.FalhaIA
import com.meuagente.app.ia.NenhumProvedorConfigurado
import com.meuagente.app.ia.TodasFalharam
import com.meuagente.app.ui.BarraDeEntrada
import com.meuagente.app.ui.BlerFundoBase
import com.meuagente.app.ui.BlerFundoTopo
import com.meuagente.app.ui.BlerTextoHora
import com.meuagente.app.ui.BolhaMensagem
import com.meuagente.app.ui.CartaoConversaGaveta
import com.meuagente.app.ui.CartaoNovaConversaBler
import com.meuagente.app.ui.DivisoriaNeonBler
import com.meuagente.app.ui.RodapeConfiguracoesBler
import com.meuagente.app.ui.TituloSecaoBler
import com.meuagente.app.ui.TopoChatBler
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
// CORES NEON DO PROJETO BLÉR
// ═══════════════════════════════════════════════════════════════════
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)

private const val MAX_CONVERSAS_FIXADAS = 5

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF05050F)) {
                    AppPrincipal()
                }
            }
        }
    }
}

@Composable
fun AppPrincipal() {
    var telaAtual by remember { mutableStateOf("chat") }

    BackHandler(enabled = telaAtual == "config") {
        telaAtual = "chat"
    }

    if (telaAtual == "config") {
        TelaConfiguracoes(aoVoltar = { telaAtual = "chat" })
    } else {
        TelaDeChat(aoAbrirConfig = { telaAtual = "config" })
    }
}

private val REGEX_GUARDAR = Regex("""\[GUARDAR:\s*(.+?)\]""")
private val REGEX_APAGAR = Regex("""\[APAGAR:\s*(.+?)\]""")
private val REGEX_BUSCAR = Regex("""\[BUSCAR:\s*(.+?)\]""")

private fun formatarHora(dataHora: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dataHora))

private fun nenhumProvedorConfigurado(contexto: android.content.Context): Boolean =
    listOf("Gemini", "OpenRouter", "OpenAI").none { provedor ->
        Configuracoes.obterProvedorAtivoNaCascata(contexto, provedor, padrao = provedor == "Gemini") &&
            Configuracoes.obterChaveDoProvedor(contexto, provedor).isNotBlank()
    }

private fun respostaDegenerada(texto: String): Boolean {
    val frases = texto.split(".", "!", "?", "\n")
        .map { it.trim() }
        .filter { it.length > 15 }
    return frases.groupingBy { it }.eachCount().values.any { it >= 3 }
}

private fun formatarHoraRelativa(dataHora: Long): String {
    val agora = java.time.LocalDate.now()
    val dia = java.time.Instant.ofEpochMilli(dataHora).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return when {
        dia == agora -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dataHora))
        dia == agora.minusDays(1) -> "Ontem"
        dia.isAfter(agora.minusDays(7)) ->
            dia.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "BR"))
                .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(dataHora))
    }
}

private fun montarInstrucaoDeMemoria(lembretes: List<LembreteEntity>): String {
    val listaTexto = if (lembretes.isEmpty()) {
        "(nenhuma memória guardada ainda)"
    } else {
        lembretes.joinToString("\n") { "- ${it.descricao}" }
    }

    val agora = java.time.LocalDateTime.now()
    val dataReal = agora.format(
        java.time.format.DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.getDefault())
    )
    val horaReal = agora.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    return """
        Você é Blér, um assistente pessoal com memória real, não apenas um chatbot comum.

        DATA REAL DE HOJE: $dataReal
        HORA REAL AGORA: $horaReal (horário local do celular do usuário)
        IMPORTANTE: esses valores de data e hora são REAIS e confiáveis, lidos do relógio do aparelho no momento desta mensagem. Confie neles para qualquer cálculo de prazo, agendamento, lembrete ou resposta sobre "hoje", "agora", "amanhã" etc. NUNCA suponha, estime ou invente data e hora por conta própria.

        Memórias guardadas até agora:
        $listaTexto

        Regras OBRIGATÓRIAS:
        1. Se o usuário pedir para lembrar de algo, adicione no FINAL da resposta, em linha separada, exatamente: [GUARDAR: descrição bem curta e resumida]
        2. Se o usuário disser que algo já foi feito, resolvido, entregue, comprado, cancelado, ou que não precisa mais lembrar daquilo, você DEVE adicionar no FINAL da resposta, em linha separada: [APAGAR: texto que identifique a memória antiga]. Isso é obrigatório sempre que o usuário confirmar que algo foi concluído.
        3. Quando o usuário perguntar o que está pendente, responda de forma BREVE e resumida, sem repetir detalhes extras de tempo que possam não fazer mais sentido depois.
        4. Nunca escreva as marcações [GUARDAR: ] ou [APAGAR: ] de forma diferente da exata, nem explique elas ao usuário.
        5. Se a pergunta depender de informação atual ou da internet (notícias, esportes, clima, preços, cotações, fatos recentes), adicione no FINAL da resposta, em linha separada, exatamente: [BUSCAR: termos de busca curtos e eficazes]. Você receberá os resultados reais da internet e deverá responder com base neles, citando de onde veio a informação quando fizer sentido. Se a mensagem que você recebe já contém "RESULTADOS DA BUSCA NA INTERNET", responda normalmente SEM pedir nova busca.
        6. Responda APENAS à ÚLTIMA mensagem do usuário, de forma direta, em um único parágrafo coerente. NUNCA repita a mesma frase ou trecho dentro da resposta.
        7. Para fatos atuais (jogos, placares, notícias, datas de eventos recentes), use SOMENTE os "RESULTADOS DA BUSCA NA INTERNET" que você receber. Se os resultados não trouxerem a informação clara, diga honestamente que não encontrou — NUNCA invente placar, data, nome ou evento.
    """.trimIndent()
}

private suspend fun processarAcoesDeMemoria(respostaIA: String, db: AgenteDatabase): String {
    val linhasParaMostrar = mutableListOf<String>()

    for (linha in respostaIA.lines()) {
        val guardarMatch = REGEX_GUARDAR.find(linha)
        val apagarMatch = REGEX_APAGAR.find(linha)

        when {
            guardarMatch != null -> {
                val descricao = guardarMatch.groupValues[1].trim()
                db.agenteDao().salvarLembrete(
                    LembreteEntity(descricao = descricao, pessoa = null, dataCriacao = System.currentTimeMillis())
                )
            }
            apagarMatch != null -> {
                val descricaoBusca = apagarMatch.groupValues[1].trim()
                val encontrado = db.agenteDao().listarTodosLembretes()
                    .firstOrNull { it.descricao.contains(descricaoBusca, ignoreCase = true) || descricaoBusca.contains(it.descricao, ignoreCase = true) }
                if (encontrado != null) {
                    db.agenteDao().apagarLembrete(encontrado.id)
                }
            }
            else -> linhasParaMostrar.add(linha)
        }
    }

    return linhasParaMostrar.joinToString("\n").trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDeChat(aoAbrirConfig: () -> Unit) {
    val contexto = LocalContext.current
    val db = remember { AgenteDatabase.obter(contexto) }
    val escopo = rememberCoroutineScope()
    val estadoGaveta = rememberDrawerState(initialValue = DrawerValue.Closed)

    var mensagens by remember { mutableStateOf(listOf<MensagemEntity>()) }
    var textoDigitado by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var buscandoWeb by remember { mutableStateOf(false) }

    // Origem (provedor · modelo) de cada resposta da IA, por id de mensagem
    val origensIA = remember { mutableStateMapOf<Int, String>() }

    // ── Estado do comando de voz imersivo ──
    var estadoVoz by remember { mutableStateOf(EstadoVoz.INATIVO) }
    var modoVozAtual by remember { mutableStateOf(ControladorModoVoz.atual(contexto)) }
    var emImersao by remember { mutableStateOf(false) }
    var intensidadeVoz by remember { mutableStateOf(0f) }
    var textoParcialVoz by remember { mutableStateOf("") }
    var caminhoVozAtivo by remember { mutableStateOf("Nativo") }

    // Referências aos recursos de voz ativos (para poder parar depois)
    var gravadorAudioRef by remember { mutableStateOf<GravadorAudio?>(null) }
    var transcricaoNativaRef by remember { mutableStateOf<TranscricaoNativa?>(null) }

    // Estado das conversas (abas)
    var conversas by remember { mutableStateOf(listOf<ConversaEntity>()) }
    var cartoesConversa by remember { mutableStateOf(listOf<CartaoConversa>()) }
    var conversaAtualId by remember { mutableStateOf(0) }

    // Estado da confirmação de exclusão de conversa (via "x" da aba)
    var confirmarExclusaoId by remember { mutableStateOf<Int?>(null) }

    // Estado da lista para rolagem automática
    val listaState = rememberLazyListState()

    // ── Limpar estado de voz ao trocar de conversa ou fechar o app ──
    fun cancelarComandoVoz() {
        transcricaoNativaRef?.destruir()
        transcricaoNativaRef = null
        gravadorAudioRef?.parar()
        gravadorAudioRef = null
        emImersao = false
        intensidadeVoz = 0f
        textoParcialVoz = ""
        estadoVoz = EstadoVoz.INATIVO
    }

    // ── Função: carregar conversas (fixadas primeiro) ──
    fun carregarConversas() {
        escopo.launch {
            conversas = db.agenteDao().listarConversasFixadasPrimeiro()
            cartoesConversa = db.agenteDao().listarCartoesDeConversa()
        }
    }

    // ── Função: criar nova conversa ──
    fun criarNovaConversa() {
        escopo.launch {
            cancelarComandoVoz()
            val novaId = db.agenteDao().criarConversa(
                ConversaEntity(titulo = "Nova conversa", dataCriacao = System.currentTimeMillis())
            )
            conversaAtualId = novaId.toInt()
            textoDigitado = ""
            mensagens = emptyList()
            Configuracoes.salvarUltimaConversa(contexto, novaId.toInt())
            carregarConversas()
            escopo.launch { estadoGaveta.close() }
        }
    }

    // ── Função: abrir uma conversa existente ──
    fun abrirConversa(id: Int) {
        escopo.launch {
            cancelarComandoVoz()
            conversaAtualId = id
            textoDigitado = ""
            carregando = false
            Configuracoes.salvarUltimaConversa(contexto, id)
            mensagens = db.agenteDao().listarMensagensDaConversa(id)
            escopo.launch { estadoGaveta.close() }
        }
    }

    // ── Função: nomear a conversa pelo contexto da primeira mensagem ──
    fun nomearConversaSeNecessario(primeiraMensagem: String, idConversa: Int) {
        escopo.launch {
            val conversaNomeada = conversas.firstOrNull { it.id == idConversa }
            if (conversaNomeada != null && conversaNomeada.titulo == "Nova conversa") {
                val titulo = primeiraMensagem.take(40)
                db.agenteDao().atualizarConversa(
                    conversaNomeada.copy(titulo = titulo)
                )
                carregarConversas()
            }
        }
    }

    // ── Função: fixar ou desfixar conversa (máximo 5 fixadas) ──
    fun fixarConversa(id: Int, fixar: Boolean) {
        escopo.launch {
            val conversa = conversas.firstOrNull { it.id == id } ?: return@launch

            if (fixar) {
                val fixadas = conversas.count { it.fixada }
                if (fixadas >= MAX_CONVERSAS_FIXADAS) {
                    db.agenteDao().salvarMensagem(
                        MensagemEntity(
                            conversaId = conversaAtualId,
                            autor = "você",
                            texto = "(sistema: você já fixou $MAX_CONVERSAS_FIXADAS conversas. Desfixe uma para fixar outra.)",
                            dataHora = System.currentTimeMillis()
                        )
                    )
                    return@launch
                }
            }

            db.agenteDao().atualizarConversa(conversa.copy(fixada = fixar))
            carregarConversas()
        }
    }

    // ── Função: excluir uma conversa ──
    fun excluirConversa(id: Int) {
        escopo.launch {
            db.agenteDao().apagarMensagensDaConversa(id)
            db.agenteDao().apagarConversa(id)

            val restantes = conversas.filter { it.id != id }

            if (id == conversaAtualId) {
                cancelarComandoVoz()
                if (restantes.isEmpty()) {
                    val novaId = db.agenteDao().criarConversa(
                        ConversaEntity(titulo = "Nova conversa", dataCriacao = System.currentTimeMillis())
                    )
                    conversaAtualId = novaId.toInt()
                    textoDigitado = ""
                    mensagens = emptyList()
                    Configuracoes.salvarUltimaConversa(contexto, novaId.toInt())
                } else {
                    val indexAntigo = conversas.indexOfFirst { it.id == id }
                    val vizinha = if (indexAntigo >= 0 && indexAntigo < restantes.size) restantes[indexAntigo] else restantes.last()
                    conversaAtualId = vizinha.id
                    textoDigitado = ""
                    mensagens = db.agenteDao().listarMensagensDaConversa(vizinha.id)
                    Configuracoes.salvarUltimaConversa(contexto, vizinha.id)
                }
            }

            carregarConversas()
        }
    }

    // ── Função: enviar mensagem para o cérebro, sempre vinculada à
    //    conversa de origem (idConversa). Se o usuário trocar de conversa
    //    enquanto a resposta está sendo gerada, a resposta continua indo
    //    para a conversa certa e não contamina a aba ativa. ──
    fun enviarMensagem(texto: String, idConversa: Int) {
        escopo.launch {
            db.agenteDao().salvarMensagem(
                MensagemEntity(conversaId = idConversa, autor = "você", texto = texto, dataHora = System.currentTimeMillis())
            )
            nomearConversaSeNecessario(texto, idConversa)
            if (conversaAtualId == idConversa) {
                mensagens = db.agenteDao().listarMensagensDaConversa(idConversa)
            }

            if (nenhumProvedorConfigurado(contexto)) {
                db.agenteDao().salvarMensagem(
                    MensagemEntity(conversaId = idConversa, autor = "agente", texto = "Nenhum provedor de IA está ativo na cascata. Abra as Configurações, ligue um provedor e adicione pelo menos um modelo.", dataHora = System.currentTimeMillis())
                )
            } else {
                carregando = true
                val lembretesAtuais = db.agenteDao().listarTodosLembretes()
                val instrucao = montarInstrucaoDeMemoria(lembretesAtuais)
                val historico = db.agenteDao().listarMensagensDaConversa(idConversa)

                try {
                    var cascata = CascataIA.perguntar(contexto, historico, instrucao)
                    var respostaLimpa = processarAcoesDeMemoria(cascata.texto, db)

                    // Proteção contra loop de repetição do modelo (degeneração):
                    // repete a cascata uma vez, pulando o modelo que degenerou.
                    if (respostaDegenerada(respostaLimpa)) {
                        val segundaTentativa = CascataIA.perguntar(
                            contexto, historico, instrucao,
                            excluirModelo = "${cascata.provedor}:${cascata.modelo}"
                        )
                        val limpaSegunda = processarAcoesDeMemoria(segundaTentativa.texto, db)
                        if (!respostaDegenerada(limpaSegunda)) {
                            cascata = segundaTentativa
                            respostaLimpa = limpaSegunda
                        }
                    }

                    // ── Busca real na internet quando a IA pede [BUSCAR: ...] ──
                    if (REGEX_BUSCAR.containsMatchIn(respostaLimpa)) {
                        val termos = REGEX_BUSCAR.findAll(respostaLimpa)
                            .joinToString("; ") { it.groupValues[1].trim() }
                            .take(300)

                        buscandoWeb = true
                        val resultadoWeb = withContext(Dispatchers.IO) {
                            runCatching {
                                PesquisadorWeb.buscar(
                                    termos,
                                    Configuracoes.obterChaveTavily(contexto),
                                    Configuracoes.obterChaveBrave(contexto)
                                )
                            }.getOrElse { "" }
                        }
                        buscandoWeb = false

                        if (resultadoWeb.isNotBlank()) {
                            val contextoWeb = "RESULTADOS DA BUSCA NA INTERNET para \"$termos\":\n\n" +
                                resultadoWeb +
                                "\n\nResponda à pergunta do usuário usando esses resultados reais. " +
                                "Se os resultados não contiverem a informação pedida, diga que não encontrou — nunca invente."
                            val mensagemSistema = MensagemEntity(
                                conversaId = idConversa,
                                autor = "sistema",
                                texto = contextoWeb,
                                dataHora = System.currentTimeMillis()
                            )
                            val respostaFinal = CascataIA.perguntar(
                                contexto, historico + mensagemSistema, instrucao
                            )
                            respostaLimpa = processarAcoesDeMemoria(respostaFinal.texto, db)
                            cascata = respostaFinal
                        } else {
                            respostaLimpa = respostaLimpa.replace(REGEX_BUSCAR, "").trim() +
                                "\n\n(Não consegui pesquisar na internet agora.)"
                        }
                    }

                    // ── Confirmação seletiva com Google (só "sensível ao tempo") ──
                    if (Configuracoes.usarConfirmacaoGoogle(contexto) &&
                        ClassificadorPergunta.sensivelAoTempo(texto) &&
                        respostaLimpa.isNotBlank()
                    ) {
                        val chaveGemini = Configuracoes.obterChaveDoProvedor(contexto, "Gemini")
                        if (chaveGemini.isNotBlank()) {
                            val correcao = withContext(Dispatchers.IO) {
                                runCatching {
                                    ConfirmacaoGoogle.confirmar(chaveGemini, respostaLimpa, texto)
                                }.getOrElse { null }
                            }
                            if (!correcao.isNullOrBlank()) {
                                respostaLimpa = correcao
                            }
                        }
                    }

                    carregando = false
                    val novoId = db.agenteDao().salvarMensagem(
                        MensagemEntity(conversaId = idConversa, autor = "agente", texto = respostaLimpa, dataHora = System.currentTimeMillis())
                    ).toInt()
                    origensIA[novoId] = "${cascata.provedor} · ${cascata.modelo}"
                } catch (e: FalhaIA.SemRede) {
                    carregando = false
                    val idErro = db.agenteDao().salvarMensagem(
                        MensagemEntity(conversaId = idConversa, autor = "agente", texto = "Não consegui me conectar à internet agora. Tenta de novo em instantes.", dataHora = System.currentTimeMillis())
                    ).toInt()
                    origensIA[idErro] = "Erro: Sem conexão com a internet"
                } catch (e: TodasFalharam) {
                    carregando = false
                    val idErro = db.agenteDao().salvarMensagem(
                        MensagemEntity(conversaId = idConversa, autor = "agente", texto = "Tentei de todas as formas responder agora e não consegui. Tenta de novo em instantes.", dataHora = System.currentTimeMillis())
                    ).toInt()
                    origensIA[idErro] = "Erro: ${e.detalhes}"
                } catch (e: NenhumProvedorConfigurado) {
                    carregando = false
                    val idErro = db.agenteDao().salvarMensagem(
                        MensagemEntity(conversaId = idConversa, autor = "agente", texto = "Nenhum provedor de IA está ativo na cascata. Abra as Configurações, ligue um provedor e adicione pelo menos um modelo.", dataHora = System.currentTimeMillis())
                    ).toInt()
                    origensIA[idErro] = "Erro: Nenhum provedor ativo com chave"
                }
            }

            if (conversaAtualId == idConversa) {
                mensagens = db.agenteDao().listarMensagensDaConversa(idConversa)
            }
        }
    }

    // ── Registrar mensagem de sistema/erro do agente ──
    fun salvarMensagemSistema(texto: String) {
        escopo.launch {
            db.agenteDao().salvarMensagem(
                MensagemEntity(conversaId = conversaAtualId, autor = "agente", texto = texto, dataHora = System.currentTimeMillis())
            )
            mensagens = db.agenteDao().listarMensagensDaConversa(conversaAtualId)
        }
    }

    // ── Texto final pronto → fecha a imersão e leva ao campo principal ──
    fun textoPronto(texto: String) {
        emImersao = false
        intensidadeVoz = 0f
        gravadorAudioRef = null
        transcricaoNativaRef = null
        estadoVoz = EstadoVoz.INATIVO
        if (!texto.isBlank() && !texto.equals("Transcrevendo...", ignoreCase = true)) {
            textoDigitado = if (textoDigitado.isBlank()) texto else textoDigitado + " " + texto
        }
    }

    // ── Erro de voz → sai da imersão e registra uma mensagem do agente ──
    fun mostrarErroVoz(mensagemErro: String) {
        emImersao = false
        intensidadeVoz = 0f
        estadoVoz = EstadoVoz.INATIVO
        salvarMensagemSistema(mensagemErro)
        gravadorAudioRef = null
        transcricaoNativaRef = null
    }

    // ── Áudio capturado no caminho IA → transcreve/processa ──
    fun processarAudioIa(wav: ByteArray) {
        escopo.launch {
            estadoVoz = EstadoVoz.TRANSCREVENDO
            textoParcialVoz = "Transcrevendo..."
            val provedor = Configuracoes.obterProvedorAtual(contexto)
            val modelo = Configuracoes.obterModeloAtual(contexto)
            val chave = Configuracoes.obterChaveAtual(contexto)

            val resultado = transcreverAudioComProvedor(wav, provedor, modelo, chave)

            // Fallback automático: se o Automático tentou IA mas ela recusou
            // áudio (modelo não-multimodal), avisa em linguagem simples.
            if (modoVozAtual == ModoVoz.AUTOMATICO && resultado.startsWith("Erro 4")) {
                salvarMensagemSistema(GerenciadorDeVoz.mensagemFallback(modelo))
                textoPronto("")
                return@launch
            }

            textoPronto(resultado)
        }
    }

    // ── Parar a gravação ao tocar no globo da imersão ──
    fun pararComandoVoz() {
        FxSons.despedir(contexto)
        gravadorAudioRef?.parar()
        transcricaoNativaRef?.parar()
    }

    // ── Iniciar o comando de voz (imersão do globo) ──
    fun iniciarComandoVoz() {
        if (estadoVoz == EstadoVoz.GRAVANDO || estadoVoz == EstadoVoz.TRANSCREVENDO) return

        val modelo = Configuracoes.obterModeloAtual(contexto)
        val usaIa = GerenciadorDeVoz.caminhoUsarIa(contexto, modelo)
        caminhoVozAtivo = GerenciadorDeVoz.nomeCaminhoAtivo(usaIa)

        FxSons.apresentar(contexto)

        textoParcialVoz = ""
        intensidadeVoz = 0f
        emImersao = true
        estadoVoz = EstadoVoz.GRAVANDO

        if (usaIa) {
            // Caminho IA: captura áudio bruto e transcreve via provedor.
            val gravador = GravadorAudio(
                aoAtualizarIntensidade = { nivel -> escopo.launch { intensidadeVoz = nivel } },
                aoFinalizar = { wav -> processarAudioIa(wav) },
                aoErro = { msg -> mostrarErroVoz(msg) }
            )
            gravadorAudioRef = gravador
            gravador.iniciar()
        } else {
            // Caminho Nativo: transcrição local com parciais ao vivo.
            val nativa = TranscricaoNativa(
                contexto = contexto,
                aoParcial = { parcial -> textoParcialVoz = parcial },
                aoResultado = { texto -> textoPronto(texto) },
                aoErro = { msg -> mostrarErroVoz(msg) }
            )
            transcricaoNativaRef = nativa
            nativa.iniciar()
        }
    }

    // ── Alternar o modo de voz (Auto → IA → Nativo) ──
    fun alternarModoVoz() {
        modoVozAtual = ControladorModoVoz.alternar(contexto)
    }

    // ── Permissão de microfone ──
    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            iniciarComandoVoz()
        }
    }

    // ── Ao abrir o app: restaura a última conversa aberta,
    //    ou cria uma nova se não houver nenhuma salva ──
    LaunchedEffect(Unit) {
        val dao = db.agenteDao()
        val idSalvo = Configuracoes.obterUltimaConversa(contexto)
        val conversaSalva = idSalvo?.let { dao.buscarConversaPorId(it) }

        if (conversaSalva != null) {
            conversaAtualId = conversaSalva.id
            mensagens = dao.listarMensagensDaConversa(conversaSalva.id)
        } else {
            val novaId = dao.criarConversa(
                ConversaEntity(titulo = "Nova conversa", dataCriacao = System.currentTimeMillis())
            )
            conversaAtualId = novaId.toInt()
            textoDigitado = ""
            mensagens = emptyList()
            Configuracoes.salvarUltimaConversa(contexto, novaId.toInt())
        }
        carregarConversas()
    }

    // ── Rolagem automática para a última mensagem ──
    LaunchedEffect(mensagens.size, carregando, estadoVoz) {
        if (mensagens.isNotEmpty()) {
            listaState.animateScrollToItem(mensagens.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = estadoGaveta,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF04060B),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                windowInsets = WindowInsets(0)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Espaço liso reservado para o logo (entrará no futuro)
                    Spacer(modifier = Modifier.height(96.dp))

                    DivisoriaNeonBler()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        CartaoNovaConversaBler(aoClicar = { criarNovaConversa() })

                        if (cartoesConversa.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(22.dp))
                            TituloSecaoBler()
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                cartoesConversa.forEach { cartao ->
                                    val subtitulo = if (cartao.id == conversaAtualId) {
                                        "Ativa agora" + (cartao.ultimaAtividade?.let { " • ${formatarHoraRelativa(it)}" } ?: "")
                                    } else {
                                        cartao.ultimaMensagem?.take(60) ?: "Sem mensagens ainda"
                                    }
                                    CartaoConversaGaveta(
                                        titulo = cartao.titulo,
                                        subtitulo = subtitulo,
                                        ativo = cartao.id == conversaAtualId,
                                        fixada = cartao.fixada,
                                        aoAbrir = { abrirConversa(cartao.id) },
                                        aoFixar = { fixarConversa(cartao.id, !cartao.fixada) },
                                        aoExcluir = { confirmarExclusaoId = cartao.id }
                                    )
                                }
                            }
                        }
                    }

                    DivisoriaNeonBler()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = Color(0xF2080C16))
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        RodapeConfiguracoesBler(aoAbrir = {
                            escopo.launch { estadoGaveta.close() }
                            aoAbrirConfig()
                        })
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 120.dp, height = 4.dp)
                                .background(
                                    color = Color(0x59334155),
                                    shape = RoundedCornerShape(50)
                                )
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(listOf(BlerFundoTopo, BlerFundoBase)))
        ) {

            TopoChatBler(
                aoAbrirGaveta = { escopo.launch { estadoGaveta.open() } },
                aoOpcoes = {}
            )

            LazyColumn(
                state = listaState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                items(mensagens) { msg ->
                    BolhaMensagem(
                        texto = msg.texto,
                        hora = formatarHora(msg.dataHora),
                        enviada = msg.autor == "você",
                        origemIA = if (msg.autor == "agente") origensIA[msg.id] else null
                    )
                }
                if (carregando) {
                    item {
                        Text(
                            text = "agente está digitando...",
                            color = BlerTextoHora,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                if (buscandoWeb) {
                    item {
                        Text(
                            text = "🌐 Buscando na internet...",
                            color = BlerTextoHora,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                if (estadoVoz == EstadoVoz.TRANSCREVENDO) {
                    item {
                        Text(
                            text = "Transcrevendo sua fala...",
                            color = BlerTextoHora,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            // ── Rodapé: campo de diálogo do novo design (único) ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                BarraDeEntrada(
                    texto = textoDigitado,
                    aoMudarTexto = { textoDigitado = it },
                    aoClicarMicrofone = {
                        val permissao = ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO)
                        if (permissao == PackageManager.PERMISSION_GRANTED) {
                            iniciarComandoVoz()
                        } else {
                            pedirPermissao.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    aoClicarEnviar = {
                        if (textoDigitado.isNotBlank() && !carregando) {
                            val texto = textoDigitado
                            textoDigitado = ""
                            enviarMensagem(texto, conversaAtualId)
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Overlay de imersão do comando de voz (globo + ondas) ──
        AnimatedVisibility(
            visible = emImersao,
            enter = slideInVertically(animationSpec = tween(550), initialOffsetY = { it }) + fadeIn(animationSpec = tween(550)),
            exit = slideOutVertically(animationSpec = tween(480), targetOffsetY = { it }) + fadeOut(animationSpec = tween(480))
        ) {
            ImersaoVoz(
                intensidade = intensidadeVoz,
                textoParcial = textoParcialVoz,
                caminhoAtivo = caminhoVozAtivo,
                onParar = { pararComandoVoz() },
                aoSair = { cancelarComandoVoz() }
            )
        }

        // ── Diálogo de confirmação ao fechar uma aba ("x") ──
        confirmarExclusaoId?.let { idParaExcluir ->
            AlertDialog(
                onDismissRequest = { confirmarExclusaoId = null },
                title = { Text("Excluir conversa?") },
                text = { Text("Esta conversa e todas as suas mensagens serão apagadas. Essa ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(onClick = {
                        excluirConversa(idParaExcluir)
                        confirmarExclusaoId = null
                    }) {
                        Text("Excluir")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmarExclusaoId = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
