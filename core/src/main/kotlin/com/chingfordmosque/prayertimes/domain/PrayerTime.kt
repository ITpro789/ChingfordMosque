package com.chingfordmosque.prayertimes.domain

/**
 * A single prayer's entry in a day's schedule: which [prayer] it is, its canonical 24-hour
 * begin/adhan time ([beginsAt]), and its optional congregational/iqamah time ([iqamahAt]).
 *
 * Instances can only be created through the validating [of] smart constructor, so an invalid
 * [PrayerTime] (iqamah before begin, or a Sunrise that carries an iqamah) cannot exist.
 *
 * Validation rules (from design, Model 2):
 * - [beginsAt] must be a valid time of day — guaranteed structurally by the [Time] type.
 * - When present, [iqamahAt] must be `>= beginsAt`.
 * - Sunrise must NOT carry an [iqamahAt] (it is informational only).
 */
class PrayerTime private constructor(
    val prayer: Prayer,
    val beginsAt: Time,
    val iqamahAt: Option<Time>,
    val customName: String? = null,
) {

    val name: String get() = customName ?: prayer.name

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is PrayerTime &&
                other.prayer == prayer &&
                other.beginsAt == beginsAt &&
                other.iqamahAt == iqamahAt &&
                other.customName == customName)

    override fun hashCode(): Int {
        var result = prayer.hashCode()
        result = 31 * result + beginsAt.hashCode()
        result = 31 * result + iqamahAt.hashCode()
        result = 31 * result + (customName?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val iqamah = iqamahAt.fold(onSome = { " iqamah=$it" }, onNone = { "" })
        val custom = customName?.let { " customName=$it" } ?: ""
        return "PrayerTime($prayer begins=$beginsAt$iqamah$custom)"
    }

    companion object {
        /**
         * Create a validated [PrayerTime].
         *
         * @param iqamahAt the congregational time; defaults to [Option.None].
         * @return [Result.Ok] with the value, or [Result.Err] with a descriptive message when
         *   a validation rule is violated.
         */
        fun of(
            prayer: Prayer,
            beginsAt: Time,
            iqamahAt: Option<Time> = Option.None,
            customName: String? = null,
        ): Result<PrayerTime, String> {
            when (iqamahAt) {
                is Option.Some -> {
                    if (prayer == Prayer.Sunrise) {
                        return Result.Err("Sunrise must not carry an iqamah time")
                    }
                    if (iqamahAt.value < beginsAt) {
                        return Result.Err(
                            "iqamah (${iqamahAt.value}) must not be before begins ($beginsAt) for $prayer",
                        )
                    }
                }
                is Option.None -> { /* no iqamah is always valid */ }
            }
            return Result.Ok(PrayerTime(prayer, beginsAt, iqamahAt, customName))
        }
    }
}
