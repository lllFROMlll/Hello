package com.meuagente.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meuagente.app.R

private data class MensagemMock(
    val texto: String,
    val hora: String,
    val enviada: Boolean
)

private val mensagensMock = listOf(
    MensagemMock("E aí! Conseguiu ver aquele projeto que te mandei?", "09:30", enviada = false),
    MensagemMock("Vi sim! Ficou sensacional parabéns pelo trabalho 🔥", "09:32", enviada = true),
    MensagemMock("Valeu demais! 🙌\nDepois te mando as próximas atualizações.", "09:34", enviada = false),
    MensagemMock("Perfeito! Tô por aqui qualquer coisa.", "09:35", enviada = true)
)

@Composable
fun TelaChatBler() {
    var textoDigitado by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BlerFundoTopo, BlerFundoBase)
                )
            )
            .statusBarsPadding()
    ) {
        CabecalhoChatBler()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            ChipData(texto = "Hoje")
        }

        val estadoLista = rememberLazyListState()
        LazyColumn(
            state = estadoLista,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            items(mensagensMock) { mensagem ->
                BolhaMensagem(
                    texto = mensagem.texto,
                    hora = mensagem.hora,
                    enviada = mensagem.enviada,
                    mostrarCheck = mensagem.enviada
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            BarraDeEntrada(
                texto = textoDigitado,
                aoMudarTexto = { textoDigitado = it },
                aoClicarMicrofone = {},
                aoClicarEnviar = {}
            )
        }
    }
}

@Composable
private fun CabecalhoChatBler() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = BlerFundoCabecalho,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            BotaoCircularNeon(
                iconeRes = R.drawable.ic_voltar,
                descricao = "Voltar",
                tamanho = 44.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, top = 10.dp),
                aoClicar = {}
            )
            BotaoCircularNeon(
                iconeRes = R.drawable.ic_mais_opcoes,
                descricao = "Mais opções",
                tamanho = 44.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, top = 10.dp),
                aoClicar = {}
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LogoBler()
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(1.dp)
        ) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BlerNeonRoxo,
                        BlerNeonAzul,
                        Color.Transparent
                    )
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
        }
    }
}
