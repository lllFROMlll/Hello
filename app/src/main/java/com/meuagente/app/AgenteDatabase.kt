package com.meuagente.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MensagemEntity::class, LembreteEntity::class, ConversaEntity::class],
    version = 4
)
@TypeConverters(Converters::class)
abstract class AgenteDatabase : RoomDatabase() {

    abstract fun agenteDao(): AgenteDao

    companion object {
        @Volatile
        private var instancia: AgenteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lembretes ADD COLUMN dataHoraAgendada INTEGER")
                db.execSQL("ALTER TABLE lembretes ADD COLUMN repeticao TEXT NOT NULL DEFAULT 'NENHUMA'")
            }
        }

        // Cria a tabela de conversas (abas) e liga as mensagens
        // antigas a uma "Conversa 1" automática, pra nada se perder.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, titulo TEXT NOT NULL, dataCriacao INTEGER NOT NULL)")
                db.execSQL("INSERT INTO conversas (id, titulo, dataCriacao) VALUES (1, 'Conversa 1', 0)")
                db.execSQL("ALTER TABLE mensagens ADD COLUMN conversaId INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Adiciona o campo "fixada" pra permitir fixar conversas no topo.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversas ADD COLUMN fixada INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun obter(contexto: Context): AgenteDatabase {
            return instancia ?: synchronized(this) {
                val nova = Room.databaseBuilder(
                    contexto.applicationContext,
                    AgenteDatabase::class.java,
                    "agente_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                instancia = nova
                nova
            }
        }
    }
}
