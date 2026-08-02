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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelaDeChat()
        }
    }
}

@Composable
fun TelaDeChat() {
    val contexto = LocalContext.current
    // Pega o "prédio" do banco de dados que já criamos
    val db = remember { AgenteDatabase.obter(contexto) }
    val escopo = rememberCoroutineScope()

    var mensagens by remember { mutableStateOf(listOf<MensagemEntity>()) }
    var textoDigitado by remember { mutableStateOf("") }

    // Assim que a tela abre, carrega o histórico salvo permanentemente
    LaunchedEffect(Unit) {
        val historico = db.agenteDao().listarMensagens()
        if (historico.isEmpty()) {
            // Primeira vez abrindo: salva a mensagem de boas-vindas
            db.agenteDao().salvarMensagem(
                MensagemEntity(autor = "agente", texto = "Oi! Eu sou seu agente. Pode escrever ou falar comigo.", dataHora = System.currentTimeMillis())
            )
        }
        mensagens = db.agenteDao().listarMensagens()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(mensagens) { msg ->
                Text(text = "${msg.autor}: ${msg.texto}", modifier = Modifier.padding(8.dp))
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
                if (textoDigitado.isNotBlank()) {
                    val texto = textoDigitado
                    textoDigitado = ""
                    escopo.launch {
                        // Salva a mensagem de verdade, com data e hora
                        db.agenteDao().salvarMensagem(
                            MensagemEntity(autor = "você", texto = texto, dataHora = System.currentTimeMillis())
                        )
                        mensagens = db.agenteDao().listarMensagens()
                    }
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
