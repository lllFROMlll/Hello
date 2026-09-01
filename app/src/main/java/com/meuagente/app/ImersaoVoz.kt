package com.meuagente.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cores neon vivas da imersão de voz
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)
private val FundoChumbo = Color(0xFF12141A)

/**
 * Tela de IMERSÃO total do comando de voz.
 *
 * - Fica sobre tudo (rodapé some) com fundo escuro.
 * - Mostra um GLOBO pulsante com cor neon vibrante.
 * - As ondas reagem à intensidade da voz em tempo real.
 * - Mostra a transcrição parcial ao vivo (nativo flui; IA mostra "escutando...").
 * - Toque no globo = parar gravação (com som de despedida).
 */
@Composable
fun ImersaoVoz(
    intensidade: Float,
    textoParcial: String,
    caminhoAtivo: String,
    onParar: () -> Unit
) {
    val contexto = LocalContext.current
    val transicao = rememberInfiniteTransition(label = "globo")

    // Pulsação lenta do globo para dar a sensação de "vivo".
    val pulso by transicao.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulso_globo"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = FundoChumbo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Globo pulsante neon ──
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        scaleX = pulso
                        scaleY = pulso
                    }
                    .shadow(40.dp, CircleShape, clip = false)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(NeonAzul, NeonLilas, NeonRosa)
                        ),
                        shape = CircleShape
                    )
                    .drawBehind {
                        val raio = size.minDimension / 2f + 18.dp.toPx()
                        drawCircle(
                            color = NeonAzul.copy(alpha = 0.35f),
                            radius = raio,
                            center = center
                        )
                    }
                    .clickable {
                        FxSons.despedir(contexto)
                        onParar()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Ícone de parar dentro do globo
                Text("▪", color = Color.White, fontSize = 56.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ── Onda reativa à intensidade da voz ──
            OndaNeonVoz(intensidade = intensidade)
            Spacer(Modifier.height(16.dp))

            // ── Canal ativo (IA / Nativo) ──
            Text(
                text = "Escutando via $caminhoAtivo",
                color = NeonLilas,
                fontSize = 14.sp,
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(Modifier.height(12.dp))

            // ── Transcrição ao vivo ──
            Text(
                text = textoParcial.ifBlank { "..." },
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 5,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Toque no globo para parar",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}