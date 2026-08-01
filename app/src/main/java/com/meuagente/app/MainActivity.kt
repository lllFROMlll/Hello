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
import androidx.compose.ui.unit.dp

data class Mensagem(val autor: String, val texto: String)

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
    var mensagens by remember { mutableStateOf(listOf(
        Mensagem("agente", "Oi! Eu sou seu agente. Pode escrever ou falar comigo.")
    )) }

    var textoDigitado by remember { mutableStateOf("") }

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
                    mensagens = mensagens + Mensagem("você", textoDigitado)
                    textoDigitado = ""
                }
            }) {
                Text("Enviar")
            }
        }
    }
}
