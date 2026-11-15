package com.mandrecode.tempo.features.tasks.domain.model

/**
 * Stand-in for "no category chosen yet", while categories load or when none exist. It is never a
 * persisted id — Room generates category ids from 1 and the seeded Inbox is -1 — so a task that
 * carries it belongs to nothing and shows up nowhere. Nothing may be created with it.
 */
const val NO_CATEGORY_ID = 0L

data class Category(
    val id: Long = 0,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * Whether the list is split into sections (#61). Turning it off hides them without deleting
     * anything, so turning it back on restores every section and membership.
     */
    val sectionsEnabled: Boolean = false,
)
