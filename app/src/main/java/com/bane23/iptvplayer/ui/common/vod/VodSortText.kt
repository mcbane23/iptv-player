package com.bane23.iptvplayer.ui.common.vod

import androidx.annotation.StringRes
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.VodSort

/** Naziv redosleda za meni. */
@get:StringRes
val VodSort.label: Int
    get() = when (this) {
        VodSort.DEFAULT -> R.string.vod_sort_default
        VodSort.ADDED -> R.string.vod_sort_added
        VodSort.NAME -> R.string.vod_sort_name
        VodSort.RATING -> R.string.vod_sort_rating
        VodSort.YEAR -> R.string.vod_sort_year
    }
