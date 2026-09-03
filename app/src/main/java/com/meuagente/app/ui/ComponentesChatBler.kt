package com.meuagente.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuagente.app.R

class FormaBolhaComCauda(
    private val caudaNaDireita: Boolean,
    private val raio: Dp = 24.dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val raioPx = with(density) { raio.toPx() }
        val caudaPx = with(density) { 12.dp.toPx() }
        val alturaCauda = with(density) { 14.dp.toPx() }
        val largura = size.width
        val altura = size.height
        val path = Path()

        path.moveTo(raioPx, 0f)
        path.lineTo(largura - raioPx, 0f)
        path.quadraticBezierTo(largura, 0f, largura, raioPx)
        path.lineTo(largura, altura - raioPx)
        path.quadraticBezierTo(largura, altura, largura - raioPx, altura)

        if (caudaNaDireita) {
            path.lineTo(largura - raioPx * 1.4f, altura)
            path.lineTo(largura - caudaPx, altura + alturaCauda * 0.55f)
            path.quadraticBezierTo(
                largura - caudaPx * 0.4f, altura + caudaPx * 0.4f,
                largura - caudaPx * 1.6f, altura - caudaPx * 0.2f
            )
            path.lineTo(raioPx, altura)
        } else {
            path.lineTo(raioPx * 1.6f, altura)
            path.lineTo(caudaPx, altura + alturaCauda * 0.55f)
            path.quadraticBezierTo(
                caudaPx * 0.4f, altura + caudaPx * 0.4f,
                caudaPx * 1.6f, altura - caudaPx * 0.2f
            )
            path.lineTo(raioPx, altura)
        }

        path.quadraticBezierTo(0f, altura, 0f, altura - raioPx)
        path.lineTo(0f, raioPx)
        path.quadraticBezierTo(0f, 0f, raioPx, 0f)
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun LogoBler(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val centro = Offset(size.width / 2f, size.height / 2f)
                val raioExterno = size.minDimension / 2f
                drawCircle(
                    brush = GradienteGloboLogo,
                    radius = raioExterno,
                    center = centro,
                    style = Stroke(width = 5f)
                )
                drawCircle(
                    brush = GradienteGloboLogo,
                    radius = raioExterno * 0.88f,
                    center = centro,
                    alpha = 0.25f,
                    style = Stroke(width = 14f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x334ADE80), Color.Transparent),
                        center = centro,
                        radius = raioExterno
                    ),
                    radius = raioExterno,
                    center = centro
                )
            }
            Text(
                text = "B",
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(brush = GradienteLogo)
            )
        }
        Text(
            text = "Blér®",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(brush = GradienteLogo)
        )
        Canvas(modifier = Modifier.padding(top = 4.dp).size(width = 120.dp, height = 10.dp)) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, BlerNeonCiano, Color.Transparent)
                ),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun BotaoCircularNeon(
    iconeRes: Int,
    descricao: String,
    modifier: Modifier = Modifier,
    corBorda: Color = BlerNeonRoxo,
    tamanho: Dp = 48.dp,
    aoClicar: () -> Unit = {}
) {
    val interacao = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(tamanho)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(corBorda, BlerNeonAzul)),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interacao,
                indication = null,
                onClick = aoClicar
            )
    ) {
        Icon(
            painter = painterResource(iconeRes),
            contentDescription = descricao,
            tint = BlerTexto,
            modifier = Modifier.size(tamanho / 2.4f)
        )
    }
}

@Composable
fun ChipData(texto: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(color = Color(0xFF15152A), shape = RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        Text(text = texto, color = BlerTextoHora, fontSize = 14.sp)
    }
}

@Composable
fun BolhaMensagem(
    texto: String,
    hora: String,
    enviada: Boolean,
    mostrarCheck: Boolean = false
) {
    val forma = FormaBolhaComCauda(caudaNaDireita = enviada)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (enviada) Arrangement.End else Arrangement.Start
    ) {
        Box(modifier = Modifier.widthIn(max = 320.dp)) {
            Column(
                modifier = Modifier
                    .background(color = BlerSuperficieBolha, shape = forma)
                    .border(
                        width = 1.6.dp,
                        brush = if (enviada) GradienteBolhaEnviada else GradienteBolhaRecebida,
                        shape = forma
                    )
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp)
            ) {
                Text(text = texto, color = BlerTexto, fontSize = 16.sp, lineHeight = 23.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = hora, color = BlerTextoHora, fontSize = 12.sp)
                    if (mostrarCheck) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "✓✓", color = BlerCheck, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BarraDeEntrada(
    texto: String,
    aoMudarTexto: (String) -> Unit,
    aoClicarMicrofone: () -> Unit,
    aoClicarEnviar: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .border(width = 2.dp, brush = GradienteEntrada, shape = RoundedCornerShape(50))
                .padding(end = 6.dp)
        ) {
            TextField(
                value = texto,
                onValueChange = aoMudarTexto,
                placeholder = {
                    Text("Digite uma mensagem...", color = BlerTextoPlaceholder, fontSize = 16.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = BlerTexto,
                    unfocusedTextColor = BlerTexto,
                    cursorColor = BlerNeonCiano,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            BotaoCircularNeon(
                iconeRes = R.drawable.ic_mic,
                descricao = "Comando de voz",
                tamanho = 44.dp,
                aoClicar = aoClicarMicrofone
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        val interacao = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(brush = GradienteEnviar, shape = CircleShape)
                .clickable(
                    interactionSource = interacao,
                    indication = null,
                    onClick = aoClicarEnviar
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Enviar",
                tint = Color(0xFF05050F),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
