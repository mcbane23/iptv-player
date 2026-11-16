package com.bane23.iptvplayer.di

import android.content.Context
import androidx.room.Room
import com.bane23.iptvplayer.BuildConfig
import com.bane23.iptvplayer.data.crypto.KeystorePasswordCipher
import com.bane23.iptvplayer.data.crypto.PasswordCipher
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.db.Migrations
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesApi
import com.bane23.iptvplayer.data.subtitles.OpenSubtitlesJson
import com.bane23.iptvplayer.data.trakt.TraktApi
import com.bane23.iptvplayer.data.trakt.TraktJson
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
            // Od verzije 5 svaka promena šeme ide sa migracijom, da liste i favoriti prežive nadogradnju.
            .addMigrations(*Migrations.ALL)
            // Samo baze iz ranog razvoja (verzije 1 do 4) i vraćanje na stariju verziju prave bazu iznova.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
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

    @Provides
    @Singleton
    fun provideTraktApi(client: OkHttpClient): TraktApi = Retrofit.Builder()
        .baseUrl("https://api.trakt.tv/")
        .client(
            client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("trakt-api-version", "2")
                            .header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
                            .header("User-Agent", "IPTVPlayer/${BuildConfig.VERSION_NAME}")
                            .build(),
                    )
                }
                .build(),
        )
        .addConverterFactory(TraktJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TraktApi::class.java)

    @Provides
    @Singleton
    fun provideOpenSubtitlesApi(client: OkHttpClient): OpenSubtitlesApi = Retrofit.Builder()
        .baseUrl("https://api.opensubtitles.com/api/v1/")
        .client(
            client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
                            // OpenSubtitles traži naziv i verziju aplikacije; podrazumevani User-Agent odbija.
                            .header("User-Agent", "Glowbox v${BuildConfig.VERSION_NAME}")
                            .header("Accept", "application/json")
                            .build(),
                    )
                }
                .build(),
        )
        .addConverterFactory(OpenSubtitlesJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenSubtitlesApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindPasswordCipher(impl: KeystorePasswordCipher): PasswordCipher
}
