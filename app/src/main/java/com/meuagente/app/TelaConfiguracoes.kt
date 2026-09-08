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
import androidx.compose.ui.unit.sp

// Cores neon do projeto Blér (mesmas do chat)
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)

// Texto secundário legível sobre fundo escuro
private val TextoSecundario = Color(0xFFB8C0D0)

// Provedores que participam da cascata hoje. 9Router e IA Local
// entram aqui quando forem implementados.
private val PROVEDORES_CASCATA = listOf("Gemini", "OpenRouter", "OpenAI")

// Modelos mais usados de cada provedor, como SUGESTÕES do campo de
// busca. Nenhuma lista é fechada: sempre dá para digitar manualmente
// qualquer modelo que não esteja aqui.
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
        "deepseek/deepseek-chat",
        "meta-llama/llama-3.3-70b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "moonshotai/glm-4.6",
        "openai/gpt-oss-120b:free",
        "anthropic/claude-3.5-sonnet",
        "google/gemini-2.5-flash"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConfiguracoes(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current

    // Blocos de provedor que o usuário adicionou. Migração: quem já tem
    // chave salva entra automaticamente como bloco na primeira abertura.
    val provedoresConfigurados = remember {
        mutableStateListOf<String>().apply {
            PROVEDORES_CASCATA.forEach { provedor ->
                if (Configuracoes.obterChaveDoProvedor(contexto, provedor).isNotBlank()) {
                    add(provedor)
                }
            }
        }
    }

    // ── Estados da seção de voz ──
    var modoVoz by remember { mutableStateOf(ControladorModoVoz.atual(contexto)) }
    var sonsAtivos by remember { mutableStateOf(Configuracoes.obterSonsAtivos(contexto)) }
    val modeloVozAtual = remember { Configuracoes.obterModeloAtual(contexto) }
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

        Text(text = "1. Cascata de IA (provedores e modelos)")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adicione provedores, ligue cada um, defina a prioridade " +
                "(1 tenta primeiro) e escolha os modelos em ordem de tentativa.",
            color = TextoSecundario,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        provedoresConfigurados.forEach { nomeProvedor ->
            BlocoProvedorIA(
                nomeProvedor = nomeProvedor,
                aoRemover = {
                    Configuracoes.salvarProvedorAtivoNaCascata(contexto, nomeProvedor, false)
                    provedoresConfigurados.remove(nomeProvedor)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val disponiveis = PROVEDORES_CASCATA.filter { !provedoresConfigurados.contains(it) }
        if (disponiveis.isNotEmpty()) {
            AdicionarProvedorBotao(disponiveis) { escolhido ->
                provedoresConfigurados.add(escolhido)
                Configuracoes.salvarProvedorAtivoNaCascata(contexto, escolhido, true)
            }
        }

        if (provedoresConfigurados.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
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
        }

        if (provedoresConfigurados.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // O "modelo atual" (usado pela voz e pelo mapa multimodal)
                // passa a ser o primeiro modelo ativo da cascata.
                val primeiroAtivo = provedoresConfigurados
                    .filter { Configuracoes.obterProvedorAtivoNaCascata(contexto, it, it == "Gemini") }
                    .firstOrNull { Configuracoes.obterChaveDoProvedor(contexto, it).isNotBlank() }
                val modelosDoProvedor = primeiroAtivo
                    ?.let { Configuracoes.obterModelosProvedor(contexto, it) }
                    .orEmpty()
                if (modelosDoProvedor.isNotEmpty() && primeiroAtivo != null) {
                    Configuracoes.salvarModeloAtual(contexto, modelosDoProvedor.first())
                    Configuracoes.salvarProvedorAtual(contexto, primeiroAtivo)
                }
            }) {
                Text("Salvar modelo padrão")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ════════════════════════════════════════════════════
        // SEÇÃO: COMANDO DE VOZ
        // ════════════════════════════════════════════════════
        Text(text = "Comando de voz", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))

        Text(text = "Modo de voz:", color = TextoSecundario)
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
        Text(
            text = ControladorModoVoz.descricao(modoVoz),
            color = TextoSecundario,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (modeloVozAtual.isNotBlank()) {
            Text(
                text = "Este modelo aceita áudio (multimodal):",
                color = TextoSecundario,
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
                color = TextoSecundario,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(text = "Efeitos sonoros:", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
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
            color = TextoSecundario,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Chave da Tavily (opcional):", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
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

        Text(text = "Chave da Brave Search (opcional):", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
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
            color = TextoSecundario,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlocoProvedorIA(
    nomeProvedor: String,
    aoRemover: () -> Unit
) {
    val contexto = LocalContext.current
    val modeloLegado = remember { Configuracoes.obterModeloAtual(contexto) }

    var ativo by remember {
        mutableStateOf(
            Configuracoes.obterProvedorAtivoNaCascata(contexto, nomeProvedor, padrao = true)
        )
    }
    var prioridadeTexto by remember {
        mutableStateOf(
            Configuracoes.obterPrioridadeProvedor(contexto, nomeProvedor)
                .takeIf { it != 99 }?.toString().orEmpty()
        )
    }
    var chave by remember { mutableStateOf(Configuracoes.obterChaveDoProvedor(contexto, nomeProvedor)) }
    val legado = modeloLegado.takeIf { it.isNotBlank() }.orEmpty()
    var modelosSelecionados by remember {
        mutableStateOf(Configuracoes.obterModelosProvedor(contexto, nomeProvedor, legado))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = ativo,
                onCheckedChange = { valor ->
                    ativo = valor
                    Configuracoes.salvarProvedorAtivoNaCascata(contexto, nomeProvedor, valor)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = nomeProvedor, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = prioridadeTexto,
                onValueChange = { valor ->
                    prioridadeTexto = valor.filter { it.isDigit() }.take(2)
                    prioridadeTexto.toIntOrNull()?.let {
                        Configuracoes.salvarPrioridadeProvedor(contexto, nomeProvedor, it)
                    }
                },
                enabled = ativo,
                modifier = Modifier.width(80.dp),
                placeholder = { Text("Nº") }
            )
            TextButton(onClick = aoRemover) {
                Text("✕", color = NeonRosa)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = chave,
            onValueChange = { valor ->
                chave = valor
                Configuracoes.salvarChaveDoProvedor(contexto, nomeProvedor, valor)
            },
            enabled = ativo,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cole a chave de API do $nomeProvedor...") }
        )

        if (ativo) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Selecione os modelos:", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            SeletorModelosIA(
                sugestoes = MODELOS_POR_PROVEDOR[nomeProvedor] ?: emptyList(),
                selecionados = modelosSelecionados,
                onChange = { novaLista ->
                    modelosSelecionados = novaLista
                    Configuracoes.salvarModelosProvedor(contexto, nomeProvedor, novaLista)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdicionarProvedorBotao(
    disponiveis: List<String>,
    aoAdicionar: (String) -> Unit
) {
    var menuAberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menuAberto = true }) {
            Text("+ Adicionar provedor")
        }
        DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
            disponiveis.forEach { provedor ->
                DropdownMenuItem(
                    text = { Text(provedor) },
                    onClick = {
                        menuAberto = false
                        aoAdicionar(provedor)
                    }
                )
            }
        }
    }
}

/**
 * Campo único "Buscar ou selecionar modelo...": ao digitar, o dropdown
 * filtra as sugestões em tempo real; dá para marcar mais de um modelo
 * e também adicionar um nome que não está na lista. Os selecionados
 * viram chips numerados (ordem de tentativa) com toque para remover.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SeletorModelosIA(
    sugestoes: List<String>,
    selecionados: List<String>,
    onChange: (List<String>) -> Unit
) {
    var consulta by remember { mutableStateOf("") }
    var menuAberto by remember { mutableStateOf(false) }

    val texto = consulta.trim()
    val filtradas = sugestoes.filter { sugestao ->
        sugestao.contains(texto, ignoreCase = true) && !selecionados.contains(sugestao)
    }
    val podeAdicionarManual = texto.isNotBlank() &&
        !selecionados.contains(texto) &&
        !sugestoes.contains(texto)

    Box {
        OutlinedTextField(
            value = consulta,
            onValueChange = {
                consulta = it
                menuAberto = true
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar ou selecionar modelo...") },
            trailingIcon = {
                Text(text = "${selecionados.size} ✓", color = TextoSecundario, fontSize = 12.sp)
            }
        )
        DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
            if (podeAdicionarManual) {
                DropdownMenuItem(
                    text = { Text("Adicionar \"$texto\"") },
                    onClick = {
                        onChange(selecionados + texto)
                        consulta = ""
                        menuAberto = false
                    }
                )
            }
            filtradas.forEach { sugestao ->
                DropdownMenuItem(
                    text = { Text(sugestao) },
                    onClick = {
                        onChange(selecionados + sugestao)
                        consulta = ""
                        menuAberto = false
                    }
                )
            }
            if (filtradas.isEmpty() && !podeAdicionarManual && texto.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Nenhum modelo encontrado", color = TextoSecundario) },
                    onClick = { menuAberto = false }
                )
            }
        }
    }

    if (selecionados.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ordem de tentativa (toque para remover):",
            color = TextoSecundario,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selecionados.forEachIndexed { indice, modelo ->
                FilterChip(
                    selected = true,
                    onClick = { onChange(selecionados.filterIndexed { i, _ -> i != indice }) },
                    label = { Text("${indice + 1}. $modelo ✕") }
                )
            }
        }
    }
}
