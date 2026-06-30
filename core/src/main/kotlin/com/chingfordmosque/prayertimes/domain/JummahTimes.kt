package com.chingfordmosque.prayertimes.domain

/**
 * The Friday (Jummah) jamā'ah times published by the mosque, e.g. the 1st/2nd/3rd jamā'ah.
 *
 * Instances are only creatable through the validating [of] smart constructor.
 *
 * Validation rules (from design, Model 3; Requirement 3.2):
 * - [jamaahTimes] must contain at least one entry (when Jummah data is available, it is
 *   represented by a [JummahTimes]; total absence is modelled as `Option.None` on the
 *   enclosing [DaySchedule]).
 * - Entries must be in ascending chronological order with no duplicates.
 */
class JummahTimes private constructor(
    val jamaahTimes: List<Time>,
) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is JummahTimes && other.jamaahTimes == jamaahTimes)

    override fun hashCode(): Int = jamaahTimes.hashCode()

    override fun toString(): String = "JummahTimes(${jamaahTimes.joinToString(", ")})"

    companion object {
        /**
         * Create a validated [JummahTimes].
         *
         * @return [Result.Ok] with the value, or [Result.Err] with a descriptive message when
         *   the list is empty or the times are not strictly ascending.
         */
        fun of(jamaahTimes: List<Time>): Result<JummahTimes, String> {
            if (jamaahTimes.isEmpty()) {
                return Result.Err("Jummah times must contain at least one entry")
            }
            for (i in 1 until jamaahTimes.size) {
                val prev = jamaahTimes[i - 1]
                val curr = jamaahTimes[i]
                if (curr <= prev) {
                    return Result.Err(
                        "Jummah times must be strictly ascending: $curr does not follow $prev",
                    )
                }
            }
            return Result.Ok(JummahTimes(jamaahTimes.toList()))
        }
    }
}
