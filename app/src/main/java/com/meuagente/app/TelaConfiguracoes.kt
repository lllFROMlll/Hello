package com.meuagente.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Lista de provedores conhecidos, só pra facilitar a escolha no menu.
// "Personalizado" permite digitar qualquer outro nome que o usuário quiser.
private val PROVEDORES_CONHECIDOS = listOf("Gemini", "OpenAI", "OpenRouter", "Anthropic", "Personalizado")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConfiguracoes(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current

    var provedor by remember { mutableStateOf(Configuracoes.obterProvedorAtual(contexto)) }
    var modelo by remember { mutableStateOf(Configuracoes.obterModeloAtual(contexto)) }
    var chave by remember { mutableStateOf(Configuracoes.obterChaveDoProvedor(contexto, provedor)) }
    var menuAberto by remember { mutableStateOf(false) }
    var salvo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(text = "Configurações", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Provedor de IA")
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = menuAberto,
            onExpandedChange = { menuAberto = it }
        ) {
            OutlinedTextField(
                value = provedor,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAberto) }
            )
            ExposedDropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                PROVEDORES_CONHECIDOS.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text(opcao) },
                        onClick = {
                            provedor = opcao
                            chave = Configuracoes.obterChaveDoProvedor(contexto, opcao)
                            salvo = false
                            menuAberto = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Modelo (nome exato, ex: gemini-2.5-flash-lite)")
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = modelo,
            onValueChange = {
                modelo = it
                salvo = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Digite o nome do modelo...") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Chave de API deste provedor")
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = chave,
            onValueChange = {
                chave = it
                salvo = false
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cole sua chave aqui...") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            Configuracoes.salvarProvedorAtual(contexto, provedor)
            Configuracoes.salvarModeloAtual(contexto, modelo)
            Configuracoes.salvarChaveDoProvedor(contexto, provedor, chave)
            salvo = true
        }) {
            Text("Salvar configurações")
        }

        if (salvo) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Configurações salvas com sucesso!")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = aoVoltar) {
            Text("Voltar para o chat")
        }
    }
}
