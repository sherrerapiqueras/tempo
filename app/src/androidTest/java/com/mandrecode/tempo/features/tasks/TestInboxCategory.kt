package com.mandrecode.tempo.features.tasks

import com.mandrecode.tempo.core.domain.model.DEFAULT_INBOX_UID
import com.mandrecode.tempo.features.tasks.domain.model.Category

/**
 * A default Inbox category for tests. Identified the way the app identifies it — the default flag
 * and the well-known uid — with an ordinary id, not the retired reserved `-1` (#326).
 */
val TEST_INBOX_CATEGORY = Category(id = 99L, name = "Inbox", isDefault = true, uid = DEFAULT_INBOX_UID)
