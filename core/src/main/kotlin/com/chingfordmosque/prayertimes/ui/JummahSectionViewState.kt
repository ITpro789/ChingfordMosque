package com.chingfordmosque.prayertimes.ui

/**
 * Platform-free view-state for the Jummah (Friday) section of the schedule screen.
 *
 * Modelled as a sealed type so that "no Jummah data available" is an explicit, first-class
 * render state ([Hidden]) rather than an error or a magic empty list. This keeps the section's
 * presence/absence a total, exhaustively-handled decision for any UI binding (Requirement 3.3).
 *
 * The Android view binding that consumes this state is deferred; this type carries only
 * already-formatted, platform-agnostic data (`List<String>` of "HH:mm" times).
 */
sealed class JummahSectionViewState {

    /**
     * The Jummah section is not shown. Produced when the schedule has no Jummah data
     * (`jummah == Option.None`) — the section is omitted without error (Requirement 3.3).
     */
    data object Hidden : JummahSectionViewState()

    enum class JummahStatus { Upcoming, Active, Done }

    /**
     * The Jummah section is shown with the listed jamā'ah [times] in ascending chronological
     * order (Requirements 3.1, 3.2). Each entry is a canonical "HH:mm" string.
     */
    data class Visible(
        val times: List<String>,
        val statuses: List<JummahStatus> = emptyList(),
    ) : JummahSectionViewState()
}
