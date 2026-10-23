package com.example.tersetransporttimes

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Commute trace log: one CSV row per GPS fix or app event, one file per UTC day
// in filesDir/gpslog. Used to tune on-train detection and learn walk times
// (see STORIES.md). Shared off the phone by tapping the distance readout.
object GpsLog {
    private const val HEADER = "utc,epoch_ms,kind,lat,lon,acc_m,speed_mps,speed_acc_mps," +
        "bearing_deg,bearing_acc_deg,alt_m,fix_age_s,calc_bearing_deg,dist_line_m,on_train,mode,detail"
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    private fun dir(context: Context) = File(context.filesDir, "gpslog").apply { mkdirs() }

    @Synchronized
    private fun append(context: Context, cols: List<Any?>) {
        val now = System.currentTimeMillis()
        val file = File(dir(context), "${DAY.format(Instant.ofEpochMilli(now))}.csv")
        try {
            if (!file.exists()) file.writeText(HEADER + "\n")
            val row = listOf(Instant.ofEpochMilli(now).toString(), now) + cols
            file.appendText(row.joinToString(",") { it?.toString()?.replace(",", ";") ?: "" } + "\n")
        } catch (e: Exception) {
            android.util.Log.e("GpsLog", "append failed", e)
        }
    }

    fun fix(context: Context, loc: Location, calcBearing: Float?, distToLine: Float?,
            onTrain: Boolean, mode: LocationMode?) {
        val ageS = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1e9
        append(context, listOf(
            "fix", loc.latitude, loc.longitude,
            if (loc.hasAccuracy()) loc.accuracy else null,
            if (loc.hasSpeed()) loc.speed else null,
            if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond else null,
            if (loc.hasBearing()) loc.bearing else null,
            if (loc.hasBearingAccuracy()) loc.bearingAccuracyDegrees else null,
            if (loc.hasAltitude()) loc.altitude else null,
            "%.1f".format(ageS), calcBearing, distToLine?.toInt(), onTrain, mode, null
        ))
    }

    // Non-fix rows: no fix, screen changes, app resume. Location columns stay empty.
    fun event(context: Context, kind: String, detail: String) {
        append(context, listOf(kind) + List(13) { null } + listOf(detail))
    }

    // All days concatenated into one CSV in cache, offered to the share sheet.
    fun shareIntent(context: Context): Intent {
        val out = File(context.cacheDir, "share").apply { mkdirs() }.let { File(it, "t3-gpslog.csv") }
        out.writeText(HEADER + "\n")
        dir(context).listFiles { f -> f.name.endsWith(".csv") }?.sortedBy { it.name }?.forEach { f ->
            f.readLines().drop(1).forEach { out.appendText(it + "\n") }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "T3 GPS log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share T3 GPS log")
    }
}
