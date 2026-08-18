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

// Marcações que a IA usa pra avisar o app que quer guardar ou apagar
// uma memória. Ficam escondidas do usuário — o app lê e remove.
private val REGEX_GUARDAR = Regex("""\[GUARDAR:\s*(.+?)\]""")
private val REGEX_APAGAR = Regex("""\[APAGAR:\s*(.+?)\]""")

// Monta a instrução que ensina a IA a se comportar como agente com
// memória, não só chatbot — inclui a lista do que já está guardado.
private fun montarInstrucaoDeMemoria(lembretes: List<LembreteEntity>): String {
    val listaTexto = if (lembretes.isEmpty()) {
        "(nenhuma memória guardada ainda)"
    } else {
        lembretes.joinToString("\n") { "- ${it.descricao}" + if (it.concluido) " (concluído)" else "" }
    }

    return """
        Você é Blér, um assistente pessoal com memória real, não apenas um chatbot comum.
        Memórias guardadas até agora:
        $listaTexto

        Regras:
        - Se o usuário pedir para você lembrar de algo (sem data específica), adicione no FINAL da sua resposta, em uma linha separada, exatamente: [GUARDAR: descrição curta e clara]
        - Se o usuário disser que algo já foi resolvido, pode apagar, ou não precisa mais lembrar, adicione no FINAL da resposta, em linha separada: [APAGAR: descrição que identifique a memória antiga]
        - Nunca explique essas marcações para o usuário, nem as escreva de outra forma — elas são só para o sistema.
        - Se o usuário perguntar o que você tem guardado, responda usando a lista acima, de forma natural e simples.
    """.trimIndent()
}

// Lê a resposta da IA, executa as ações de memória marcadas nela, e
// devolve o texto já limpo (sem as marcações) pra mostrar ao usuário.
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
                    .firstOrNull { it.descricao.contains(descricaoBusca, ignoreCase = true) }
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

@Composable
fun TelaDeChat(aoAbrirConfig: () -> Unit) {
    val contexto = LocalContext.current
    val db = remember { AgenteDatabase.obter(contexto) }
    val escopo = rememberCoroutineScope()

    var mensagens by remember { mutableStateOf(listOf<MensagemEntity>()) }
    var textoDigitado by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val historico = db.agenteDao().listarMensagens()
        if (historico.isEmpty()) {
            db.agenteDao().salvarMensagem(
                MensagemEntity(autor = "agente", texto = "Oi! Eu sou o Blér. Antes de conversarmos, toque no ícone de engrenagem para escolher o provedor de IA e colar sua chave de API.", dataHora = System.currentTimeMillis())
            )
        }
        mensagens = db.agenteDao().listarMensagens()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Blér", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = aoAbrirConfig) {
                Text("⚙️")
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
                    val provedor = Configuracoes.obterProvedorAtual(contexto)
                    val modelo = Configuracoes.obterModeloAtual(contexto)
                    val chave = Configuracoes.obterChaveAtual(contexto)

                    escopo.launch {
                        db.agenteDao().salvarMensagem(
                            MensagemEntity(autor = "você", texto = texto, dataHora = System.currentTimeMillis())
                        )
                        mensagens = db.agenteDao().listarMensagens()

                        if (chave.isBlank()) {
                            db.agenteDao().salvarMensagem(
                                MensagemEntity(autor = "agente", texto = "Você ainda não configurou uma chave de API para o provedor \"$provedor\". Toque no ícone de engrenagem para adicionar uma.", dataHora = System.currentTimeMillis())
                            )
                        } else {
                            carregando = true
                            val lembretesAtuais = db.agenteDao().listarTodosLembretes()
                            val instrucao = montarInstrucaoDeMemoria(lembretesAtuais)
                            val respostaBruta = perguntarComProvedor(mensagens, provedor, modelo, chave, instrucao)
                            val respostaLimpa = processarAcoesDeMemoria(respostaBruta, db)
                            carregando = false
                            db.agenteDao().salvarMensagem(
                                MensagemEntity(autor = "agente", texto = respostaLimpa, dataHora = System.currentTimeMillis())
                            )
                        }
                        mensagens = db.agenteDao().listarMensagens()
                    }
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
