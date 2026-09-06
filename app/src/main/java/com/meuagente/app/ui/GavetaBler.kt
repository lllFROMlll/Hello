package com.meuagente.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CartaoTopo = Color(0xD9151C2C)
private val CartaoBase = Color(0xF20A0E18)
private val CianoNeon = Color(0xFF00F2FE)
private val MagentaNeon = Color(0xFFC026D3)
private val RoxoNeon = Color(0xFFA855F7)
private val TextoClaro = Color(0xFFF1F5F9)
private val TextoSuave = Color(0xFF94A3B8)

val FormaCartao = RoundedCornerShape(20.dp)

@Composable
fun DivisoriaNeonBler(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    MagentaNeon.copy(alpha = 0.6f),
                    CianoNeon.copy(alpha = 0.6f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2f
        )
    }
}

@Composable
fun CartaoNovaConversaBler(aoClicar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(listOf(CartaoTopo, CartaoBase)),
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(listOf(CianoNeon.copy(alpha = 0.75f), RoxoNeon.copy(alpha = 0.75f), Color(0xB3EC4899))),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(color = RoxoNeon.copy(alpha = 0.20f), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, RoxoNeon.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Text(text = "+", color = CianoNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Nova conversa",
                color = TextoClaro,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TituloSecaoBler() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color = CianoNeon, shape = androidx.compose.foundation.shape.CircleShape)
                .drawBehind {
                    drawCircle(color = CianoNeon.copy(alpha = 0.5f), radius = size.minDimension)
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "CONVERSAS",
            color = TextoSuave,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "Recentes", color = TextoSuave, fontSize = 11.sp)
    }
}

@Composable
fun CartaoConversaGaveta(
    titulo: String,
    subtitulo: String,
    ativo: Boolean,
    fixada: Boolean,
    aoAbrir: () -> Unit,
    aoFixar: () -> Unit,
    aoExcluir: () -> Unit
) {
    val interacao = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(listOf(CartaoTopo, CartaoBase)),
                shape = FormaCartao
            )
            .drawBehind {
                if (ativo) {
                    val raio = 20.dp.toPx()
                    drawRoundRect(
                        color = CianoNeon.copy(alpha = 0.10f),
                        cornerRadius = CornerRadius(raio, raio)
                    )
                }
            }
            .border(
                width = if (ativo) 1.5.dp else 1.dp,
                brush = if (ativo) {
                    Brush.verticalGradient(listOf(CianoNeon.copy(alpha = 0.8f), CianoNeon.copy(alpha = 0.4f)))
                } else {
                    Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x66000000)))
                },
                shape = FormaCartao
            )
            .clickable(interactionSource = interacao, indication = null, onClick = aoAbrir)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (ativo) CianoNeon else RoxoNeon.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    color = if (ativo) Color.White else TextoClaro.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitulo,
                    color = if (ativo) CianoNeon.copy(alpha = 0.8f) else TextoSuave,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconeAcaoGaveta(
                iconeRes = com.meuagente.app.R.drawable.ic_pin,
                descricao = if (fixada) "Desafixar" else "Fixar",
                cor = if (fixada) Color(0xFFC084FC) else RoxoNeon.copy(alpha = 0.7f),
                aoClicar = aoFixar
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconeAcaoGaveta(
                iconeRes = com.meuagente.app.R.drawable.ic_trash,
                descricao = "Excluir conversa",
                cor = Color(0xFFEC4899),
                aoClicar = aoExcluir
            )
        }
    }
}

@Composable
private fun IconeAcaoGaveta(
    iconeRes: Int,
    descricao: String,
    cor: Color,
    aoClicar: () -> Unit
) {
    val interacao = remember { MutableInteractionSource() }
    Icon(
        painter = painterResource(iconeRes),
        contentDescription = descricao,
        tint = cor,
        modifier = Modifier
            .size(20.dp)
            .clickable(interactionSource = interacao, indication = null, onClick = aoClicar)
    )
}

@Composable
fun RodapeConfiguracoesBler(aoAbrir: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interacao, indication = null, onClick = aoAbrir)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(color = Color(0xFF0B0F19), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x59334155), RoundedCornerShape(12.dp))
        ) {
            Icon(
                painter = painterResource(com.meuagente.app.R.drawable.ic_settings),
                contentDescription = "Configurações",
                tint = TextoClaro.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Configurações", color = TextoClaro, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "Preferências e conta", color = TextoSuave, fontSize = 11.sp)
        }
        Text(text = "›", color = TextoSuave, fontSize = 20.sp)
    }
}
