package com.bane23.iptvplayer.ui.common.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.Channel
import com.bane23.iptvplayer.domain.Program

/** Jedna radnja nad emisijom iz panela „Program” (podsetnik, snimanje). */
class ScheduleAction(val label: String, val run: () -> Unit)

/** Raspored kanala za panel „Program”; null dok se čita. Osvežava se kad se promeni kanal. */
@Composable
fun rememberChannelSchedule(channel: Channel, guide: GuideViewModel): State<List<Program>?> =
    produceState<List<Program>?>(initialValue = null, channel.id) { value = guide.schedule(channel.id) }

/**
 * Šta može da se uradi sa emisijom: buduća dobija podsetnik i zakazano snimanje, a ona koja je u
 * toku samo snimanje do svog kraja. Natpisi prate stanje (npr. „Ukloni podsetnik”).
 */
@Composable
fun scheduleActions(
    channel: Channel,
    program: Program,
    now: Long,
    reminders: Set<String>,
    recordings: GuideRecordings,
    guide: GuideViewModel,
): List<ScheduleAction> {
    val key = GuideRecordings.key(channel, program.startMs)
    val recorded = recordings.marks(channel, program, now)
    val record = ScheduleAction(
        label = stringResource(
            when {
                program.startMs > now && recorded -> R.string.recording_schedule_cancel
                program.startMs > now -> R.string.recording_schedule
                recorded -> R.string.recording_chip_active
                else -> R.string.recording_chip
            },
        ),
        run = { guide.toggleRecording(channel, program) },
    )
    if (program.startMs <= now) return listOf(record)
    val reminder = ScheduleAction(
        label = stringResource(if (key in reminders) R.string.reminder_remove else R.string.reminder_add),
        run = { guide.toggleReminder(channel, program) },
    )
    return listOf(reminder, record)
}

/** Oznaka uz emisiju u spisku: zvonce za podsetnik, tačka za snimanje. */
fun scheduleMark(channel: Channel, program: Program, now: Long, reminders: Set<String>, recordings: GuideRecordings): String? =
    when {
        recordings.marks(channel, program, now) -> "●"
        GuideRecordings.key(channel, program.startMs) in reminders -> "🔔"
        else -> null
    }
