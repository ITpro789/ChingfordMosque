package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * One prayer row as read from the "Daily Salah Times" widget, before any schedule-level
 * validation: which [prayer] it is, its normalized 24-hour begin time, and its optional
 * iqamah time (always [Option.None] for Sunrise).
 *
 * This is an *intermediate* structure. The smart constructor [com.chingfordmosque.prayertimes.domain.PrayerTime.of]
 * already enforces the per-entry invariants (iqamah-not-before-begin, Sunrise-has-no-iqamah);
 * the schedule-level invariants (all required salah present, strictly increasing begins) are
 * applied during assembly in task 5.3, not here.
 */
data class ParsedPrayerEntry(
    val prayer: Prayer,
    val beginsAt: Time,
    val iqamahAt: Option<Time>,
)

/**
 * The raw, source-specific result of parsing the mosque page, kept deliberately as a flat
 * intermediate so that schedule completeness/ordering validation and the final assembly into a
 * [com.chingfordmosque.prayertimes.domain.DaySchedule] live in task 5.3 — not in this parser.
 *
 * @property scheduleDate the date the widget says the times apply to, when it could be parsed
 *   (best-effort; missing/garbled dates are surfaced as [Option.None] rather than failing the
 *   whole parse, since the daily salah times are the essential payload).
 * @property entries the prayer rows found, in the order encountered (canonical on this site).
 * @property jummah the Friday jamā'ah times when the "Jummah Timing" line is present and
 *   parseable; [Option.None] when the line is absent (Requirement 3.3 — omit without error).
 */
data class ParsedSalahTimes(
    val scheduleDate: Option<Date>,
    val entries: List<ParsedPrayerEntry>,
    val jummah: Option<JummahTimes>,
)

/**
 * The single source-specific adapter that understands the shape of the chingfordmosque.com
 * "Daily Salah Times" widget and "Jummah Timing" line, and turns raw (untrusted) HTML into the
 * neutral [ParsedSalahTimes] intermediate (design Component 1; Requirements 1.2, 1.3, 1.4, 3.1,
 * 3.3, 8.2, 8.4).
 *
 * ALL knowledge of the website's markup, field labels, and time formats is confined here, so
 * that a change to the site only requires updating this class — display, scheduling, and
 * notification logic depend on the validated domain model alone (Requirement 8.4).
 *
 * Parsing is deliberately defensive: the widget is located primarily by its CSS class but falls
 * back to structural cues, prayer names are matched by label (with common aliases) rather than by
 * fragile positional indexing, and any markup/format the parser cannot make sense of is mapped to
 * [ProviderError.ParseError] (Requirement 8.2) rather than throwing.
 *
 * Note: this parser does NOT perform schedule-level validation (missing required salah, strictly
 * increasing begins). That is task 5.3's responsibility; here we only normalize what is present.
 */
class SalahTimesParser {

    /**
     * Parse the mosque homepage HTML into the [ParsedSalahTimes] intermediate.
     *
     * @param html the raw response body from [HttpFetcher] (untrusted; never executed).
     * @return [Result.Ok] with the parsed structure, or [Result.Err] with a
     *   [ProviderError.ParseError] when the widget cannot be located or a time cannot be
     *   normalized.
     */
    fun parse(html: String): Result<ParsedSalahTimes, ProviderError> {
        val doc = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            return Result.Err(ProviderError.ParseError("Unable to parse HTML document: ${e.message}"))
        }

        // Locate the "Daily Salah Times" table. Prefer the widget's own class, but fall back to
        // any table that mentions the prayers, so a class rename alone does not break parsing.
        val table = doc.selectFirst("table.dptTimetable")
            ?: doc.select("table").firstOrNull { tbl ->
                val text = tbl.text().lowercase()
                ANCHOR_PRAYER_LABELS.all { it in text }
            }
            ?: return Result.Err(
                ProviderError.ParseError("Could not locate the 'Daily Salah Times' widget table"),
            )

