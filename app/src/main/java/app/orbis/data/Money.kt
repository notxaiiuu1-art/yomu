package app.orbis.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Money {
    private val symbols = DecimalFormatSymbols(Locale.GERMANY)
    private val format = DecimalFormat("#,##0.00", symbols)

    /** 170000 -> "1.700,00" */
    fun plain(cents: Long): String = format.format(cents / 100.0)

    fun format(cents: Long, s: Settings, signed: Boolean = false): String {
        val sign = when {
            signed && cents > 0 -> "+"
            cents < 0 -> "−"
            else -> ""
        }
        val body = plain(abs(cents))
        return if (s.symbolBefore) "$sign${s.currencySymbol} $body" else "$sign$body ${s.currencySymbol}"
    }

    /** "1.234,5" / "12.5" / "7" -> cents, or null when not a valid amount. */
    fun parse(text: String): Long? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Amount typed on the keypad ("12,5") -> cents. */
    fun fromInput(input: String): Long = parse(input) ?: 0L
}

object Dates {
    private val day = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)
    private val full = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY)
    private val time = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val month = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
    private val shortDay = SimpleDateFormat("dd.MM.", Locale.GERMANY)
    private val weekday = SimpleDateFormat("EE", Locale.GERMANY)

    fun full(t: Long): String = full.format(Date(t))
    fun time(t: Long): String = time.format(Date(t))
    fun month(t: Long): String = month.format(Date(t)).replaceFirstChar { it.uppercase() }
    fun shortDay(t: Long): String = shortDay.format(Date(t))
    fun weekday(t: Long): String = weekday.format(Date(t)).take(2)

    fun startOfDay(t: Long): Long = Calendar.getInstance().run {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun startOfMonth(t: Long): Long = Calendar.getInstance().run {
        timeInMillis = startOfDay(t)
        set(Calendar.DAY_OF_MONTH, 1)
        timeInMillis
    }

    fun addMonths(t: Long, n: Int): Long = Calendar.getInstance().run {
        timeInMillis = t
        add(Calendar.MONTH, n)
        timeInMillis
    }

    fun daysInMonth(t: Long): Int = Calendar.getInstance().run {
        timeInMillis = t
        getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun dayHeader(t: Long, now: Long = System.currentTimeMillis()): String {
        val d = startOfDay(t)
        val today = startOfDay(now)
        return when (d) {
            today -> "Heute"
            today - DAY -> "Gestern"
            else -> day.format(Date(t))
        }
    }

    fun relative(t: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - t) / 1000
        return when {
            diff < 10 -> "gerade eben"
            diff < 60 -> "vor ${diff}s"
            diff < 3600 -> "vor ${diff / 60} Min."
            diff < 86400 -> "vor ${diff / 3600} Std."
            else -> full(t)
        }
    }

    const val DAY = 24L * 60 * 60 * 1000
}
