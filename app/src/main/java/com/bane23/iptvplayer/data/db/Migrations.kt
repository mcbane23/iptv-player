package com.bane23.iptvplayer.data.db

import androidx.room.migration.Migration

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

    val ALL: Array<Migration> = arrayOf()
}
