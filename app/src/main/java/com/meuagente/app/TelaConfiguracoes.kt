package com.meuagente.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
                            salvo = false
                            menuProvedorAberto = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "2. Cascata de IA (provedores e modelos)")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ligue os provedores, defina a prioridade (1 tenta primeiro) " +
                "e escolha os modelos em ordem de tentativa.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        var autoCascata by remember { mutableStateOf(Configuracoes.obterAutoCascata(contexto)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = autoCascata,
                onCheckedChange = { ativa ->
                    autoCascata = ativa
                    Configuracoes.salvarAutoCascata(contexto, ativa)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ordem automática (por complexidade da pergunta)")
        }
        Spacer(modifier = Modifier.height(8.dp))

        val provedorDasChaves = provedor
        for (provedorCascata in listOf("Gemini", "OpenRouter")) {
            var ativo by remember {
                mutableStateOf(Configuracoes.obterProvedorAtivoNaCascata(contexto, provedorCascata, provedorCascata == "Gemini"))
            }
            var prioridadeTexto by remember {
                mutableStateOf(
                    Configuracoes.obterPrioridadeProvedor(contexto, provedorCascata)
                        .takeIf { it != 99 }?.toString().orEmpty()
                )
            }
            val legado = modeloSalvo
                .takeIf { provedorCascata == Configuracoes.obterProvedorAtual(contexto) && it.isNotBlank() }
                .orEmpty()
            var modelosSelecionados by remember {
                mutableStateOf(Configuracoes.obterModelosProvedor(contexto, provedorCascata, legado))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = ativo,
                        onCheckedChange = { valor ->
                            ativo = valor
                            Configuracoes.salvarProvedorAtivoNaCascata(contexto, provedorCascata, valor)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = provedorCascata, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = prioridadeTexto,
                        onValueChange = { valor ->
                            prioridadeTexto = valor.filter { it.isDigit() }.take(2)
                            prioridadeTexto.toIntOrNull()?.let {
                                Configuracoes.salvarPrioridadeProvedor(contexto, provedorCascata, it)
                            }
                        },
                        enabled = ativo,
                        modifier = Modifier.width(80.dp),
                        placeholder = { Text("Nº") }
                    )
                }
                if (ativo) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SeletorModelosIA(
                        sugestoes = MODELOS_POR_PROVEDOR[provedorCascata] ?: emptyList(),
                        selecionados = modelosSelecionados,
                        onChange = { novaLista ->
                            modelosSelecionados = novaLista
                            Configuracoes.salvarModelosProvedor(contexto, provedorCascata, novaLista)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "3. Cole a chave de API do provedor selecionado acima (\"$provedorDasChaves\")")
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
            // O "modelo atual" passa a ser o primeiro da lista do provedor
            // selecionado (mantém a voz/multimodal e demais leituras ok).
            val modelosDoProvedor = Configuracoes.obterModelosProvedor(contexto, provedor)
            if (modelosDoProvedor.isNotEmpty()) {
                Configuracoes.salvarModeloAtual(contexto, modelosDoProvedor.first())
            }
            Configuracoes.salvarProvedorAtual(contexto, provedor)
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

        // ════════════════════════════════════════════════════
        // SEÇÃO: BUSCA NA INTERNET
        // ════════════════════════════════════════════════════
        Text(text = "Busca na internet", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sem as chaves abaixo, a busca usa fontes abertas " +
                "(DuckDuckGo, Bing, Mojeek, SearXNG, Wikipedia). " +
                "Com as chaves, a busca fica mais estável e completa.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Chave da Tavily (opcional):", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        var chaveTavily by remember { mutableStateOf(Configuracoes.obterChaveTavily(contexto)) }
        OutlinedTextField(
            value = chaveTavily,
            onValueChange = { valor ->
                chaveTavily = valor
                Configuracoes.salvarChaveTavily(contexto, valor)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("tvly-...") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Chave da Brave Search (opcional):", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        var chaveBrave by remember { mutableStateOf(Configuracoes.obterChaveBrave(contexto)) }
        OutlinedTextField(
            value = chaveBrave,
            onValueChange = { valor ->
                chaveBrave = valor
                Configuracoes.salvarChaveBrave(contexto, valor)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("BSA...") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        var confirmacaoGoogle by remember { mutableStateOf(Configuracoes.usarConfirmacaoGoogle(contexto)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = confirmacaoGoogle,
                onCheckedChange = { ativa ->
                    confirmacaoGoogle = ativa
                    Configuracoes.salvarConfirmacaoGoogle(contexto, ativa)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirmar fatos atuais com o Google (usa sua chave Gemini)")
        }
        Text(
            text = "Para perguntas sensíveis ao tempo (notícias, placares, " +
                "cotações), o Blér faz 1 verificação extra no Google antes " +
                "de responder, para evitar dados desatualizados.",
            style = MaterialTheme.typography.bodySmall
        )

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeletorModelosIA(
    sugestoes: List<String>,
    selecionados: List<String>,
    onChange: (List<String>) -> Unit
) {
    var consulta by remember { mutableStateOf("") }

    OutlinedTextField(
        value = consulta,
        onValueChange = { consulta = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar ou digitar modelo...") },
        trailingIcon = {
            if (consulta.isNotBlank()) {
                TextButton(onClick = {
                    val nome = consulta.trim()
                    if (nome.isNotBlank() && !selecionados.contains(nome)) {
                        onChange(selecionados + nome)
                    }
                    consulta = ""
                }) {
                    Text("+")
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(6.dp))

    if (selecionados.isNotEmpty()) {
        Text(
            text = "Ordem de tentativa (toque para remover):",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selecionados.forEachIndexed { indice, modelo ->
                FilterChip(
                    selected = true,
                    onClick = { onChange(selecionados.filterIndexed { i, _ -> i != indice }) },
                    label = { Text("${indice + 1}. $modelo") }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    val filtradas = sugestoes.filter { sugestao ->
        sugestao.contains(consulta.trim(), ignoreCase = true) && !selecionados.contains(sugestao)
    }

    if (filtradas.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filtradas.forEach { sugestao ->
                FilterChip(
                    selected = false,
                    onClick = { onChange(selecionados + sugestao) },
                    label = { Text(sugestao) }
                )
            }
        }
    }
}
