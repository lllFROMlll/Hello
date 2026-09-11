package com.meuagente.app.lembretes

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Sincronização OPCIONAL com o Google Agenda / Calendário padrão do Android.
 * Grava o lembrete diretamente como evento de calendário para que o usuário
 * tenha redundância garantida caso deseje.
 */
object SincronizadorGoogleAgenda {

    private const val PREFS = "config_google_agenda"
    private const val CHAVE_SINCRONIZAR = "sincronizar_agenda"

    fun estaAtivo(contexto: Context): Boolean {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(CHAVE_SINCRONIZAR, false)
    }

    fun salvarAtivo(contexto: Context, ativo: Boolean) {
        val prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(CHAVE_SINCRONIZAR, ativo).apply()
    }

    fun temPermissao(contexto: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            contexto,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            contexto,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Insere ou atualiza um evento na agenda padrão do usuário.
     * Retorna o ID do evento criado/atualizado ou null em caso de falha.
     */
    fun sincronizarLembrete(
        contexto: Context,
        titulo: String,
        descricaoContexto: String?,
        dataHoraMillis: Long,
        eventoIdExistente: Long? = null
    ): Long? {
        if (!estaAtivo(contexto) || !temPermissao(contexto)) return null

        return try {
            val calendarId = obterIdCalendarioPrincipal(contexto) ?: return null
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, dataHoraMillis)
                put(CalendarContract.Events.DTEND, dataHoraMillis + 15 * 60 * 1000) // 15 minutos de duração
                put(CalendarContract.Events.TITLE, "Blér: $titulo")
                put(CalendarContract.Events.DESCRIPTION, descricaoContexto ?: "Lembrete criado pelo Blér Assistente")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            if (eventoIdExistente != null && eventoIdExistente > 0) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventoIdExistente)
                val rows = contexto.contentResolver.update(updateUri, values, null, null)
                if (rows > 0) return eventoIdExistente
            }

            val uri: Uri? = contexto.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val novoId = uri?.lastPathSegment?.toLongOrNull()

            // Adiciona lembrete nativo de notificação no calendário
            novoId?.let { id ->
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, 0)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                contexto.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }

            novoId
        } catch (e: Exception) {
            null
        }
    }

    private fun obterIdCalendarioPrincipal(contexto: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        var cursor: Cursor? = null
        try {
            cursor = contexto.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                CalendarContract.Calendars.VISIBLE + " = 1",
                null,
                CalendarContract.Calendars.IS_PRIMARY + " DESC"
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                return cursor.getLong(idIndex)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return 1L // Fallback padrão
    }
}
