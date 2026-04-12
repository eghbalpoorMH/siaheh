package ir.siaheh.util

/**
 * Jalali (Shamsi) ↔ Gregorian converter.
 *
 * Based on the standard algorithm by Kazimierz M. Borkowski.
 */
object JalaliConverter {

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        /** Format as yyyy-MM-dd Persian string for display. */
        fun toDisplayString(): String {
            val y = year.toString().toPersianDigits()
            val m = month.toString().padStart(2, '0').toPersianDigits()
            val d = day.toString().padStart(2, '0').toPersianDigits()
            return "$y/$m/$d"
        }

        fun monthName(): String = persianMonths[month - 1]
    }

    data class GregorianDate(val year: Int, val month: Int, val day: Int) {
        /** Format as yyyy-MM-dd for API. */
        fun toApiString(): String =
            "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند",
    )

    fun isJalaliLeapYear(jy: Int): Boolean {
        val breaks = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
        )
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) {
                var n = jy - jp
                if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
                var leapJ = ((n + 1) % 33 - 1) % 4
                if (leapJ == -1) leapJ = 4
                return leapJ == 0
            }
            jp = jm
        }
        return false
    }

    fun jalaliMonthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isJalaliLeapYear(jy)) 30 else 29
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): GregorianDate {
        val jy1 = jy - 979
        val jm1 = jm - 1
        val jd1 = jd - 1

        var jDayNo = 365 * jy1 + (jy1 / 33) * 8 + (jy1 % 33 + 3) / 4
        for (i in 0 until jm1) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jd1

        var gDayNo = jDayNo + 79

        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097

        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }

        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461

        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }

        val gDaysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && gDayNo >= gDaysInMonth[gm]) {
            gDayNo -= gDaysInMonth[gm]
            gm++
        }

        return GregorianDate(gy, gm + 1, gDayNo + 1)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy1 = gy - 1600
        val gm1 = gm - 1
        val gd1 = gd - 1

        var gDayNo = 365 * gy1 + ((gy1 + 3) / 4) - ((gy1 + 99) / 100) + ((gy1 + 399) / 400)
        for (i in 0 until gm1) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm1 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd1

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        while (jm < 11) {
            val jml = if (jm < 6) 31 else 30
            if (jDayNo < jml) break
            jDayNo -= jml
            jm++
        }

        return JalaliDate(jy, jm + 1, jDayNo + 1)
    }

    /** Parse "yyyy-MM-dd" Gregorian string to JalaliDate. */
    fun fromApiString(dateStr: String): JalaliDate? {
        return try {
            val parts = dateStr.split("-")
            gregorianToJalali(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: Exception) {
            null
        }
    }
}
