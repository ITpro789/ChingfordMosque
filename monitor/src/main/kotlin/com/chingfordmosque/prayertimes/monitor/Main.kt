package com.chingfordmosque.prayertimes.monitor

import com.chingfordmosque.prayertimes.data.provider.HttpTimesProvider
import com.chingfordmosque.prayertimes.data.provider.JvmHttpFetcher
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.SystemClock
import kotlin.system.exitProcess

/**
 * Scrape-health canary for the Chingford Mosque website.
 *
 * This is a tiny, headless JVM program that performs a LIVE fetch + parse of the mosque's
 * "Daily Salah Times" widget through the exact same [HttpTimesProvider] pipeline the app uses.
 * Its sole job is to be an early-warning system: if the mosque changes its page structure (so
 * the parser can no longer extract a valid schedule) or the site becomes unreachable, this
 * program exits non-zero. Wired into a scheduled GitHub Actions job, that non-zero exit turns
 * into a failed run that alerts the maintainers before users ever see broken times.
 *
 * Exit codes:
 * - 0  -> the site was fetched and parsed into a valid schedule (prints the prayers + "OK"),
 * - 1  -> the fetch/parse failed (prints the typed error detail), or an unexpected exception.
 */
fun main() {
    try {
        val provider = HttpTimesProvider(
            fetcher = JvmHttpFetcher(),
            clock = SystemClock(),
        )

        when (val result = provider.fetchTodaySchedule()) {
            is Result.Ok -> {
                val schedule = result.value
                println("Parsed schedule for ${schedule.scheduleDate}:")
                for (prayer in schedule.prayers) {
                    val iqamah = prayer.iqamahAt.fold(
                        onSome = { it.toString() },
                        onNone = { "\u2014" },
                    )
                    println("  ${prayer.prayer}: begins=${prayer.beginsAt} iqamah=$iqamah")
                }
                when (val jummah = schedule.jummah) {
                    is Option.Some -> println("  Jummah: ${jummah.value}")
                    is Option.None -> println("  Jummah: (none published)")
                }
                println("OK")
                exitProcess(0)
            }

            is Result.Err -> {
                System.err.println("SCRAPE HEALTH FAILED: ${result.error}")
                System.err.println("Detail: ${result.error.detail}")
                exitProcess(1)
            }
        }
    } catch (t: Throwable) {
        System.err.println("SCRAPE HEALTH FAILED (exception): ${t.message}")
        exitProcess(1)
    }
}
