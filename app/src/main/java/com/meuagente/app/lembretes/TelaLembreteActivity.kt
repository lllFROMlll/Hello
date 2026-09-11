package com.meuagente.app.lembretes

import android.app.KeyguardManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meuagente.app.AgenteDatabase
import com.meuagente.app.Configuracoes
import com.meuagente.app.LembreteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Paleta Neon do Projeto Blér
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)

class TelaLembreteActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuração de prioridade máxima sobre tela de bloqueio
        configurarTelaBloqueio()

        val lembreteId = intent.getIntExtra(NotificadorLembrete.EXTRA_LEMBRETE_ID, -1)

        // Toca o som configurado pelo usuário e vibra
        iniciarAlarmeSonoro()

        setContent {
            TelaLembreteConteudo(
                lembreteId = lembreteId,
                aoConcluir = {
                    pararAlarmeSonoro()
                    GerenciadorLembretes.marcarConcluido(this, lembreteId) {
                        finishAndRemoveTask()
                    }
                },
                aoAdiarMillis = { novoHorarioMillis ->
                    pararAlarmeSonoro()
                    GerenciadorLembretes.adiar(this, lembreteId, novoHorarioMillis) {
                        finishAndRemoveTask()
                    }
                },
                aoDispensar = {
                    pararAlarmeSonoro()
                    finishAndRemoveTask()
                }
            )
        }
    }

    private fun configurarTelaBloqueio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun iniciarAlarmeSonoro() {
        try {
            val uriAlarme = Configuracoes.obterUriSomLembrete(applicationContext)
            ringtone = RingtoneManager.getRingtone(applicationContext, uriAlarme).apply {
                play()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 300, 500, 300, 700),
                        0 // repete em loop
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 300, 500, 300, 700), 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun pararAlarmeSonoro() {
        try {
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pararAlarmeSonoro()
    }
}

@Composable
private fun TelaLembreteConteudo(
    lembreteId: Int,
    aoConcluir: () -> Unit,
    aoAdiarMillis: (Long) -> Unit,
    aoDispensar: () -> Unit
) {
    val contexto = LocalContext.current
    var lembrete by remember { mutableStateOf<LembreteEntity?>(null) }
    var contextoIa by remember { mutableStateOf<String?>(null) }
    var carregandoContexto by remember { mutableStateOf(true) }

    // Carrega o lembrete do banco e busca o contexto em segundo plano
    LaunchedEffect(lembreteId) {
        withContext(Dispatchers.IO) {
            val db = AgenteDatabase.obter(contexto)
            val registro = db.agenteDao().buscarLembretePorId(lembreteId)
            lembrete = registro
            contextoIa = registro?.contexto

            if (registro != null) {
                val resumoIa = GeradorContextoLembrete.buscarResumoContextual(contexto, registro)
                if (!resumoIa.isNullOrBlank()) {
                    contextoIa = resumoIa
                }
                carregandoContexto = false
            }
        }
    }

    // ── Animação: Orbe Quântico Holográfico ──
    val transicaoInfinita = rememberInfiniteTransition(label = "OrbeQuantico")
    val pulsoOrbe = transicaoInfinita.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulsoOrbe"
    )
    val rotacaoAngulo = transicaoInfinita.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotacaoParticulas"
    )

    // Efeito de ignição inicial e onda de choque expansiva
    val escalaOnda = remember { Animatable(0.2f) }
    val opacidadeOnda = remember { Animatable(1f) }
    val escalaEntrada = remember { Animatable(0f) }
    var mostrarConteudo by remember { mutableStateOf(false) }

    // Estados dos diálogos de adiamento
    var mostrarMenuAdiar by remember { mutableStateOf(false) }
    var mostrarRelogioDigital by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            escalaEntrada.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            escalaOnda.animateTo(
                targetValue = 4.0f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
        }
        launch {
            opacidadeOnda.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
            )
        }

        delay(350)
        mostrarConteudo = true
    }

    BackHandler { aoDispensar() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03060E))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Botão discreto para fechar/dispensar o alarme
        IconButton(
            onClick = aoDispensar,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 4.dp)
        ) {
            Text("✕", color = Color(0xFF94A3B8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // ── Canvas: Orbe Quântico Holográfico ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centroX = size.width / 2f
            val centroY = size.height * 0.28f

            // 1. Onda de choque expansiva inicial
            if (opacidadeOnda.value > 0f) {
                val raioOnda = 80.dp.toPx() * escalaOnda.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonAzul.copy(alpha = 0.6f * opacidadeOnda.value),
                            NeonLilas.copy(alpha = 0.3f * opacidadeOnda.value),
                            Color.Transparent
                        ),
                        center = Offset(centroX, centroY),
                        radius = raioOnda.coerceAtLeast(1f)
                    ),
                    center = Offset(centroX, centroY),
                    radius = raioOnda
                )
                drawCircle(
                    color = NeonAzul.copy(alpha = 0.8f * opacidadeOnda.value),
                    center = Offset(centroX, centroY),
                    radius = raioOnda * 0.85f,
                    style = Stroke(width = 3.dp.toPx() * opacidadeOnda.value)
                )
                drawCircle(
                    color = NeonRosa.copy(alpha = 0.6f * opacidadeOnda.value),
                    center = Offset(centroX, centroY),
                    radius = raioOnda * 0.5f,
                    style = Stroke(width = 2.dp.toPx() * opacidadeOnda.value)
                )
            }

            // 2. Halo difuso do núcleo quântico
            val raioOrbBase = 52.dp.toPx() * pulsoOrbe.value * escalaEntrada.value
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonAzul.copy(alpha = 0.35f),
                        NeonLilas.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(centroX, centroY),
                    radius = (raioOrbBase * 2.6f).coerceAtLeast(1f)
                ),
                center = Offset(centroX, centroY),
                radius = raioOrbBase * 2.6f
            )

            // 3. Anéis orbitais finos
            drawCircle(
                color = NeonAzul.copy(alpha = 0.3f),
                center = Offset(centroX, centroY),
                radius = raioOrbBase * 1.5f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = NeonLilas.copy(alpha = 0.25f),
                center = Offset(centroX, centroY),
                radius = raioOrbBase * 1.9f,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 4. Partículas quânticas em órbita
            val rad1 = Math.toRadians(rotacaoAngulo.value.toDouble())
            val p1X = centroX + (raioOrbBase * 1.5f) * cos(rad1).toFloat()
            val p1Y = centroY + (raioOrbBase * 1.5f) * sin(rad1).toFloat()
            drawCircle(color = NeonAzul, radius = 5.dp.toPx(), center = Offset(p1X, p1Y))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(p1X, p1Y))

            val rad2 = Math.toRadians((rotacaoAngulo.value * 1.4 + 120.0))
            val p2X = centroX + (raioOrbBase * 1.9f) * cos(rad2).toFloat()
            val p2Y = centroY + (raioOrbBase * 1.9f) * sin(rad2).toFloat()
            drawCircle(color = NeonRosa, radius = 4.5.dp.toPx(), center = Offset(p2X, p2Y))
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(p2X, p2Y))

            val rad3 = Math.toRadians((-rotacaoAngulo.value * 0.8 + 240.0))
            val p3X = centroX + (raioOrbBase * 1.3f) * cos(rad3).toFloat()
            val p3Y = centroY + (raioOrbBase * 1.3f) * sin(rad3).toFloat()
            drawCircle(color = NeonLilas, radius = 4.dp.toPx(), center = Offset(p3X, p3Y))

            val rad4 = Math.toRadians((rotacaoAngulo.value * 2.0 + 45.0))
            val p4X = centroX + (raioOrbBase * 1.7f) * cos(rad4).toFloat()
            val p4Y = centroY + (raioOrbBase * 1.7f) * sin(rad4).toFloat()
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(p4X, p4Y))

            // 5. Núcleo central holográfico
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        NeonAzul,
                        NeonLilas,
                        NeonRosa.copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(centroX, centroY),
                    radius = raioOrbBase.coerceAtLeast(1f)
                ),
                center = Offset(centroX, centroY),
                radius = raioOrbBase
            )

            // Ponto de luz interno super brilhante
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                center = Offset(centroX, centroY),
                radius = raioOrbBase * 0.35f
            )
        }

        // ── Conteúdo do Lembrete Revelado ──
        AnimatedVisibility(
            visible = mostrarConteudo,
            enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.85f),
            modifier = Modifier.padding(top = 100.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Emblema holográfico
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0C1226).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "⚡ LEMBRETE BLÉR",
                        color = NeonAzul,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Título principal do Lembrete
                Text(
                    text = lembrete?.descricao ?: "Lembrete Agendado",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )

                // Horário agendado
                lembrete?.dataHoraAgendada?.let { millis ->
                    Spacer(modifier = Modifier.height(6.dp))
                    val dataFormatada = remember(millis) {
                        SimpleDateFormat("HH:mm · EEEE, dd 'de' MMMM", Locale.getDefault()).format(Date(millis))
                    }
                    Text(
                        text = dataFormatada.replaceFirstChar { it.uppercase() },
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }

                // Card de Contexto Inteligente da IA
                if (!contextoIa.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D142C)),
                        border = BorderStroke(1.dp, Color(0xFF263560)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                Text(
                                    text = "Apoio contextual da conversa",
                                    color = NeonLilas,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = contextoIa.orEmpty(),
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ── Botões de Ação ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botão Adiar (Abre menu completo)
                    OutlinedButton(
                        onClick = { mostrarMenuAdiar = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, NeonLilas),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonLilas)
                    ) {
                        Text(
                            text = "⏱ Adiar...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Botão Concluir
                    Button(
                        onClick = aoConcluir,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAzul,
                            contentColor = Color(0xFF03060E)
                        )
                    ) {
                        Text(
                            text = "✓ Concluir",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════
        // DIÁLOGO: MENU DE ADIAMENTO
        // ════════════════════════════════════════════════════
        if (mostrarMenuAdiar) {
            DialogoMenuAdiar(
                aoEscolherMillis = { novoMillis ->
                    mostrarMenuAdiar = false
                    aoAdiarMillis(novoMillis)
                },
                aoAbrirRelogioDigital = {
                    mostrarMenuAdiar = false
                    mostrarRelogioDigital = true
                },
                aoFechar = { mostrarMenuAdiar = false }
            )
        }

        // ════════════════════════════════════════════════════
        // DIÁLOGO: RELÓGIO DIGITAL NEON (SUBSTITUI RELÓGIO REDONDO)
        // ════════════════════════════════════════════════════
        if (mostrarRelogioDigital) {
            DialogoRelogioDigitalNeon(
                aoConfirmarMillis = { novoMillis ->
                    mostrarRelogioDigital = false
                    aoAdiarMillis(novoMillis)
                },
                aoFechar = { mostrarRelogioDigital = false }
            )
        }
    }
}

/**
 * Menu com opções rápidas de adiamento (10 min, 30 min, 1h, 2h)
 * e acesso direto ao Relógio Digital Neon para personalização.
 */
@Composable
private fun DialogoMenuAdiar(
    aoEscolherMillis: (Long) -> Unit,
    aoAbrirRelogioDigital: () -> Unit,
    aoFechar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = aoFechar,
        containerColor = Color(0xFF0D1428),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏱", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = "Adiar lembrete",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Escolha para quando deseja adiar:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Atalhos rápidos em Grade 2x2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemAtalhoAdiar("10 minutos", Modifier.weight(1f)) {
                        aoEscolherMillis(System.currentTimeMillis() + 10 * 60 * 1000)
                    }
                    ItemAtalhoAdiar("30 minutos", Modifier.weight(1f)) {
                        aoEscolherMillis(System.currentTimeMillis() + 30 * 60 * 1000)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemAtalhoAdiar("1 hora", Modifier.weight(1f)) {
                        aoEscolherMillis(System.currentTimeMillis() + 60 * 60 * 1000)
                    }
                    ItemAtalhoAdiar("2 horas", Modifier.weight(1f)) {
                        aoEscolherMillis(System.currentTimeMillis() + 120 * 60 * 1000)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF1E284A), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Opção Personalizada: Relógio Digital Temático
                Surface(
                    onClick = aoAbrirRelogioDigital,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131D36),
                    border = BorderStroke(1.dp, NeonAzul.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("⏰", fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ajustar no Relógio Digital",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Escolha hora, minuto e dia em formato digital",
                                color = NeonAzul,
                                fontSize = 11.sp
                            )
                        }
                        Text("➔", color = NeonAzul, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = aoFechar) {
                Text("Cancelar", color = Color(0xFF94A3B8))
            }
        }
    )
}