        val entries = mutableListOf<ParsedPrayerEntry>()
        val seen = mutableSetOf<Prayer>()
        for (row in table.select("tr")) {
            // The prayer label sits in the row's heading cell; non-prayer rows (the date header,
            // the "Prayer/Begins/Iqamah" header) simply do not match a known prayer and are skipped.
            val labelCell = row.selectFirst("th") ?: continue
            val prayer = matchPrayer(labelCell.text()) ?: continue
            if (!seen.add(prayer)) continue // ignore an accidental duplicate row defensively

            val timeCells = row.select("td")
            if (timeCells.isEmpty()) {
                return Result.Err(
                    ProviderError.ParseError("No time cells found for $prayer in the salah table"),
                )
            }

            val beginsAt = when (val r = normalizeTime(timeCells[0].text())) {
                is Result.Ok -> r.value
                is Result.Err -> return Result.Err(
                    ProviderError.ParseError("Invalid begin time for $prayer: ${r.error}"),
                )
            }

            // Sunrise is informational and never carries an iqamah; for every other prayer the
            // second cell, when present, is the iqamah/jamā'ah time.
            val iqamahAt: Option<Time> = if (prayer == Prayer.Sunrise || timeCells.size < 2) {
                Option.None
            } else {
                val raw = timeCells[1].text().trim()
                if (raw.isEmpty()) {
                    Option.None
                } else {
                    when (val r = normalizeTime(raw)) {
                        is Result.Ok -> Option.Some(r.value)
                        is Result.Err -> return Result.Err(
                            ProviderError.ParseError("Invalid iqamah time for $prayer: ${r.error}"),
                        )
                    }
                }
            }

            entries += ParsedPrayerEntry(prayer, beginsAt, iqamahAt)
        }

        if (entries.isEmpty()) {
            return Result.Err(
                ProviderError.ParseError("Salah table located but no prayer rows could be parsed"),
            )
        }

        val scheduleDate = parseScheduleDate(table, doc)

        val jummah = when (val r = parseJummah(doc)) {
            is Result.Ok -> r.value
            is Result.Err -> return r
        }

