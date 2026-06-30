package com.chingfordmosque.prayertimes.domain

/**
 * User preferences governing adhan notifications (design, Model 6):
 * - [enabledPrayers]: which prayers fire an adhan alert. Sunrise is non-alerting and is
 *   therefore filtered out on construction so it can never be enabled.
 * - [playAdhanSound]: whether the adhan audio plays with the notification, versus a
 *   silent/standard notification.
 */
class NotificationPreferences private constructor(
    val enabledPrayers: Set<Prayer>,
    val playAdhanSound: Boolean,
) {

    /** Whether [prayer] should fire an adhan alert under these preferences. */
    fun isEnabled(prayer: Prayer): Boolean = prayer in enabledPrayers

    fun withPlayAdhanSound(play: Boolean): NotificationPreferences =
        NotificationPreferences(enabledPrayers, play)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is NotificationPreferences &&
                other.enabledPrayers == enabledPrayers &&
                other.playAdhanSound == playAdhanSound)

    override fun hashCode(): Int = enabledPrayers.hashCode() * 31 + playAdhanSound.hashCode()

    override fun toString(): String =
        "NotificationPreferences(enabled=$enabledPrayers, playAdhanSound=$playAdhanSound)"

    companion object {
        /**
         * Create preferences. Any non-alerting prayer (Sunrise) present in [enabledPrayers] is
         * silently dropped, upholding the invariant that Sunrise never alerts.
         */
        fun of(
            enabledPrayers: Set<Prayer>,
            playAdhanSound: Boolean,
        ): NotificationPreferences =
            NotificationPreferences(
                enabledPrayers.filter { it.isAlerting }.toSet(),
                playAdhanSound,
            )

        /** Sensible default: every alerting prayer enabled with adhan sound on. */
        fun default(): NotificationPreferences =
            NotificationPreferences(Prayer.alerting().toSet(), playAdhanSound = true)
    }
}
