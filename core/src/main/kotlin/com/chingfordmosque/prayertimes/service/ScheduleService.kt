package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Duration
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime

/**
 * Pure domain logic over a [DaySchedule]: determine the next upcoming prayer relative to a
 * supplied "now" and the countdown until it begins, plus the ordered list of prayers for
 * display.
 *
 * The service is intentionally side-effect free (design, Component 3): it performs no I/O and
 * never reads a system clock — the current instant is always passed in as a [DateTime] so the
 * logic is deterministic and trivially testable.
 *
 * "Next prayer" is computed from prayer **begin** times among the *alerting* prayers only;
 * Sunrise is informational and is therefore excluded from next-prayer / countdown computation
 * while still being included in [orderedPrayers] for display (Requirements 4.1, 4.2).
 */
object ScheduleService {

    /**
     * The next alerting prayer whose begin time is strictly after [now].
     *
     * Begin times are interpreted on the schedule's own [DaySchedule.scheduleDate], so the
     * comparison against [now] is a full instant comparison (handling a "now" that is before,
     * during, or after the schedule's day).
     *
     * When every alerting prayer in [schedule] has already begun relative to [now], the first
     * alerting prayer of [nextDaySchedule] is returned when that schedule is available
     * (Requirement 4.5); otherwise [Option.None] is returned to indicate none remain.
     *
     * Sunrise is never returned (Requirement 4.2, design Property 6).
     *
     * @param schedule today's schedule.
     * @param now the current instant (injected; the service holds no clock).
     * @param nextDaySchedule optionally, the following day's schedule used only when today's
     *   alerting prayers are exhausted.
     */
    fun getNextPrayer(
        schedule: DaySchedule,
        now: DateTime,
        nextDaySchedule: Option<DaySchedule> = Option.None,
    ): Option<PrayerTime> =
        nextPrayerWithInstant(schedule, now, nextDaySchedule).map { it.first }

    /**
     * The [Duration] remaining until the next alerting prayer begins, derived from the same
     * next-prayer selection as [getNextPrayer] (Requirement 4.3). [Option.None] when no prayer
     * remains (and no next-day schedule is supplied).
     *
     * The duration is always non-negative because the next prayer's begin instant is strictly
     * after [now] (see [DateTime.durationUntil]).
     */
    fun timeUntilNext(
        schedule: DaySchedule,
        now: DateTime,
        nextDaySchedule: Option<DaySchedule> = Option.None,
    ): Option<Duration> =
        nextPrayerWithInstant(schedule, now, nextDaySchedule).map { (_, beginsAtInstant) ->
            now.durationUntil(beginsAtInstant)
        }

    /**
     * The schedule's prayers in canonical chronological order for display (Requirement 2.1),
     * including Sunrise as an informational entry (Requirement 2.3 — Sunrise carries no iqamah,
     * already enforced by [PrayerTime]).
     */
    fun orderedPrayers(schedule: DaySchedule): List<PrayerTime> =
        schedule.prayers.sortedBy { it.prayer.canonicalIndex }

    /**
     * Selects the next alerting prayer together with its absolute begin instant, so both
     * [getNextPrayer] (which needs the prayer) and [timeUntilNext] (which needs the instant)
     * share one source of truth.
     */
    private fun nextPrayerWithInstant(
        schedule: DaySchedule,
        now: DateTime,
        nextDaySchedule: Option<DaySchedule>,
    ): Option<Pair<PrayerTime, DateTime>> {
        val today = nextAlertingAfter(schedule, now)
        if (today is Option.Some) return today

        // Today's alerting prayers are exhausted: fall back to the next day's first prayer
        // when that schedule is available, otherwise none remain.
        return when (nextDaySchedule) {
            is Option.Some -> nextAlertingAfter(nextDaySchedule.value, now)
            is Option.None -> Option.None
        }
    }

