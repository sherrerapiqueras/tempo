package com.mandrecode.tempo.features.routines.domain.model

import com.mandrecode.tempo.core.domain.model.DayOfWeek
import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class HabitChain(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val colorKey: String? = null,
    val icon: String? = null,
    val habitIds: List<Long> = emptyList(),
    val periodicReminder: LocalDateTime? = null,
    val createdDate: LocalDateTime =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault()),
    val completionHistory: String = "",
    val repeatDays: Set<DayOfWeek>? = null,
    /** Stable global identity (#30), assigned by the database on insert; [UNSET_UID] until stored. */
    val uid: String = UNSET_UID,
    /** Epoch millis UTC, stamped by the database; [UNSET_TIMESTAMP] until stored. */
    val createdAt: Long = UNSET_TIMESTAMP,
    /** Epoch millis UTC of the last stored change, stamped by the database. */
    val updatedAt: Long = UNSET_TIMESTAMP,
) {
    /**
     * This record saved as a *new* one: no local id and no identity, so the database assigns both.
     * Any copy that becomes another row must go through here — reusing the source's uid fails (#30).
     */
    fun asNew(): HabitChain = copy(id = 0, uid = UNSET_UID, createdAt = UNSET_TIMESTAMP, updatedAt = UNSET_TIMESTAMP)
}
