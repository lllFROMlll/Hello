package com.meuagente.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuagente.app.ui.BotaoCircularNeon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val FundoImersao = Color(0xFF040610)
private val TealAnel = Color(0xFF38E8C8)

/**
 * Tela de IMERSÃO do comando de voz, fiel ao vídeo de referência:
 * - Topo do chat permanece visível sob véu escuro, com a seta ‹ para sair.
 * - Orbe luminosa (ciano/azul com manchas magenta) cercada por anéis
 *   concêntricos que pulam/expandem conforme a intensidade da voz.
 * - Cartão de legenda com a transcrição ao vivo em aparecimento suave
 *   (trecho reconhecido destacado em ciano).
 * - Equalizador de barras no rodapé reagindo ao áudio.
 * - Toque na orbe ou na seta ‹ encerra a imersão.
 */
@Composable
fun ImersaoVoz(
    intensidade: Float,
    textoParcial: String,
    caminhoAtivo: String,
    onParar: () -> Unit,
    aoSair: () -> Unit = onParar
) {
    val contexto = LocalContext.current
    val transicao = rememberInfiniteTransition(label = "voz")

    val tempo by transicao.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(4200), repeatMode = RepeatMode.Restart),
        label = "tempo"
    )
    val pulso by transicao.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "pulso"
    )

    val nivel = intensidade.coerceIn(0f, 1f)
    val interacaoOrbe = remember { MutableInteractionSource() }

    Surface(color = Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x33040610),
                            Color(0xD9040610),
                            FundoImersao
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        ) {
            // Seta ‹ sobre o topo: volta ao chat normal
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 42.dp)
            ) {
                BotaoCircularNeon(
                    iconeRes = com.meuagente.app.R.drawable.ic_voltar,
                    descricao = "Voltar ao chat",
                    tamanho = 48.dp,
                    aoClicar = aoSair
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(120.dp))

                // ── Orbe luminosa com anéis reativos ──
                Canvas(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            val escala = pulso + nivel * 0.06f
                            scaleX = escala
                            scaleY = escala
                        }
                        .clickable(
                            interactionSource = interacaoOrbe,
                            indication = null
                        ) {
                            FxSons.despedir(contexto)
                            onParar()
                        }
                ) {
                    val centro = Offset(size.width / 2f, size.height / 2f)
                    val raioBase = size.minDimension * 0.27f
                    val traco = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))

                    // Halo difuso geral
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x334FC8F8), Color.Transparent),
                            center = centro,
                            radius = raioBase * 2.1f
                        ),
                        radius = raioBase * 2.1f,
                        center = centro
                    )

                    // Pulsos reativos: nascem grossos junto ao orbe (cor da
                    // esfera) e se dissipam ao expandir com a voz.
                    for (i in 0 until 4) {
                        val p = ((tempo * 0.4f) + i / 4f) % 1f
                        val raioPulso = raioBase * (1.05f + p * 1.25f * (0.55f + 0.45f * nivel))
                        val largura = 5.5f * (1f - p) + 0.8f
                        val alpha = (1f - p) * (0.30f + 0.65f * nivel)
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF54CDF8),
                                    Color(0xFFA5F3FF),
                                    Color(0xFF2E6FD8),
                                    Color(0xFF54CDF8)
                                ),
                                center = centro
                            ),
                            radius = raioPulso,
                            center = centro,
                            alpha = alpha,
                            style = Stroke(width = largura, pathEffect = traco)
                        )
                    }

                    // Anel-moldura externo fixo, sutil
                    drawCircle(
                        color = TealAnel.copy(alpha = 0.30f),
                        radius = raioBase * 1.85f,
                        center = centro,
                        style = Stroke(width = 2f, pathEffect = traco)
                    )

                    // Esfera central
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF4FFFF),
                                Color(0xFFA5F3FF),
                                Color(0xFF54CDF8),
                                Color(0xFF2E6FD8)
                            ),
                            center = centro,
                            radius = raioBase,
                            tileMode = TileMode.Clamp
                        ),
                        radius = raioBase,
                        center = centro
                    )

                    // Camada de cores rotativa: ciano/magenta/azul se fundindo
                    rotate(degrees = tempo * 40f, pivot = centro) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF54CDF8),
                                    Color(0xFFE34FD0),
                                    Color(0xFF2E6FD8),
                                    Color(0xFF54CDF8)
                                ),
                                center = centro
                            ),
                            radius = raioBase,
                            center = centro,
                            alpha = 0.30f
                        )
                    }

                    // Manchas magenta internas em deriva lenta
                    val deriva1 = Offset(
                        centro.x + raioBase * (0.42f + 0.10f * sin(tempo * 0.8f)),
                        centro.y + raioBase * (0.38f + 0.10f * cos(tempo * 0.6f))
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x99E34FD0), Color.Transparent),
                            center = deriva1,
                            radius = raioBase * 0.55f
                        ),
                        radius = raioBase * 0.55f,
                        center = deriva1
                    )
                    val deriva2 = Offset(
                        centro.x - raioBase * (0.5f + 0.08f * cos(tempo * 0.5f)),
                        centro.y - raioBase * (0.1f + 0.08f * sin(tempo * 0.7f))
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x66FF5EDB), Color.Transparent),
                            center = deriva2,
                            radius = raioBase * 0.4f
                        ),
                        radius = raioBase * 0.4f,
                        center = deriva2
                    )

                    // Contorno suave da esfera
                    drawCircle(
                        color = Color(0xFF9FF2FF).copy(alpha = 0.5f),
                        radius = raioBase,
                        center = centro,
                        style = Stroke(width = 1.5f)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ── Cartão de legenda com transcrição ao vivo ──
                val texto = textoParcial.ifBlank { "..." }
                val inicioDestaque = if (texto.length > 18) texto.length - (texto.length / 3).coerceIn(6, 26) else 0
                val legenda = buildAnnotatedString {
                    append("\u201C")
                    if (inicioDestaque > 0) append(texto, 0, inicioDestaque)
                    withStyle(SpanStyle(color = Color(0xFF35E0FF), fontWeight = FontWeight.SemiBold)) {
                        append(texto, inicioDestaque, texto.length)
                    }
                    append("\u201D")
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .background(
                            color = Color(0xE6101426),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(1.dp, Color(0x3338E8C8), RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    AnimatedContent(
                        targetState = legenda,
                        transitionSpec = {
                            (slideInVertically(animationSpec = tween(420)) { it / 3 } + fadeIn(animationSpec = tween(420)))
                                .togetherWith(fadeOut(animationSpec = tween(260)))
                        },
                        label = "legenda_viva"
                    ) { textoAnimado ->
                        Text(
                            text = textoAnimado,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Equalizador reativo no rodapé ──
                Canvas(
                    modifier = Modifier
                        .padding(bottom = 48.dp)
                        .size(width = 130.dp, height = 30.dp)
                ) {
                    desenharEqualizador(nivel = nivel, tempo = tempo, size = size)
                }
            }
        }
    }
}

private fun DrawScope.desenharEqualizador(nivel: Float, tempo: Float, size: Size) {
    val barras = 7
    val espaco = size.width / (barras * 1.7f)
    val larguraBarra = espaco * 0.55f
    for (i in 0 until barras) {
        val fase = tempo + i * 0.85f
        val fator = 0.18f + 0.16f * nivel + 0.66f * nivel * abs(sin(fase))
        val altura = (size.height * fator).coerceAtLeast(3f)
        val x = i * espaco + espaco / 2f
        val cor = if (i % 2 == 0) Color(0xFF35E0FF) else Color(0xFF4ADE9C)
        drawRoundRect(
            color = cor.copy(alpha = 0.85f),
            topLeft = Offset(x, (size.height - altura) / 2f),
            size = Size(larguraBarra, altura),
            cornerRadius = CornerRadius(larguraBarra / 2f, larguraBarra / 2f)
        )
    }
}
