package com.meuagente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            AppPrincipal()
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

suspend fun perguntarAoGemini(historico: List<MensagemEntity>, chaveApi: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$chaveApi"

            val contents = JSONArray()
            for (msg in historico) {
                val papel = if (msg.autor == "você") "user" else "model"
                val parte = JSONObject().put("text", msg.texto)
                val partes = JSONArray().put(parte)
                val item = JSONObject().put("role", papel).put("parts", partes)
                contents.put(item)
            }

            val corpoJson = JSONObject().put("contents", contents)
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
                MensagemEntity(autor = "agente", texto = "Oi! Eu sou seu agente. Antes de conversarmos, toque no ícone de engrenagem para adicionar sua chave de API.", dataHora = System.currentTimeMillis())
            )
        }
        mensagens = db.agenteDao().listarMensagens()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Meu Agente", style = MaterialTheme.typography.headlineSmall)
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
                    val chave = Configuracoes.obterChave(contexto)

                    escopo.launch {
                        db.agenteDao().salvarMensagem(
                            MensagemEntity(autor = "você", texto = texto, dataHora = System.currentTimeMillis())
                        )
                        mensagens = db.agenteDao().listarMensagens()

                        if (chave.isBlank()) {
                            db.agenteDao().salvarMensagem(
                                MensagemEntity(autor = "agente", texto = "Você ainda não configurou uma chave de API. Toque no ícone de engrenagem para adicionar uma.", dataHora = System.currentTimeMillis())
                            )
                        } else {
                            carregando = true
                            val resposta = perguntarAoGemini(mensagens, chave)
                            carregando = false
                            db.agenteDao().salvarMensagem(
                                MensagemEntity(autor = "agente", texto = resposta, dataHora = System.currentTimeMillis())
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
