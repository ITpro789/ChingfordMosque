package com.chingfordmosque.prayertimes.data.repository

import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Option
import java.util.concurrent.atomic.AtomicReference

/**
 * The lightweight local key-value store that backs [LocalScheduleRepository]. This is the
 * single platform seam: the JVM build uses [InMemoryLocalStore]; an Android build would
 * implement this interface over DataStore/SharedPreferences/Room (serialising the
 * [CachedSchedule] to/from its native format) **without any change to the repository,
 * domain, or service layers**. That Android binding is intentionally deferred.
 *
 * Implementations MUST treat [write] and [delete] as atomic with respect to [read]: a reader
 * always observes either the fully previous value or the fully new one, never a partial
 * state. (DataStore and a temp-file-then-rename file store both provide this naturally.)
 */
interface LocalStore {

    /** The currently stored value, or [Option.None] when empty. */
    fun read(): Option<CachedSchedule>

    /** Atomically replace the stored value with [value]. */
    fun write(value: CachedSchedule)

    /** Atomically clear the stored value. */
    fun delete()
}

/**
 * In-memory [LocalStore] for the JVM build and tests. Backed by an [AtomicReference] so the
 * single-value swap in [write]/[delete] is atomic and visible across threads — satisfying the
 * repository's atomicity requirement without a real persistence layer.
 *
 * State does not survive process restart; durable persistence is the responsibility of a
 * platform-specific [LocalStore] binding (deferred — see the interface docs).
 */
class InMemoryLocalStore : LocalStore {

    private val ref = AtomicReference<CachedSchedule?>(null)

    override fun read(): Option<CachedSchedule> = Option.ofNullable(ref.get())

    override fun write(value: CachedSchedule) {
        ref.set(value)
    }

    override fun delete() {
        ref.set(null)
    }
}
