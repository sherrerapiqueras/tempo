package com.mandrecode.tempo.core.domain.model

/**
 * Value of a record's `uid` meaning "not yet stored". The database assigns every stored record a
 * stable, globally unique identifier (a lowercase canonical UUID) on insert, and never changes it
 * afterwards (#30). Unlike the local autoincrement `id`, a `uid` means the same thing on every
 * device.
 */
const val UNSET_UID = ""

/**
 * The well-known `uid` of the seeded default (Inbox) category, identical on every install so two
 * installs' Inboxes are recognised as the same category.
 */
const val DEFAULT_INBOX_UID = "00000000-0000-4000-8000-000000000001"

/**
 * Value of `createdAt` / `updatedAt` meaning "not yet stored": the database stamps the current
 * time on insert. Any other value is kept as supplied (restore, merge, future sync).
 */
const val UNSET_TIMESTAMP = 0L
