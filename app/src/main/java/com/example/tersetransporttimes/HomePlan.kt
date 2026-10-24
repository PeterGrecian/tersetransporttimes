package com.example.tersetransporttimes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Screen H (STORIES.md): at home, each of the next K2s with the Surbiton -> Waterloo
// trains it gets you to, plus trains only walking reaches. Rules decided 2026-10-24:
//  - catchable = on the platform >= 2 min before the expected departure
//  - each train appears once, under the latest bus that still makes it
//  - star = earliest Waterloo arrival (among trains inside the 60+ concession)
//  - outside the concession (weekday trains before 09:30, buses before 09:00): greyed, £
//  - 7-stoppers are not offered, but count towards service health

const val WALK_TO_STOP_MIN = 3L        // home -> Parklands stop
const val BUS_TO_PLATFORM_MIN = 12L    // K2 ride + walk to the Surbiton platform
const val WALK_TO_PLATFORM_MIN = 12L   // home -> platform on foot (Timeline median 11.9)
const val PLATFORM_MARGIN_MIN = 2L
const val WAT_TO_WORK_MIN = 21L        // Timeline median, W&C + walks
const val MAX_TRAINS_PER_GROUP = 3
val LONDON: ZoneId = ZoneId.of("Europe/London")

data class PlanTrain(
    val dep: LocalDateTime, val stops: Int, val delayMin: Int,
    val watArrival: LocalDateTime?, val paid: Boolean, var star: Boolean = false
)

data class PlanGroup(
    val busIndex: Int?,            // null = walk now
    val busMin: Int?, val leaveInMin: Int?, val paid: Boolean,
    val trains: List<PlanTrain>
)

data class HomePlanModel(val now: LocalDateTime, val groups: List<PlanGroup>, val healthPct: Int, val workEta: LocalDateTime?)

// "HHmm" (or "HH:mm") on or after `now`'s date; times well before now are tomorrow's
private fun hhmm(s: String, now: LocalDateTime): LocalDateTime? {
    val digits = s.filter { it.isDigit() }
    if (digits.length != 4) return null
    val t = runCatching { LocalTime.of(digits.substring(0, 2).toInt(), digits.substring(2).toInt()) }.getOrNull() ?: return null
    var dt = now.toLocalDate().atTime(t)
    if (dt.isBefore(now.minusHours(6))) dt = dt.plusDays(1)
    return dt
}

private fun weekday(dt: LocalDateTime) = dt.dayOfWeek != DayOfWeek.SATURDAY && dt.dayOfWeek != DayOfWeek.SUNDAY
fun trainPaid(dep: LocalDateTime) = weekday(dep) && dep.toLocalTime().isBefore(LocalTime.of(9, 30))
fun busPaid(at: LocalDateTime) = weekday(at) && at.toLocalTime().isBefore(LocalTime.of(9, 0))

// Share of the service that is broken: each train scores delay/30 min (capped at 1),
// a cancellation scores 1. Draft formula, to tune against Peter's sense of "5%".
fun serviceHealthPct(trains: List<TrainDeparture>): Int {
    if (trains.isEmpty()) return 0
    val score = trains.map { if (it.cancelled) 1.0 else minOf(it.delayMinutes / 30.0, 1.0) }.average()
    return (score * 100).roundToInt()
}

