package com.bane23.iptvplayer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migracije baze. Liste, favoriti i istorija moraju da prežive svaku nadogradnju aplikacije.
 *
 * Kad god se promeni šema (nova tabela, kolona ili indeks):
 *  1. povećati `version` u [AppDatabase],
 *  2. ovde dodati `Migration(stara, nova)` sa SQL naredbama koje staru šemu prevode u novu
 *     (`CREATE TABLE`, `ALTER TABLE ... ADD COLUMN`, `CREATE INDEX`), tačno kako ih Room očekuje,
 *  3. dodati je u [ALL].
 *
 * Verzije 1 do 4 su postojale samo tokom ranog razvoja i za njih migracija nema; sa njih se baza
 * pravi iznova (vidi `AppModule`).
 */
object Migrations {
    /** Prva verzija od koje se podaci čuvaju pri nadogradnji. */
    const val FIRST_KEPT_VERSION = 5

    /** 5 → 6: naziv, poster i nastavak fajla uz poziciju gledanja, za red „Nastavi gledanje”. */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN poster TEXT")
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN extension TEXT")
        }
    }

    /** Verzija 7: omiljeni filmovi i serije. */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vod_favorites` (`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                    "`itemId` INTEGER NOT NULL, `name` TEXT NOT NULL, `poster` TEXT, `extension` TEXT, " +
                    "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `kind`, `itemId`), " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vod_favorites_playlistId_kind_addedAt` " +
                    "ON `vod_favorites` (`playlistId`, `kind`, `addedAt`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7)
}
