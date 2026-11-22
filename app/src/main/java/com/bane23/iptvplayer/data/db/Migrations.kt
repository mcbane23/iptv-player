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

    /** 10 → 11: snimci kanala. */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, `channelKey` TEXT NOT NULL, " +
                    "`channelName` TEXT NOT NULL, `title` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `stopAtMs` INTEGER, " +
                    "`sizeBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `error` TEXT)",
            )
        }
    }

    /** 11 → 12: oznaka da je film ili serija sklonjena iz reda „Nastavi gledanje”. */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN dismissedAt INTEGER")
        }
    }

    /** 12 → 13: podaci o Xtream nalogu uz listu (istek i broj veza). */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN accountExpiresAt INTEGER")
            db.execSQL("ALTER TABLE playlists ADD COLUMN maxConnections INTEGER")
        }
    }

    /** 13 → 14: oznaka da se kanali liste prikazuju zajedno sa drugim spojenim listama. */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN merged INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 14 → 15: spisak kanala iz vodiča i ručne veze kanala liste sa njima. */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `epg_channels` (`playlistId` INTEGER NOT NULL, `epgKey` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `epgKey`), " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `epg_links` (`playlistId` INTEGER NOT NULL, `stableKey` TEXT NOT NULL, " +
                    "`epgKey` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `stableKey`), " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** 15 → 16: liste omiljenih kanala sa imenom i kanali u njima. */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `position` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite_list_items` (`listId` INTEGER NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, `channelKey` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`listId`, `playlistId`, `channelKey`), " +
                    "FOREIGN KEY(`listId`) REFERENCES `favorite_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_favorite_list_items_playlistId_channelKey` " +
                    "ON `favorite_list_items` (`playlistId`, `channelKey`)",
            )
        }
    }

    /** 16 → 17: filmovi i epizode preuzeti na telefon. */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlistId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `seriesId` INTEGER, " +
                    "`title` TEXT NOT NULL, `subtitle` TEXT, `poster` TEXT, `extension` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`sizeBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `error` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_playlistId_kind_itemId` " +
                    "ON `downloads` (`playlistId`, `kind`, `itemId`)",
            )
        }
    }

    /** 17 → 18: oznaka da je kategorija sada zaključana, da se njeni kanali ne vide bez PIN-a. */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** 18 → 19: kanali koje je korisnik sakrio. */
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hidden_channels` (`playlistId` INTEGER NOT NULL, `channelKey` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `hiddenAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `channelKey`), " +
                    "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * 19 → 20: spajanje lista je jedan prekidač za sve liste. Ko je do sada imao spojene bar dve
     * liste, dobija sve liste spojene; inače nijedna nije spojena. Šema se ne menja.
     */
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE playlists SET merged = (SELECT COUNT(*) FROM playlists WHERE merged = 1) >= 2")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
    )
}
