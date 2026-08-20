package com.meuagente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val escuro = isSystemInDarkTheme()
            val cores = if (escuro) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = cores) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppPrincipal()
                }
            }
        }
    }
}

@Composable
fun AppPrincipal() {
    var telaAtual by remember { mutableStateOf("chat") }

    if (telaAtual == "config") {
        TelaConfiguracoes(aoVoltar = { telaAtual = "chat" })
    } else {
        TelaDeChat(aoAbrirConfig = { telaAtual = "config" })
    }
}

private val REGEX_GUARDAR = Regex("""\[GUARDAR:\s*(.+?)\]""")
private val REGEX_APAGAR = Regex("""\[APAGAR:\s*(.+?)\]""")

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
                    .firstOrNull {
                        it.descricao.contains(descricaoBusca, ignoreCase = true) ||
                            descricaoBusca.contains(it.descricao, ignoreCase = true)
                    }
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
                val papel = if (msg.autor == "você") "user" else "assistant"
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
            "Não consegui me conectar à internet agora. Tenta de novo em instantes."
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

    var conversas by remember { mutableStateOf(listOf<ConversaEntity>()) }
    var conversaAtivaId by remember { mutableStateOf(1) }

    var mensagens by remember { mutableStateOf(listOf<MensagemEntity>()) }
    var textoDigitado by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }

    var dialogNovaConversaAberta by remember { mutableStateOf(false) }
    var novoTituloConversa by remember { mutableStateOf("") }
    var erroTituloConversa by remember { mutableStateOf<String?>(null) }

    val conversaAtiva = conversas.firstOrNull { it.id == conversaAtivaId }

    fun carregarMensagensDaConversa(conversaId: Int) {
        mensagens = db.agenteDao().listarMensagensDaConversa(conversaId)
        if (mensagens.isEmpty()) {
            // Garante que a conversa "recém-criada" não fica vazia.
            db.agenteDao().salvarMensagem(
                MensagemEntity(
                    conversaId = conversaId,
                    autor = "agente",
                    texto = "Oi! Eu sou o Blér. Toque no menu (☰) no canto superior esquerdo para escolher o provedor de IA e colar sua chave de API.",
                    dataHora = System.currentTimeMillis()
                )
            )
            mensagens = db.agenteDao().listarMensagensDaConversa(conversaId)
        }
    }

    LaunchedEffect(Unit) {
        val conversasCarregadas = db.agenteDao().listarConversas()
        if (conversasCarregadas.isEmpty()) {
            // Em instalação nova, não existe migração rodando; então criamos a "Conversa 1" aqui.
            val idCriado = db.agenteDao().criarConversa(
                ConversaEntity(id = 0, titulo = "Conversa 1", dataCriacao = 0L)
            )
            conversas = db.agenteDao().listarConversas()
            conversaAtivaId = idCriado.toInt()
        } else {
            conversas = conversasCarregadas
            conversaAtivaId = conversasCarregadas.firstOrNull()?.id ?: 1
        }

        carregarMensagensDaConversa(conversaAtivaId)
    }

    if (dialogNovaConversaAberta) {
        AlertDialog(
            onDismissRequest = {
                dialogNovaConversaAberta = false
                erroTituloConversa = null
            },
            title = { Text("Nova conversa") },
            text = {
                Column {
                    OutlinedTextField(
                        value = novoTituloConversa,
                        onValueChange = {
                            novoTituloConversa = it
                            erroTituloConversa = null
                        },
                        placeholder = { Text("Digite um título...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!erroTituloConversa.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = erroTituloConversa ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val titulo = novoTituloConversa.trim()
                        if (titulo.isBlank()) {
                            erroTituloConversa = "Informe um título para a conversa."
                            return@TextButton
                        }

                        escopo.launch {
                            val idNova = db.agenteDao().criarConversa(
                                ConversaEntity(id = 0, titulo = titulo, dataCriacao = System.currentTimeMillis())
                            ).toInt()

                            conversas = db.agenteDao().listarConversas()
                            conversaAtivaId = idNova

                            dialogNovaConversaAberta = false
                            novoTituloConversa = ""
                            erroTituloConversa = null

                            carregarMensagensDaConversa(idNova)
                            estadoGaveta.close()
                        }
                    }
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogNovaConversaAberta = false
                        erroTituloConversa = null
                    }
                ) { Text("Cancelar") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = estadoGaveta,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxSize().padding(0.dp)) {

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Blér",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                        items(conversas) { conv ->
                            NavigationDrawerItem(
                                label = { Text(conv.titulo) },
                                selected = conv.id == conversaAtivaId,
                                onClick = {
                                    escopo.launch {
                                        conversaAtivaId = conv.id
                                        carregarMensagensDaConversa(conv.id)
                                        estadoGaveta.close()
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            novoTituloConversa = ""
                            erroTituloConversa = null
                            dialogNovaConversaAberta = true
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("Nova conversa")
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()

                    NavigationDrawerItem(
                        label = { Text("Configurações") },
                        selected = false,
                        onClick = {
                            escopo.launch { estadoGaveta.close() }
                            aoAbrirConfig()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { escopo.launch { estadoGaveta.open() } }) {
                    Text("☰", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(text = "Blér", style = MaterialTheme.typography.headlineSmall)
                    val titulo = conversaAtiva?.titulo ?: "Conversa"
                    Text(text = titulo, style = MaterialTheme.typography.labelLarge)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(mensagens) { msg ->
                    Text(text = "${msg.autor}: ${msg.texto}", modifier = Modifier.padding(8.dp))
                }
                if (carregando) {
                    item {
                        Text(text = "agente está digitando...", modifier = Modifier.padding(8.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = textoDigitado,
                    onValueChange = { textoDigitado = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite sua mensagem...") }
                )
                Button(onClick = {
                    if (textoDigitado.isNotBlank() && !carregando) {
                        val texto = textoDigitado
                        textoDigitado = ""
                        val conversaIdAtual = conversaAtivaId

                        val provedor = Configuracoes.obterProvedorAtual(contexto)
                        val modelo = Configuracoes.obterModeloAtual(contexto)
                        val chave = Configuracoes.obterChaveAtual(contexto)

                        escopo.launch {
                            db.agenteDao().salvarMensagem(
                                MensagemEntity(
                                    conversaId = conversaIdAtual,
                                    autor = "você",
                                    texto = texto,
                                    dataHora = System.currentTimeMillis()
                                )
                            )
                            mensagens = db.agenteDao().listarMensagensDaConversa(conversaIdAtual)

                            if (chave.isBlank()) {
                                db.agenteDao().salvarMensagem(
                                    MensagemEntity(
                                        conversaId = conversaIdAtual,
                                        autor = "agente",
                                        texto = "Você ainda não configurou uma chave de API para o provedor \"$provedor\". Toque no menu (☰) para adicionar uma.",
                                        dataHora = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                carregando = true
                                val lembretesAtuais = db.agenteDao().listarTodosLembretes()
                                val instrucao = montarInstrucaoDeMemoria(lembretesAtuais)

                                val respostaBruta = perguntarComProvedor(
                                    historico = mensagens,
                                    provedor = provedor,
                                    modelo = modelo,
                                    chaveApi = chave,
                                    instrucaoSistema = instrucao
                                )

                                val respostaLimpa = processarAcoesDeMemoria(respostaBruta, db)
                                carregando = false

                                db.agenteDao().salvarMensagem(
                                    MensagemEntity(
                                        conversaId = conversaIdAtual,
                                        autor = "agente",
                                        texto = respostaLimpa,
                                        dataHora = System.currentTimeMillis()
                                    )
                                )
                            }

                            mensagens = db.agenteDao().listarMensagensDaConversa(conversaIdAtual)
                        }
                    }
                }) {
                    Text("Enviar")
                }
            }
        }
    }
}
