package com.mandrecode.tempo.infrastructure.receivers

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiverWorkTest {
    @Test
    fun `successful work finishes without reporting an error`() =
        runTest {
            var ran = false
            val errors = mutableListOf<Throwable>()
            var finished = false

            runReceiverWork(block = { ran = true }, onError = { errors += it }, finish = { finished = true })

            assertThat(ran).isTrue()
            assertThat(errors).isEmpty()
            assertThat(finished).isTrue()
        }

    @Test
    fun `a failure is reported instead of thrown and the broadcast still finishes`() =
        runTest {
            val failure = IllegalStateException("database could not be opened")
            val errors = mutableListOf<Throwable>()
            var finished = false

            runReceiverWork(block = { throw failure }, onError = { errors += it }, finish = { finished = true })

            assertThat(errors).containsExactly(failure)
            assertThat(finished).isTrue()
        }

    @Test
    fun `cancellation is not swallowed and the broadcast still finishes`() {
        val errors = mutableListOf<Throwable>()
        var finished = false

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runReceiverWork(
                    block = { throw CancellationException("cancelled") },
                    onError = { errors += it },
                    finish = { finished = true },
                )
            }
        }

        assertThat(errors).isEmpty()
        assertThat(finished).isTrue()
    }
}
