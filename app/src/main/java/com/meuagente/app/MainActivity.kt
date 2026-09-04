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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.meuagente.app.ui.BarraDeEntrada
import com.meuagente.app.ui.BlerFundoBase
import com.meuagente.app.ui.BlerFundoTopo
import com.meuagente.app.ui.BlerTextoHora
import com.meuagente.app.ui.BolhaMensagem
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

private fun formatarHora(dataHora: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dataHora))

private fun montarInstrucaoDeMemoria(lembretes: List<LembreteEntity>): String {
    val listaTexto = if (lembretes.isEmpty()) {
        "(nenhuma memória guardada ainda)"
    } else {
        lembretes.joinToString("\n") { "- ${it.descricao}" }
    }

    return """
        Você é Blér, um assistente pessoal com memória real, não apenas um chatbot comum.

        Memórias guardadas até agora:
        $listaTexto

        Regras OBRIGATÓRIAS:
        1. Se o usuário pedir para lembrar de algo, adicione no FINAL da resposta, em linha separada, exatamente: [GUARDAR: descrição bem curta e resumida]
        2. Se o usuário disser que algo já foi feito, resolvido, entregue, comprado, cancelado, ou que não precisa mais lembrar daquilo, você DEVE adicionar no FINAL da resposta, em linha separada: [APAGAR: texto que identifique a memória antiga]. Isso é obrigatório sempre que o usuário confirmar que algo foi concluído.
        3. Quando o usuário perguntar o que está pendente, responda de forma BREVE e resumida, sem repetir detalhes extras de tempo que possam não fazer mais sentido depois.
        4. Nunca escreva as marcações [GUARDAR: ] ou [APAGAR: ] de forma diferente da exata, nem explique elas ao usuário.
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

suspend fun perguntarComProvedor(
    historico: List<MensagemEntity>,
    provedor: String,
    modelo: String,
    chaveApi: String,
    instrucaoSistema: String
): String {
    return when (provedor) {
        "Gemini" -> chamarGemini(historico, chaveApi, modelo.ifBlank { "gemini-2.5-flash-lite" }, instrucaoSistema)
        "OpenAI" -> chamarFormatoOpenAI(
            historico, chaveApi, modelo.ifBlank { "gpt-4o-mini" },
            "https://api.openai.com/v1/chat/completions", instrucaoSistema
        )
        "OpenRouter" -> chamarFormatoOpenAI(
            historico, chaveApi, modelo,
            "https://openrouter.ai/api/v1/chat/completions", instrucaoSistema
        )
        "Anthropic" -> "O suporte ao provedor Anthropic ainda não foi implementado. Escolha Gemini, OpenAI ou OpenRouter por enquanto."
        else -> "Provedor \"$provedor\" ainda não é reconhecido pelo app."
    }
}

private suspend fun chamarGemini(
    historico: List<MensagemEntity>,
    chaveApi: String,
    modelo: String,
    instrucaoSistema: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent?key=$chaveApi"

            val contents = JSONArray()
            for (msg in historico) {
                val papel = if (msg.autor == "você") "user" else "model"
                val parte = JSONObject().put("text", msg.texto)
                val partes = JSONArray().put(parte)
                val item = JSONObject().put("role", papel).put("parts", partes)
                contents.put(item)
            }

            val instrucao = JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", instrucaoSistema))
            )

            val corpoJson = JSONObject()
                .put("contents", contents)
                .put("systemInstruction", instrucao)

            val mediaType = "application/json".toMediaType()
            val corpo = corpoJson.toString().toRequestBody(mediaType)

            val request = Request.Builder().url(url).post(corpo).build()
            val resposta = client.newCall(request).execute()
            val textoResposta = resposta.body?.string() ?: ""

            if (!resposta.isSuccessful) {
                return@withContext "Erro ${resposta.code}: $textoResposta"
            }

            val json = JSONObject(textoResposta)
            val candidatos = json.getJSONArray("candidates")
            val primeiro = candidatos.getJSONObject(0)
            val conteudo = primeiro.getJSONObject("content")
            val partesResposta = conteudo.getJSONArray("parts")
            partesResposta.getJSONObject(0).getString("text")
        } catch (e: IOException) {
            "Não consegui me conectar à internet agora. Tenta de novo em instantes."
        } catch (e: Exception) {
            "Algo deu errado ao processar a resposta do Gemini: ${e.message}"
        }
    }
}

private suspend fun chamarFormatoOpenAI(
    historico: List<MensagemEntity>,
    chaveApi: String,
    modelo: String,
    url: String,
    instrucaoSistema: String
): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()

            val mensagens = JSONArray()
            mensagens.put(JSONObject().put("role", "system").put("content", instrucaoSistema))
            for (msg in historico) {
                val papel = if (msg.autor == "user") "user" else "assistant"
                val item = JSONObject().put("role", papel).put("content", msg.texto)
                mensagens.put(item)
            }

            val corpoJson = JSONObject()
                .put("model", modelo)
                .put("messages", mensagens)

            val mediaType = "application/json".toMediaType()
            val corpo = corpoJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $chaveApi")
                .post(corpo)
                .build()

            val resposta = client.newCall(request).execute()
            val textoResposta = resposta.body?.string() ?: ""

            if (!resposta.isSuccessful) {
                return@withContext "Erro ${resposta.code}: $textoResposta"
            }

            val json = JSONObject(textoResposta)
            val escolhas = json.getJSONArray("choices")
            val primeira = escolhas.getJSONObject(0)
            val mensagem = primeira.getJSONObject("message")
            mensagem.getString("content")
        } catch (e: IOException) {
            "Não consegui me conectar à internet. Tenta de novo em instantes."
        } catch (e: Exception) {
            "Algo deu errado ao processar a resposta: ${e.message}"
        }
    }
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

    // ── Estado do comando de voz imersivo ──
    var estadoVoz by remember { mutableStateOf(EstadoVoz.INATIVO) }
    var modoVozAtual by remember { mutableStateOf(ControladorModoVoz.atual(contexto)) }
    var emImersao by remember { mutableStateOf(false) }
    var intensidadeVoz by remember { mutableStateOf(0f) }
    var textoParcialVoz by remember { mutableStateOf("") }
    var textoTranscrito by remember { mutableStateOf("") }
    var caminhoVozAtivo by remember { mutableStateOf("Nativo") }

    // Referências aos recursos de voz ativos (para poder parar depois)
    var gravadorAudioRef by remember { mutableStateOf<GravadorAudio?>(null) }
    var transcricaoNativaRef by remember { mutableStateOf<TranscricaoNativa?>(null) }

    // Estado das conversas (abas)
    var conversas by remember { mutableStateOf(listOf<ConversaEntity>()) }
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
        val provedor = Configuracoes.obterProvedorAtual(contexto)
        val modelo = Configuracoes.obterModeloAtual(contexto)
        val chave = Configuracoes.obterChaveAtual(contexto)

        escopo.launch {
            db.agenteDao().salvarMensagem(
                MensagemEntity(conversaId = idConversa, autor = "você", texto = texto, dataHora = System.currentTimeMillis())
            )
            nomearConversaSeNecessario(texto, idConversa)
            if (conversaAtualId == idConversa) {
                mensagens = db.agenteDao().listarMensagensDaConversa(idConversa)
            }

            if (chave.isBlank()) {
                db.agenteDao().salvarMensagem(
                    MensagemEntity(conversaId = idConversa, autor = "agente", texto = "Você ainda não configurou uma chave de API para o provedor \"$provedor\". Toque no menu (☰) para adicionar uma.", dataHora = System.currentTimeMillis())
                )
            } else {
                carregando = true
                val lembretesAtuais = db.agenteDao().listarTodosLembretes()
                val instrucao = montarInstrucaoDeMemoria(lembretesAtuais)
                val historico = db.agenteDao().listarMensagensDaConversa(idConversa)
                val respostaBruta = perguntarComProvedor(historico, provedor, modelo, chave, instrucao)
                val respostaLimpa = processarAcoesDeMemoria(respostaBruta, db)
                carregando = false
                db.agenteDao().salvarMensagem(
                    MensagemEntity(conversaId = idConversa, autor = "agente", texto = respostaLimpa, dataHora = System.currentTimeMillis())
                )
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

    // ── Texto final pronto → fecha a imersão e mostra para revisar ──
    fun textoPronto(texto: String) {
        emImersao = false
        intensidadeVoz = 0f
        if (texto.isBlank() || texto.equals("Transcrevendo...", ignoreCase = true)) {
            estadoVoz = EstadoVoz.INATIVO
        } else {
            textoTranscrito = texto
            estadoVoz = EstadoVoz.PRONTO
        }
        gravadorAudioRef = null
        transcricaoNativaRef = null
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
        textoTranscrito = ""
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

    // ── Enviar o texto transcrito como mensagem normal de chat ──
    fun enviarTranscricao() {
        val texto = textoTranscrito.trim()
        if (texto.isBlank()) {
            estadoVoz = EstadoVoz.INATIVO
            return
        }
        textoTranscrito = ""
        estadoVoz = EstadoVoz.INATIVO
        enviarMensagem(texto, conversaAtualId)
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
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(text = "Blér", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(NeonAzul, NeonLilas, NeonRosa)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f
                    )
                }
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Nova conversa") },
                    selected = false,
                    onClick = { criarNovaConversa() },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            tint = NeonLilas,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                HorizontalDivider()

                Text(
                    text = "Conversas",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
                conversas.forEach { conversa ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp)
                    ) {
                        NavigationDrawerItem(
                            label = { Text(conversa.titulo) },
                            selected = conversa.id == conversaAtualId,
                            onClick = { abrirConversa(conversa.id) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { fixarConversa(conversa.id, !conversa.fixada) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_pin),
                                contentDescription = "Fixar",
                                tint = if (conversa.fixada) NeonRosa else NeonLilas,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { excluirConversa(conversa.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_trash),
                                contentDescription = "Excluir conversa",
                                tint = NeonRosa,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(NeonRosa, NeonLilas, NeonAzul)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f
                    )
                }
                Spacer(Modifier.height(8.dp))

                NavigationDrawerItem(
                    label = { Text("Configurações") },
                    selected = false,
                    onClick = {
                        escopo.launch { estadoGaveta.close() }
                        aoAbrirConfig()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
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
                        enviada = msg.autor == "você"
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

            // ── Rodapé de voz: apenas revisão de transcrição pronta ──
            if (estadoVoz == EstadoVoz.PRONTO) {
                RodapeDeVoz(
                    estado = estadoVoz,
                    modoAtual = modoVozAtual,
                    textoTranscrito = textoTranscrito,
                    aoAlternarModo = { alternarModoVoz() },
                    aoIniciar = {},
                    aoEnviar = { enviarTranscricao() },
                    aoEditarTexto = { textoTranscrito = it },
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Linha de digitação (texto) com comando de voz ──
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
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ImersaoVoz(
                intensidade = intensidadeVoz,
                textoParcial = textoParcialVoz,
                caminhoAtivo = caminhoVozAtivo,
                onParar = { pararComandoVoz() }
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