/**
 * Diálogo com Relógio Digital Temático Neon
 * Permite selecionar dia (Hoje, Amanhã, +2 Dias, +3 Dias) e horas/minutos digitais com steppers ▲/▼
 * e atalhos rápidos (+10m, +15m, +30m, +1h).
 */
@Composable
private fun DialogoRelogioDigitalNeon(
    aoConfirmarMillis: (Long) -> Unit,
    aoFechar: () -> Unit
) {
    val inicial = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }

    var diasAdicionais by remember { mutableStateOf(0) }
    var hora by remember { mutableStateOf(inicial.get(Calendar.HOUR_OF_DAY)) }
    var minuto by remember { mutableStateOf(inicial.get(Calendar.MINUTE)) }

    // Timestamp resultante
    val calEscolhido = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, diasAdicionais)
        set(Calendar.HOUR_OF_DAY, hora)
        set(Calendar.MINUTE, minuto)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val millisEscolhido = calEscolhido.timeInMillis
    val horarioNoPassado = diasAdicionais == 0 && millisEscolhido <= System.currentTimeMillis()

    Dialog(
        onDismissRequest = aoFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0A0F1E),
            border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(NeonAzul, NeonLilas)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabeçalho
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⏰", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(
                            text = "Relógio Digital Blér",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Ajuste preciso do novo horário",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seletor de Dia
                Text(
                    text = "SELECIONE O DIA",
                    color = NeonAzul,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val diasOpcoes = listOf("Hoje" to 0, "Amanhã" to 1, "+2 Dias" to 2, "+3 Dias" to 3)
                    diasOpcoes.forEach { (rotulo, offsetDia) ->
                        val selecionado = diasAdicionais == offsetDia
                        Surface(
                            onClick = { diasAdicionais = offsetDia },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selecionado) NeonLilas.copy(alpha = 0.25f) else Color(0xFF131D36),
                            border = BorderStroke(
                                1.dp,
                                if (selecionado) NeonLilas else Color(0xFF263760)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = rotulo,
                                    color = if (selecionado) NeonLilas else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Display Digital e Steppers (HH : MM)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Coluna Hora
                    ColunaDigitoNeon(
                        valor = hora,
                        corDestaque = NeonAzul,
                        aoIncrementar = { hora = (hora + 1) % 24 },
                        aoDecrementar = { hora = if (hora == 0) 23 else hora - 1 }
                    )

                    // Dois pontos estáticos/neon
                    Text(
                        text = ":",
                        color = NeonLilas,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    // Coluna Minuto
                    ColunaDigitoNeon(
                        valor = minuto,
                        corDestaque = NeonLilas,
                        aoIncrementar = { minuto = (minuto + 1) % 60 },
                        aoDecrementar = { minuto = if (minuto == 0) 59 else minuto - 1 }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chips de incremento rápido (+10m, +15m, +30m, +1h)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val increments = listOf(
                        "+10m" to 10,
                        "+15m" to 15,
                        "+30m" to 30,
                        "+1h" to 60
                    )
                    increments.forEach { (rotulo, minutos) ->
                        Surface(
                            onClick = {
                                val total = hora * 60 + minuto + minutos
                                hora = (total / 60) % 24
                                minuto = total % 60
                                if (total >= 24 * 60 && diasAdicionais == 0) {
                                    diasAdicionais = 1
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF131D36),
                            border = BorderStroke(1.dp, Color(0xFF263760)),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = rotulo,
                                    color = NeonAzul,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Resumo do horário calculado
                val formatoPreview = SimpleDateFormat("EEEE, dd/MM 'às' HH:mm", Locale.getDefault())
                val textoPreview = formatoPreview.format(Date(millisEscolhido)).replaceFirstChar { it.uppercase() }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (horarioNoPassado) Color(0xFF2A1215) else Color(0xFF101935),
                    border = BorderStroke(1.dp, if (horarioNoPassado) Color(0xFFFF5252) else Color(0xFF263760)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (horarioNoPassado)
                                "⚠️ Horário já passou hoje. Ajuste a hora ou selecione Amanhã."
                            else
                                "Reagendar para: $textoPreview",
                            color = if (horarioNoPassado) Color(0xFFFF8A80) else Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botões Cancelar e Confirmar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = aoFechar,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text("Cancelar", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (!horarioNoPassado) {
                                aoConfirmarMillis(millisEscolhido)
                            }
                        },
                        enabled = !horarioNoPassado,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAzul,
                            contentColor = Color(0xFF03060E),
                            disabledContainerColor = Color(0xFF1E293B),
                            disabledContentColor = Color(0xFF64748B)
                        )
                    ) {
                        Text("✓ Confirmar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColunaDigitoNeon(
    valor: Int,
    corDestaque: Color,
    aoIncrementar: () -> Unit,
    aoDecrementar: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = aoIncrementar, modifier = Modifier.size(36.dp)) {
            Text("▲", color = corDestaque, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(68.dp)
                .background(Color(0xFF111A35), RoundedCornerShape(12.dp))
                .border(1.5.dp, corDestaque, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%02d", valor),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = aoDecrementar, modifier = Modifier.size(36.dp)) {
            Text("▼", color = corDestaque, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ItemAtalhoAdiar(
    rotulo: String,
    modifier: Modifier = Modifier,
    aoClicar: () -> Unit
) {
    Surface(
        onClick = aoClicar,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131D38),
        border = BorderStroke(1.dp, Color(0xFF263760)),
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rotulo,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}