        return Result.Ok(ParsedSalahTimes(scheduleDate, entries, jummah))
    }

    /**
     * Parse the "Jummah Timing" line into ascending [JummahTimes].
     *
     * @return [Result.Ok] of [Option.Some] when the line is present and at least one time parses;
     *   [Result.Ok] of [Option.None] when the line is absent (Requirement 3.3 — omit without
     *   error); [Result.Err] with a [ProviderError.ParseError] when the line is present but no
     *   usable time can be extracted (unexpected markup, Requirement 8.2).
     */
    private fun parseJummah(doc: Element): Result<Option<JummahTimes>, ProviderError> {
        val label = doc.allElements.firstOrNull {
            it.ownText().trim().lowercase().startsWith(JUMMAH_LABEL)
        } ?: return Result.Ok(Option.None) // no Jummah line published → omit silently

        // The label and its times usually live in the same small container; read the container's
        // text and take everything after the label so we only see the Jummah times.
        val containerText = (label.parent() ?: label).text()
        val idx = containerText.lowercase().indexOf(JUMMAH_LABEL)
        val region = if (idx >= 0) containerText.substring(idx + JUMMAH_LABEL.length) else containerText

        val times = TIME_TOKEN.findAll(region)
            .mapNotNull { normalizeTime(it.value).getOrNull() }
            .toList()

        if (times.isEmpty()) {
            return Result.Err(
                ProviderError.ParseError("'Jummah Timing' line present but no times could be parsed"),
            )
        }

        // Present in ascending chronological order and drop duplicates so JummahTimes.of (which
        // requires a strictly ascending list) accepts the source even if it lists times oddly.
        val ordered = times.distinct().sorted()
        return when (val r = JummahTimes.of(ordered)) {
            is Result.Ok -> Result.Ok(Option.Some(r.value))
            is Result.Err -> Result.Err(
                ProviderError.ParseError("Could not build Jummah times: ${r.error}"),
            )
        }
    }

    /**
     * Best-effort parse of the date the widget applies to (e.g. "June 30, 2026"), read from the
     * table's heading and falling back to a document-wide scan. Returns [Option.None] rather than
     * failing the whole parse when no date can be found — the salah times are the essential data.
     */
    private fun parseScheduleDate(table: Element, doc: Element): Option<Date> {
        val candidates = buildList {
            table.selectFirst("th")?.let { add(it.ownText()) }
            add(table.text())
            add(doc.text())
        }
        for (text in candidates) {
            val date = DATE_REGEX.find(text)?.let { m ->
                val month = MONTHS[m.groupValues[1].lowercase()] ?: return@let null
                val day = m.groupValues[2].toIntOrNull() ?: return@let null
                val year = m.groupValues[3].toIntOrNull() ?: return@let null
                Date.of(year, month, day).getOrNull()
            }
            if (date != null) return Option.Some(date)
        }
        return Option.None
    }

    /** Match a cell label to a [Prayer], tolerating common spelling variants; null if unknown. */
    private fun matchPrayer(rawLabel: String): Prayer? {
        val label = rawLabel.trim().lowercase()
        if (label.isEmpty()) return null
        return PRAYER_ALIASES.entries.firstOrNull { (alias, _) -> label == alias || label.startsWith(alias) }?.value
    }

    companion object {
        /**
         * Normalize a single time token into the canonical 24-hour [Time] (Requirement 1.4).
         * Accepts 12-hour input with an am/pm suffix (e.g. "2:46 am", "9:24pm", "12:00 a.m.") and
         * bare 24-hour input (e.g. "21:24"); the ':' or '.' may separate hours and minutes.
         *
         * @return [Result.Ok] with the [Time], or [Result.Err] with a message when the token is
         *   not a recognizable time of day.
         */
        fun normalizeTime(raw: String): Result<Time, String> {
            val token = raw.trim()
            val match = TIME_EXACT.matchEntire(token)
                ?: return Result.Err("unrecognised time format: '$raw'")
            var hour = match.groupValues[1].toIntOrNull()
                ?: return Result.Err("non-numeric hour in '$raw'")
            val minute = match.groupValues[2].toIntOrNull()
                ?: return Result.Err("non-numeric minute in '$raw'")
            val meridiem = match.groupValues[3].lowercase().replace(".", "")

            when {
                meridiem.startsWith("a") -> { // am: 12 am is midnight (00:xx)
                    if (hour !in 1..12) return Result.Err("hour out of range for am: $hour in '$raw'")
                    if (hour == 12) hour = 0
                }
                meridiem.startsWith("p") -> { // pm: 12 pm stays noon, otherwise + 12
                    if (hour !in 1..12) return Result.Err("hour out of range for pm: $hour in '$raw'")
                    if (hour != 12) hour += 12
                }
                else -> { // bare 24-hour input
                    if (hour !in 0..23) return Result.Err("hour out of range for 24h: $hour in '$raw'")
                }
            }
            return Time.of(hour, minute).mapError { "$it (from '$raw')" }
        }

        /** Prayer names that must all appear in a table for it to be treated as the salah widget. */
        private val ANCHOR_PRAYER_LABELS = listOf("fajr", "maghrib", "isha")

        private const val JUMMAH_LABEL = "jummah timing"

        /** Lowercased label → [Prayer], including common transliteration variants seen in the wild. */
        private val PRAYER_ALIASES: Map<String, Prayer> = linkedMapOf(
            "fajr" to Prayer.Fajr,
            "sunrise" to Prayer.Sunrise,
            "shuruk" to Prayer.Sunrise,
            "shourouk" to Prayer.Sunrise,
            "ishraq" to Prayer.Sunrise,
            "zuhr" to Prayer.Zuhr,
            "dhuhr" to Prayer.Zuhr,
            "duhr" to Prayer.Zuhr,
            "zohr" to Prayer.Zuhr,
            "asr" to Prayer.Asr,
            "maghrib" to Prayer.Maghrib,
            "isha" to Prayer.Isha,
            "esha" to Prayer.Isha,
        )

        /** A time of day anchored to the whole token (for normalizing a single field). */
        private val TIME_EXACT =
            Regex("""(\d{1,2})[:.](\d{2})\s*([ap]\.?m\.?)?""", RegexOption.IGNORE_CASE)

        /** A time of day found anywhere within free text (for the Jummah line). */
        private val TIME_TOKEN =
            Regex("""\d{1,2}[:.]\d{2}\s*[ap]\.?m\.?""", RegexOption.IGNORE_CASE)

        /** "Month Day, Year" as published in the widget heading. */
        private val DATE_REGEX =
            Regex("""([A-Za-z]+)\s+(\d{1,2}),?\s+(\d{4})""")

        private val MONTHS: Map<String, Int> = mapOf(
            "january" to 1, "february" to 2, "march" to 3, "april" to 4,
            "may" to 5, "june" to 6, "july" to 7, "august" to 8,
            "september" to 9, "october" to 10, "november" to 11, "december" to 12,
            // common abbreviations
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7,
            "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )
    }
}
