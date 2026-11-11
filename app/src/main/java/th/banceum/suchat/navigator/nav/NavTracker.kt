package th.banceum.suchat.navigator.nav

import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route
import th.banceum.suchat.navigator.route.Step
import th.banceum.suchat.navigator.route.ThaiInstructions

/** สถานะการนำทาง ณ ตำแหน่งล่าสุด */
data class NavState(
    /** ช่วงที่กำลังเดินทางอยู่ (index ใน route.steps) */
    val stepIndex: Int,
    /** การเลี้ยวครั้งถัดไป (null = ช่วงสุดท้าย) */
    val next: Step?,
    /** ระยะถึงการเลี้ยวครั้งถัดไป (เมตร) */
    val toNext: Double,
    /** ระยะที่เหลือถึงจุดหมาย (เมตร) */
    val remaining: Double,
    /** เวลาที่เหลือโดยประมาณ (วินาที) */
    val remainingTime: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
    /** ระยะที่เดินทางมาแล้วตามแนวเส้นทาง (เมตร) ใช้หาจุดเตือนข้างหน้า */
    val along: Double = 0.0,
)

/** ผลของการอัปเดตตำแหน่งหนึ่งครั้ง */
data class NavUpdate(
    val state: NavState,
    /** ประโยคที่ควรพูด (null = ไม่ต้องพูด) */
    val speech: String? = null,
    /** ออกนอกเส้นทางติดต่อกันหลายครั้ง ควรหาเส้นทางใหม่ */
    val reroute: Boolean = false,
)

/**
 * ติดตามตำแหน่งเทียบกับเส้นทาง: หาช่วงปัจจุบัน ระยะถึงการเลี้ยว ตรวจออกนอกเส้นทาง และเลือกจังหวะพูด
 * ไม่ขึ้นกับ Android เพื่อให้ทดสอบด้วย unit test ได้
 */
class NavTracker(val route: Route) {
    private val cum = Geo.cumulative(route.points)
    private val total = cum.lastOrNull() ?: 0.0
    private var lastSeg = 0
    private var offCount = 0
    private var arrivedSpoken = false
    /** การเลี้ยวที่พูดไปแล้ว: step index → ระยะเตือนที่เล็กที่สุดที่พูดแล้ว */
    private val spoken = HashMap<Int, Double>()

    /** ระยะเตือนล่วงหน้า (เมตร) จากไกลไปใกล้ ค่าสุดท้าย = พูด "ตอนนี้" โดยไม่บอกระยะ */
    private val thresholds = when (route.profile) {
        Profile.CAR -> listOf(800.0, 300.0, 40.0)
        Profile.BIKE -> listOf(300.0, 100.0, 20.0)
        Profile.WALK -> listOf(150.0, 50.0, 12.0)
    }

    /** ระยะห่างจากเส้นที่ถือว่าออกนอกเส้นทาง */
    private val offLimit = if (route.profile == Profile.CAR) 50.0 else 30.0

    /** ประโยคเริ่มนำทาง */
    fun intro(): String {
        val first = route.steps.firstOrNull()
        val next = route.steps.getOrNull(1)
        val head = "เริ่มนำทาง ระยะทาง ${Geo.spokenDistance(route.distance)}"
        if (first == null) return head
        val road = ThaiInstructions.roadName(first.name)?.let { " ไปตาม $it" } ?: ""
        val then = next?.takeIf { it.type != ThaiInstructions.STRAIGHT }
            ?.let { " แล้ว${ThaiInstructions.spoken(it, Geo.spokenDistance(first.distance))}" } ?: ""
        return "$head ออกเดินทาง$road$then"
    }

    fun update(p: LatLon, accuracy: Float = 10f): NavUpdate {
        val pts = route.points
        // ค้นใกล้ ๆ ช่วงเดิมก่อน กันกระโดดไปเส้นที่วนกลับมาใกล้กัน; ถ้าห่างมากค่อยค้นทั้งเส้น
        var snap = Geo.snap(pts, p, lastSeg - 3, lastSeg + 150)
        if (snap == null || snap.distance > offLimit) {
            val all = Geo.snap(pts, p)
            if (all != null && (snap == null || all.distance < snap.distance)) snap = all
        }
        if (snap == null) return NavUpdate(state(0, total, false))

        val limit = maxOf(offLimit, accuracy * 1.5)
        val off = snap.distance > limit
        offCount = if (off) offCount + 1 else 0
        if (!off) lastSeg = snap.segment

        val seg = snap.segment
        val along = cum[seg] + snap.fraction * (cum[minOf(seg + 1, cum.size - 1)] - cum[seg])
        val remaining = (total - along).coerceAtLeast(0.0)
        val st = state(seg, along, off)

        if (!off && (remaining < 25 || Geo.distance(p, pts.last()) < 20)) {
            val arrived = st.copy(arrived = true, remaining = 0.0, remainingTime = 0.0, toNext = 0.0)
            val say = if (arrivedSpoken) null else "ถึงจุดหมายแล้ว".also { arrivedSpoken = true }
            return NavUpdate(arrived, speech = say)
        }
        return NavUpdate(st, speech = if (off) null else speechFor(st), reroute = offCount >= 3)
    }

    private fun state(seg: Int, along: Double, off: Boolean): NavState {
        val steps = route.steps
        var idx = steps.indexOfLast { it.start <= seg && seg < maxOf(it.end, it.start + 1) }
        if (idx < 0) idx = 0
        val next = steps.getOrNull(idx + 1)
        val toNext = if (next == null) total - along else (cum[next.start.coerceIn(0, cum.size - 1)] - along).coerceAtLeast(0.0)
        val remaining = (total - along).coerceAtLeast(0.0)
        val time = if (route.distance > 0) route.duration * remaining / route.distance else 0.0
        return NavState(idx, next, toNext, remaining, time, off, false, along)
    }

    private fun speechFor(st: NavState): String? {
        val next = st.next ?: return null
        // "ตรงไป" ไม่ต้องพูด (แสดงบนจอพอ) — พูดทุกช่วงถนนแล้วรบกวนผู้ขับ
        if (next.type == ThaiInstructions.STRAIGHT) return null
        val nextIdx = st.stepIndex + 1
        // ระยะเตือนที่เล็กที่สุดที่ยังครอบระยะปัจจุบัน
        val t = thresholds.lastOrNull { st.toNext <= it } ?: return null
        val done = spoken[nextIdx]
        if (done != null && done <= t) return null
        spoken[nextIdx] = t
        return if (t == thresholds.last()) ThaiInstructions.text(next)
        else ThaiInstructions.spoken(next, Geo.spokenDistance(st.toNext))
    }
}
