package com.mandrecode.tempo.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.UUID

class EntityIdentityTest {
    @Test
    fun `default inbox uid is a valid lowercase canonical UUID`() {
        assertThat(UUID.fromString(DEFAULT_INBOX_UID).toString()).isEqualTo(DEFAULT_INBOX_UID)
    }

    @Test
    fun `unset uid is empty so the database assigns one`() {
        assertThat(UNSET_UID).isEmpty()
    }
}
