package dev.albert.calmerge.ui

import java.time.LocalDate
import java.time.ZoneId

/**
 * FR-20: pure layout engine for Day and Week time-grid views.
 *
 * Given a list of events (timed + all-day) and a set of dates to display, it:
 *  - Routes all-day events to an [AllDaySlot] strip at the top.
 *  - Splits multi-day timed events at midnight boundaries into per-day segments.
 *  - Packs overlapping segments using a greedy column-assignment sweep so they
 *    render side-by-side (classic calendar layout).
 *
 * All inputs use UTC epoch-millis for timed events and ISO LocalDate strings for all-day events,
 * matching [EventInstanceEntity] exactly. No Android framework references — fully JVM-testable.
 */
object TimeGridLayout {

    /** Raw input event for layout — only the fields the layout engine needs. */
    data class InputEvent(
        val id: String,
        val startUtc: Long?,
        val endUtc: Long?,
        val isAllDay: Boolean,
        val startDate: String?,
        val endDate: String?,
    )

    /** A timed event segment clipped to one calendar day. */
    data class TimedSlot(
        /** Refers back to the original [InputEvent.id]. */
        val eventId: String,
        /** The day this segment belongs to, used to place it in a column. */
        val date: LocalDate,
        /** Start fraction of the day [0..1), where 0 = midnight and 1 = next midnight. */
        val startFraction: Float,
        /** End fraction [0..1], clamped to 1 for segments that reach midnight. */
        val endFraction: Float,
        /** Zero-based column index within this day's overlap group. */
        val column: Int,
        /** Total number of parallel columns in this event's overlap cluster. */
        val columnCount: Int,
    )

    /** An all-day event occupying one or more days. */
    data class AllDaySlot(
        val eventId: String,
        /** First day (inclusive). */
        val startDate: LocalDate,
        /** Last day (inclusive) — for all-day events endDate in iCal is exclusive, so caller passes the exclusive end and we subtract 1 here internally. */
        val endDateInclusive: LocalDate,
        /** Row index in the all-day strip (for multi-event days). */
        val row: Int,
    )

    data class LayoutResult(
        val timedSlots: List<TimedSlot>,
        val allDaySlots: List<AllDaySlot>,
    )

