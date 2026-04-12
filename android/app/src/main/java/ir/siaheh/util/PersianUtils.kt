package ir.siaheh.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val persianDigits = charArrayOf('\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4', '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9')
private val latinDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

fun String.toPersianDigits(): String = buildString {
    for (c in this@toPersianDigits) {
        if (c in '0'..'9') append(persianDigits[c - '0'])
        else append(c)
    }
}

fun Int.toPersianDigits(): String = this.toString().toPersianDigits()

fun String.toLatinDigits(): String = buildString {
    for (c in this@toLatinDigits) {
        val idx = persianDigits.indexOf(c)
        if (idx >= 0) append(latinDigits[idx])
        else append(c)
    }
}

fun Int.formatPersianNumber(): String {
    val formatted = "%,d".format(this)
    return formatted.toPersianDigits()
}

fun Int.formatPrice(): String = "${this.formatPersianNumber()} \u062A\u0648\u0645\u0627\u0646"

fun isValidPhone(phone: String): Boolean {
    val latin = phone.toLatinDigits().replace(" ", "")
    return latin.matches(Regex("^09\\d{9}$"))
}

fun normalizePhone(phone: String): String =
    phone.toLatinDigits().replace(" ", "")

fun isValidOtp(code: String): Boolean {
    val latin = code.toLatinDigits()
    return latin.matches(Regex("^\\d{5}$"))
}

fun formatRelativeTime(isoDate: String?): String {
    if (isoDate == null) return ""
    return try {
        val instant = parseIsoToInstant(isoDate) ?: return ""
        val now = Instant.now()
        val diff = Duration.between(instant, now)
        val minutes = diff.toMinutes()
        val hours = diff.toHours()
        val days = diff.toDays()
        val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()

        when {
            minutes < 1 -> "\u0627\u0644\u0627\u0646"
            minutes < 60 -> "${minutes.toInt().toPersianDigits()} \u062F\u0642\u06CC\u0642\u0647 \u067E\u06CC\u0634"
            hours < 24 -> "${hours.toInt().toPersianDigits()} \u0633\u0627\u0639\u062A \u067E\u06CC\u0634"
            days < 7 -> "${days.toInt().toPersianDigits()} \u0631\u0648\u0632 \u067E\u06CC\u0634"
            else -> localDateTime.format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.US)).toPersianDigits()
        }
    } catch (_: Exception) {
        ""
    }
}

fun formatTime(isoDate: String?): String {
    if (isoDate == null) return ""
    return try {
        val instant = parseIsoToInstant(isoDate) ?: return ""
        instant
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            .toPersianDigits()
    } catch (_: Exception) {
        ""
    }
}

private fun parseIsoToInstant(raw: String): Instant? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    // 1) Full ISO strings with timezone or offset (preferred)
    runCatching {
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant()
    }

    // 2) Pure UTC instant form
    runCatching {
        return Instant.parse(value)
    }

    // 3) Datetime without offset: treat as local device time (no extra shift)
    runCatching {
        return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }

    return null
}
