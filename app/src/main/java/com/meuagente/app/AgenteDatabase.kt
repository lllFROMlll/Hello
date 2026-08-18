package com.meuagente.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MensagemEntity::class, LembreteEntity::class],
    version = 2
)
@TypeConverters(Converters::class)
abstract class AgenteDatabase : RoomDatabase() {

    abstract fun agenteDao(): AgenteDao

    companion object {
        @Volatile
        private var instancia: AgenteDatabase? = null

        // Migração da versão 1 para a 2: adiciona os campos novos de
        // agendamento e repetição na tabela de lembretes, preservando
        // tudo que já existia (lembretes antigos e mensagens).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lembretes ADD COLUMN dataHoraAgendada INTEGER")
                db.execSQL("ALTER TABLE lembretes ADD COLUMN repeticao TEXT NOT NULL DEFAULT 'NENHUMA'")
            }
        }

        fun obter(contexto: Context): AgenteDatabase {
            return instancia ?: synchronized(this) {
                val nova = Room.databaseBuilder(
                    contexto.applicationContext,
                    AgenteDatabase::class.java,
                    "agente_database"
                ).addMigrations(MIGRATION_1_2).build()
                instancia = nova
                nova
            }
        }
    }
}
