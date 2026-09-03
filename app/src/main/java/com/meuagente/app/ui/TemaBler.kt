package com.meuagente.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BlerFundoTopo = Color(0xFF05050F)
val BlerFundoBase = Color(0xFF0A0A1E)
val BlerFundoCabecalho = Color(0xFF04040C)
val BlerSuperficieBolha = Color(0xEB101024)
val BlerBordaSutil = Color(0xFF1C1C3A)

val BlerNeonRoxo = Color(0xFFA855F7)
val BlerNeonAzul = Color(0xFF3B82F6)
val BlerNeonCiano = Color(0xFF22D3EE)
val BlerNeonVerde = Color(0xFF4ADE80)
val BlerNeonRosa = Color(0xFFEC4899)

val BlerTexto = Color(0xFFF4F4FF)
val BlerTextoHora = Color(0xFF8A8AA8)
val BlerTextoPlaceholder = Color(0xFF6B6B8A)
val BlerCheck = Color(0xFF22D3EE)

val GradienteBolhaRecebida = Brush.linearGradient(
    colors = listOf(BlerNeonRoxo, BlerNeonAzul)
)

val GradienteBolhaEnviada = Brush.linearGradient(
    colors = listOf(BlerNeonAzul, BlerNeonCiano, BlerNeonRoxo)
)

val GradienteEntrada = Brush.linearGradient(
    colors = listOf(BlerNeonRoxo, BlerNeonAzul),
    start = Offset.Zero,
    end = Offset.Infinite
)

val GradienteEnviar = Brush.linearGradient(
    colors = listOf(BlerNeonCiano, BlerNeonVerde)
)

val GradienteLogo = Brush.linearGradient(
    colors = listOf(BlerNeonVerde, BlerNeonCiano, BlerNeonAzul, BlerNeonRoxo, BlerNeonRosa)
)

val GradienteGloboLogo = Brush.sweepGradient(
    colors = listOf(BlerNeonVerde, BlerNeonAzul, BlerNeonRoxo, BlerNeonRosa, BlerNeonVerde)
)
