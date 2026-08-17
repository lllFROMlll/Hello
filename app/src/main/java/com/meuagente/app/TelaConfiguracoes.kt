package com.meuagente.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val PROVEDORES_CONHECIDOS = listOf("Gemini", "OpenAI", "OpenRouter", "Anthropic", "Personalizado")

private const val OPCAO_MANUAL = "Outro (digitar manualmente)"

// Modelos mais usados de cada provedor, pra facilitar a escolha.
// Nenhuma lista é fechada: sempre tem a opção de digitar manualmente
// no final, pra qualquer modelo que não esteja aqui.
private val MODELOS_POR_PROVEDOR: Map<String, List<String>> = mapOf(
    "Gemini" to listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-pro",
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemini-3.1-pro"
    ),
    "OpenAI" to listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1",
        "gpt-4.1-mini",
        "o3-mini"
    ),
    "OpenRouter" to listOf(
        "openrouter/auto",
        "meta-llama/llama-3.3-70b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "google/gemma-2-9b-it:free",
        "openai/gpt-oss-120b:free",
        "anthropic/claude-3.5-sonnet",
        "openai/gpt-4o",
        "google/gemini-2.5-flash"
    ),
    "Anthropic" to listOf(
        "claude-sonnet-4-6",
        "claude-opus-4-8",
        "claude-haiku-4-5-20251001"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConfiguracoes(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current

    var provedor by remember { mutableStateOf(Configuracoes.obterProvedorAtual(contexto)) }
    var menuProvedorAberto by remember { mutableStateOf(false) }

    val modeloSalvo = remember { Configuracoes.obterModeloAtual(contexto) }
    val listaModelosInicial = MODELOS_POR_PROVEDOR[provedor] ?: emptyList()

    // Se o modelo salvo está na lista conhecida do provedor, começa
    // com ele selecionado. Senão, começa em "manual" com o valor salvo.
    var modeloSelecionado by remember {
        mutableStateOf(if (listaModelosInicial.contains(modeloSalvo)) modeloSalvo else OPCAO_MANUAL)
    }
    var modeloManual by remember {
        mutableStateOf(if (listaModelosInicial.contains(modeloSalvo)) "" else modeloSalvo)
    }
    var menuModeloAberto by remember { mutableStateOf(false) }

    var chave by remember { mutableStateOf(Configuracoes.obterChaveDoProvedor(contexto, provedor)) }
    var salvo by remember { mutableStateOf(false) }

    val listaModelos = MODELOS_POR_PROVEDOR[provedor] ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text(text = "Configurações", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "1. Selecione o provedor da chave de API")
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = menuProvedorAberto,
            onExpandedChange = { menuProvedorAberto = it }
        ) {
            OutlinedTextField(
                value = provedor,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuProvedorAberto) }
            )
            ExposedDropdownMenu(expanded = menuProvedorAberto, onDismissRequest = { menuProvedorAberto = false }) {
                PROVEDORES_CONHECIDOS.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text(opcao) },
                        onClick = {
                            provedor = opcao
                            chave = Configuracoes.obterChaveDoProvedor(contexto, opcao)
                            // Trocou de provedor: reseta a escolha de modelo,
                            // já que a lista de opções é outra agora.
                            modeloSelecionado = OPCAO_MANUAL
                            modeloManual = ""
                            salvo = false
                            menuProvedorAberto = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "2. Selecione o modelo")
        Spacer(modifier = Modifier.height(4.dp))

        if (listaModelos.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = menuModeloAberto,
                onExpandedChange = { menuModeloAberto = it }
            ) {
                OutlinedTextField(
                    value = modeloSelecionado,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuModeloAberto) }
                )
                ExposedDropdownMenu(expanded = menuModeloAberto, onDismissRequest = { menuModeloAberto = false }) {
                    (listaModelos + OPCAO_MANUAL).forEach { opcao ->
                        DropdownMenuItem(
                            text = { Text(opcao) },
                            onClick = {
                                modeloSelecionado = opcao
                                salvo = false
                                menuModeloAberto = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (listaModelos.isEmpty() || modeloSelecionado == OPCAO_MANUAL) {
            OutlinedTextField(
                value = modeloManual,
                onValueChange = {
                    modeloManual = it
                    salvo = false
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Digite o nome exato do modelo...") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "3. Cole a chave de API deste provedor")
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
            val modeloFinal = if (modeloSelecionado == OPCAO_MANUAL) modeloManual else modeloSelecionado
            Configuracoes.salvarProvedorAtual(contexto, provedor)
            Configuracoes.salvarModeloAtual(contexto, modeloFinal)
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