    /**
     * Computes layout slots for all events across [dates].
     *
     * @param events  Mixed timed + all-day events.
     * @param dates   Ordered dates visible in the view (1 for Day view, 7 for Week view).
     * @param zone    Device timezone — used to convert UTC to local fractions.
     */
    fun layout(events: List<InputEvent>, dates: List<LocalDate>, zone: ZoneId): LayoutResult {
        val dateSet = dates.toSet()

        // ---- All-day events ------------------------------------------------
        val allDaySlots = layoutAllDay(events.filter { it.isAllDay }, dates)

        // ---- Timed events --------------------------------------------------
        // 1. Expand each event into per-day segments for visible dates.
        data class RawSegment(
            val eventId: String,
            val date: LocalDate,
            val startFrac: Float,
            val endFrac: Float,
        )

        val rawSegments = mutableListOf<RawSegment>()
        for (ev in events) {
            if (ev.isAllDay) continue
            val startMs = ev.startUtc ?: continue
            val endMs = ev.endUtc ?: startMs   // zero-duration if no end

            // Determine which dates the event spans and clip.
            for (date in dates) {
                val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val dayLenMs = (dayEndMs - dayStartMs).toFloat()

                // Event must overlap [dayStart, dayEnd).
                if (endMs <= dayStartMs || startMs >= dayEndMs) continue

                val clippedStart = maxOf(startMs, dayStartMs)
                val clippedEnd = minOf(endMs, dayEndMs)

                val startFrac = ((clippedStart - dayStartMs) / dayLenMs).coerceIn(0f, 1f)
                val endFrac = ((clippedEnd - dayStartMs) / dayLenMs).coerceIn(0f, 1f)

                rawSegments += RawSegment(ev.id, date, startFrac, endFrac)
            }
        }

        // 2. For each date, pack overlapping segments into columns.
        val timedSlots = mutableListOf<TimedSlot>()
        val byDate = rawSegments.groupBy { it.date }

        for (date in dates) {
            val segs = (byDate[date] ?: emptyList()).sortedWith(
                compareBy({ it.startFrac }, { -(it.endFrac - it.startFrac) })
            )
            if (segs.isEmpty()) continue

            // Greedy column assignment: each segment goes into the first column
            // whose last segment has already ended.
            val columns = mutableListOf<Float>()   // tracks the end-fraction of the last event in each column
            val assignedColumn = mutableListOf<Int>()

            for (seg in segs) {
                val col = columns.indexOfFirst { it <= seg.startFrac }
                val assigned = if (col == -1) {
                    columns += seg.endFrac
                    columns.size - 1
                } else {
                    columns[col] = seg.endFrac
                    col
                }
                assignedColumn += assigned
            }

            // Determine cluster column-counts: events in overlapping clusters
            // share the same columnCount = width of their cluster.
            // Build clusters by union-find on overlap.
            val n = segs.size
            val parent = IntArray(n) { it }
            fun find(x: Int): Int {
                var r = x; while (parent[r] != r) r = parent[r]
                var c = x; while (c != r) { val nx = parent[c]; parent[c] = r; c = nx }
                return r
            }
            fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

            for (i in segs.indices) {
                for (j in i + 1 until segs.size) {
                    if (segs[i].endFrac > segs[j].startFrac) union(i, j)
                }
            }

            // For each cluster, columnCount = max assigned column + 1.
            val clusterMaxCol = mutableMapOf<Int, Int>()
            for (i in segs.indices) {
                val root = find(i)
                clusterMaxCol[root] = maxOf(clusterMaxCol.getOrDefault(root, 0), assignedColumn[i])
            }

            for (i in segs.indices) {
                val seg = segs[i]
                val root = find(i)
                val colCount = (clusterMaxCol[root] ?: 0) + 1
                timedSlots += TimedSlot(
                    eventId = seg.eventId,
                    date = date,
                    startFraction = seg.startFrac,
                    endFraction = seg.endFrac,
                    column = assignedColumn[i],
                    columnCount = colCount,
                )
            }
        }

        return LayoutResult(timedSlots, allDaySlots)
    }

    private fun layoutAllDay(events: List<InputEvent>, dates: List<LocalDate>): List<AllDaySlot> {
        if (events.isEmpty()) return emptyList()

        data class AllDayRaw(
            val eventId: String,
            val start: LocalDate,
            val endInclusive: LocalDate,
        )

        val parsed = events.mapNotNull { ev ->
            val start = ev.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            // iCal all-day endDate is exclusive; missing = start+1 day; inclusive = exclusive - 1.
            val endExclusive = ev.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: start.plusDays(1)
            val endInclusive = endExclusive.minusDays(1)
            if (endInclusive < start) return@mapNotNull null
            AllDayRaw(ev.id, start, endInclusive)
        }

        val visibleDates = dates.toSortedSet()
        val minDate = visibleDates.first()
        val maxDate = visibleDates.last()

        // Only include events that overlap the visible range.
        val visible = parsed.filter { it.endInclusive >= minDate && it.start <= maxDate }
            .sortedWith(compareBy({ it.start }, { it.endInclusive }))

        // Assign rows: each event goes into the first row where the last event ended.
        val rowEnds = mutableListOf<LocalDate>()
        val slots = mutableListOf<AllDaySlot>()
        for (ev in visible) {
            val row = rowEnds.indexOfFirst { it < ev.start }
            val assignedRow = if (row == -1) {
                rowEnds += ev.endInclusive
                rowEnds.size - 1
            } else {
                rowEnds[row] = ev.endInclusive
                row
            }
            slots += AllDaySlot(
                eventId = ev.eventId,
                startDate = ev.start,
                endDateInclusive = ev.endInclusive,
                row = assignedRow,
            )
        }
        return slots
    }
}
