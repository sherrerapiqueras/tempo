package com.mandrecode.tempo.features.focus.domain.model

import com.mandrecode.tempo.features.tasks.domain.model.Task

/**
 * A session the user has named and kept: "Study", 25 minutes, 5 of rest, four rounds (#517).
 *
 * Not a task. It has nothing to complete, no date and no reminder, and it lives on Focus alone — it
 * is a way of spending time, where a task is a thing to get done. It may point at a task, and then
 * the time it runs is that task's time.
 *
 * Listed in the order they were made, which is the order of [id].
 */
data class SavedSession(
    val id: Long = 0,
    val title: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int = 1,
    /** The task this session works on, or `null` when it stands alone. */
    val taskId: Long? = null,
) {
    /** The same session with every number inside what a timer can run, and its name trimmed. */
    fun normalized(): SavedSession =
        copy(
            title = title.trim(),
            focusMinutes = focusMinutes.coerceIn(FocusSession.SESSION_LENGTH_RANGE),
            breakMinutes = breakMinutes.coerceIn(FocusSession.BREAK_LENGTH_RANGE),
            rounds = rounds.coerceIn(FocusSession.ROUNDS_RANGE),
        )

    companion object {
        /** The id of a session that has not been saved yet. */
        const val NEW_ID: Long = 0
    }

    /** Same name and same numbers: what a merge import calls "already here". */
    fun hasSameSetupAs(other: SavedSession): Boolean =
        title == other.title &&
            focusMinutes == other.focusMinutes &&
            breakMinutes == other.breakMinutes &&
            rounds == other.rounds
}

/** A saved session with the task it points at resolved, for the surfaces that show both. */
data class SavedSessionEntry(
    val session: SavedSession,
    val linkedTask: Task? = null,
)
