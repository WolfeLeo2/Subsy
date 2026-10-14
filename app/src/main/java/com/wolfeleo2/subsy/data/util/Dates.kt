package com.wolfeleo2.subsy.data.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(this), zoneId)

fun LocalDate.toEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long = atStartOfDay(zoneId).toInstant().toEpochMilli()

fun today(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zoneId)