fun planHome(busSeconds: List<Int>, trains: List<TrainDeparture>, now: LocalDateTime = LocalDateTime.now(LONDON)): HomePlanModel {
    val candidates = trains.filter { !it.cancelled && it.stops < 7 }.mapNotNull { t ->
        val dep = hhmm(t.expectedDeparture, now) ?: hhmm(t.scheduledDeparture, now) ?: return@mapNotNull null
        val sched = hhmm(t.scheduledDeparture, now) ?: dep
        PlanTrain(dep, t.stops, t.delayMinutes, hhmm(t.eta, now), trainPaid(sched))
    }.sortedBy { it.dep }

    // Ways to reach the platform, earliest first: walking now, then each reachable K2
    data class Way(val busIndex: Int?, val platform: LocalDateTime, val busMin: Int?, val leaveIn: Int?, val paid: Boolean)
    val ways = mutableListOf(Way(null, now.plusMinutes(WALK_TO_PLATFORM_MIN), null, null, false))
    busSeconds.withIndex()
        .filter { (_, s) -> s / 60 >= WALK_TO_STOP_MIN }   // can still reach the stop
        .take(2)
        .forEach { (i, s) ->
            val at = now.plusSeconds(s.toLong())
            ways.add(Way(i, at.plusMinutes(BUS_TO_PLATFORM_MIN), s / 60, (s / 60 - WALK_TO_STOP_MIN).toInt(), busPaid(at)))
        }

    val byWay = ways.associateWith { mutableListOf<PlanTrain>() }
    for (t in candidates) {
        // the latest way that still makes this train with the margin
        val w = ways.lastOrNull { !t.dep.isBefore(it.platform.plusMinutes(PLATFORM_MARGIN_MIN)) } ?: continue
        byWay[w]!!.add(t)
    }
    val groups = ways.map { w ->
        PlanGroup(w.busIndex, w.busMin, w.leaveIn, w.paid, byWay[w]!!.take(MAX_TRAINS_PER_GROUP))
    }.filter { it.trains.isNotEmpty() || it.busIndex != null }

    val shown = groups.flatMap { it.trains }
    val best = shown.filter { !it.paid && it.watArrival != null }.minByOrNull { it.watArrival!! }
        ?: shown.filter { it.watArrival != null }.minByOrNull { it.watArrival!! }
    best?.star = true
    return HomePlanModel(now, groups, serviceHealthPct(trains), best?.watArrival?.plusMinutes(WAT_TO_WORK_MIN))
}

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TEXT = Color(0xFFE0E0E0)
private val DIM = Color(0xFF5A5A5E)
private val LABEL = Color(0xFF8E8E93)
private val ACCENT = Color(0xFF4A9EFF)
private val WARN = Color(0xFFFF9500)
private val ERR = Color(0xFFFF3B30)

@Composable
fun HomePlanView(model: HomePlanModel, armedBusKey: String?, onBusTap: (String, Int) -> Unit, busSeconds: List<Int>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("HOME → WORK", color = LABEL, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(model.now.format(HM), color = LABEL, fontSize = 14.sp)
            val h = model.healthPct
            Text("● $h%", color = when { h >= 20 -> ERR; h >= 5 -> WARN; else -> Color(0xFF34C759) }, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        // Caption over the right-hand column of the train lines
        Text(" ".repeat(19) + "at work", color = DIM, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        for (g in model.groups) {
            val key = g.busIndex?.toString()
            val armed = key != null && key == armedBusKey
            val header = if (g.busIndex == null) "walk now" else
                "K2 ${g.busMin} min · " + (if (g.leaveInMin!! <= 0) "leave now"
                    else "leave in ${g.leaveInMin} @${model.now.plusMinutes(g.leaveInMin.toLong()).format(HM)}") +
                    (if (g.paid) "  £" else "")
            Text(header + if (armed) "  ⏰" else "",
                color = if (g.busIndex == null) DIM else if (armed) ACCENT else TEXT,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = if (key != null) Modifier.clickable { onBusTap(key, busSeconds.getOrElse(g.busIndex!!) { 0 }) } else Modifier)
            if (g.trains.isEmpty()) {
                Text("   no train within reach", color = DIM, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
            for (t in g.trains) {
                val grey = g.busIndex == null || t.paid
                // Left: how to read the train; right column: arrival at work
                val left = buildString {
                    append(if (g.busIndex == null) " w " else "   ")
                    append(t.dep.format(HM)).append(" ·").append(t.stops)
                    if (t.star) append(" ★")
                    if (t.delayMin > 0) append(" +").append(t.delayMin)
                    if (t.paid) append(" £")
                }
                val line = left.padEnd(19) + (t.watArrival?.plusMinutes(WAT_TO_WORK_MIN)?.format(HM) ?: "")
                Text(line, color = if (grey) DIM else TEXT, fontSize = 18.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = if (t.star) FontWeight.Bold else FontWeight.Normal)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
