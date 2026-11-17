package com.mandrecode.tempo.features.focus.domain.model

import com.mandrecode.tempo.core.domain.model.UNSET_TIMESTAMP
import com.mandrecode.tempo.core.domain.model.UNSET_UID

/**
 * A topic session the user has started before: "Study", 25 minutes, 5 of rest, four rounds (#517).
 *
 * Not a task. It has nothing to complete, no date and no reminder — it is a way of spending time
 * on something general, where a task is a thing to get done.
 *
 * Remembered by use rather than saved. Only the [RECENT_LIMIT] most recently started are kept, so
 * the list offered when starting a new one limits itself: there is nothing to tidy.
 */
data class SavedSession(
    val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int = 1,
    /** Epoch milliseconds of the last start. */
    val lastUsedAt: Long = 0,
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
    fun asNew(): SavedSession = copy(id = 0, uid = UNSET_UID, createdAt = UNSET_TIMESTAMP, updatedAt = UNSET_TIMESTAMP)

    /** The same session with every number inside what a timer can run, and its name trimmed. */
    fun normalized(): SavedSession =
        copy(
            title = title.trim(),
            focusMinutes = focusMinutes.coerceIn(FocusSession.SESSION_LENGTH_RANGE),
            breakMinutes = breakMinutes.coerceIn(FocusSession.BREAK_LENGTH_RANGE),
            rounds = rounds.coerceIn(FocusSession.ROUNDS_RANGE),
        )

    /** The same topic: names are what tell sessions apart, and "study" is "Study". */
    fun hasSameNameAs(other: SavedSession): Boolean = title.trim().equals(other.title.trim(), ignoreCase = true)

    companion object {
        /** The id of a session that has not been remembered yet. */
        const val NEW_ID: Long = 0

        /** How many recent sessions are kept. */
        const val RECENT_LIMIT: Int = 5
    }
}
