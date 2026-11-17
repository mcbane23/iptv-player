package com.bane23.iptvplayer.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ChannelRepository
import com.bane23.iptvplayer.data.repo.VodKind
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.domain.CategoryArrange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kartice na ekranu za uređivanje kategorija. */
enum class CategorySection { LIVE, MOVIES, SERIES }

/** Kartica po imenu iz odredišta `ManageCategories`; nepoznato ime daje Uživo. */
fun categorySection(name: String): CategorySection =
    CategorySection.entries.firstOrNull { it.name == name } ?: CategorySection.LIVE

/** Naziv kartice (ID teksta). */
fun CategorySection.label(): Int = when (this) {
    CategorySection.LIVE -> R.string.nav_live
    CategorySection.MOVIES -> R.string.vod_movies
    CategorySection.SERIES -> R.string.vod_series
}

/** Kategorija na ekranu za uređivanje. [count] je broj kanala; filmovi i serije ga nemaju. */
data class ManagedCategory(val key: String, val name: String, val count: Int?, val hidden: Boolean)

data class CategoriesUiState(
    val section: CategorySection = CategorySection.LIVE,
    /** Lista je Xtream nalog, pa ima i kartice Filmovi i Serije. */
    val hasVod: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** Redom kojim se prikazuju: prvo vidljive, na dnu one koje su bile sakrivene kad je kartica otvorena. */
    val categories: List<ManagedCategory> = emptyList(),
)

/** Sakrivanje kategorija i promena njihovog redosleda, za kanale, filmove i serije aktivne liste. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val channels: ChannelRepository,
    private val vod: VodRepository,
) : ViewModel() {

    private val section = MutableStateFlow(CategorySection.LIVE)
    private val attempt = MutableStateFlow(0)

    /**
     * Kategorije koje su bile sakrivene kad je kartica otvorena. One stoje na dnu; kategorija koja
     * se sakrije dok je ekran otvoren ostaje na svom mestu, da red ne pobegne ispod prsta ili fokusa.
     */
    private var hiddenAtOpen: Set<String>? = null

    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data class Ready(val categories: List<ManagedCategory>) : Load
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CategoriesUiState> =
        combine(channels.activePlaylistId, section, attempt) { playlistId, tab, _ -> playlistId to tab }
            .flatMapLatest { (playlistId, section) ->
                hiddenAtOpen = null
                if (playlistId == null) {
                    flowOf(CategoriesUiState(section = section, loading = false))
                } else {
                    combine(source(playlistId, section), channels.observeIsXtream(playlistId)) { load, xtream ->
                        when (load) {
                            Load.Loading -> CategoriesUiState(section, xtream, loading = true)
                            Load.Failed -> CategoriesUiState(section, xtream, loading = false, failed = true)
                            is Load.Ready -> CategoriesUiState(section, xtream, loading = false, categories = arrange(load.categories))
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private fun source(playlistId: Long, section: CategorySection): Flow<Load> = when (section) {
        CategorySection.LIVE -> channels.observeAllCategories(playlistId).map { list ->
            Load.Ready(list.map { ManagedCategory(it.id.toString(), it.name, it.channelCount, it.hidden) })
        }

        else -> flow<Load> {
            emit(Load.Loading)
            val kind = section.kind
            val all = try {
                vod.allCategories(kind)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emit(Load.Failed)
                return@flow
            }
            emitAll(
                vod.observeCategoryPrefs(playlistId, kind).map { prefs ->
                    val ordered = CategoryArrange.ordered(all, prefs.order) { it.id }
                    Load.Ready(ordered.map { ManagedCategory(it.id, it.name, null, it.id in prefs.hidden) })
                },
            )
        }
    }

    private fun arrange(list: List<ManagedCategory>): List<ManagedCategory> {
        val bottom = hiddenAtOpen ?: list.filter { it.hidden }.mapTo(HashSet()) { it.key }.also { hiddenAtOpen = it }
        return CategoryArrange.withBottom(list, bottom) { it.key }
    }

    fun setSection(value: CategorySection) {
        section.value = value
    }

    fun retry() {
        attempt.value++
    }

    fun setHidden(category: ManagedCategory, hidden: Boolean) {
        val current = state.value.section
        viewModelScope.launch {
            if (current == CategorySection.LIVE) {
                category.key.toLongOrNull()?.let { channels.setCategoryHidden(it, hidden) }
            } else {
                vod.setCategoriesHidden(current.kind, listOf(category.key), hidden)
            }
        }
    }

    /** Sakriva ili prikazuje sve kategorije kartice odjednom. */
    fun setAllHidden(hidden: Boolean) {
        val current = state.value
        viewModelScope.launch {
            if (current.section == CategorySection.LIVE) {
                channels.activePlaylistIdNow()?.let { channels.setAllCategoriesHidden(it, hidden) }
            } else {
                vod.setCategoriesHidden(current.section.kind, current.categories.map { it.key }, hidden)
            }
        }
    }

    /** Sakriva ili prikazuje samo [categories], npr. one koje je pretraga našla. */
    fun setHidden(categories: List<ManagedCategory>, hidden: Boolean) {
        val current = state.value.section
        viewModelScope.launch {
            if (current == CategorySection.LIVE) {
                channels.setCategoriesHidden(categories.mapNotNull { it.key.toLongOrNull() }, hidden)
            } else {
                vod.setCategoriesHidden(current.kind, categories.map { it.key }, hidden)
            }
        }
    }

    /**
     * Pamti redosled koji je korisnik složio prevlačenjem (telefon) ili premeštanjem (TV).
     * [ordered] su sve kategorije kartice, redom sa ekrana.
     */
    fun reorder(ordered: List<ManagedCategory>) {
        val current = state.value.categories
        if (ordered.size != current.size) return
        save(ordered)
    }

    fun moveToTop(category: ManagedCategory) {
        val list = state.value.categories
        save(CategoryArrange.movedToTop(list, list.indexOfFirst { it.key == category.key }))
    }

    /** Pamti redosled sa ekrana kao redosled kategorija. */
    private fun save(ordered: List<ManagedCategory>) {
        val current = state.value
        if (ordered.map { it.key } == current.categories.map { it.key }) return
        viewModelScope.launch {
            if (current.section == CategorySection.LIVE) {
                val playlistId = channels.activePlaylistIdNow() ?: return@launch
                channels.setCategoryOrder(playlistId, ordered.mapNotNull { it.key.toLongOrNull() })
            } else {
                vod.setCategoryOrder(current.section.kind, ordered.map { it.key })
            }
        }
    }

    private val CategorySection.kind get() = if (this == CategorySection.SERIES) VodKind.SERIES else VodKind.MOVIES
}
