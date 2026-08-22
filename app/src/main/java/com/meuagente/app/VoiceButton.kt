import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════
// CORES NEON DO PROJETO BLÉR
// ═══════════════════════════════════════════════════════════════════
val AzulNeon = Color(0xFF00D4FF)
val LilasNeon = Color(0xFFB44DFF)
val RosaNeon = Color(0xFFFF4D94)
val CinzaChumboFundo = Color(0xFF2A2A35)

// ═══════════════════════════════════════════════════════════════════
// ESTADOS DO BOTÃO DE VOZ
// ═══════════════════════════════════════════════════════════════════
enum class EstadoGravacao {
    INATIVO,       // Microfone normal, sem nada acontecendo
    GRAVANDO,      // Gravando áudio (pulsa + muda cor)
    TRANSCREVENDO, // Enviando áudio para IA processar (loading)
    PRONTO         // Texto transcrito pronto para revisão/envio
}

// ═══════════════════════════════════════════════════════════════════
// COMPONENTE VOICE BUTTON
// ═══════════════════════════════════════════════════════════════════
@Composable
fun VoiceButton(
    estado: EstadoGravacao,
    onIniciar: () -> Unit,
    onStop: () -> Unit,
    onEnviar: () -> Unit,
    textoTranscrito: String,
    onTextoChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Animação de pulsação (escala) ──
    val pulseValue by animateFloatAsState(
        targetValue = if (estado == EstadoGravacao.GRAVANDO) 1.08f else 1.0f,
        animationSpec = if (estado == EstadoGravacao.GRAVANDO)
            repeatable(
                repetition = InfiniteRepetition(1, AnimationSpec.Default),
                animation = {
                    easeInOut(durationMillis = 600) {
                        progress * 2f - 1f
                    }
                }
            ) else tween(300),
        label = "pulse"
    )

    // ── Alpha do glow externo ──
    val glowAlpha by animateFloatAsState(
        targetValue = when (estado) {
            EstadoGravacao.GRAVANDO -> 0.7f
            EstadoGravacao.TRANSCREVENDO -> 0.5f
            else -> 0.3f
        },
        animationSpec = tween(300),
        label = "glowAlpha"
    )

    // ── Cor principal (muda conforme estado) ──
    val corPrincipal by animateColorAsState(
        targetValue = when (estado) {
            EstadoGravacao.GRAVANDO -> LilasNeon
            EstadoGravacao.TRANSCREVENDO -> RosaNeon
            else -> AzulNeon
        },
        animationSpec = tween(500),
        label = "corPrincipal"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ── Campo de texto transcrito (apenas quando PRONTO) ──
        if (estado == EstadoGravacao.PRONTO) {
            OutlinedTextField(
                value = textoTranscrito,
                onValueChange = onTextoChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text("Texto transcrito...") },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AzulNeon,
                    cursorColor = AzulNeon,
                    focusedContainerColor = CinzaChumboFundo
                )
            )
        }

        // ── Linha com botões (enviar + microfone) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Botão de enviar (apenas quando PRONTO)
            if (estado == EstadoGravacao.PRONTO) {
                IconButton(
                    onClick = onEnviar,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("➤", color = AzulNeon, fontSize = 22.sp)
                }
            }

            // ── Botão principal de voz (neon com glow) ──
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = pulseValue
                        scaleY = pulseValue
                    }
                    .drawBehind {
                        // Glow externo (círculo colorido ao redor)
                        val radius = size.minDimension / 2f + 6.dp.toPx()
                        drawCircle(
                            color = corPrincipal.copy(alpha = glowAlpha),
                            radius = radius,
                            center = center
                        )
                    }
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = when (estado) {
                                EstadoGravacao.GRAVANDO -> listOf(LilasNeon, AzulNeon)
                                EstadoGravacao.TRANSCREVENDO -> listOf(RosaNeon, LilasNeon)
                                EstadoGravacao.PRONTO -> listOf(AzulNeon.copy(alpha = 0.9f), CinzaChumboFundo)
                                else -> listOf(AzulNeon.copy(alpha = 0.8f), CinzaChumboFundo)
                            },
                            center = Center
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = corPrincipal,
                        shape = CircleShape
                    )
                    .clickable {
                        when (estado) {
                            EstadoGravacao.INATIVO -> onIniciar()
                            EstadoGravacao.GRAVANDO -> onStop()
                            EstadoGravacao.TRANSCREVENDO -> {} // Não pode parar transcrição
                            EstadoGravacao.PRONTO -> {} // Não pode tocar quando pronto
                        }
                    }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                // ── Ícone conforme estado ──
                when (estado) {
                    EstadoGravacao.INATIVO -> {
                        Text("🎤", color = Color.White, fontSize = 22.sp)
                    }
                    EstadoGravacao.GRAVANDO -> {
                        Text("⏹", color = Color.White, fontSize = 18.sp)
                    }
                    EstadoGravacao.TRANSCREVENDO -> {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = RosaNeon,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    EstadoGravacao.PRONTO -> {
                        Text("🎤", color = Color.White, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
```
