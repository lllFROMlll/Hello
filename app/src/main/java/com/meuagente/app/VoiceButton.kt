package com.meuagente.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Cores neon do rodapé de voz
private val NeonAzul = Color(0xFF00E5FF)
private val NeonLilas = Color(0xFFB388FF)
private val NeonRosa = Color(0xFFFF4DDE)

/**
 * Estados do botão de voz no rodapé.
 */
enum class EstadoVoz {
    INATIVO,     // microfone normal
    GRAVANDO,    // gravação em andamento (imersão aberta)
    TRANSCREVENDO, // processando o áudio (loading)
    PRONTO       // texto transcrito pronto para revisão/envio
}

/**
 * Rodapé de voz: alternador de modo + botão de microfone + (quando PRONTO)
 * campo de texto editável e botão de envio.
 */
@Composable
fun RodapeDeVoz(
    estado: EstadoVoz,
    modoAtual: ModoVoz,
    textoTranscrito: String,
    aoAlternarModo: () -> Unit,
    aoIniciar: () -> Unit,
    aoEnviar: () -> Unit,
    aoEditarTexto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {

        // ── Campo de texto transcrito (apenas quando PRONTO) ──
        if (estado == EstadoVoz.PRONTO) {
            OutlinedTextField(
                value = textoTranscrito,
                onValueChange = aoEditarTexto,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Texto transcrito...") },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonAzul,
                    focusedContainerColor = Color(0xFF1A1D22),
                    cursorColor = NeonAzul
                )
            )
        }

        // ── Revisão da transcrição (apenas PRONTO) ──
        if (estado == EstadoVoz.PRONTO) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        FxSons.clique(contexto)
                        aoEnviar()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = "Enviar",
                        tint = NeonAzul,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Alternador de modo (Auto / IA / Nativo) ──
                IconButton(
                    onClick = {
                        FxSons.clique(contexto)
                        aoAlternarModo()
                    }
                ) {
                    Icon(
                        painter = painterResource(ControladorModoVoz.icone(modoAtual)),
                        contentDescription = "Modo de voz: ${modoAtual.name}",
                        tint = NeonLilas,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // ── Botão principal de microfone ──
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(NeonAzul, NeonLilas)
                            ),
                            shape = CircleShape
                        )
                ) {
                    IconButton(
                        onClick = {
                            FxSons.clique(contexto)
                            when (estado) {
                                EstadoVoz.INATIVO -> aoIniciar()
                                EstadoVoz.TRANSCREVENDO -> {}
                                else -> {}
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        enabled = estado != EstadoVoz.TRANSCREVENDO
                    ) {
                        when (estado) {
                            EstadoVoz.TRANSCREVENDO -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            }
                            else -> {
                                Icon(
                                    painter = painterResource(
                                        if (estado == EstadoVoz.GRAVANDO) R.drawable.ic_stop else R.drawable.ic_mic
                                    ),
                                    contentDescription = "Microfone",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}