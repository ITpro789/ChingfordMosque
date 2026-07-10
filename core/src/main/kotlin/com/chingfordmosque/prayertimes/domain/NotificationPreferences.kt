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
    val isLocalAdhan: Boolean = false,
    val iqamahOffset: Int = 15,
    val durationSeconds: Int = 180, // Default 3 minutes to avoid dua
) {

    /** Whether [prayer] should fire an adhan alert under these preferences. */
    fun isEnabled(prayer: Prayer): Boolean = prayer in enabledPrayers

    fun withPlayAdhanSound(play: Boolean): NotificationPreferences =
        NotificationPreferences(enabledPrayers, play, isLocalAdhan, iqamahOffset, durationSeconds)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is NotificationPreferences &&
                other.enabledPrayers == enabledPrayers &&
                other.playAdhanSound == playAdhanSound &&
                other.isLocalAdhan == isLocalAdhan &&
                other.iqamahOffset == iqamahOffset &&
                other.durationSeconds == durationSeconds)

    override fun hashCode(): Int {
        var result = enabledPrayers.hashCode()
        result = 31 * result + playAdhanSound.hashCode()
        result = 31 * result + isLocalAdhan.hashCode()
        result = 31 * result + iqamahOffset
        result = 31 * result + durationSeconds
        return result
    }

    override fun toString(): String =
        "NotificationPreferences(enabled=$enabledPrayers, playAdhanSound=$playAdhanSound, localAdhan=$isLocalAdhan, offset=$iqamahOffset, duration=$durationSeconds)"

    companion object {
        /**
         * Create preferences. Any non-alerting prayer (Sunrise) present in [enabledPrayers] is
         * silently dropped, upholding the invariant that Sunrise never alerts.
         */
        fun of(
            enabledPrayers: Set<Prayer>,
            playAdhanSound: Boolean,
            isLocalAdhan: Boolean = false,
            iqamahOffset: Int = 15,
            durationSeconds: Int = 180,
        ): NotificationPreferences =
            NotificationPreferences(
                enabledPrayers.filter { it.isAlerting }.toSet(),
                playAdhanSound,
                isLocalAdhan,
                iqamahOffset,
                durationSeconds
            )

        /** Sensible default: every alerting prayer enabled with adhan sound on. */
        fun default(): NotificationPreferences =
            NotificationPreferences(Prayer.alerting().toSet(), playAdhanSound = true)
    }
}
