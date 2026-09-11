package com.meuagente.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meuagente.app.ia.ModeloOpenRouter
import com.meuagente.app.ia.RepositorioModelosOpenRouter
import kotlinx.coroutines.launch

// ── Paleta Neon do Projeto Blér ──
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)
private val VerdeStatus = Color(0xFF69F0AE)

// ── Tipografia de Alto Contraste sobre Fundo Escuro ──
private val TextoPrimario = Color(0xFFF8FAFC)
private val TextoExplicativo = Color(0xFFCBD5E1) // Alto contraste (Slate 300)
private val TextoApoio = Color(0xFF94A3B8)       // Legenda secundária nítida (Slate 400)
private val TextoPlaceholder = Color(0xFF64748B) // Placeholder legível
private val TextoDesativado = Color(0xFF64748B)

// Provedores que participam da cascata
private val PROVEDORES_CASCATA = listOf("Gemini", "OpenRouter", "OpenAI")

data class SugestaoModelo(
    val id: String,
    val nomeExibicao: String = id,
    val ehGratuito: Boolean = false
)

// Modelos mais usados de cada provedor como sugestões rápidas
private val MODELOS_POR_PROVEDOR: Map<String, List<SugestaoModelo>> = mapOf(
    "Gemini" to listOf(
        SugestaoModelo("gemini-2.5-flash", "Gemini 2.5 Flash"),
        SugestaoModelo("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),
        SugestaoModelo("gemini-2.5-pro", "Gemini 2.5 Pro"),
        SugestaoModelo("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
        SugestaoModelo("gemini-3.1-pro", "Gemini 3.1 Pro")
    ),
    "OpenAI" to listOf(
        SugestaoModelo("gpt-4o-mini", "GPT-4o mini"),
        SugestaoModelo("gpt-4o", "GPT-4o"),
        SugestaoModelo("gpt-4.1-mini", "GPT-4.1 mini"),
        SugestaoModelo("o3-mini", "o3-mini")
    )
)

@Composable
private fun coresCampoTexto() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF8FAFC),
    unfocusedTextColor = Color(0xFFF8FAFC),
    focusedContainerColor = Color(0xFF080C1A),
    unfocusedContainerColor = Color(0xFF080C1A),
    cursorColor = NeonAzul,
    focusedBorderColor = NeonAzul,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = NeonAzul,
    unfocusedLabelColor = Color(0xFFCBD5E1),
    focusedPlaceholderColor = TextoPlaceholder,
    unfocusedPlaceholderColor = TextoPlaceholder,
    disabledTextColor = TextoDesativado,
    disabledBorderColor = Color(0xFF1E293B),
    disabledContainerColor = Color(0xFF080C1A)
)

@Composable
private fun coresInterruptor() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = NeonAzul,
    uncheckedThumbColor = Color(0xFFCBD5E1),
    uncheckedTrackColor = Color(0xFF1E293B),
    uncheckedBorderColor = Color(0xFF475569)
)

