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
    val playDua: Boolean = false,
    val azaanVolume: Int = 100,
    val shortAzaan: Boolean = false,
) {

    /** Whether [prayer] should fire an adhan alert under these preferences. */
    fun isEnabled(prayer: Prayer): Boolean = prayer in enabledPrayers

    fun withPlayAdhanSound(play: Boolean): NotificationPreferences =
        NotificationPreferences(enabledPrayers, play, isLocalAdhan, iqamahOffset, playDua, azaanVolume, shortAzaan)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is NotificationPreferences &&
                other.enabledPrayers == enabledPrayers &&
                other.playAdhanSound == playAdhanSound &&
                other.isLocalAdhan == isLocalAdhan &&
                other.iqamahOffset == iqamahOffset &&
                other.playDua == playDua &&
                other.azaanVolume == azaanVolume &&
                other.shortAzaan == shortAzaan)

    override fun hashCode(): Int {
        var result = enabledPrayers.hashCode()
        result = 31 * result + playAdhanSound.hashCode()
        result = 31 * result + isLocalAdhan.hashCode()
        result = 31 * result + iqamahOffset
        result = 31 * result + playDua.hashCode()
        result = 31 * result + azaanVolume
        result = 31 * result + shortAzaan.hashCode()
        return result
    }

    override fun toString(): String =
        "NotificationPreferences(enabled=$enabledPrayers, playAdhanSound=$playAdhanSound, localAdhan=$isLocalAdhan, offset=$iqamahOffset, playDua=$playDua, volume=$azaanVolume, shortAzaan=$shortAzaan)"

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
            playDua: Boolean = false,
            azaanVolume: Int = 100,
            shortAzaan: Boolean = false,
        ): NotificationPreferences =
            NotificationPreferences(
                enabledPrayers.filter { it.isAlerting }.toSet(),
                playAdhanSound,
                isLocalAdhan,
                iqamahOffset,
                playDua,
                azaanVolume,
                shortAzaan
            )

        /** Sensible default: every alerting prayer enabled with adhan sound on. */
        fun default(): NotificationPreferences =
            NotificationPreferences(Prayer.alerting().toSet(), playAdhanSound = true)
    }
}
