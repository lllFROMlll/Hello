package com.meuagente.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TelaConfiguracoes(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    var chave by remember { mutableStateOf(Configuracoes.obterChave(contexto)) }
    var salvo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(text = "Configurações", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Cole aqui sua chave de API (Gemini, OpenAI, OpenRouter, ou qualquer outra que use o mesmo formato).")

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = chave,
            onValueChange = {
                chave = it
                salvo = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cole sua chave aqui...") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            Configuracoes.salvarChave(contexto, chave)
            salvo = true
        }) {
            Text("Salvar chave")
        }

        if (salvo) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Chave salva com sucesso!")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = aoVoltar) {
            Text("Voltar para o chat")
        }
    }
}
