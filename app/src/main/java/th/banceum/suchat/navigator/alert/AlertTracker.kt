package th.banceum.suchat.navigator.alert

import th.banceum.suchat.navigator.geo.Geo
import th.banceum.suchat.navigator.geo.LatLon
import th.banceum.suchat.navigator.route.Profile
import th.banceum.suchat.navigator.route.Route
import kotlin.math.cos

/** จุดเตือนที่อยู่บนเส้นทาง พร้อมระยะจากต้นทางตามแนวเส้นทาง */
data class RouteAlert(val point: AlertPoint, val along: Double)

/** จุดเตือนถัดไปสำหรับแสดงบนจอ */
data class UpcomingAlert(val point: AlertPoint, val distance: Double)

/**
 * หาจุดเตือนที่อยู่บนเส้นทาง แล้วเลือกจังหวะพูดเมื่อใกล้ถึง (พูดครั้งเดียวต่อจุด)
 * - ใช้เฉพาะจุดที่ห่างเส้นทางไม่เกิน AlertKind.maxOffset (และอยู่ทางซ้ายสำหรับด่านชั่ง) กันจุดบนถนนคู่ขนาน/ฝั่งตรงข้าม
 * - จุดชนิดเดียวกันที่ห่างกันไม่เกิน [MERGE] เมตรตามแนวทาง (เช่น ด่านเก็บเงินหลายช่อง) รวมเป็นจุดเดียว
 * ไม่ขึ้นกับ Android เพื่อให้ทดสอบด้วย unit test ได้
 */
class AlertTracker(route: Route, points: List<AlertPoint>) {
    val alerts: List<RouteAlert> = onRoute(route, points)
    private val announced = HashSet<Int>()

    /** ระยะเตือนล่วงหน้า (เมตร) */
    private val announceAt = when (route.profile) {
        Profile.CAR -> 500.0
        Profile.BIKE -> 200.0
        Profile.WALK -> 100.0
    }

    /** จุดถัดไปที่ยังไม่ผ่าน ภายใน [within] เมตร */
    fun upcoming(along: Double, within: Double = 1000.0): UpcomingAlert? =
        alerts.firstOrNull { it.along >= along - PASSED }
            ?.let { UpcomingAlert(it.point, (it.along - along).coerceAtLeast(0.0)) }
            ?.takeIf { it.distance <= within }

    /** ประโยคที่ควรพูด ณ ระยะ [along] จากต้นทาง (null = ไม่ต้องพูด) */
    fun speech(along: Double): String? {
        val texts = mutableListOf<String>()
        alerts.forEachIndexed { i, a ->
            val d = a.along - along
            if (d < -PASSED || d > announceAt || i in announced) return@forEachIndexed
            announced += i
            texts += text(a.point, d.coerceAtLeast(0.0))
        }
        return texts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    companion object {
        /** ระยะห่างสูงสุดจากเส้นทางของทุกชนิด ใช้กรองด้วยกรอบสี่เหลี่ยม */
        private val MAX_OFFSET = AlertKind.entries.maxOf { it.maxOffset }
        const val MERGE = 250.0
        /** ผ่านจุดไปแล้วเกินระยะนี้ถือว่าเลยแล้ว ไม่ต้องพูด/แสดง */
        const val PASSED = 30.0

        fun text(p: AlertPoint, distance: Double): String {
            val head = if (distance < 60) "ข้างหน้า" else "อีก ${Geo.spokenDistance(distance)}"
            val body = when (p.kind) {
                AlertKind.SPEED_CAMERA -> "มีกล้องตรวจจับความเร็ว" +
                    (p.maxspeed?.let { " จำกัด $it กิโลเมตรต่อชั่วโมง" } ?: "")
                else -> p.kind.label + (p.name?.let { " $it" } ?: "")
            }
            return "$head $body"
        }

        fun onRoute(route: Route, points: List<AlertPoint>): List<RouteAlert> {
            val line = route.points
            if (line.size < 2 || points.isEmpty()) return emptyList()
            val cum = Geo.cumulative(line)
            // กรองด้วยกรอบสี่เหลี่ยมของเส้นทางก่อน (จุดทั้งประเทศมีหลายร้อย)
            val pad = MAX_OFFSET / 111_000.0
            val minLat = line.minOf { it.lat } - pad
            val maxLat = line.maxOf { it.lat } + pad
            val padLon = pad / cos(Math.toRadians((minLat + maxLat) / 2))
            val minLon = line.minOf { it.lon } - padLon
            val maxLon = line.maxOf { it.lon } + padLon
            val found = points
                .filter { it.pos.lat in minLat..maxLat && it.pos.lon in minLon..maxLon }
                .mapNotNull { p ->
                    val s = Geo.snap(line, p.pos) ?: return@mapNotNull null
                    if (s.distance > p.kind.maxOffset) return@mapNotNull null
                    if (p.kind.leftOnly && !isLeft(line[s.segment], line[s.segment + 1], p.pos)) return@mapNotNull null
                    val along = cum[s.segment] + s.fraction * (cum[s.segment + 1] - cum[s.segment])
                    RouteAlert(p, along)
                }
                .sortedBy { it.along }
            // รวมจุดชนิดเดียวกันที่ติดกัน เก็บจุดแรกที่จะถึง
            val merged = mutableListOf<RouteAlert>()
            for (a in found) {
                val prev = merged.lastOrNull { it.point.kind == a.point.kind }
                if (prev != null && a.along - prev.along <= MERGE) continue
                merged += a
            }
            return merged
        }

        /** จุด [p] อยู่ทางซ้ายของทิศทาง a→b หรือไม่ (ระนาบแบนรอบจุด, x = ตะวันออก, y = เหนือ) */
        fun isLeft(a: LatLon, b: LatLon, p: LatLon): Boolean {
            val k = cos(Math.toRadians(p.lat))
            val ax = (a.lon - p.lon) * k; val ay = a.lat - p.lat
            val bx = (b.lon - p.lon) * k; val by = b.lat - p.lat
            return (bx - ax) * (-ay) - (by - ay) * (-ax) > 0
        }
    }
}
