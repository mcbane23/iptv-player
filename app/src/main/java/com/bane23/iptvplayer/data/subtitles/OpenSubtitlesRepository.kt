package com.bane23.iptvplayer.data.subtitles

import android.content.Context
import com.bane23.iptvplayer.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Zašto pretraga, preuzimanje ili prijava nisu uspeli, u obliku koji UI prevodi u poruku. */
enum class SubtitleFailure { NETWORK, QUOTA, BAD_LOGIN, FAILED }

class SubtitleException(val failure: SubtitleFailure) : IOException(failure.name)

/** Preuzet titl na uređaju. [remaining] je koliko preuzimanja je ostalo za danas, ako server to javi. */
class DownloadedSubtitle(val file: File, val result: SubtitleResult, val remaining: Int?)

/**
 * Titlovi sa OpenSubtitles-a: pretraga po TMDB ID-ju ili nazivu, preuzimanje u keš aplikacije i
 * prijava na korisnikov nalog (nije obavezna; nalog ima veći dnevni broj preuzimanja).
 */
@Singleton
class OpenSubtitlesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: OpenSubtitlesApi,
    private val store: OpenSubtitlesStore,
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) {
    private val directory = File(context.cacheDir, "subtitles")

    val isAvailable: Boolean get() = isOpenSubtitlesConfigured

    /** Korisničko ime prijavljenog naloga, ili null kad prijave nema. */
    val username: Flow<String?> = store.username

    suspend fun search(query: SubtitleQuery): List<SubtitleResult> = withContext(Dispatchers.IO) {
        val languages = SubtitleSearch.languages(
            preferred = settings.playerSettings.first().subtitleLanguage,
            device = Locale.getDefault().language,
        )
        val languageParam = languages.sorted().joinToString(",")
        val clean = SubtitleSearch.cleanTitle(query.title)
        call {
            var response = OsSearchResponse()
            // Prvo po TMDB ID-ju (tačno poklapanje), a ako ga nema ili ne nađe ništa, po nazivu.
            if (query.tmdbId != null) {
                response = if (query.isEpisode) {
                    api.search(
                        episode = query.episode,
                        languages = languageParam,
                        parentTmdbId = query.tmdbId,
                        season = query.season,
                    )
                } else {
                    api.search(languages = languageParam, tmdbId = query.tmdbId, type = "movie")
                }
            }
            if (response.data.isEmpty() && clean.title.isNotBlank()) {
                response = if (query.isEpisode) {
                    api.search(
                        episode = query.episode,
                        languages = languageParam,
                        query = clean.title.lowercase(),
                        season = query.season,
                        type = "episode",
                    )
                } else {
                    api.search(languages = languageParam, query = clean.title.lowercase(), type = "movie", year = clean.year)
                }
            }
            SubtitleSearch.results(response, languages)
        }
    }

    /** Preuzima titl u keš. Jednom preuzet titl se ne preuzima ponovo, pa ne troši dnevni broj. */
    suspend fun download(result: SubtitleResult): DownloadedSubtitle = withContext(Dispatchers.IO) {
        val target = File(directory, "${result.fileId}.srt")
        if (target.length() > 0) return@withContext DownloadedSubtitle(target, result, remaining = null)
        call {
            val body = OsDownloadRequest(result.fileId)
            var response = api.download(bearer(store.account()?.token), body)
            if (response.code() == 401) {
                // Token je istekao: prijava se ponavlja sačuvanim podacima; ako ni to ne prođe, ide bez naloga.
                store.clearToken()
                response = api.download(bearer(renewToken()), body)
            }
            when (response.code()) {
                200 -> Unit
                406, 429 -> throw SubtitleException(SubtitleFailure.QUOTA)
                else -> throw SubtitleException(SubtitleFailure.FAILED)
            }
            val info = response.body()
            val link = info?.link?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: throw SubtitleException(SubtitleFailure.FAILED)
            directory.mkdirs()
            val partial = File(directory, "${result.fileId}.part")
            client.newCall(Request.Builder().url(link).build()).execute().use { file ->
                if (!file.isSuccessful) throw SubtitleException(SubtitleFailure.FAILED)
                partial.outputStream().use { out -> file.body.byteStream().copyTo(out) }
            }
            if (partial.length() == 0L || partial.length() > MAX_BYTES || !partial.renameTo(target)) {
                partial.delete()
                throw SubtitleException(SubtitleFailure.FAILED)
            }
            trimCache(keep = target)
            DownloadedSubtitle(target, result, info?.remaining)
        }
    }

    suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        call {
            val response = api.login(OsLoginRequest(username.trim(), password))
            val token = response.body()?.token
            when {
                response.isSuccessful && token != null -> store.save(username.trim(), password, token)
                response.code() in listOf(400, 401, 403) -> throw SubtitleException(SubtitleFailure.BAD_LOGIN)
                else -> throw SubtitleException(SubtitleFailure.FAILED)
            }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = store.account()?.token
        store.clear()
        if (token != null) {
            try {
                api.logout("Bearer $token")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Odjava na serveru je samo uljudnost; nalog je na uređaju već zaboravljen.
            }
        }
    }

    private suspend fun renewToken(): String? {
        val account = store.account() ?: return null
        val response = api.login(OsLoginRequest(account.username, account.password))
        val token = response.body()?.token?.takeIf { response.isSuccessful }
        if (token != null) store.save(account.username, account.password, token)
        return token
    }

    private fun bearer(token: String?): String? = token?.let { "Bearer $it" }

    /** Mrežne i neočekivane greške postaju [SubtitleException], da ih UI prikaže kao poruku. */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SubtitleException) {
        throw e
    } catch (e: retrofit2.HttpException) {
        throw SubtitleException(if (e.code() == 429 || e.code() == 406) SubtitleFailure.QUOTA else SubtitleFailure.FAILED)
    } catch (_: IOException) {
        throw SubtitleException(SubtitleFailure.NETWORK)
    } catch (_: Exception) {
        throw SubtitleException(SubtitleFailure.FAILED)
    }

    /** Keš čuva samo poslednjih nekoliko titlova. */
    private fun trimCache(keep: File) {
        val files = directory.listFiles()?.filter { it != keep }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES - 1).forEach { it.delete() }
    }

    private companion object {
        const val MAX_FILES = 30
        const val MAX_BYTES = 5L * 1024 * 1024
    }
}
