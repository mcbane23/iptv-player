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

    /**
     * 7 → 8: oznaka „odgledano”. Ono što je već pogledano do kraja (95%) dobija oznaku odmah,
     * sa vremenom poslednjeg gledanja.
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN watchedAt INTEGER")
            db.execSQL(
                "UPDATE watch_progress SET watchedAt = updatedAt " +
                    "WHERE durationMs > 0 AND positionMs * 100 >= durationMs * 95",
            )
        }
    }

    /** 8 → 9: Trakt (odgledano na nalogu i promene koje čekaju slanje). */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trakt_watched` (`kind` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, " +
                    "`season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`kind`, `tmdbId`, `season`, `episode`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trakt_pending` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` INTEGER NOT NULL, " +
                    "`seriesId` INTEGER NOT NULL, `watched` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
        }
    }

    /** 9 → 10: pozicije gledanja koje čekaju slanje na Trakt. */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `trakt_playback_pending` (`playlistId` INTEGER NOT NULL, " +
                    "`kind` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `seriesId` INTEGER NOT NULL, " +
                    "`progress` REAL NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `kind`, `itemId`))",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
}
