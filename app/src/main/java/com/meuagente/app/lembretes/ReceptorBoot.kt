package com.meuagente.app.lembretes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reagenda todos os lembretes pendentes após o usuário reiniciar o smartphone.
 */
class ReceptorBoot : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            GerenciadorLembretes.reagendarTodosPendentes(context)
        }
    }
}
