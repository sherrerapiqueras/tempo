package com.mandrecode.tempo.core.data.entity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    // LocalDateTime conversions
    @Test
    fun `fromString parses valid ISO date time`() {
        val result = converters.fromString("2024-01-15T10:30:00")
        assertThat(result).isEqualTo(LocalDateTime(2024, 1, 15, 10, 30, 0))
    }

    @Test
    fun `fromString returns null for null input`() {
        assertThat(converters.fromString(null)).isNull()
    }

    @Test
    fun `dateToString serializes LocalDateTime`() {
        val dateTime = LocalDateTime(2024, 6, 15, 14, 30, 0)
        assertThat(converters.dateToString(dateTime)).isEqualTo("2024-06-15T14:30")
    }

    @Test
    fun `dateToString returns null for null input`() {
        assertThat(converters.dateToString(null)).isNull()
    }

    @Test
    fun `LocalDateTime round-trip conversion preserves value`() {
        val original = LocalDateTime(2024, 12, 25, 23, 59, 59)
        val string = converters.dateToString(original)
        val restored = converters.fromString(string)
        assertThat(restored).isEqualTo(original)
    }

    // Color conversions
    @Test
    fun `fromColorToInt converts Color to ARGB int`() {
        val color = Color.Red
        assertThat(converters.fromColorToInt(color)).isEqualTo(color.toArgb())
    }

    @Test
    fun `fromColorToInt returns null for null input`() {
        assertThat(converters.fromColorToInt(null)).isNull()
    }

    @Test
    fun `fromIntToColor converts ARGB int to Color`() {
        val argb = Color.Blue.toArgb()
        assertThat(converters.fromIntToColor(argb)).isEqualTo(Color(argb))
    }

    @Test
    fun `fromIntToColor returns null for null input`() {
        assertThat(converters.fromIntToColor(null)).isNull()
    }

    @Test
    fun `Color round-trip conversion preserves value`() {
        val original = Color(0xFF123456.toInt())
        val asInt = converters.fromColorToInt(original)
        val restored = converters.fromIntToColor(asInt)
        assertThat(restored).isEqualTo(original)
    }

    // DayOfWeek set conversions
    @Test
    fun `fromDayOfWeekSet serializes set to CSV`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val result = converters.fromDayOfWeekSet(days)
        assertThat(result).isNotNull()
        val values = result!!.split(",").map { it.toInt() }.toSet()
        assertThat(values).containsExactly(1, 3, 5)
    }

    @Test
    fun `fromDayOfWeekSet returns null for null input`() {
        assertThat(converters.fromDayOfWeekSet(null)).isNull()
    }

    @Test
    fun `toDayOfWeekSet deserializes CSV to set`() {
        val result = converters.toDayOfWeekSet("1,3,5")
        assertThat(result).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    }

    @Test
    fun `toDayOfWeekSet returns null for null input`() {
        assertThat(converters.toDayOfWeekSet(null)).isNull()
    }

    @Test
    fun `toDayOfWeekSet returns null for blank input`() {
        assertThat(converters.toDayOfWeekSet("")).isNull()
    }

    @Test
    fun `toDayOfWeekSet ignores invalid values`() {
        val result = converters.toDayOfWeekSet("1,99,3")
        assertThat(result).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
    }

    @Test
    fun `DayOfWeek set round-trip preserves value`() {
        val original = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
        val csv = converters.fromDayOfWeekSet(original)
        val restored = converters.toDayOfWeekSet(csv)
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `LocalDate round-trip preserves value`() {
        val date = LocalDate(2027, 3, 31)

        assertThat(converters.localDateToString(date)).isEqualTo("2027-03-31")
        assertThat(converters.fromLocalDateString("2027-03-31")).isEqualTo(date)
    }

    @Test
    fun `LocalDate converters pass null through`() {
        assertThat(converters.localDateToString(null)).isNull()
        assertThat(converters.fromLocalDateString(null)).isNull()
    }
}