    /**
     * The alerting prayer in [schedule] with the smallest begin instant strictly after [now],
     * paired with that instant. Sunrise is excluded. [Option.None] when none qualify.
     */
    private fun nextAlertingAfter(
        schedule: DaySchedule,
        now: DateTime,
    ): Option<Pair<PrayerTime, DateTime>> {
        val candidate = schedule.prayers
            .asSequence()
            .filter { it.prayer.isAlerting }
            .map { it to DateTime.of(schedule.scheduleDate, it.beginsAt) }
            .filter { (_, instant) -> instant > now }
            .minByOrNull { (_, instant) -> instant }
        return Option.ofNullable(candidate)
    }

    /**
     * The "current prayer period" status relative to [now] — the data driving the circular
     * countdown timer (see [PrayerStatus]).
     *
     * Windows are computed on the schedule's own [DaySchedule.scheduleDate] using prayer begin
     * instants, encoding these period rules exactly:
     *  1. Carryover Isha:  [scheduleDate 00:00:00, Fajr.begin)
     *  2. Fajr:            [Fajr.begin, Sunrise.begin)
     *  3. Zuhr:            [Zuhr.begin, Asr.begin)
     *  4. Asr:             [Asr.begin, Maghrib.begin)
     *  5. Maghrib:         [Maghrib.begin, Isha.begin)
     *  6. Isha:            [Isha.begin, next-day Fajr.begin)
     *
     * A window is skipped if any begin it requires is absent from the schedule. If [now] falls
     * inside a window → [PrayerStatus.Active]. Otherwise the earliest window starting after
     * [now] → [PrayerStatus.Upcoming] (with [PrayerStatus.Upcoming.windowStartsAt] = the latest
     * preceding window-end at/ before [now], or midnight). Otherwise → [PrayerStatus.None].
     */
    fun getPrayerStatus(schedule: DaySchedule, now: DateTime): PrayerStatus {
        val date = schedule.scheduleDate
        val midnight = DateTime.of(date, 0, 0, 0).getOrThrow()

        fun begin(p: Prayer): DateTime? =
            schedule.prayer(p).getOrNull()?.let { DateTime.of(date, it.beginsAt) }

        val fajr = begin(Prayer.Fajr)
        val sunrise = begin(Prayer.Sunrise)
        val zuhr = begin(Prayer.Zuhr)
        val asr = begin(Prayer.Asr)
        val maghrib = begin(Prayer.Maghrib)
        val isha = begin(Prayer.Isha)
        val nextDayFajr = schedule.prayer(Prayer.Fajr).getOrNull()
            ?.let { DateTime.of(date.nextDay(), it.beginsAt) }

        // (prayer, start, end) windows, in chronological order; only those fully defined.
        val windows = buildList {
            if (fajr != null) add(Triple(Prayer.Isha, midnight, fajr))            // carryover
            if (fajr != null && sunrise != null) add(Triple(Prayer.Fajr, fajr, sunrise))
            if (zuhr != null && asr != null) add(Triple(Prayer.Zuhr, zuhr, asr))
            if (asr != null && maghrib != null) add(Triple(Prayer.Asr, asr, maghrib))
            if (maghrib != null && isha != null) add(Triple(Prayer.Maghrib, maghrib, isha))
            if (isha != null && nextDayFajr != null) add(Triple(Prayer.Isha, isha, nextDayFajr))
        }

        // Inside a window [start, end) -> Active.
        windows.firstOrNull { (_, start, end) -> now >= start && now < end }
            ?.let { (prayer, start, end) -> return PrayerStatus.Active(prayer, start, end) }

        // Earliest window starting strictly after now -> Upcoming.
        val upcoming = windows.filter { (_, start, _) -> start > now }
            .minByOrNull { (_, start, _) -> start }
            ?: return PrayerStatus.None

        // windowStartsAt = latest preceding window-end that is <= now, else midnight.
        val windowStart = windows
            .map { (_, _, end) -> end }
            .filter { it <= now }
            .maxOrNull()
            ?: midnight

        return PrayerStatus.Upcoming(
            prayer = upcoming.first,
            windowStartsAt = windowStart,
            beginsAt = upcoming.second,
        )
    }
}
