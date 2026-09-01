package com.meuagente.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

// Neon fluorescente para a interface cinza-chumbo:
// azul como cor principal, com lilás e toques de rosa.
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)
private val FundoChumbo = Color(0xFF1A1D22)

/**
 * Onda/pulso animado que reage à intensidade do microfone em tempo real.
 */
@Composable
fun OndaNeonVoz(
    intensidade: Float,
    modifier: Modifier = Modifier
) {
    val transicao = rememberInfiniteTransition(label = "onda_neon")
    val fase by transicao.animateFloat(
        initialValue = 0f,
        targetValue = Math.PI.toFloat() * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fase_onda"
    )

    val nivel = intensidade.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FundoChumbo)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val barras = 28
            val espaco = size.width / barras
            val larguraBarra = espaco * 0.55f
            val centroY = size.height / 2f

            for (i in 0 until barras) {
                val onda = (sin(fase + i * 0.35f) + 1f) / 2f
                val alturaBase = size.height * (0.18f + nivel * 0.72f * onda)
                val x = i * espaco + (espaco - larguraBarra) / 2f
                val top = centroY - alturaBase / 2f

                // Camada de glow suave (não excessiva)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NeonAzul.copy(alpha = 0.18f + nivel * 0.22f),
                            NeonLilas.copy(alpha = 0.14f + nivel * 0.18f),
                            NeonRosa.copy(alpha = 0.10f + nivel * 0.12f)
                        )
                    ),
                    topLeft = Offset(i.toFloat() * espaco - 2f, top - 3f),
                    size = Size(larguraBarra + 4f, alturaBase + 6f),
                    cornerRadius = CornerRadius(larguraBarra, larguraBarra)
                )

                val corBarra = when {
                    i % 7 == 0 -> NeonRosa
                    i % 3 == 0 -> NeonLilas
                    else -> NeonAzul
                }

                drawRoundRect(
                    color = corBarra.copy(alpha = 0.55f + nivel * 0.45f),
                    topLeft = Offset(x, top),
                    size = Size(larguraBarra, alturaBase),
                    cornerRadius = CornerRadius(larguraBarra / 2f, larguraBarra / 2f)
                )
            }
        }
    }
}
