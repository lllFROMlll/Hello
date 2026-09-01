package com.meuagente.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Cores neon do projeto Blér (mesmas do chat)
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)

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

    // ── Estados da nova seção de voz ──
    var modoVoz by remember { mutableStateOf(ControladorModoVoz.atual(contexto)) }
    var sonsAtivos by remember { mutableStateOf(Configuracoes.obterSonsAtivos(contexto)) }
    val modeloVozAtual = remember {
        Configuracoes.obterModeloAtual(contexto)
    }
    var modeloAceitaAudio by remember {
        mutableStateOf(
            if (modeloVozAtual.isNotBlank()) MapaMultimodal.ehMultimodal(contexto, modeloVozAtual) else true
        )
    }

    val listaModelos = MODELOS_POR_PROVEDOR[provedor] ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {

        Text(text = "Configurações", style = MaterialTheme.typography.headlineSmall)

        // ── Linha neon fina abaixo do título ──
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(NeonAzul, NeonLilas, NeonRosa)),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }

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

        // ════════════════════════════════════════════════════
        // SEÇÃO: COMANDO DE VOZ
        // ════════════════════════════════════════════════════
        Text(text = "Comando de voz", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))

        Text(text = "Modo de voz:")
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            for (modo in ModoVoz.values()) {
                FilterChip(
                    label = { Text(ControladorModoVoz.rotulo(modo)) },
                    selected = modo == modoVoz,
                    onClick = {
                        modoVoz = modo
                        ControladorModoVoz.salvar(contexto, modo)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = ControladorModoVoz.descricao(modoVoz), style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(12.dp))

        if (modeloVozAtual.isNotBlank()) {
            Text(
                text = "Este modelo aceita áudio (multimodal):",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = modeloAceitaAudio,
                    onCheckedChange = { aceita ->
                        modeloAceitaAudio = aceita
                        MapaMultimodal.marcarAceitaAudio(contexto, modeloVozAtual, aceita)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("$modeloVozAtual")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "O modo Automático usa esta marcação para decidir entre IA e nativo.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(text = "Efeitos sonoros:", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = sonsAtivos,
                onCheckedChange = { ativos ->
                    sonsAtivos = ativos
                    Configuracoes.salvarSonsAtivos(contexto, ativos)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sons neon em botões e microfone")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = aoVoltar) {
            Text("Voltar para o chat")
        }

        // ── Linha neon fina na base ──
        Spacer(modifier = Modifier.height(16.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(NeonRosa, NeonLilas, NeonAzul)),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }
    }
}
