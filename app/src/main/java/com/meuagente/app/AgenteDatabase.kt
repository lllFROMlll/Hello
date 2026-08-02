package com.meuagente.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Isso é o "prédio" do banco de dados: junta as fichas
// (MensagemEntity, LembreteEntity) com o bibliotecário (AgenteDao).
@Database(
    entities = [MensagemEntity::class, LembreteEntity::class],
    version = 1
)
abstract class AgenteDatabase : RoomDatabase() {

    abstract fun agenteDao(): AgenteDao

    companion object {
        @Volatile
        private var instancia: AgenteDatabase? = null

        fun obter(contexto: Context): AgenteDatabase {
            return instancia ?: synchronized(this) {
                val nova = Room.databaseBuilder(
                    contexto.applicationContext,
                    AgenteDatabase::class.java,
                    "agente_database"
                ).build()
                instancia = nova
                nova
            }
        }
    }
}