@Composable
private fun CardSecao(
    icone: String,
    titulo: String,
    subtitulo: String,
    corDestaque: Color = NeonAzul,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10162D)),
        border = BorderStroke(1.dp, Color(0xFF263359))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icone,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = corDestaque
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = TextoExplicativo,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            conteudo()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConfiguracoes(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current

    // Provedores configurados (inicia com os que têm chave ou estão ativos; se vazio, inicia com Gemini)
    val provedoresConfigurados = remember {
        mutableStateListOf<String>().apply {
            PROVEDORES_CASCATA.forEach { provedor ->
                val temChave = Configuracoes.obterChaveDoProvedor(contexto, provedor).isNotBlank()
                val estaAtivo = Configuracoes.obterProvedorAtivoNaCascata(contexto, provedor, padrao = false)
                if (temChave || estaAtivo) {
                    add(provedor)
                }
            }
            if (isEmpty()) {
                add("Gemini")
                Configuracoes.salvarProvedorAtivoNaCascata(contexto, "Gemini", true)
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
    var mensagemModeloPadrao by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060B))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Cabeçalho da Tela ──
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Personalize as inteligências artificiais, voz e pesquisa na web do Blér.",
            color = TextoExplicativo,
            style = MaterialTheme.typography.bodySmall
        )

        // ── Linha neon de destaque ──
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(NeonAzul, NeonLilas, NeonRosa)),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ════════════════════════════════════════════════════
        // CARD 1: CASCATA MULTI-MODELOS E PROVEDORES
        // ════════════════════════════════════════════════════
        CardSecao(
            icone = "🤖",
            titulo = "1. Provedores e Modelos de IA",
            subtitulo = "Cadastre as IAs que o Blér pode consultar. Se a primeira falhar, esgotar a cota gratuita ou demorar para responder, o app pula automaticamente para a próxima.",
            corDestaque = NeonAzul
        ) {
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
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ════════════════════════════════════════════════════
        // CARD 2: REGRAS DE FUNCIONAMENTO
        // ════════════════════════════════════════════════════
        CardSecao(
            icone = "⚡",
            titulo = "2. Regras de Funcionamento",
            subtitulo = "Defina como o Blér decide qual inteligência artificial acionar para cada tipo de pergunta.",
            corDestaque = NeonLilas
        ) {
            var autoCascata by remember { mutableStateOf(Configuracoes.obterAutoCascata(contexto)) }

            // Item 1: Cascata inteligente por complexidade
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = autoCascata,
                    onCheckedChange = { ativa ->
                        autoCascata = ativa
                        Configuracoes.salvarAutoCascata(contexto, ativa)
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ordem automática inteligente",
                        fontWeight = FontWeight.SemiBold,
                        color = TextoPrimario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Quando ativado: perguntas simples e saudações vão direto para modelos ultrarrápidos e gratuitos. Perguntas longas, código ou raciocínio complexo acionam modelos avançados.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Item 2: Salvar modelo padrão
            Text(
                text = "Sincronizar Modelo Principal",
                fontWeight = FontWeight.SemiBold,
                color = TextoPrimario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Define o primeiro modelo configurado na cascata como o padrão oficial do app (usado no comando de voz e em novas conversas).",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val primeiroAtivo = provedoresConfigurados
                        .filter { Configuracoes.obterProvedorAtivoNaCascata(contexto, it, it == "Gemini") }
                        .firstOrNull { Configuracoes.obterChaveDoProvedor(contexto, it).isNotBlank() }
                    val modelosDoProvedor = primeiroAtivo
                        ?.let { Configuracoes.obterModelosProvedor(contexto, it) }
                        .orEmpty()

                    if (modelosDoProvedor.isNotEmpty() && primeiroAtivo != null) {
                        val novoPadrao = modelosDoProvedor.first()
                        Configuracoes.salvarModeloAtual(contexto, novoPadrao)
                        Configuracoes.salvarProvedorAtual(contexto, primeiroAtivo)
                        mensagemModeloPadrao = "✓ Modelo padrão salvo: $novoPadrao ($primeiroAtivo)"
                    } else {
                        mensagemModeloPadrao = "Nenhum provedor ativo com chave e modelo encontrado."
                    }
                },
                border = BorderStroke(1.dp, NeonLilas.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonLilas)
            ) {
                Text("Definir 1º modelo ativo como padrão", fontWeight = FontWeight.SemiBold)
            }

            if (mensagemModeloPadrao.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mensagemModeloPadrao,
                    color = VerdeStatus,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ════════════════════════════════════════════════════
        // CARD 3: COMANDO DE VOZ E ÁUDIO
        // ════════════════════════════════════════════════════
        CardSecao(
            icone = "🎙️",
            titulo = "3. Comando de Voz e Áudio",
            subtitulo = "Escolha como o microfone do celular ouve sua fala e processa suas instruções.",
            corDestaque = NeonRosa
        ) {
            // Item 1: Seletor de Modo de Voz
            Text(
                text = "Modo de escuta do microfone:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextoPrimario
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (modo in ModoVoz.values()) {
                    val selecionado = (modo == modoVoz)
                    FilterChip(
                        label = {
                            Text(
                                text = ControladorModoVoz.rotulo(modo),
                                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                                color = if (selecionado) Color.White else TextoExplicativo
                            )
                        },
                        selected = selecionado,
                        onClick = {
                            modoVoz = modo
                            ControladorModoVoz.salvar(contexto, modo)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF090D1C),
                            selectedContainerColor = NeonRosa.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selecionado) NeonRosa else Color(0xFF263259)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Explicação detalhada e simples de cada modo
            val explicacaoModo = when (modoVoz) {
                ModoVoz.AUTOMATICO -> "Automático (Recomendado): O app analisa o modelo atual. Se ele aceitar áudio direto, envia a gravação; se for apenas texto, o celular converte sua voz em texto antes de enviar."
                ModoVoz.NATIVO -> "Nativo do Android: O celular converte sua voz em texto no próprio aparelho antes de enviar à IA. Funciona com qualquer modelo e economiza dados móveis."
                ModoVoz.IA_AUDIO -> "Direto para a IA: O áudio da sua gravação é enviado diretamente para a IA (ex: Gemini Flash). Oferece respostas mais naturais e percebe entonação, mas exige modelo compatível com áudio."
            }

            Surface(
                color = Color(0xFF080C1A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E284A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = explicacaoModo,
                    color = TextoExplicativo,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Item 2: Modelo aceita áudio direto (multimodal)
            if (modeloVozAtual.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = modeloAceitaAudio,
                        onCheckedChange = { aceita ->
                            modeloAceitaAudio = aceita
                            MapaMultimodal.marcarAceitaAudio(contexto, modeloVozAtual, aceita)
                        },
                        colors = coresInterruptor()
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Este modelo aceita áudio direto",
                            fontWeight = FontWeight.SemiBold,
                            color = TextoPrimario,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Modelo: $modeloVozAtual",
                            color = NeonRosa,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "O modo Automático usa esta informação para saber se pode mandar o áudio bruto gravado ou se deve transcrever antes.",
                    color = TextoApoio,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Item 3: Efeitos sonoros neon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = sonsAtivos,
                    onCheckedChange = { ativos ->
                        sonsAtivos = ativos
                        Configuracoes.salvarSonsAtivos(contexto, ativos)
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Efeitos sonoros neon",
                    fontWeight = FontWeight.SemiBold,
                    color = TextoPrimario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toca bips sonoros suaves estilo sci-fi ao tocar nos botões de envio e ao acionar o microfone.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ════════════════════════════════════════════════════
        // CARD 4: PESQUISA NA INTERNET E CHECAGEM GOOGLE
        // ════════════════════════════════════════════════════
        CardSecao(
            icone = "🌐",
            titulo = "4. Pesquisa na Internet e Fatos",
            subtitulo = "Permite ao Blér navegar na web em tempo real para responder sobre notícias recentes e acontecimentos do mundo.",
            corDestaque = NeonAzul
        ) {
            // Destaque explicativo sobre a busca gratuita
            Surface(
                color = Color(0xFF081C15),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1B5E20)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "✓ Pesquisa web gratuita e ilimitada inclusa",
                        color = Color(0xFF69F0AE),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "O Blér já consulta automaticamente fontes abertas (DuckDuckGo, Bing, Wikipedia, SearXNG) sem custos. As chaves abaixo são 100% opcionais para quem quiser motores adicionais.",
                        color = Color(0xFFC8E6C9),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Campo Tavily
            Text(
                text = "Chave da Tavily (Opcional):",
                fontWeight = FontWeight.SemiBold,
                color = TextoPrimario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Buscador especializado para IAs. Fornece resultados mais limpos e rápidos.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            var chaveTavily by remember { mutableStateOf(Configuracoes.obterChaveTavily(contexto)) }
            OutlinedTextField(
                value = chaveTavily,
                onValueChange = { valor ->
                    chaveTavily = valor
                    Configuracoes.salvarChaveTavily(contexto, valor)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cole aqui sua chave (ex: tvly-...)", color = TextoPlaceholder) },
                singleLine = true,
                colors = coresCampoTexto()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Campo Brave Search
            Text(
                text = "Chave da Brave Search (Opcional):",
                fontWeight = FontWeight.SemiBold,
                color = TextoPrimario,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Índice de busca independente da Brave. Aumenta a velocidade e a cobertura de resultados.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            var chaveBrave by remember { mutableStateOf(Configuracoes.obterChaveBrave(contexto)) }
            OutlinedTextField(
                value = chaveBrave,
                onValueChange = { valor ->
                    chaveBrave = valor
                    Configuracoes.salvarChaveBrave(contexto, valor)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cole aqui sua chave (ex: BSA...)", color = TextoPlaceholder) },
                singleLine = true,
                colors = coresCampoTexto()
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Switch Grounding Google
            var confirmacaoGoogle by remember { mutableStateOf(Configuracoes.usarConfirmacaoGoogle(contexto)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = confirmacaoGoogle,
                    onCheckedChange = { ativa ->
                        confirmacaoGoogle = ativa
                        Configuracoes.salvarConfirmacaoGoogle(contexto, ativa)
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confirmar fatos recentes no Google",
                        fontWeight = FontWeight.SemiBold,
                        color = TextoPrimario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Usa sua chave Gemini cadastrada",
                        color = NeonAzul,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Para notícias do dia, placares de futebol ou cotações de moedas, o Blér faz 1 checagem em tempo real no Google antes de responder, evitando dados desatualizados.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ════════════════════════════════════════════════════
        // CARD 5: LEMBRETES, ALARME E GOOGLE AGENDA
        // ════════════════════════════════════════════════════
        CardSecao(
            icone = "⏰",
            titulo = "5. Lembretes, Alarmes e Diagnóstico",
            subtitulo = "Configure a experiência dos seus lembretes e verifique o status das permissões vitais do Android para disparos pontuais no segundo exato.",
            corDestaque = NeonLilas
        ) {
            // Item 1: Tela cheia com gota d'água
            var usarTelaCheia by remember { mutableStateOf(Configuracoes.usarTelaCheiaLembrete(contexto)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = usarTelaCheia,
                    onCheckedChange = { ativa ->
                        usarTelaCheia = ativa
                        Configuracoes.salvarUsarTelaCheiaLembrete(contexto, ativa)
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tela cheia com animação da gota",
                        fontWeight = FontWeight.SemiBold,
                        color = TextoPrimario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (usarTelaCheia) "Acorda o visor sobre a tela de bloqueio" else "Apenas notificação padrão",
                        color = if (usarTelaCheia) VerdeStatus else TextoApoio,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ao tocar o alarme, abre a tela de prioridade máxima com a animação da gota d'água, resumo contextual e menu de adiamento. Se desativado, exibe apenas a notificação padrão do Android.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Item 2: Sincronização opcional com Google Agenda
            var syncAgenda by remember { mutableStateOf(com.meuagente.app.lembretes.SincronizadorGoogleAgenda.estaAtivo(contexto)) }
            val launcherCalendario = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissoes ->
                val concedido = permissoes[android.Manifest.permission.WRITE_CALENDAR] == true
                syncAgenda = concedido
                com.meuagente.app.lembretes.SincronizadorGoogleAgenda.salvarAtivo(contexto, concedido)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = syncAgenda,
                    onCheckedChange = { ativa ->
                        if (ativa) {
                            if (com.meuagente.app.lembretes.SincronizadorGoogleAgenda.temPermissao(contexto)) {
                                syncAgenda = true
                                com.meuagente.app.lembretes.SincronizadorGoogleAgenda.salvarAtivo(contexto, true)
                            } else {
                                launcherCalendario.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            }
                        } else {
                            syncAgenda = false
                            com.meuagente.app.lembretes.SincronizadorGoogleAgenda.salvarAtivo(contexto, false)
                        }
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronizar com o Google Agenda",
                        fontWeight = FontWeight.SemiBold,
                        color = TextoPrimario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (syncAgenda) "Sincronização ativa (cópia no Google Agenda)" else "Desativado (somente no Blér)",
                        color = if (syncAgenda) VerdeStatus else TextoApoio,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cria automaticamente uma cópia de cada lembrete agendado na sua agenda principal do Google no celular, servindo como camada extra de segurança para você nunca esquecer um compromisso.",
                color = TextoApoio,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // ── PAINEL DE DIAGNÓSTICO EM TEMPO REAL DAS PERMISSÕES ──
            Text(
                text = "Diagnóstico e Permissões do Sistema",
                fontWeight = FontWeight.Bold,
                color = NeonAzul,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "O Android exige 3 permissões específicas para tocar alarmes e acordar a tela:",
                color = TextoApoio,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            val lifecycleOwner = LocalLifecycleOwner.current
            var cicloPermissao by remember { mutableStateOf(0) }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        cicloPermissao++
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val launcherNotifConfig = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ ->
                cicloPermissao++
            }

            val temNotificacao = remember(cicloPermissao) {
                com.meuagente.app.lembretes.NotificadorLembrete.temPermissaoNotificacao(contexto)
            }
            val temAlarmeExato = remember(cicloPermissao) {
                com.meuagente.app.lembretes.GerenciadorLembretes.podeAgendarAlarmesExatos(contexto)
            }
            val isentoBateria = remember(cicloPermissao) {
                !com.meuagente.app.lembretes.GerenciadorBateria.precisaPedirIsencao(contexto)
            }

            // Permissão 1: Notificações
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D1C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Notificações",
                            fontWeight = FontWeight.SemiBold,
                            color = TextoPrimario,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (temNotificacao) "✓ Ativo" else "✗ Desativado",
                            color = if (temNotificacao) VerdeStatus else Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (temNotificacao) "Permissão concedida para avisos sonoros" else "Toque ao lado para permitir avisos e alarme",
                        color = TextoApoio,
                        fontSize = 10.sp
                    )
                }
                if (!temNotificacao) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                launcherNotifConfig.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                com.meuagente.app.lembretes.NotificadorLembrete.abrirConfiguracaoNotificacoes(contexto)
                            }
                        },
                        border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAzul),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Autorizar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Permissão 2: Alarmes no Segundo Exato
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D1C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏰", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Alarmes no minuto exato",
                            fontWeight = FontWeight.SemiBold,
                            color = TextoPrimario,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (temAlarmeExato) "✓ Ativo" else "✗ Bloqueado",
                            color = if (temAlarmeExato) VerdeStatus else Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (temAlarmeExato) "Disparos sem agrupamento ou atraso" else "Android pode atrasar o alarme em 15-60m",
                        color = TextoApoio,
                        fontSize = 10.sp
                    )
                }
                if (!temAlarmeExato) {
                    OutlinedButton(
                        onClick = {
                            com.meuagente.app.lembretes.GerenciadorLembretes.abrirConfiguracaoAlarmesExatos(contexto)
                        },
                        border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAzul),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Habilitar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Permissão 3: Otimização de Bateria
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D1C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔋", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Isenção de Bateria",
                            fontWeight = FontWeight.SemiBold,
                            color = TextoPrimario,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isentoBateria) "✓ Isento" else "⚠ Restrito",
                            color = if (isentoBateria) VerdeStatus else Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (isentoBateria) "App não é congelado em modo Doze" else "Celular pode suspender o app em repouso",
                        color = TextoApoio,
                        fontSize = 10.sp
                    )
                }
                if (!isentoBateria) {
                    OutlinedButton(
                        onClick = {
                            com.meuagente.app.lembretes.GerenciadorBateria.abrirConfiguracaoBateria(contexto)
                        },
                        border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAzul),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Isentar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Permissão 4: Sobrepor a outros apps (SYSTEM_ALERT_WINDOW)
            val podeSobrepor = remember(cicloPermissao) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(contexto)
                } else {
                    true
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D1C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🪟", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = "Sobrepor a outros apps",
                            fontWeight = FontWeight.SemiBold,
                            color = TextoPrimario,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (podeSobrepor) "✓ Ativo" else "✗ Desativado",
                            color = if (podeSobrepor) VerdeStatus else Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (podeSobrepor) "Aparece na tela mesmo usando outros apps" else "Aparece apenas na barra de notificações",
                        color = TextoApoio,
                        fontSize = 10.sp
                    )
                }
                if (!podeSobrepor) {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${contexto.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    contexto.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAzul),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Autorizar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // ── SELETOR DE SOM DA ENTREGA COM PRÉ-ESCUTA (PREVIEW) ──
            var tipoSomAtual by remember { mutableStateOf(Configuracoes.obterTipoSomLembrete(contexto)) }
            var nomeSomPersonalizado by remember { mutableStateOf(Configuracoes.obterNomeSomLembrete(contexto)) }
            var tocandoPreview by remember { mutableStateOf(false) }
            var ringtonePreview by remember { mutableStateOf<android.media.Ringtone?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    ringtonePreview?.stop()
                }
            }

            fun alternarPreview() {
                if (tocandoPreview) {
                    ringtonePreview?.stop()
                    ringtonePreview = null
                    tocandoPreview = false
                } else {
                    val uri = Configuracoes.obterUriSomLembrete(contexto)
                    try {
                        ringtonePreview = android.media.RingtoneManager.getRingtone(contexto, uri).apply {
                            play()
                        }
                        tocandoPreview = true
                    } catch (_: Exception) {}
                }
            }

            val launcherEscolherSom = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    }
                    if (uri != null) {
                        val ringtone = android.media.RingtoneManager.getRingtone(contexto, uri)
                        val titulo = ringtone?.getTitle(contexto) ?: "Toque Escolhido"
                        tipoSomAtual = "personalizado"
                        nomeSomPersonalizado = titulo
                        Configuracoes.salvarSomLembrete(contexto, "personalizado", uri.toString(), titulo)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Som do Alarme e Notificação",
                    fontWeight = FontWeight.Bold,
                    color = NeonAzul,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { alternarPreview() },
                    border = BorderStroke(1.dp, if (tocandoPreview) Color(0xFFFF5252) else NeonAzul.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (tocandoPreview) Color(0xFFFF5252) else NeonAzul),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (tocandoPreview) "⏹ Parar" else "▶ Ouvir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Escolha qual som tocará na tela do alarme e nas notificações:",
                color = TextoApoio,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val opcoesSons = listOf(
                "alarme_padrao" to "⏰ Alarme do Relógio",
                "notificacao" to "🔔 Notificação Suave",
                "toque_chamada" to "📞 Toque de Chamada",
                "personalizado" to if (nomeSomPersonalizado.isNotBlank()) "🎵 $nomeSomPersonalizado" else "🎵 Escolher do Celular..."
            )

            opcoesSons.forEach { (tipo, rotulo) ->
                val selecionado = tipoSomAtual == tipo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (tocandoPreview) {
                                ringtonePreview?.stop()
                                tocandoPreview = false
                            }
                            if (tipo == "personalizado") {
                                val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                launcherEscolherSom.launch(intent)
                            } else {
                                tipoSomAtual = tipo
                                Configuracoes.salvarSomLembrete(contexto, tipo)
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = selecionado,
                        onClick = {
                            if (tocandoPreview) {
                                ringtonePreview?.stop()
                                tocandoPreview = false
                            }
                            if (tipo == "personalizado") {
                                val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                launcherEscolherSom.launch(intent)
                            } else {
                                tipoSomAtual = tipo
                                Configuracoes.salvarSomLembrete(contexto, tipo)
                            }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NeonAzul, unselectedColor = TextoApoio)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rotulo,
                        color = if (selecionado) Color.White else TextoApoio,
                        fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // ── BOTÃO DE TESTE RÁPIDO EM 10 SEGUNDOS ──
            var agendandoTeste by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (!agendandoTeste) {
                        agendandoTeste = true
                        com.meuagente.app.lembretes.GerenciadorLembretes.agendarTeste10Segundos(contexto) { _ ->
                            agendandoTeste = false
                            Toast.makeText(
                                contexto,
                                "⏰ Alarme de teste agendado para daqui a 10s! Pode trocar de app ou bloquear.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF131E3A),
                    contentColor = NeonAzul
                ),
                border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.7f)),
                enabled = !agendandoTeste
            ) {
                Text(
                    text = if (agendandoTeste) "⏳ Agendando teste..." else "⚡ Testar Alarme em 10 Segundos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dispara um lembrete teste daqui a 10 segundos. Toque no botão e vá para outro aplicativo (ex: WhatsApp/YouTube) ou bloqueie a tela para ver o Orbe Quântico neon surgir por cima de tudo!",
                color = TextoApoio,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        // ════════════════════════════════════════════════════
        // BOTÃO INFERIOR: VOLTAR PARA O CHAT
        // ════════════════════════════════════════════════════
        Button(
            onClick = aoVoltar,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonAzul,
                contentColor = Color(0xFF04060B)
            )
        ) {
            Text(
                text = "✓ Voltar para o chat",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Linha neon na base ──
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(NeonRosa, NeonLilas, NeonAzul)),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1F)),
        border = BorderStroke(1.dp, if (ativo) Color(0xFF2E3D6B) else Color(0xFF1B233D))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Linha superior: Switch, Nome do Provedor, Campo de Prioridade, Botão Remover
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Switch(
                    checked = ativo,
                    onCheckedChange = { valor ->
                        ativo = valor
                        Configuracoes.salvarProvedorAtivoNaCascata(contexto, nomeProvedor, valor)
                    },
                    colors = coresInterruptor()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nomeProvedor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (ativo) Color.White else TextoDesativado
                    )
                    Text(
                        text = if (ativo) "Ativo na cascata" else "Pausado (ignorado)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ativo) VerdeStatus else TextoDesativado,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ordem:",
                            color = TextoApoio,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = prioridadeTexto,
                            onValueChange = { valor ->
                                prioridadeTexto = valor.filter { it.isDigit() }.take(2)
                                prioridadeTexto.toIntOrNull()?.let {
                                    Configuracoes.salvarPrioridadeProvedor(contexto, nomeProvedor, it)
                                }
                            },
                            enabled = ativo,
                            modifier = Modifier.width(56.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            placeholder = { Text("1", color = TextoPlaceholder) },
                            singleLine = true,
                            colors = coresCampoTexto()
                        )
                    }
                    Text(
                        text = "(1 tenta 1º)",
                        color = TextoApoio,
                        fontSize = 10.sp
                    )
                }
                IconButton(onClick = aoRemover) {
                    Text("✕", color = NeonRosa, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Campo de chave de API
            Text(
                text = "Chave de API do $nomeProvedor:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (ativo) Color(0xFFF1F5F9) else TextoDesativado
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sua chave de acesso privada (armazenada apenas no seu celular).",
                style = MaterialTheme.typography.bodySmall,
                color = TextoApoio,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = chave,
                onValueChange = { valor ->
                    chave = valor
                    Configuracoes.salvarChaveDoProvedor(contexto, nomeProvedor, valor)
                },
                enabled = ativo,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cole a chave do $nomeProvedor...", color = TextoPlaceholder) },
                singleLine = true,
                colors = coresCampoTexto()
            )

            if (ativo) {
                val escopo = rememberCoroutineScope()
                var modelosOpenRouter by remember {
                    mutableStateOf(
                        if (nomeProvedor == "OpenRouter") RepositorioModelosOpenRouter.lerCacheLocal(contexto) else emptyList()
                    )
                }
                var carregandoModelos by remember { mutableStateOf(false) }

                LaunchedEffect(nomeProvedor) {
                    if (nomeProvedor == "OpenRouter" && RepositorioModelosOpenRouter.precisaAtualizar(contexto)) {
                        carregandoModelos = true
                        modelosOpenRouter = RepositorioModelosOpenRouter.buscarAoVivo(contexto)
                        carregandoModelos = false
                    }
                }

                val aoAtualizarModelos: (() -> Unit)? = if (nomeProvedor == "OpenRouter") {
                    {
                        escopo.launch {
                            carregandoModelos = true
                            modelosOpenRouter = RepositorioModelosOpenRouter.buscarAoVivo(contexto)
                            carregandoModelos = false
                        }
                    }
                } else null

                val sugestoes: List<SugestaoModelo> = if (nomeProvedor == "OpenRouter") {
                    modelosOpenRouter.map { SugestaoModelo(it.id, it.nome, it.ehGratuito) }
                } else {
                    MODELOS_POR_PROVEDOR[nomeProvedor] ?: emptyList()
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modelos a consultar:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9)
                        )
                        Text(
                            text = "Ordem de tentativa se a anterior der erro ou limite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextoApoio,
                            fontSize = 11.sp
                        )
                    }
                    if (nomeProvedor == "OpenRouter") {
                        TextButton(
                            onClick = { aoAtualizarModelos?.invoke() },
                            enabled = !carregandoModelos
                        ) {
                            Text(
                                text = if (carregandoModelos) "Atualizando..." else "↻ Atualizar da web",
                                color = NeonAzul,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                SeletorModelosIA(
                    sugestoes = sugestoes,
                    selecionados = modelosSelecionados,
                    onChange = { novaLista ->
                        modelosSelecionados = novaLista
                        Configuracoes.salvarModelosProvedor(contexto, nomeProvedor, novaLista)
                    },
                    carregando = carregandoModelos,
                    aoAtualizar = aoAtualizarModelos
                )
            }
        }
    }
}

@Composable
private fun AdicionarProvedorBotao(
    disponiveis: List<String>,
    aoAdicionar: (String) -> Unit
) {
    var menuAberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { menuAberto = true },
            border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.8f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAzul)
        ) {
            Text(
                text = "+ Adicionar provedor",
                fontWeight = FontWeight.Bold,
                color = NeonAzul
            )
        }
        DropdownMenu(
            expanded = menuAberto,
            onDismissRequest = { menuAberto = false },
            modifier = Modifier.background(Color(0xFF0D1224))
        ) {
            disponiveis.forEach { provedor ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = provedor,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
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
 * filtra as sugestões em tempo real SEM fechar o teclado; dá para marcar mais de um modelo
 * e também adicionar um nome que não está na lista. Os selecionados
 * viram chips numerados (ordem de tentativa) com toque para remover.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SeletorModelosIA(
    sugestoes: List<SugestaoModelo>,
    selecionados: List<String>,
    onChange: (List<String>) -> Unit,
    carregando: Boolean = false,
    aoAtualizar: (() -> Unit)? = null
) {
    var consulta by remember { mutableStateOf("") }
    var menuAberto by remember { mutableStateOf(false) }

    val texto = consulta.trim()
    val filtradas = sugestoes.filter { item ->
        (item.id.contains(texto, ignoreCase = true) || item.nomeExibicao.contains(texto, ignoreCase = true)) &&
            !selecionados.contains(item.id)
    }
    val podeAdicionarManual = texto.isNotBlank() &&
        !selecionados.contains(texto) &&
        sugestoes.none { it.id.equals(texto, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = consulta,
            onValueChange = {
                consulta = it
                menuAberto = true
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar ou selecionar modelo...", color = TextoPlaceholder) },
            singleLine = true,
            colors = coresCampoTexto(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (carregando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NeonAzul
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (aoAtualizar != null) {
                        IconButton(
                            onClick = aoAtualizar,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("↻", color = NeonAzul, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "${selecionados.size} ativo(s)",
                        color = if (selecionados.isNotEmpty()) NeonAzul else TextoApoio,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        )
        DropdownMenu(
            expanded = menuAberto && (filtradas.isNotEmpty() || podeAdicionarManual || (texto.isNotBlank() && filtradas.isEmpty())),
            onDismissRequest = { menuAberto = false },
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 280.dp)
                .background(Color(0xFF0D1224))
        ) {
            if (podeAdicionarManual) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "+ Adicionar \"$texto\"",
                            color = NeonAzul,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        onChange(selecionados + texto)
                        consulta = ""
                        menuAberto = false
                    }
                )
            }
            val visiveis = filtradas.take(40)
            visiveis.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFF8FAFC),
                                    fontWeight = FontWeight.Medium
                                )
                                if (item.nomeExibicao.isNotBlank() && item.nomeExibicao != item.id) {
                                    Text(
                                        text = item.nomeExibicao,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextoExplicativo,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (item.ehGratuito) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF1B5E20),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "GRÁTIS",
                                        color = Color(0xFF69F0AE),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        onChange(selecionados + item.id)
                        consulta = ""
                        menuAberto = false
                    }
                )
            }
            if (filtradas.size > 40) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Mais ${filtradas.size - 40} modelos... Digite para filtrar",
                            color = TextoApoio,
                            fontSize = 12.sp
                        )
                    },
                    onClick = { }
                )
            }
            if (filtradas.isEmpty() && !podeAdicionarManual && texto.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Nenhum modelo encontrado", color = TextoApoio) },
                    onClick = { menuAberto = false }
                )
            }
        }
    }

    if (selecionados.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ordem de tentativa (toque em um modelo para remover):",
            color = TextoExplicativo,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            selecionados.forEachIndexed { indice, modelo ->
                FilterChip(
                    selected = true,
                    onClick = { onChange(selecionados.filterIndexed { i, _ -> i != indice }) },
                    label = {
                        Text(
                            text = "${indice + 1}º $modelo ✕",
                            color = Color(0xFFF8FAFC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF182245)
                    ),
                    border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.7f))
                )
            }
        }
    }
}
