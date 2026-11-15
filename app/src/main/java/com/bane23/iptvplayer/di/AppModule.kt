package com.bane23.iptvplayer.di

import android.content.Context
import androidx.room.Room
import com.bane23.iptvplayer.data.crypto.KeystorePasswordCipher
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.xtream.XtreamApi
import com.bane23.iptvplayer.data.xtream.XtreamJson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db")
            // Dok je aplikacija u razvoju šema se menja bez migracija; pre izdanja (Faza 8) uvesti migracije.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideXtreamApi(client: OkHttpClient): XtreamApi = Retrofit.Builder()
        // Prava adresa se prosleđuje uz svaki poziv (@Url); ova se nikad ne koristi.
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(XtreamJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(XtreamApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindPasswordCipher(impl: KeystorePasswordCipher): PasswordCipher
}
