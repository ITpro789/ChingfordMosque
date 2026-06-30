package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for permission-gated notification scheduling (Requirement 5.7; design Error Scenario 4).
 *
 * When permission is NOT granted the app must keep working (times/countdown are untouched here)
 * while no adhan alert is armed, and the user is prompted exactly once. When permission IS
 * granted the wrapper must behave identically to the underlying [AdhanNotificationScheduler].
 */
class PermissionGatedNotificationSchedulerTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    fun schedule(): DaySchedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Fajr, 5, 0),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Zuhr, 12, 0),
            pt(Prayer.Asr, 15, 0),
            pt(Prayer.Maghrib, 18, 0),
            pt(Prayer.Isha, 20, 0),
        ),
    ).getOrThrow()

    // Before any prayer, so all five alerting prayers are still upcoming.
    val now = DateTime.of(date, 3, 0, 0).getOrThrow()

    fun fixture(granted: Boolean): Triple<PermissionGatedNotificationScheduler, InMemoryAdhanAlarmPort, RecordingNotificationPermissionPrompt> {
        val port = InMemoryAdhanAlarmPort()
        val permission = InMemoryNotificationPermission(granted = granted)
        val prompt = RecordingNotificationPermissionPrompt()
        val gated = PermissionGatedNotificationScheduler(
            delegate = AdhanNotificationScheduler(port),
            permission = permission,
            prompt = prompt,
        )
        return Triple(gated, port, prompt)
    }

    "when permission is granted, alerts are armed exactly as the underlying scheduler" {
        val (gated, port, prompt) = fixture(granted = true)

        gated.reschedule(schedule(), now)

        // Five alerting prayers (Sunrise excluded) are armed; no prompt is shown.
        port.pending().map { it.prayer } shouldBe listOf(
            Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
        prompt.promptCount shouldBe 0
    }

    "when permission is not granted, no alert is armed but the user is prompted once" {
        val (gated, port, prompt) = fixture(granted = false)

        gated.reschedule(schedule(), now)

        port.pending() shouldBe emptyList()
        prompt.promptCount shouldBe 1
    }

    "repeated reschedules without permission prompt the user only once" {
        val (gated, port, prompt) = fixture(granted = false)

        gated.reschedule(schedule(), now)
        gated.reschedule(schedule(), now)
        gated.reschedule(schedule(), now)

        port.pending() shouldBe emptyList()
        prompt.promptCount shouldBe 1
    }

    "granting permission after a denial arms alerts and shows no further prompt" {
        val port = InMemoryAdhanAlarmPort()
        val permission = InMemoryNotificationPermission(granted = false)
        val prompt = RecordingNotificationPermissionPrompt()
        val gated = PermissionGatedNotificationScheduler(
            AdhanNotificationScheduler(port), permission, prompt,
        )

        gated.reschedule(schedule(), now) // denied -> prompted once, nothing armed
        permission.setGranted(true)
        gated.reschedule(schedule(), now) // granted -> armed, no extra prompt

        port.pending().map { it.prayer } shouldBe listOf(
            Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
        prompt.promptCount shouldBe 1
    }

    "revoking permission cancels previously armed alerts so none can fire" {
        val port = InMemoryAdhanAlarmPort()
        val permission = InMemoryNotificationPermission(granted = true)
        val prompt = RecordingNotificationPermissionPrompt()
        val gated = PermissionGatedNotificationScheduler(
            AdhanNotificationScheduler(port), permission, prompt,
        )

        gated.reschedule(schedule(), now) // granted -> alerts armed
        port.pending().isEmpty() shouldBe false

        permission.setGranted(false)
        gated.reschedule(schedule(), now) // revoked -> cancel and prompt

        port.pending() shouldBe emptyList()
        prompt.promptCount shouldBe 1
    }

    "a fresh denial episode after a grant prompts the user again" {
        val port = InMemoryAdhanAlarmPort()
        val permission = InMemoryNotificationPermission(granted = false)
        val prompt = RecordingNotificationPermissionPrompt()
        val gated = PermissionGatedNotificationScheduler(
            AdhanNotificationScheduler(port), permission, prompt,
        )

        gated.reschedule(schedule(), now) // denied -> prompt #1
        permission.setGranted(true)
        gated.reschedule(schedule(), now) // granted -> resets the one-shot guard
        permission.setGranted(false)
        gated.reschedule(schedule(), now) // denied again -> prompt #2

        prompt.promptCount shouldBe 2
    }

    "cancelAll and setPreferences are delegated regardless of permission" {
        val (gated, port, _) = fixture(granted = true)
        gated.reschedule(schedule(), now)
        port.pending().isEmpty() shouldBe false

        gated.cancelAll()
        port.pending() shouldBe emptyList()
    }
})
