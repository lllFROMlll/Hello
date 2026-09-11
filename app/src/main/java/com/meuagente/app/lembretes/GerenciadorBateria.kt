package com.meuagente.app.lembretes

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Gerencia a solicitação e verificação da isenção de otimização de bateria (Doze mode),
 * essencial para garantir que os alarmes do AlarmManager disparem pontualmente mesmo
 * quando o aparelho estiver em repouso prolongado.
 */
object GerenciadorBateria {

    private const val PREFS = "config_bateria"
    private const val JA_SOLICITOU = "ja_solicitou_bateria"

    fun precisaPedirIsencao(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jaPediu = prefs.getBoolean(JA_SOLICITOU, false)
        if (jaPediu) return false

        val powerManager = contexto.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignorando = powerManager?.isIgnoringBatteryOptimizations(contexto.packageName) ?: false
        return !ignorando
    }

    fun marcarComoSolicitado(contexto: Context) {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(JA_SOLICITOU, true).apply()
    }

    @SuppressLint("BatteryLife")
    fun abrirConfiguracaoBateria(contexto: Context) {
        marcarComoSolicitado(contexto)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${contexto.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                contexto.startActivity(intent)
            } catch (_: Exception) {
                // Fallback para tela geral de bateria caso a intent direta seja restrita
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    contexto.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    }
}
