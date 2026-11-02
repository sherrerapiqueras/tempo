package com.mandrecode.tempo.core.data.entity

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.TypeConverter
import com.mandrecode.tempo.core.domain.model.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class Converters {
    @TypeConverter
    fun fromString(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    @TypeConverter
    fun dateToString(date: LocalDateTime?): String? = date?.toString()

    @TypeConverter
    fun fromLocalDateString(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun localDateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromColorToInt(color: Color?): Int? = color?.toArgb()

    @TypeConverter
    fun fromIntToColor(value: Int?): Color? = value?.let { Color(it) }

    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>?): String? = value?.joinToString(",") { it.value.toString() }

    @TypeConverter
    fun toDayOfWeekSet(value: String?): Set<DayOfWeek>? =
        value
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull {
                it.toIntOrNull()?.let { num -> DayOfWeek.fromValue(num) }
            }?.toSet()
}
