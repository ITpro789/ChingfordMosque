package com.chingfordmosque.prayertimes.android.platform

import android.content.Context
import android.content.SharedPreferences
import com.chingfordmosque.prayertimes.data.repository.LocalStore
import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android binding of the core [LocalStore] seam over [SharedPreferences].
 *
 * A single [CachedSchedule] is serialised to a compact JSON document (via `org.json`, which
 * ships with Android) and reconstructed on read using ONLY the core smart constructors
 * ([Date.of], [Time.of], [PrayerTime.of], [JummahTimes.of], [DaySchedule.of], [DateTime.of]).
 * If the stored document is absent, malformed, or fails any domain validation rule, [read]
 * returns [Option.None] rather than throwing — the repository then behaves exactly as it would
 * with an empty cache. [write] overwrites; [delete] clears.
 *
 * Persisted shape:
 * ```
 * {
 *   "scheduleDate": { "y": 2026, "mo": 6, "d": 30 },
 *   "fetchedAt":    { "y": 2026, "mo": 6, "d": 30, "h": 7, "mi": 12, "s": 5 },
 *   "prayers": [ { "prayer": "Fajr", "beginsHour": 3, "beginsMinute": 3,
 *                  "iqamahHour": 3, "iqamahMinute": 30 }, ... ],
 *   "jummah":  [ { "h": 13, "mi": 0 }, { "h": 13, "mi": 30 } ]   // null/absent when none
 * }
 * ```
 */
class PrefsLocalStore(
    context: Context,
) : LocalStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): Option<CachedSchedule> {
        val raw = prefs.getString(KEY_SCHEDULE, null) ?: return Option.None
        return Option.ofNullable(runCatching { reconstruct(JSONObject(raw)) }.getOrNull())
    }

    override fun write(value: CachedSchedule) {
        val json = runCatching { serialize(value).toString() }.getOrNull()
        if (json == null) {
            // Never persist a half-encoded document.
            delete()
            return
        }
        prefs.edit().putString(KEY_SCHEDULE, json).apply()
    }

    override fun delete() {
        prefs.edit().remove(KEY_SCHEDULE).apply()
    }

    // --- serialisation -----------------------------------------------------------------

    private fun serialize(cached: CachedSchedule): JSONObject {
        val schedule = cached.schedule
        val root = JSONObject()

        root.put(
            "scheduleDate",
            JSONObject()
                .put("y", schedule.scheduleDate.year)
                .put("mo", schedule.scheduleDate.month)
                .put("d", schedule.scheduleDate.day),
        )

        val fetchedAt = cached.fetchedAt
        root.put(
            "fetchedAt",
            JSONObject()
                .put("y", fetchedAt.date.year)
                .put("mo", fetchedAt.date.month)
                .put("d", fetchedAt.date.day)
                .put("h", fetchedAt.hour)
                .put("mi", fetchedAt.minute)
                .put("s", fetchedAt.second),
        )

        val prayers = JSONArray()
        for (pt in schedule.prayers) {
            val obj = JSONObject()
                .put("prayer", pt.prayer.name)
                .put("beginsHour", pt.beginsAt.hour)
                .put("beginsMinute", pt.beginsAt.minute)
            pt.iqamahAt.fold(
                onSome = { iqamah ->
                    obj.put("iqamahHour", iqamah.hour)
                    obj.put("iqamahMinute", iqamah.minute)
                },
                onNone = { /* omit iqamah keys entirely */ },
            )
            prayers.put(obj)
        }
        root.put("prayers", prayers)

        schedule.jummah.fold(
            onSome = { jummah ->
                val arr = JSONArray()
                for (t in jummah.jamaahTimes) {
                    arr.put(JSONObject().put("h", t.hour).put("mi", t.minute))
                }
                root.put("jummah", arr)
            },
            onNone = { /* omit the key entirely */ },
        )

        return root
    }

    // --- reconstruction (smart constructors only) --------------------------------------

    /** Rebuild a [CachedSchedule], or return null if anything is missing/invalid. */
    private fun reconstruct(root: JSONObject): CachedSchedule? {
        val scheduleDateObj = root.optJSONObject("scheduleDate") ?: return null
        val scheduleDate = Date.of(
            scheduleDateObj.getInt("y"),
            scheduleDateObj.getInt("mo"),
            scheduleDateObj.getInt("d"),
        ).getOrNull() ?: return null

        val fetchedAtObj = root.optJSONObject("fetchedAt") ?: return null
        val fetchedDate = Date.of(
            fetchedAtObj.getInt("y"),
            fetchedAtObj.getInt("mo"),
            fetchedAtObj.getInt("d"),
        ).getOrNull() ?: return null
        val fetchedAt = DateTime.of(
            fetchedDate,
            fetchedAtObj.getInt("h"),
            fetchedAtObj.getInt("mi"),
            fetchedAtObj.getInt("s"),
        ).getOrNull() ?: return null

        val prayersArr = root.optJSONArray("prayers") ?: return null
        val prayerTimes = ArrayList<PrayerTime>(prayersArr.length())
        for (i in 0 until prayersArr.length()) {
            val obj = prayersArr.optJSONObject(i) ?: return null
            val prayer = runCatching { Prayer.valueOf(obj.getString("prayer")) }.getOrNull()
                ?: return null
            val begins = Time.of(obj.getInt("beginsHour"), obj.getInt("beginsMinute"))
                .getOrNull() ?: return null
            val iqamah: Option<Time> = if (obj.has("iqamahHour") && obj.has("iqamahMinute")) {
                val t = Time.of(obj.getInt("iqamahHour"), obj.getInt("iqamahMinute"))
                    .getOrNull() ?: return null
                Option.Some(t)
            } else {
                Option.None
            }
            val prayerTime = PrayerTime.of(prayer, begins, iqamah).getOrNull() ?: return null
            prayerTimes.add(prayerTime)
        }

        val jummah: Option<JummahTimes> = run {
            val arr = root.optJSONArray("jummah") ?: return@run Option.None
            val times = ArrayList<Time>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: return null
                val t = Time.of(obj.getInt("h"), obj.getInt("mi")).getOrNull() ?: return null
                times.add(t)
            }
            val built = JummahTimes.of(times).getOrNull() ?: return null
            Option.Some(built)
        }

        val schedule = DaySchedule.of(scheduleDate, prayerTimes, jummah).getOrNull() ?: return null
        return CachedSchedule(schedule, fetchedAt)
    }

    companion object {
        private const val PREFS_NAME = "prayer_times_cache"
        private const val KEY_SCHEDULE = "cached_schedule"
    }
}
